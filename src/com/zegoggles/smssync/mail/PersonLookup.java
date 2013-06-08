package com.zegoggles.smssync.mail;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import com.zegoggles.smssync.preferences.AddressStyle;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.MessageConverter.ECLAIR_CONTENT_URI;
import static com.zegoggles.smssync.utils.Sanitizer.encodeLocal;
import static com.zegoggles.smssync.utils.Sanitizer.sanitize;

public class PersonLookup {
    private static final boolean NEW_CONTACT_API = Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.ECLAIR;

    private static final String[] PHONE_PROJECTION = getPhoneProjection();
    // PhoneLookup.CONTENT_FILTER_URI
    private static final Uri ECLAIR_CONTENT_FILTER_URI =
            Uri.parse("content://com.android.contacts/phone_lookup");

    private static final String UNKNOWN_NUMBER = "unknown.number";
    private static final String UNKNOWN_EMAIL = "unknown.email";

    private static final int MAX_PEOPLE_CACHE_SIZE = 500;

    // simple LRU cache
    private final Map<String, PersonRecord> mPeopleCache =
            new LinkedHashMap<String, PersonRecord>(MAX_PEOPLE_CACHE_SIZE + 1, .75F, true) {
                @Override
                public boolean removeEldestEntry(Map.Entry<String, PersonRecord> eldest) {
                    return size() > MAX_PEOPLE_CACHE_SIZE;
                }
            };

    private final AddressStyle mStyle;
    private final ContentResolver mResolver;

    public PersonLookup(ContentResolver resolver, AddressStyle style) {
        mStyle = style;
        mResolver = resolver;
        Log.d(TAG, String.format(Locale.ENGLISH, "using %s contacts API", NEW_CONTACT_API ? "new" : "old"));
    }

    /* Look up a person */
    @SuppressWarnings("deprecation")
    public PersonRecord lookupPerson(final String address) {
        if (TextUtils.isEmpty(address)) {
            final PersonRecord record = new PersonRecord(mStyle);
            record.number = "-1";
            record.email = getUnknownEmail(null);
            record.unknown = true;
            return record;
        } else if (!mPeopleCache.containsKey(address)) {
            Uri personUri = Uri.withAppendedPath(NEW_CONTACT_API ? ECLAIR_CONTENT_FILTER_URI :
                    android.provider.Contacts.Phones.CONTENT_FILTER_URL, Uri.encode(address));

            Cursor c = mResolver.query(personUri, PHONE_PROJECTION, null, null, null);
            final PersonRecord record = new PersonRecord(mStyle);
            if (c != null && c.moveToFirst()) {
                record._id = c.getLong(c.getColumnIndex(PHONE_PROJECTION[0]));
                record.name = sanitize(c.getString(c.getColumnIndex(PHONE_PROJECTION[1])));
                record.number = sanitize(NEW_CONTACT_API ? address :
                        c.getString(c.getColumnIndex(PHONE_PROJECTION[2])));
                record.email = getPrimaryEmail(record._id, record.number);
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "Looked up unknown address: " + address);

                record.number = sanitize(address);
                record.email = getUnknownEmail(address);
                record.unknown = true;
            }
            mPeopleCache.put(address, record);

            if (c != null) c.close();
        }
        return mPeopleCache.get(address);
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    @SuppressWarnings("deprecation")
    private String getPrimaryEmail(final long personId, final String number) {
        if (personId <= 0) {
            return getUnknownEmail(number);
        }
        String primaryEmail = null;

        // Get all e-mail addresses for that person.
        Cursor c;
        int columnIndex;
        if (NEW_CONTACT_API) {
            c = mResolver.query(
                    ECLAIR_CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Email.DATA},
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", new String[]{String.valueOf(personId)},
                    ContactsContract.CommonDataKinds.Email.IS_PRIMARY + " DESC");
            columnIndex = c != null ? c.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA) : -1;
        } else {
            c = mResolver.query(
                    android.provider.Contacts.ContactMethods.CONTENT_EMAIL_URI,
                    new String[]{
                            android.provider.Contacts.ContactMethods.DATA
                    },
                    android.provider.Contacts.ContactMethods.PERSON_ID + " = ?",
                    new String[]{String.valueOf(personId)},
                    android.provider.Contacts.ContactMethods.ISPRIMARY + " DESC");

            columnIndex = c != null ? c.getColumnIndex(android.provider.Contacts.ContactMethods.DATA) : -1;
        }

        // Loop over cursor and find a Gmail address for that person.
        // If there is none, pick first e-mail address.
        while (c != null && c.moveToNext()) {
            String e = c.getString(columnIndex);
            if (primaryEmail == null) {
                primaryEmail = e;
            }
            if (isGmailAddress(e)) {
                primaryEmail = e;
                break;
            }
        }

        if (c != null) c.close();
        return (primaryEmail != null) ? primaryEmail : getUnknownEmail(number);
    }

    private static String getUnknownEmail(String number) {
        final String no = (number == null || "-1".equals(number)) ? UNKNOWN_NUMBER : number;
        return encodeLocal(no.trim()) + "@" + UNKNOWN_EMAIL;
    }

    // Returns whether the given e-mail address is a Gmail address or not.
    private static boolean isGmailAddress(String email) {
        return email != null &&
                (email.toLowerCase(Locale.ENGLISH).endsWith("gmail.com") ||
                        email.toLowerCase(Locale.ENGLISH).endsWith("googlemail.com"));
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    @SuppressWarnings("deprecation")
    private static String[] getPhoneProjection() {
        return NEW_CONTACT_API ?
                new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME} :
                new String[]{
                        android.provider.Contacts.Phones.PERSON_ID,
                        android.provider.Contacts.People.NAME,
                        android.provider.Contacts.Phones.NUMBER
                };
    }
}
