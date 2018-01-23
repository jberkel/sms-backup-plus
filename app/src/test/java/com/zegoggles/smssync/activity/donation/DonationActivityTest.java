package com.zegoggles.smssync.activity.donation;

import com.android.billingclient.api.BillingClient.BillingResponse;
import com.android.billingclient.api.Purchase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

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
        activity.onPurchasesUpdated(BillingResponse.OK, Collections.<Purchase>emptyList());
        assertToast("Donation successful, thank you!");
    }

    @Test public void testResultMessageItemUnavailable() {
        activity.onPurchasesUpdated(BillingResponse.ITEM_UNAVAILABLE, Collections.<Purchase>emptyList());
        assertToast("Donation failed: not available");
    }

    @Test public void testResultMessageItemAlreadyOwned() {
        activity.onPurchasesUpdated(BillingResponse.ITEM_ALREADY_OWNED, Collections.<Purchase>emptyList());
        assertToast("Donation failed: you have already donated");
    }

    @Test public void testResultMessageUserCanceled() {
        activity.onPurchasesUpdated(BillingResponse.USER_CANCELED, Collections.<Purchase>emptyList());
        assertToast("Donation failed: canceled");
    }

    @Test public void testResultMessageError() {
        activity.onPurchasesUpdated(BillingResponse.ERROR, Collections.<Purchase>emptyList());
        assertToast("Donation failed: unspecified error: 6");
    }

    private void assertToast(String message) {
        assertThat(getTextOfLatestToast()).isEqualTo(message);
        assertThat(shownToastCount()).isEqualTo(1);
    }
}
