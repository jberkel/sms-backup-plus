package com.zegoggles.smssync.mail;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.PhoneLookup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class PersonLookup {
    private static final int MAX_PEOPLE_CACHE_SIZE = 500;

    // simple LRU cache
    private final Map<String, PersonRecord> personCache =
            new LinkedHashMap<String, PersonRecord>(MAX_PEOPLE_CACHE_SIZE + 1, .75F, true) {
                @Override
                public boolean removeEldestEntry(Map.Entry<String, PersonRecord> eldest) {
                    return size() > MAX_PEOPLE_CACHE_SIZE;
                }
            };

    private final ContentResolver resolver;

    public PersonLookup(ContentResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Look up a person
     * @throws SecurityException if the caller does not hold READ_CONTACTS
     */
    public @NonNull PersonRecord lookupPerson(final String address) {
        if (TextUtils.isEmpty(address)) {
            return new PersonRecord(0, null, null, "-1");
        } else if (!personCache.containsKey(address)) {
            final Uri personUri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address));

            Cursor c = null;
            try {
                c = resolver.query(personUri, new String[] {
                    PhoneLookup._ID,
                    PhoneLookup.DISPLAY_NAME
                }, null, null, null);
            } catch (IllegalArgumentException e) {
                // https://github.com/jberkel/sms-backup-plus/issues/870
                Log.wtf(TAG, "avoided a crash with address: " + address, e);
            }

            final PersonRecord record;
            if (c != null && c.moveToFirst()) {
                final long id = c.getLong(0);

                record = new PersonRecord(
                    id,
                    c.getString(1),
                    getPrimaryEmail(id),
                    address
                );
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "Looked up unknown address: " + address);
                record = new PersonRecord(0, null, null, address);
            }
            personCache.put(address, record);

            if (c != null) c.close();
        }
        return personCache.get(address);
    }

    private @Nullable String getPrimaryEmail(final long personId) {
        if (personId <= 0) {
            return null;
        }
        String primaryEmail = null;

        // Get all e-mail addresses for that person.
        Cursor c = resolver.query(
            Email.CONTENT_URI,
            new String[]{ Email.DATA },
            Email.CONTACT_ID + " = ?", new String[]{String.valueOf(personId)},
            Email.IS_PRIMARY + " DESC");
        int columnIndex = c != null ? c.getColumnIndex(Email.DATA) : -1;

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
