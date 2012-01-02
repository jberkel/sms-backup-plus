package com.zegoggles.smssync;

import java.math.BigDecimal;

import android.content.Intent;
import android.content.DialogInterface;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Dialog;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.preference.PreferenceManager;

import com.flattr4android.sdk.FlattrSDK;
import com.flattr4android.sdk.FlattrSDKException;
import com.paypal.android.MEP.PayPal;
import com.paypal.android.MEP.PayPalPayment;
import com.paypal.android.MEP.PayPalInvoiceItem;
import com.paypal.android.MEP.PayPalInvoiceData;
import com.paypal.android.MEP.PayPalResultDelegate;
import com.paypal.android.MEP.CheckoutButton;

import static com.zegoggles.smssync.App.*;

final class Donations implements PayPalResultDelegate, java.io.Serializable {
  /** @noinspection UnusedDeclaration*/
  public static final long serialVersionUID = 0L;

  private static final String DEFAULT_CURRENCY = "USD";
  private static SmsSync sContext;

  private transient NumberPicker mNumberPicker;
  private transient Spinner mCurrency;
  private transient Dialog mDonateDialog;

  static final String PREF_DONATED = "donated";

  public Donations(SmsSync context) {
    sContext = context;
  }

  public boolean hasUserDonated() {
    return PreferenceManager.getDefaultSharedPreferences(sContext)
                            .getBoolean(PREF_DONATED, false);
  }

  private void userDonated() {
      PreferenceManager.getDefaultSharedPreferences(sContext)
        .edit().putBoolean(PREF_DONATED, true).commit();
  }

  /** @noinspection UnusedDeclaration*/
  public void flattr() {
    if (LOCAL_LOGV) Log.v(TAG, "flattr()");
    sContext.runOnUiThread(new Runnable() {
      public void run() {
        try {
          FlattrSDK.displayThing(sContext, sContext.getString(R.string.donation_flattr_thing_id));
        } catch (FlattrSDKException e) {
          Log.e(TAG, "error", e);
        }
      }
    });
  }

  /** @noinspection UnusedDeclaration*/
  public void paypal() {
    if (LOCAL_LOGV) Log.v(TAG, "paypal()");

    NetworkInfo active = getConnectivityManager().getActiveNetworkInfo();
    if (active != null && active.isConnectedOrConnecting()) {
      if (PayPal.getInstance() == null || mDonateDialog == null) {
        final Dialog progress = getProgressDialog();

        sContext.runOnUiThread(new Runnable() {
          public void run() { progress.show(); }
        });

        new Thread() {
          public void run() {
            initLibrary();

            sContext.runOnUiThread(new Runnable() {
              public void run() {
                progress.dismiss();
                mDonateDialog = getDonateDialog();
                mDonateDialog.show();
              }
            });
          }
        }.start();
      } else {
        if (LOCAL_LOGV) Log.v(TAG, "PayPal already initialised");

        sContext.runOnUiThread(new Runnable() {
          public void run() { mDonateDialog.show(); }
        });
      }
    } else {
      // oh noez, no network
      sContext.runOnUiThread(new Runnable() {
        public void run() {
          Toast.makeText(sContext,
                   sContext.getString(R.string.ui_donation_oh_noez_no_network),
                   Toast.LENGTH_LONG).show();
        }
      });
    }
  }

  private ConnectivityManager getConnectivityManager() {
    return (ConnectivityManager) sContext.getSystemService(Context.CONNECTIVITY_SERVICE);
  }

  private Dialog getProgressDialog() {
    if (LOCAL_LOGV) Log.v(TAG, "getProgressDialog()");

    final ProgressDialog prep = new ProgressDialog(sContext);
    prep.setTitle(null);
    prep.setMessage(sContext.getString(R.string.ui_dialog_donate_prepare_message));
    prep.setIndeterminate(true);
    prep.setCancelable(false);
    return prep;
  }

