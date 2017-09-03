package com.zegoggles.smssync.preferences;

public enum AddressStyle {
    NAME,
    NAME_AND_NUMBER,
    NUMBER;

    private static final String EMAIL_ADDRESS_STYLE = "email_address_style";

    public static AddressStyle getEmailAddressStyle(Preferences preferences) {
        return preferences.getDefaultType(EMAIL_ADDRESS_STYLE, AddressStyle.class, AddressStyle.NAME);
    }
 }
