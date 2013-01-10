package com.zegoggles.smssync;

import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.DonationActivity.DonationStatusListener.State;
import static com.zegoggles.smssync.billing.BillingConsts.*;
import static com.zegoggles.smssync.billing.IabHelper.*;

import com.zegoggles.smssync.billing.BillingConsts;
import com.zegoggles.smssync.billing.IabHelper;
import com.zegoggles.smssync.billing.IabResult;
import com.zegoggles.smssync.billing.Inventory;
import com.zegoggles.smssync.billing.Purchase;
import com.zegoggles.smssync.billing.SkuDetails;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DonationActivity extends Activity implements
        IabHelper.QueryInventoryFinishedListener,
        IabHelper.OnIabPurchaseFinishedListener {

    private static boolean DEBUG_IAB = BuildConfig.DEBUG;
    private static final int PURCHASE_REQUEST = 1;

    private IabHelper mIabHelper;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIabHelper = new IabHelper(this, BillingConsts.PUBLIC_KEY);
        mIabHelper.enableDebugLogging(DEBUG_IAB);

        mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    String message;
                    switch (result.getResponse()) {
                        case BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE:
                            message = getString(R.string.donation_error_iab_unavailable);
                            break;
                        default:
                            message = result.getMessage();
                    }

                    Toast.makeText(DonationActivity.this, message, Toast.LENGTH_LONG).show();
                    Log.w(TAG, "Problem setting up in-app billing: " + result);
                    finish();
                    return;
                }
                List<String> moreSkus = new ArrayList<String>();
                Collections.addAll(moreSkus, BillingConsts.ALL_SKUS);
                mIabHelper.queryInventoryAsync(true, moreSkus, DonationActivity.this);
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
        log("onQueryInventoryFinished(" + result + ", " + inventory);
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

        if (!userHasDonated(inventory)) {
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
            skus.add(new SkuDetails(SKU_ANDROID_TEST_PURCHASED, null ,null,   "Test (purchased)", null));
            skus.add(new SkuDetails(SKU_ANDROID_TEST_CANCELED, null ,null,    "Test (canceled)", null));
            skus.add(new SkuDetails(SKU_ANDROID_TEST_UNAVAILABLE, null ,null, "Test (unvailable)", null));
            skus.add(new SkuDetails(SKU_ANDROID_TEST_REFUNDED, null ,null,    "Test (refunded)", null));
        }
        String[] items = new String[skus.size()];
        for (int i = 0; i<skus.size(); i++) {
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
                PURCHASE_REQUEST,
                DonationActivity.this);
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
        log( "onActivityResult(" + requestCode + "," + resultCode + "," + data);
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
                case BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE:
                    message = getString(R.string.donation_error_unavailable);
                    break;
                case BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED:
                    message = getString(R.string.donation_error_already_owned);
                    break;
                case IABHELPER_USER_CANCELLED:
                case BILLING_RESPONSE_RESULT_USER_CANCELED:
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
            UNKNOWN
        }
        void userDonationState(State s);
    }

    public static void checkUserHasDonated(Context c, final DonationStatusListener l) {
        final IabHelper helper = new IabHelper(c, PUBLIC_KEY);
        helper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (result.isSuccess()) {
                    helper.queryInventoryAsync(new IabHelper.QueryInventoryFinishedListener() {
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
                    l.userDonationState(State.UNKNOWN);
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