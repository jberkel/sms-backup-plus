package com.zegoggles.smssync.mail;

import android.text.TextUtils;
import com.fsck.k9.mail.Address;
import com.zegoggles.smssync.preferences.AddressStyle;

import java.util.Locale;

import static com.zegoggles.smssync.utils.Sanitizer.encodeLocal;
import static com.zegoggles.smssync.utils.Sanitizer.sanitize;

public class PersonRecord {
    private static final String UNKNOWN_NUMBER = "unknown.number";
    private static final String UNKNOWN_EMAIL = "unknown.email";

    private final long _id;
    private final String name, email, number;

    /**
     * @param id the id of the record
     * @param name email name
     * @param email the actual email address
     * @param number the telephone number
     */
    public PersonRecord(long id, String name, String email, String number) {
        this._id = id;
        this.name = sanitize(name);
        this.number = sanitize(number);
        this.email =  sanitize(email);
    }

    public boolean isUnknown() {
        return _id <= 0;
    }

    public Address getAddress(AddressStyle style) {
        final String name;
        switch (style) {
            case NUMBER:
                name = getNumber(); break;
            case NAME_AND_NUMBER:
                name = getNameWithNumber(); break;
            case NAME:
                name = getName(); break;
            default:
                name = null;
        }
        return new Address(getEmail(), name, !isEmailUnknown());
    }

    public String getEmail() {
        return isEmailUnknown() ? getUnknownEmail(number) : email;
    }

    public String getId() {
        return isUnknown() ? number : String.valueOf(getContactId());
    }

    public long getContactId() {
        return _id;
    }

    public String getNumber() {
        return (TextUtils.isEmpty(number) || "-1".equals(number) || "-2".equals(number)) ? "Unknown" : number;
    }

    public String getName() {
        return !TextUtils.isEmpty(name) ? name : getNumber();
    }

    public String toString() {
        return String.format(Locale.ENGLISH, "[name=%s email=%s id=%d]", getName(), email, _id);
    }

    private boolean isEmailUnknown() {
        return isUnknown() || TextUtils.isEmpty(email);
    }

    private String getNameWithNumber() {
        return name != null ? String.format(Locale.ENGLISH, "%s (%s)", getName(), getNumber()) : getNumber();
    }

    private static String getUnknownEmail(String number) {
        final String no = (number == null || "-1".equals(number)) ? UNKNOWN_NUMBER : number;
        return encodeLocal(no.trim()) + "@" + UNKNOWN_EMAIL;
    }
}