  private Dialog getDonateDialog() {
    if (LOCAL_LOGV) Log.v(TAG, "getDonateDialog()");

    final LinearLayout layout = (LinearLayout) sContext.getLayoutInflater().inflate(R.layout.donate_dialog,
                                (ViewGroup) sContext.findViewById(R.id.donate_dialog_layout));
    final ArrayAdapter<CharSequence> adapter =
      ArrayAdapter.createFromResource(sContext, R.array.donation_currencies,
                                      android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    mNumberPicker = (NumberPicker) layout.findViewById(R.id.amount);
    mCurrency = (Spinner) layout.findViewById(R.id.currency);
    mCurrency.setAdapter(adapter);

    layout.addView(getDonateButton());
    return new AlertDialog.Builder(sContext)
      .setTitle(R.string.donation)
      .setMessage(R.string.ui_dialog_donate_message)
      .setPositiveButton(R.string.donate, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
           showPaypal();
        }
      })
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
           dialog.cancel();
        }
      })
      .setView(layout)
      .create();
  }

  private BigDecimal getDonationAmount() {
    return new BigDecimal(mNumberPicker.getValue());
  }

  private String getCurrencyType() {
    if (mCurrency == null) return DEFAULT_CURRENCY;

    if (mCurrency.getSelectedItem() == null) {
     return mCurrency.getItemAtPosition(0).toString();
    } else {
     return mCurrency.getSelectedItem().toString();
    }
  }

  private void showPaypal() {
    if (LOCAL_LOGV) Log.v(TAG, "showPaypal()");
    sContext.runOnUiThread(new Runnable() {
      public void run() {
        if (mDonateDialog != null) {
            mDonateDialog.cancel();
            mDonateDialog = null;
        }

        final BigDecimal amount = getDonationAmount();
        final PayPalPayment payment = new PayPalPayment();

        payment.setCurrencyType(getCurrencyType());
        payment.setPaymentType(PayPal.PAYMENT_TYPE_PERSONAL);
        payment.setMerchantName(sContext.getString(R.string.donation_merchant_name));
        payment.setRecipient(sContext.getString(R.string.donation_recipient));
        payment.setDescription(String.format("Version: %s (%s, %s)",
                                             PrefStore.getVersion(sContext, false),
                                             Build.MODEL,
                                             Build.VERSION.RELEASE));

        payment.setSubtotal(amount);

        final PayPalInvoiceData invoice = new PayPalInvoiceData();
        final PayPalInvoiceItem item = new PayPalInvoiceItem();

        item.setName(sContext.getString(R.string.donation_item_name));
        item.setID(sContext.getString(R.string.donation_item_id));
        item.setTotalPrice(amount);
        item.setUnitPrice(amount);
        item.setQuantity(1);

        invoice.getInvoiceItems().add(item);
        payment.setInvoiceData(invoice);

        Intent checkoutIntent = PayPal.getInstance().checkout(payment, sContext, Donations.this);
        sContext.startActivityForResult(checkoutIntent, 1);
    }
    });
  }

  private synchronized void initLibrary() {
    PayPal pp = PayPal.getInstance();
    if (pp == null) {
        if (LOCAL_LOGV) Log.v(TAG, "initLibrary()");

        try {
          final long start = System.currentTimeMillis();

          pp = PayPal.initWithAppID(sContext, sContext.getString(R.string.donation_paypal_app_id),
                                    PayPal.ENV_LIVE);
          pp.setFeesPayer(PayPal.FEEPAYER_EACHRECEIVER);
          pp.setShippingEnabled(false);
          pp.setDynamicAmountCalculationEnabled(false);

          Log.d(TAG, String.format("PayPal initialised in %d ms", System.currentTimeMillis() - start));
        } catch (Exception e) {
          Log.e(TAG, "error initialising PayPal", e);
        }
    }
  }

  private CheckoutButton getDonateButton() {
      if (PayPal.getInstance() == null) throw new IllegalStateException("Paypal not initialised");
      final CheckoutButton button = PayPal.getInstance().getCheckoutButton(sContext, PayPal.BUTTON_152x33,
                                    CheckoutButton.TEXT_PAY);

      button.setOnClickListener(new OnClickListener() {
        public void onClick(View v) {
          if (LOCAL_LOGV) Log.v(TAG, "getButton#onClick()");
          showPaypal();
        }
      });
      return button;
  }

  // paypal callbacks
  public void onPaymentSucceeded(final String payKey, final String paymentStatus) {
    Log.d(TAG, String.format("onPaymentSucceeded(%s, %s)", payKey, paymentStatus));
    sContext.runOnUiThread(new Runnable() {
      public void run() {
        userDonated();
        sContext.remove(SmsSync.Dialogs.ABOUT);
        Toast.makeText(sContext,
                       sContext.getString(R.string.ui_donation_success_message),
                       Toast.LENGTH_LONG).show();
      }
    });
  }

  public void onPaymentFailed(final String paymentStatus, final String correlationID,
   final String payKey, final String errorID, final String errorMessage) {
   Log.d(TAG, String.format("onPaymentFailed(%s, %s, %s, %s, %s)", paymentStatus, correlationID,
                            payKey, errorID, errorMessage));

    sContext.runOnUiThread(new Runnable() {
      public void run() {
        Toast.makeText(sContext,
                       sContext.getString(R.string.ui_donation_failure_message, errorMessage),
                       Toast.LENGTH_LONG).show();
      }
    });

  }

  public void onPaymentCanceled(final String paymentStatus) {
    Log.d(TAG, String.format("onPaymentCanceled(%s)",  paymentStatus));
    sContext.runOnUiThread(new Runnable() {
      public void run() {
        Toast.makeText(sContext,
                       sContext.getString(R.string.ui_donation_canceled_message),
                       Toast.LENGTH_LONG).show();
      }
    });
  }
}
