package com.zegoggles.smssync.activity.donation;

import android.os.Parcel;
import android.os.Parcelable;
import com.android.billingclient.api.SkuDetails;

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

    Sku(SkuDetails detail) {
        this(detail.getType(), detail.getSku(), detail.getPrice(), detail.getTitle(), detail.getDescription());
    }

    Sku(String type, String sku, String price, String title, String description) {
        this.type = type;
        this.sku = sku;
        this.price = price;
        this.title = title;
        this.description = description;
    }

    private Sku(Parcel in) {
        type = in.readString();
        sku = in.readString();
        price = in.readString();
        title = in.readString();
        description = in.readString();
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(type);
        parcel.writeString(sku);
        parcel.writeString(price);
        parcel.writeString(title);
        parcel.writeString(description);
    }

    @Override
    public int compareTo(Sku rhs) {
        if (getPrice() != null && rhs.getPrice() != null) {
            return getPrice().compareTo(rhs.getPrice());
        } else if (getTitle() != null && rhs.getTitle() != null) {
            return getTitle().compareTo(rhs.getTitle());
        } else if (getSku() != null && rhs.getSku() != null) {
            return getSku().compareTo(rhs.getSku());
        } else {
            return 0;
        }
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
        static final String TEST_PRICE = "0.00 USD";

        /**
         * When you make an In-app Billing request with this product ID, Google Play responds as though
         * you successfully purchased an item. The response includes a JSON string, which contains fake
         * purchase information (for example, a fake order ID). In some cases, the JSON string is signed
         * and the response includes the signature so you can test your signature verification implementation
         * using these responses.
         */
        static final Sku PURCHASED =
                new Sku(INAPP, TEST_PREFIX + "purchased", TEST_PRICE, "Test (purchased)", "Purchased");


        /**
         * When you make an In-app Billing request with this product ID Google Play responds as though
         * the purchase was canceled. This can occur when an error is encountered in the order process,
         * such as an invalid credit card, or when you cancel a user's order before it is charged.
         */
        static final Sku CANCELED =
                new Sku(INAPP, TEST_PREFIX + "canceled", TEST_PRICE, "Test (canceled)", "Canceled");

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
                new Sku(INAPP, TEST_PREFIX + "refunded", TEST_PRICE, "Test (refunded)", "Refunded");

        /**
         * When you make an In-app Billing request with this product ID, Google Play responds as though
         * the item being purchased was not listed in your application's product list.
         */
        static final Sku UNAVAILABLE =
                new Sku(INAPP, TEST_PREFIX + "item_unavailable", TEST_PRICE, "Test (unavailable)", "Unavailable");

        static final Sku[] SKUS = {
                PURCHASED, CANCELED, REFUNDED, UNAVAILABLE
        };
    }
}
