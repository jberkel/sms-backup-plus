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

    public PersonRecord(long id, String name, String email, String number) {
        this._id = id;
        this.name = sanitize(name);
        this.email = sanitize(email);
        this.number = sanitize(number);
    }

    public boolean isUnknown() {
        return _id <= 0;
    }

    public Address getAddress(AddressStyle style) {
        switch (style) {
            case NUMBER:
                return new Address(email, getNumber());
            case NAME_AND_NUMBER:
                return new Address(email,
                        name == null ? getNumber() :
                                String.format(Locale.ENGLISH, "%s (%s)", getName(), getNumber()));
            case NAME:
                return new Address(email, getName());
            default:
                return new Address(email);
        }
    }

    public String getEmail() {
        return isUnknown() || TextUtils.isEmpty(email) ? getUnknownEmail(number) : email;
    }

    public String getId() {
        return isUnknown() ? number : String.valueOf(getContactId());
    }

    public long getContactId() {
        return _id;
    }

    public String getNumber() {
        return ("-1".equals(number) || "-2".equals(number)) ? "Unknown" : number;
    }

    public String getName() {
        return !TextUtils.isEmpty(name) ? name : getNumber();
    }

    public String toString() {
        return String.format(Locale.ENGLISH, "[name=%s email=%s id=%d]", getName(), email, _id);
    }

    private static String getUnknownEmail(String number) {
        final String no = (number == null || "-1".equals(number)) ? UNKNOWN_NUMBER : number;
        return encodeLocal(no.trim()) + "@" + UNKNOWN_EMAIL;
    }
}
