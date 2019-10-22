package com.zegoggles.smssync.activity.donation;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.Purchase.PurchasesResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.zegoggles.smssync.BuildConfig;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.ThemeActivity;
import com.zegoggles.smssync.activity.donation.DonationListFragment.SkuSelectionListener;
import com.zegoggles.smssync.utils.BundleBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.widget.Toast.LENGTH_LONG;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.*;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.BILLING_UNAVAILABLE;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.ITEM_NOT_OWNED;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.ITEM_UNAVAILABLE;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.OK;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.SERVICE_DISCONNECTED;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED;
import static com.android.billingclient.api.BillingClient.SkuType.INAPP;
import static com.android.billingclient.api.Purchase.PurchaseState.PURCHASED;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.Consts.Billing.ALL_SKUS;
import static com.zegoggles.smssync.Consts.Billing.DONATION_PREFIX;
import static com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener.State.DONATED;
import static com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener.State.NOT_AVAILABLE;
import static com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener.State.NOT_DONATED;
import static com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener.State.UNKNOWN;
import static com.zegoggles.smssync.activity.donation.DonationListFragment.SKUS;

public class DonationActivity extends ThemeActivity implements
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

    private static boolean DEBUG_IAB = BuildConfig.DEBUG;
    private @Nullable BillingClient billingClient;
    private boolean stateSaved;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        billingClient = BillingClient.newBuilder(this).setListener(this).enablePendingPurchases().build();
        billingClient.startConnection(new BillingClientStateListener() {

            @Override public void onBillingSetupFinished(BillingResult resultCode) {
                log("onBillingSetupFinished(" + resultCode + ")" + Thread.currentThread().getName());

                switch (resultCode.getResponseCode()) {
                    case OK:
                        queryAvailableSkus();
                        break;

                    case BILLING_UNAVAILABLE:
                    case DEVELOPER_ERROR:
                    case ERROR:
                    case FEATURE_NOT_SUPPORTED:
                    case ITEM_ALREADY_OWNED:
                    case ITEM_NOT_OWNED:
                    case ITEM_UNAVAILABLE:
                    case SERVICE_DISCONNECTED:
                    case SERVICE_UNAVAILABLE:
                    case USER_CANCELED:
                    case SERVICE_TIMEOUT:
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        stateSaved = true;
    }

    @Override
    public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> details) {
        log("onSkuDetailsResponse(" + billingResult + ", " + details + ")");
        if (billingResult.getResponseCode() != OK) {
            Log.w(TAG, "failed to query inventory: " + billingResult);
            return;
        }

        if (isFinishing() || stateSaved) {
            Log.w(TAG, "activity no longer active");
            return;
        }

        List<SkuDetails> skuDetailsList = new ArrayList<SkuDetails>();
        for (SkuDetails d : details) {
            if (d.getSku().startsWith(DONATION_PREFIX)) {
                skuDetailsList.add(d);
            }
        }
        showSelectDialog(skuDetailsList);
    }

    /**
     * @param result response code of the update
     * @param purchases list of updated purchases if present
     */
    @Override
    public void onPurchasesUpdated(BillingResult result, @Nullable List<Purchase> purchases) {
        log("onPurchasesUpdated(" + result + ", " + purchases + ")");
        String message;
        switch (result.getResponseCode()) {
            case OK:
                if (purchases != null)  {
                    for (Purchase p : purchases) {
                        acknowledgePurchase(p);
                    }
                }
                message = getString(R.string.ui_donation_success_message);
                break;
            case ITEM_UNAVAILABLE:
                message = getString(R.string.ui_donation_failure_message,
                        getString(R.string.donation_error_unavailable));
                break;
            case ITEM_ALREADY_OWNED:
                message = getString(R.string.ui_donation_failure_message,
                        getString(R.string.donation_error_already_owned));
                break;
            case USER_CANCELED:
                message = getString(R.string.ui_donation_failure_message,
                        getString(R.string.donation_error_canceled));
                break;
            case BILLING_UNAVAILABLE:
            case FEATURE_NOT_SUPPORTED:
            case ITEM_NOT_OWNED:
            case SERVICE_DISCONNECTED:
            case SERVICE_UNAVAILABLE:
            case DEVELOPER_ERROR:
            case ERROR:
            case SERVICE_TIMEOUT:
            default:
                message = getString(R.string.ui_donation_failure_message,
                        getString(R.string.donation_unspecified_error, result.getResponseCode()));
                break;
        }

        Toast.makeText(this, message, LENGTH_LONG).show();
        finish();
    }

    @Override
    public void selectedSku(SkuDetails details) {
        if (billingClient == null) return;
        if (DEBUG_IAB) {
            Log.v(TAG, "selectedSku("+details+")");
        }
        billingClient.launchBillingFlow(this, BillingFlowParams.newBuilder()
                .setSkuDetails(details)
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
        if (DEBUG_IAB) {
            Collections.addAll(skus, Sku.Test.SKUS);
        }
        Collections.sort(skus);
        final DonationListFragment donationList = new DonationListFragment();
        donationList.setArguments(new BundleBuilder().putParcelableArrayList(SKUS, skus).build());
        donationList.show(getSupportFragmentManager(), null);
    }

    private static void log(String s) {
        if (DEBUG_IAB) {
            Log.d(TAG, s);
        }
    }

    // https://developer.android.com/google/play/billing/billing_library_overview#acknowledge
    private void acknowledgePurchase(final Purchase purchase) {
        if (purchase.getPurchaseState() == PURCHASED && !purchase.isAcknowledged() && billingClient != null) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();

            billingClient.acknowledgePurchase(params, new AcknowledgePurchaseResponseListener() {
                @Override
                public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                    log("onAcknowledgePurchaseResponse(" + billingResult + ")");
                    if (billingResult.getResponseCode() != OK) {
                        Log.w(TAG, "not acknowledged purchase " + purchase + ":" + billingResult);
                    }
                }
            });
        }
    }


    public static void checkUserDonationStatus(Context context,
                                               final DonationStatusListener listener) {
        final BillingClient helper = BillingClient.newBuilder(context)
                .enablePendingPurchases()
                .setListener(new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(BillingResult result, @Nullable List<Purchase> purchases) {
                log("onPurchasesUpdated("+result+", "+purchases+")");
            }
        }).build();
        helper.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                    log("checkUserHasDonated: onBillingSetupFinished("+result+")");
                try {
                    if (result.getResponseCode() == OK) {
                        PurchasesResult purchasesResult = helper.queryPurchases(INAPP);
                        if (result.getResponseCode() == OK) {
                            listener.userDonationState(userHasDonated(purchasesResult.getPurchasesList()) ? DONATED : NOT_DONATED);
                        } else {
                            listener.userDonationState(UNKNOWN);
                        }
                    } else {
                        listener.userDonationState(result.getResponseCode() == BILLING_UNAVAILABLE ? NOT_AVAILABLE : UNKNOWN);
                    }
                } finally {
                    try {
                        helper.endConnection();
                    } catch (Exception ignored) {
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

