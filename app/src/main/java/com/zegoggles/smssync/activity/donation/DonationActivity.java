package com.zegoggles.smssync.activity.donation;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponse;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchasesResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.zegoggles.smssync.BuildConfig;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.donation.DonationListFragment.SkuSelectionListener;
import com.zegoggles.smssync.utils.BundleBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.widget.Toast.LENGTH_LONG;
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
import static com.zegoggles.smssync.activity.donation.DonationListFragment.SKUS;

public class DonationActivity extends AppCompatActivity implements
        SkuDetailsResponseListener,
        PurchasesUpdatedListener,
        SkuSelectionListener {

    public interface DonationStatusListener {
        enum State {
            DONATED,
            NOT_DONATED,
            UNKNOWN,
            NOT_AVAILABLE
        }
        void userDonationState(State state);
    }

    static boolean DEBUG_IAB = BuildConfig.DEBUG;
    private @Nullable BillingClient billingClient;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        billingClient = BillingClient.newBuilder(this).setListener(this).build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(@BillingResponse int resultCode) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "onBillingSetupFinished(" + resultCode + ")" + Thread.currentThread().getName());
                }
                switch (resultCode) {
                    case OK:
                        queryAvailableSkus();
                        break;
                    default:
                        Toast.makeText(DonationActivity.this, R.string.donation_error_iab_unavailable, LENGTH_LONG).show();
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
        if (billingClient != null) {
            billingClient.endConnection();
            billingClient = null;
        }
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

    @Override
    public void onPurchasesUpdated(@BillingResponse int responseCode, @Nullable List<Purchase> purchases) {
        log("onIabPurchaseFinished(" + responseCode + ", " + purchases);
        if (responseCode == OK) {
            Toast.makeText(this,
                    R.string.ui_donation_success_message,
                    LENGTH_LONG)
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
                LENGTH_LONG)
                .show();
        }
        finish();
    }

    @Override
    public void selectedSku(String sku) {
        if (billingClient == null) return;
        billingClient.launchBillingFlow(this, BillingFlowParams.newBuilder()
                .setType(INAPP)
                .setSku(sku)
                .build()
        );
    }

    private void queryAvailableSkus() {
        if (billingClient == null) return;
        billingClient.querySkuDetailsAsync(SkuDetailsParams.newBuilder()
                .setType(INAPP)
                .setSkusList(Arrays.asList(ALL_SKUS))
                .build(), this);
    }

    private void showSelectDialog(List<SkuDetails> skuDetails) {
        if (billingClient == null) return;
        ArrayList<Sku> skus = new ArrayList<Sku>(skuDetails.size());
        for (SkuDetails detail : skuDetails) {
            skus.add(new Sku(detail));
        }
        final DonationListFragment donationList = new DonationListFragment();
        donationList.setArguments(new BundleBuilder().putParcelableArrayList(SKUS, skus).build());
        donationList.show(getSupportFragmentManager(), null);
    }

    private static void log(String s) {
        if (DEBUG_IAB) {
            Log.d(TAG, s);
        }
    }

    public static void checkUserDonationStatus(Context context,
                                               final DonationStatusListener listener) {
        final BillingClient helper = BillingClient.newBuilder(context).setListener(new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(@BillingResponse int responseCode, @Nullable List<Purchase> purchases) {
                log("onPurchasesUpdated("+responseCode+", "+purchases+")");
            }
        }).build();
        helper.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@BillingResponse int resultCode) {
                    log("checkUserHasDonated: onBillingSetupFinished("+resultCode+")");
                try {
                    if (resultCode == OK) {
                        PurchasesResult result = helper.queryPurchases(INAPP);
                        if (result.getResponseCode() == OK) {
                            listener.userDonationState(userHasDonated(result.getPurchasesList()) ? DONATED : NOT_DONATED);
                        } else {
                            listener.userDonationState(UNKNOWN);
                        }
                    } else {
                        listener.userDonationState(resultCode == BILLING_UNAVAILABLE ? NOT_AVAILABLE : UNKNOWN);
                    }
                } finally {
                    try {
                        helper.endConnection();
                    } catch (Exception ignored) {
                        Log.w(TAG, "ignoring error during endConnection()", ignored);
                    }
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
}

