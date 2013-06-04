package com.zegoggles.smssync.mail;

import com.fsck.k9.mail.Address;
import com.zegoggles.smssync.preferences.AddressStyle;

import java.util.Locale;

import static com.zegoggles.smssync.utils.Sanitizer.sanitize;

public class PersonRecord {
    public long _id;
    public String name, email, number;
    public boolean unknown = false;
    private Address mAddress;
    private AddressStyle addressStyle;

    public PersonRecord(AddressStyle style) {
        this.addressStyle = style;
    }

    public Address getAddress() {
        if (mAddress == null) {
            switch (addressStyle) {
                case NUMBER:
                    mAddress = new Address(email, getNumber());
                    break;
                case NAME_AND_NUMBER:
                    mAddress = new Address(email,
                            name == null ? getNumber() :
                                    String.format(Locale.ENGLISH, "%s (%s)", getName(), getNumber()));
                    break;
                case NAME:
                    mAddress = new Address(email, getName());
                    break;
                default:
                    mAddress = new Address(email);
            }
        }
        return mAddress;
    }

    public String getId() {
        return sanitize(unknown ? number : String.valueOf(_id));
    }

    public String getNumber() {
        return sanitize("-1".equals(number) || "-2".equals(number) ? "Unknown" : number);
    }

    public String getName() {
        return sanitize(name != null ? name : getNumber());
    }

    public String toString() {
        return String.format(Locale.ENGLISH, "[name=%s email=%s id=%d]", getName(), email, _id);
    }
}
