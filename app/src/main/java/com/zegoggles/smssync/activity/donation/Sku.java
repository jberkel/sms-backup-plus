package com.zegoggles.smssync.activity.donation;

import android.os.Parcel;
import android.os.ParcelFormatException;
import android.os.Parcelable;
import com.android.billingclient.api.SkuDetails;

import org.json.JSONException;

import static com.android.billingclient.api.BillingClient.SkuType.INAPP;

/**
 * A parcelable and comparable implementation of {@link SkuDetails}.
 */
public class Sku implements Parcelable, Comparable<Sku> {
    private final String type;
    private final String sku;
    private final String price;
    private final String title;
    private final String description;
    private final long priceAmountMicros;
    private final String originalJson;

    Sku(SkuDetails detail) {
        this(detail.getType(), detail.getSku(), detail.getPrice(), detail.getTitle(), detail.getDescription(), detail.getPriceAmountMicros(), detail.getOriginalJson());
    }

    /**
     * @param type SKU type
     * @param sku  the product Id
     * @param price formatted price of the item, including its currency sign
     * @param title  the title of the product
     * @param description the description of the product
     * @param priceAmountMicros the price in micro-units, where 1,000,000 micro-units equal one unit of the currency
     */
    Sku(String type, String sku, String price, String title, String description, long priceAmountMicros, String originalJson) {
        this.type = type;
        this.sku = sku;
        this.price = price;
        this.title = title;
        this.description = description;
        this.priceAmountMicros = priceAmountMicros;
        this.originalJson = originalJson;
    }

    private Sku(Parcel in) {
        originalJson = in.readString();
        if (originalJson == null) {
            throw new ParcelFormatException();
        }
        try {
            SkuDetails skuDetails = new SkuDetails(originalJson);
            sku = skuDetails.getSku();
            price = skuDetails.getPrice();
            type = skuDetails.getType();
            title = skuDetails.getTitle();
            description = skuDetails.getPrice();
            priceAmountMicros = skuDetails.getPriceAmountMicros();
        } catch (JSONException e) {
           throw new ParcelFormatException(e.getMessage());
        }
    }

    public static final Creator<Sku> CREATOR = new Creator<Sku>() {
        @Override
        public Sku createFromParcel(Parcel in) {
            return new Sku(in);
        }

        @Override
        public Sku[] newArray(int size) {
            return new Sku[size];
        }
    };

    public String getType() {
        return type;
    }
    public String getSku() {
        return sku;
    }
    public String getPrice() {
        return price;
    }
    public String getTitle() {
        return title;
    }
    public String getDescription() {
        return description;
    }

    public String getOriginalJson() {
        return originalJson;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(originalJson);
    }

    @Override
    public int compareTo(Sku other) {
        int diff = Long.valueOf(priceAmountMicros).compareTo(other.priceAmountMicros);
        if (diff == 0) {
            diff = title.compareTo(other.title);
        }
        return diff;
    }

    /**
     * To test your implementation with static responses, you make an In-app Billing request using a special
     * item that has a reserved product ID. Each reserved product ID returns a specific static response
     * from Google Play. No money is transferred when you make In-app Billing requests with the reserved product
     * IDs. Also, you cannot specify the form of payment when you make a billing request with a
     * reserved product ID.
     *
     * @see <a href="http://developer.android.com/google/play/billing/billing_testing.html">
     * Testing in-app purchases with static responses
     * </a>
     */
    static class Test {
        static final String TEST_PREFIX = "android.test.";
        static final String TEST_PRICE = "$0.00";

        /**
         * When you make an In-app Billing request with this product ID, Google Play responds as though
         * you successfully purchased an item. The response includes a JSON string, which contains fake
         * purchase information (for example, a fake order ID). In some cases, the JSON string is signed
         * and the response includes the signature so you can test your signature verification implementation
         * using these responses.
         */
        static final Sku PURCHASED =
                new Sku(INAPP, TEST_PREFIX + "purchased", TEST_PRICE, "Test (purchased)", "Purchased", 0, null);


        /**
         * When you make an In-app Billing request with this product ID Google Play responds as though
         * the purchase was canceled. This can occur when an error is encountered in the order process,
         * such as an invalid credit card, or when you cancel a user's order before it is charged.
         */
        static final Sku CANCELED =
                new Sku(INAPP, TEST_PREFIX + "canceled", TEST_PRICE, "Test (canceled)", "Canceled", 0, null);

        /**
         * When you make an In-app Billing request with this product ID, Google Play responds as though
         * the purchase was refunded. Refunds cannot be initiated through Google Play's in-app billing service.
         * Refunds must be initiated by you (the merchant). After you process a refund request through your
         * Google Wallet merchant account, a refund message is sent to your application by Google Play.
         * This occurs only when Google Play gets notification from Google Wallet that a refund has been made.
         * For more information about refunds, see
         * <a href="http://developer.android.com/google/play/billing/v2/api.html#billing-action-notify">
         * Handling IN_APP_NOTIFY messages</a> and
         * <a href="http://support.google.com/googleplay/android-developer/bin/answer.py?hl=en&answer=1153485">
         * In-app Billing Pricing
         * </a>.
         */
        static final Sku REFUNDED =
                new Sku(INAPP, TEST_PREFIX + "refunded", TEST_PRICE, "Test (refunded)", "Refunded", 0, null);

        /**
         * When you make an In-app Billing request with this product ID, Google Play responds as though
         * the item being purchased was not listed in your application's product list.
         */
        static final Sku UNAVAILABLE =
                new Sku(INAPP, TEST_PREFIX + "item_unavailable", TEST_PRICE, "Test (unavailable)", "Unavailable", 0, null);

        static final Sku[] SKUS = {
                PURCHASED, CANCELED, REFUNDED, UNAVAILABLE
        };
    }
}
