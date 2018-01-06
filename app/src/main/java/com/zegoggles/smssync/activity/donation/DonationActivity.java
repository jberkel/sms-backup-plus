package com.zegoggles.smssync.activity.donation;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.support.annotation.Nullable;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponse;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.zegoggles.smssync.BuildConfig;
import com.zegoggles.smssync.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.android.billingclient.api.BillingClient.BillingResponse.BILLING_UNAVAILABLE;
import static com.android.billingclient.api.BillingClient.BillingResponse.ITEM_ALREADY_OWNED;
import static com.android.billingclient.api.BillingClient.BillingResponse.ITEM_UNAVAILABLE;
import static com.android.billingclient.api.BillingClient.BillingResponse.OK;
import static com.android.billingclient.api.BillingClient.BillingResponse.USER_CANCELED;
import static com.android.billingclient.api.BillingClient.SkuType.INAPP;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.Consts.Billing.ALL_SKUS;
import static com.zegoggles.smssync.Consts.Billing.DONATION_PREFIX;
import static com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener.State.DONATED;
import static com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener.State.NOT_AVAILABLE;
import static com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener.State.NOT_DONATED;
import static com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener.State.UNKNOWN;

public class DonationActivity extends Activity implements SkuDetailsResponseListener, PurchasesUpdatedListener {
    private static boolean DEBUG_IAB = BuildConfig.DEBUG;
    private @Nullable BillingClient billingClient;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        billingClient = BillingClient.newBuilder(this).setListener(this).build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@BillingResponse int resultCode) {
                Log.d(TAG, "onBillingSetupFinished(" + resultCode + ")" + Thread.currentThread().getName());
                switch (resultCode) {
                    case OK:
                        queryAvailableSkus();
                        break;
                    default:
                        String message = getString(R.string.donation_error_iab_unavailable);
                        Toast.makeText(DonationActivity.this, message, Toast.LENGTH_LONG).show();
                        Log.w(TAG, "Problem setting up in-app billing: " + resultCode);
                        finish();
                        break;
                }
            }
            @Override
            public void onBillingServiceDisconnected() {
                Log.d(TAG, "onBillingServiceDisconnected");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        billingClient = null;
    }

    @Override
    public void onSkuDetailsResponse(@BillingResponse int responseCode, List<SkuDetails> details) {
        log("onSkuDetailsResponse(" + responseCode + ", " + details + ")");

        if (responseCode != OK) {
            Log.w(TAG, "failed to query inventory: " + responseCode);
            return;
        }

        List<SkuDetails> skuDetailsList = new ArrayList<SkuDetails>();
        for (SkuDetails d : details) {
            if (d.getSku().startsWith(DONATION_PREFIX)) {
                skuDetailsList.add(d);
            }
        }

        if (!isFinishing()) {
            showSelectDialog(skuDetailsList);
        } else {
            finish();
        }
    }

    private void queryAvailableSkus() {
        if (billingClient == null) return;
        billingClient.querySkuDetailsAsync(SkuDetailsParams.newBuilder()
                .setType(INAPP)
                .setSkusList(Arrays.asList(ALL_SKUS))
                .build(), DonationActivity.this);
    }

    private void showSelectDialog(List<SkuDetails> skuDetails) {
        if (billingClient == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final List<SkuDetails> skus = new ArrayList<SkuDetails>(skuDetails);
        Collections.sort(skus, SkuComparator.INSTANCE);
        //noinspection ConstantConditions
        List<String> options = new ArrayList<String>();
        if (DEBUG_IAB) {
            for (TestSkus.TestSku testSku : TestSkus.SKUS) {
                options.add(testSku.getDescription());
            }
        }
        for (final SkuDetails sku : skus) {
            String item = sku.getTitle();
            if (!TextUtils.isEmpty(sku.getPrice())) {
                item += "  " + sku.getPrice();
            }
            options.add(item);
        }
        String[] items = new String[options.size()];
        options.toArray(items);
        builder.setTitle(R.string.ui_dialog_donate_message)
               .setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedSku;
                if (DEBUG_IAB) {
                    if (which < TestSkus.SKUS.length) {
                        selectedSku = TestSkus.SKUS[which].getSku();
                    } else {
                        selectedSku = skus.get(which - TestSkus.SKUS.length).getSku();
                    }
                } else {
                    selectedSku = skus.get(which).getSku();
                }
                billingClient.launchBillingFlow(DonationActivity.this, BillingFlowParams.newBuilder()
                        .setType(INAPP)
                        .setSku(selectedSku)
                        .build()
                );
            }
        }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                finish();
            }
        })
          .show();
    }

    @Override
    public void onPurchasesUpdated(@BillingResponse int responseCode, @Nullable List<Purchase> purchases) {
        log("onIabPurchaseFinished(" + responseCode + ", " + purchases);
        if (responseCode == OK) {
            Toast.makeText(this,
                    R.string.ui_donation_success_message,
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            String message;
            switch (responseCode) {
                case ITEM_UNAVAILABLE:
                    message = getString(R.string.donation_error_unavailable);
                    break;
                case ITEM_ALREADY_OWNED:
                    message = getString(R.string.donation_error_already_owned);
                    break;
                case USER_CANCELED:
                    message = getString(R.string.donation_error_canceled);
                    break;
                default:
                    message = getString(R.string.donation_unspecified_error, responseCode);
            }
            Toast.makeText(this,
                    getString(R.string.ui_donation_failure_message, message),
                    Toast.LENGTH_LONG)
                    .show();
        }
        finish();
    }

    private void log(String s) {
        if (DEBUG_IAB) {
            Log.d(TAG, s);
        }
    }

    public interface DonationStatusListener {
        enum State {
            DONATED,
            NOT_DONATED,
            UNKNOWN,
            NOT_AVAILABLE
        }
        void userDonationState(State state);
    }

    public static void checkUserHasDonated(Context context, final DonationStatusListener listener) {
        final BillingClient helper = BillingClient.newBuilder(context).setListener(new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(int responseCode, List<Purchase> purchases) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "onPurchasesUpdated(" + responseCode + ")");
                }
            }
        }).build();
        helper.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@BillingResponse int resultCode) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "checkUserHasDonated: onBillingSetupFinished("+resultCode+")");
                }
                if (resultCode == OK) {
                    Purchase.PurchasesResult result = helper.queryPurchases(INAPP);
                    if (result.getResponseCode() == OK) {
                        listener.userDonationState(userHasDonated(result.getPurchasesList()) ? DONATED : NOT_DONATED);
                    } else {
                        listener.userDonationState(UNKNOWN);
                    }
                } else {
                    listener.userDonationState(resultCode == BILLING_UNAVAILABLE ? NOT_AVAILABLE : UNKNOWN);
                }
                try {
                    helper.endConnection();
                } catch (Exception ignored) {
                }
            }
            public void onBillingServiceDisconnected() {
            }
        });
    }

    private static boolean userHasDonated(List<Purchase> result) {
        for (String sku : ALL_SKUS) {
            for (Purchase purchase : result) {
                if (purchase.getSku().equals(sku)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class SkuComparator implements Comparator<SkuDetails> {
        static final SkuComparator INSTANCE = new SkuComparator();

        @Override
        public int compare(SkuDetails lhs, SkuDetails rhs) {
            if (lhs.getPrice() != null && rhs.getPrice() != null) {
                return lhs.getPrice().compareTo(rhs.getPrice());
            } else if (lhs.getTitle() != null && rhs.getTitle() != null) {
                return lhs.getTitle().compareTo(rhs.getTitle());
            } else if (lhs.getSku() != null && rhs.getSku() != null) {
                return lhs.getSku().compareTo(rhs.getSku());
            } else {
                return 0;
            }
        }
    }
}

