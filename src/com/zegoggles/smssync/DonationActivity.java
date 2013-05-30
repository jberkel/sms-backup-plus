package com.zegoggles.smssync;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import com.github.jberkel.pay.me.IabHelper;
import com.github.jberkel.pay.me.IabResult;
import com.github.jberkel.pay.me.listener.OnConsumeFinishedListener;
import com.github.jberkel.pay.me.listener.OnIabPurchaseFinishedListener;
import com.github.jberkel.pay.me.listener.OnIabSetupFinishedListener;
import com.github.jberkel.pay.me.listener.QueryInventoryFinishedListener;
import com.github.jberkel.pay.me.model.Inventory;
import com.github.jberkel.pay.me.model.ItemType;
import com.github.jberkel.pay.me.model.Purchase;
import com.github.jberkel.pay.me.model.SkuDetails;
import com.github.jberkel.pay.me.model.TestSkus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.github.jberkel.pay.me.Response.BILLING_UNAVAILABLE;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.BillingConsts.*;
import static com.zegoggles.smssync.DonationActivity.DonationStatusListener.State;

public class DonationActivity extends Activity implements
        QueryInventoryFinishedListener,
        OnIabPurchaseFinishedListener {

    private static boolean DEBUG_IAB = BuildConfig.DEBUG;
    private static final int PURCHASE_REQUEST = 1;

    private IabHelper mIabHelper;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIabHelper = new IabHelper(this, PUBLIC_KEY);
        mIabHelper.enableDebugLogging(DEBUG_IAB);

        mIabHelper.startSetup(new OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    String message;
                    switch (result.getResponse()) {
                        case BILLING_UNAVAILABLE:
                            message = getString(R.string.donation_error_iab_unavailable);
                            break;
                        default:
                            message = result.getMessage();
                    }

                    Toast.makeText(DonationActivity.this, message, Toast.LENGTH_LONG).show();
                    Log.w(TAG, "Problem setting up in-app billing: " + result);
                    finish();
                } else if (mIabHelper != null) {
                    List<String> moreSkus = new ArrayList<String>();
                    Collections.addAll(moreSkus, BillingConsts.ALL_SKUS);
                    mIabHelper.queryInventoryAsync(true, moreSkus, null, DonationActivity.this);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mIabHelper != null) {
            mIabHelper.dispose();
            mIabHelper = null;
        }
    }

    @Override
    public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
        log("onQueryInventoryFinished(" + result + ", " + inventory + ")");
        if (result.isFailure()) {
            Log.w(TAG, "failed to query inventory: " + result);
            return;
        }

        List<SkuDetails> skuDetailsList = new ArrayList<SkuDetails>();
        for (SkuDetails d : inventory.getSkuDetails()) {
            if (d.getSku().startsWith(DONATION_PREFIX)) {
                skuDetailsList.add(d);
            }
        }
        if (DEBUG_IAB) {
            Purchase testPurchase = inventory.getPurchase(TestSkus.PURCHASED.getSku());
            if (testPurchase != null) {
                mIabHelper.consumeAsync(testPurchase, new OnConsumeFinishedListener() {
                    @Override
                    public void onConsumeFinished(Purchase purchase, IabResult result) {
                        Log.d(TAG, "onConsumeFinished:" + purchase + ", " + result);
                    }
                });
            }
        }

        if (!isFinishing() && !userHasDonated(inventory)) {
            showSelectDialog(skuDetailsList);
        } else {
            finish();
        }
    }


    private void showSelectDialog(List<SkuDetails> skuDetails) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        final List<SkuDetails> skus = new ArrayList<SkuDetails>(skuDetails);
        Collections.sort(skus, SkuComparator.INSTANCE);
        //noinspection ConstantConditions
        if (DEBUG_IAB) {
            skus.add(TestSkus.PURCHASED);
            skus.add(TestSkus.CANCELED);
            skus.add(TestSkus.UNAVAILABLE);
            skus.add(TestSkus.REFUNDED);
        }
        String[] items = new String[skus.size()];
        for (int i = 0; i < skus.size(); i++) {
            final SkuDetails sku = skus.get(i);

            String item = sku.getTitle();
            if (!TextUtils.isEmpty(sku.getPrice())) {
                item += "  " + sku.getPrice();
            }
            items[i] = item;
        }

        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mIabHelper.launchPurchaseFlow(DonationActivity.this,
                        skus.get(which).getSku(),
                        ItemType.INAPP,
                        PURCHASE_REQUEST,
                        DonationActivity.this,
                        null);
            }
        });
        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });

        builder.setTitle(R.string.ui_dialog_donate_message)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        log("onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (!mIabHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        } else {
            log("onActivityResult handled by IABUtil.");
        }
    }

    @Override
    public void onIabPurchaseFinished(IabResult result, Purchase info) {
        log("onIabPurchaseFinished(" + result + ", " + info);
        if (result.isSuccess()) {
            Toast.makeText(this,
                    R.string.ui_donation_success_message,
                    Toast.LENGTH_LONG)
                    .show();
        } else {
            String message;
            switch (result.getResponse()) {
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
                    message = result.getMessage();
            }
            Toast.makeText(this,
                    getString(R.string.ui_donation_failure_message, message),
                    Toast.LENGTH_LONG)
                    .show();
        }
        finish();
    }

    private static boolean userHasDonated(Inventory inventory) {
        for (String sku : ALL_SKUS) {
            if (inventory.hasPurchase(sku)) return true;
        }
        return false;
    }

    private void log(String s) {
        if (DEBUG_IAB) {
            Log.d(TAG, s);
        }
    }

    public static interface DonationStatusListener {
        public enum State {
            DONATED,
            NOT_DONATED,
            UNKNOWN,
            NOT_AVAILABLE
        }

        void userDonationState(State s);
    }

    public static void checkUserHasDonated(Context c, final DonationStatusListener l) {
        if (Build.VERSION.SDK_INT < 8) {
            l.userDonationState(State.NOT_AVAILABLE);
            return;
        }

        final IabHelper helper = new IabHelper(c, PUBLIC_KEY);
        helper.startSetup(new OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (result.isSuccess()) {
                    helper.queryInventoryAsync(new QueryInventoryFinishedListener() {
                        @Override
                        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                            try {
                                if (result.isSuccess()) {
                                    final State s = userHasDonated(inv) ? State.DONATED : State.NOT_DONATED;
                                    l.userDonationState(s);
                                } else {
                                    l.userDonationState(State.UNKNOWN);
                                }
                            } finally {
                                helper.dispose();
                            }
                        }
                    });
                } else {
                    l.userDonationState(result.getResponse() == BILLING_UNAVAILABLE ? State.NOT_AVAILABLE : State.UNKNOWN);
                    helper.dispose();
                }
            }
        });
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