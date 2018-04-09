package com.zegoggles.smssync.mail;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class PersonLookup {
    private static final String[] PHONE_PROJECTION = new String[] {
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME
    };
    private static final int MAX_PEOPLE_CACHE_SIZE = 500;

    // simple LRU cache
    private final Map<String, PersonRecord> mPeopleCache =
            new LinkedHashMap<String, PersonRecord>(MAX_PEOPLE_CACHE_SIZE + 1, .75F, true) {
                @Override
                public boolean removeEldestEntry(Map.Entry<String, PersonRecord> eldest) {
                    return size() > MAX_PEOPLE_CACHE_SIZE;
                }
            };

    private final ContentResolver mResolver;

    public PersonLookup(ContentResolver resolver) {
        mResolver = resolver;
    }

    /**
     * Look up a person
     * @throws SecurityException if the caller does not hold READ_CONTACTS
     */
    public @NonNull PersonRecord lookupPerson(final String address) {
        if (TextUtils.isEmpty(address)) {
            return new PersonRecord(0, null, null, "-1");
        } else if (!mPeopleCache.containsKey(address)) {
            Uri personUri = Uri.withAppendedPath(CONTENT_FILTER_URI, Uri.encode(address));

            Cursor c = null;
            try {
                // Issue #870
                // Some phone numbers trigger a full app crash when reaching this line.
                // The reason for the crash...
                //   Caused by: java.lang.IllegalArgumentException: column 'data1' does not exist
                // ...is unknown, hence the try-catch.
                // TODO: Find a way to avoid the crash without the need for a try-catch
                c = mResolver.query(personUri, PHONE_PROJECTION, null, null, null);
            } catch (Exception e) {
                Log.wtf(TAG, "Avoided a crash with address: " + address + "; Error Message: \"" + e.getMessage() + "\" in PersonLookup.java in lookupPerson(final String address) in c = mResolver.query(personUri, PHONE_PROJECTION, null, null, null);");
            }

            final PersonRecord record;
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(c.getColumnIndex(PHONE_PROJECTION[0]));

                record = new PersonRecord(
                    id,
                    c.getString(c.getColumnIndex(PHONE_PROJECTION[1])),
                    getPrimaryEmail(id),
                        address
                );

            } else {
                if (LOCAL_LOGV) Log.v(TAG, "Looked up unknown address: " + address);
                record = new PersonRecord(0, null, null, address);
            }
            mPeopleCache.put(address, record);

            if (c != null) c.close();
        }
        return mPeopleCache.get(address);
    }

    private String getPrimaryEmail(final long personId) {
        if (personId <= 0) {
            return null;
        }
        String primaryEmail = null;

        // Get all e-mail addresses for that person.
        Cursor c = mResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Email.DATA},
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", new String[]{String.valueOf(personId)},
                ContactsContract.CommonDataKinds.Email.IS_PRIMARY + " DESC");
        int columnIndex = c != null ? c.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA) : -1;

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
        return primaryEmail;
    }

    // Returns whether the given e-mail address is a Gmail address or not.
    private static boolean isGmailAddress(String email) {
        return email != null &&
                (email.toLowerCase(Locale.ENGLISH).endsWith("gmail.com") ||
                 email.toLowerCase(Locale.ENGLISH).endsWith("googlemail.com"));
    }
}
