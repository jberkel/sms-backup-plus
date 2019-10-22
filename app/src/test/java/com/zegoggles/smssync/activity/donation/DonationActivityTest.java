package com.zegoggles.smssync.activity.donation;

import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import static com.android.billingclient.api.BillingClient.BillingResponseCode.ERROR;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.ITEM_UNAVAILABLE;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.OK;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Robolectric.buildActivity;
import static org.robolectric.shadows.ShadowToast.getTextOfLatestToast;
import static org.robolectric.shadows.ShadowToast.shownToastCount;

@RunWith(RobolectricTestRunner.class)
public class DonationActivityTest {
    DonationActivity activity;
    @Before
    public void setUp() throws Exception {
        activity = buildActivity(DonationActivity.class).get();
    }

    @Test public void testResultMessageOK() {
        activity.onPurchasesUpdated(BillingResult.newBuilder().setResponseCode(OK).build(), Collections.<Purchase>emptyList());
        assertToast("Donation successful, thank you!");
    }

    @Test public void testResultMessageItemUnavailable() {
        activity.onPurchasesUpdated(BillingResult.newBuilder().setResponseCode(ITEM_UNAVAILABLE).build(), Collections.<Purchase>emptyList());
        assertToast("Donation failed: not available");
    }

    @Test public void testResultMessageItemAlreadyOwned() {
        activity.onPurchasesUpdated(BillingResult.newBuilder().setResponseCode(ITEM_ALREADY_OWNED).build(), Collections.<Purchase>emptyList());
        assertToast("Donation failed: you have already donated");
    }

    @Test public void testResultMessageUserCanceled() {
        activity.onPurchasesUpdated(BillingResult.newBuilder().setResponseCode(USER_CANCELED).build(), Collections.<Purchase>emptyList());
        assertToast("Donation failed: canceled");
    }

    @Test public void testResultMessageError() {
        activity.onPurchasesUpdated(BillingResult.newBuilder().setResponseCode(ERROR).build(), Collections.<Purchase>emptyList());
        assertToast("Donation failed: unspecified error: 6");
    }

    private void assertToast(String message) {
        assertThat(getTextOfLatestToast()).isEqualTo(message);
        assertThat(shownToastCount()).isEqualTo(1);
    }
}
