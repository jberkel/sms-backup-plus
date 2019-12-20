package com.zegoggles.smssync.activity.donation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class SkuTest {
    private Sku sku1 = new Sku("test", "test.sku.1", "$10.00", "Test 1", "Test 1", 10000, null);
    private Sku sku2 = new Sku("test", "test.sku.2", "$7.00", "Test 2", "Test 2", 7000, null);
    private Sku sku3 = new Sku("test", "test.sku.3", "$3.50", "Test 3", "Test 3", 3500, null);
    private Sku sku4 = new Sku("test", "test.sku.4", "$3.50", "Test 4", "Test 3", 3500, null);

    @Test public void testComparableComparesByPrice() {
        assertThat(sku1).isGreaterThan(sku2);
        assertThat(sku1).isGreaterThan(sku3);
        assertThat(sku3).isLessThan(sku2);
        assertThat(sku3).isLessThan(sku1);
    }

    @Test public void testSameObjectIsEqual() {
        assertThat(sku1).isEquivalentAccordingToCompareTo(sku1);
    }

    @Test public void testEqualPricesAreComparedByTitle() {
        assertThat(sku4).isGreaterThan(sku3);
    }

    @Test public void testTestSkus() {
        for (Sku testSku : Sku.Test.SKUS) {
            assertThat(testSku.getSku()).startsWith("android.test.");
        }
    }
}
