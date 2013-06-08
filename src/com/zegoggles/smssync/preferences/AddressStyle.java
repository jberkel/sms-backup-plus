package com.zegoggles.smssync.preferences;

import android.content.Context;

public enum AddressStyle {
    NAME,
    NAME_AND_NUMBER,
    NUMBER;

    private static final String EMAIL_ADDRESS_STYLE = "email_address_style";

    public static AddressStyle getEmailAddressStyle(Context ctx) {
        return Preferences.getDefaultType(ctx, EMAIL_ADDRESS_STYLE, AddressStyle.class, AddressStyle.NAME);
    }
 }
