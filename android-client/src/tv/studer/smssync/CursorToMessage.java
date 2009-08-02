/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.studer.smssync;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.util.Log;

import com.android.email.mail.Address;
import com.android.email.mail.Flag;
import com.android.email.mail.Message;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Message.RecipientType;
import com.android.email.mail.internet.MimeMessage;
import com.android.email.mail.internet.TextBody;

public class CursorToMessage {
    
    private static final String REFERENCE_UID_TEMPLATE = "<%s.%s@smssync.studer.tv>";
    
    private static final String[] PHONE_PROJECTION = new String[] {
            Phones.PERSON_ID, People.NAME, Phones.NUMBER
    };

    private static final String[] EMAIL_PROJECTION = new String[] {
        ContactMethods.DATA
    };

    private static final String UNKNOWN_NUMBER = "unknown_number";
    
    private static final String UNKNOWN_EMAIL = "unknown.email";
    
    private static final String UNKNOWN_PERSON = "unknown.person";

    private static final int MAX_PEOPLE_CACHE_SIZE = 100;

    private Context mContext;

    private Address mUserAddress;

    private Map<String, PersonRecord> mPeopleCache;
    
    private String mReferenceValue;
    
    private boolean mMarkAsRead = false;

    public CursorToMessage(Context ctx, String userEmail) {
        mContext = ctx;
        mPeopleCache = new HashMap<String, PersonRecord>();
        mUserAddress = new Address(userEmail);
        
        mReferenceValue = PrefStore.getReferenceUid(ctx);
        if (mReferenceValue == null) {
            mReferenceValue = generateReferenceValue();
            PrefStore.setReferenceUid(ctx, mReferenceValue);
        }
        
        mMarkAsRead = PrefStore.getMarkAsRead(ctx);
    }

    public ConversionResult cursorToMessageArray(Cursor cursor, int maxEntries)
            throws MessagingException {
        List<Message> messageList = new ArrayList<Message>(maxEntries);
        long maxDate = PrefStore.DEFAULT_MAX_SYNCED_DATE;

        String[] columns = cursor.getColumnNames();
        int indexDate = cursor.getColumnIndex(SmsConsts.DATE);
        while (cursor.moveToNext()) {
            HashMap<String, String> msgMap = new HashMap<String, String>(columns.length);

            long date = cursor.getLong(indexDate);
            if (date > maxDate) {
                maxDate = date;
            }
            for (int i = 0; i < columns.length; i++) {
                msgMap.put(columns[i], cursor.getString(i));
            }
            messageList.add(messageFromHashMap(msgMap));
            if (messageList.size() == maxEntries) {
                // Only consume up to 'maxEntries' items.
                break;
            }
        }
        //TODO: Be more clever and MFU or LRU people.
        if (mPeopleCache.size() > MAX_PEOPLE_CACHE_SIZE) {
            mPeopleCache.clear();
        }

        ConversionResult result = new ConversionResult();
        result.maxDate = maxDate;
        result.messageList = messageList;
        return result;
    }

    private Message messageFromHashMap(HashMap<String, String> msgMap) throws MessagingException {
        Message msg = new MimeMessage();

        PersonRecord record = null;
        String address = msgMap.get("address");
        if (address != null) {
            address = address.trim();
            if (address.length() > 0) {
                record = lookupPerson(address);
            }
        }
        
        if (record == null) {
            record = new PersonRecord();
            record._id = address;
            record.name = address;
            record.address = new Address(address + "@" + UNKNOWN_PERSON);
        }

        msg.setSubject("SMS with " + record.name);

        TextBody body = new TextBody(msgMap.get(SmsConsts.BODY));

        int messageType = Integer.valueOf(msgMap.get(SmsConsts.TYPE));
        if (SmsConsts.MESSAGE_TYPE_INBOX == messageType) {
            // Received message
            msg.setFrom(record.address);
            msg.setRecipient(RecipientType.TO, mUserAddress);
        } else {
            // Sent message
            msg.setRecipient(RecipientType.TO, record.address);
            msg.setFrom(mUserAddress);
        }

        msg.setBody(body);
        Date then = new Date(Long.valueOf(msgMap.get(SmsConsts.DATE)));
        msg.setSentDate(then);
        msg.setInternalDate(then);
        // Threading by person ID, not by thread ID. I think this value is more
        // stable.
        msg.setHeader("References", String.format(REFERENCE_UID_TEMPLATE, mReferenceValue,
                record._id));
        
        msg.setHeader("X-smssync-id", msgMap.get(SmsConsts.ID));
        msg.setHeader("X-smssync-address", address);
        msg.setHeader("X-smssync-type", msgMap.get(SmsConsts.TYPE));
        msg.setHeader("X-smssync-date", msgMap.get(SmsConsts.DATE));
        msg.setHeader("X-smssync-thread", msgMap.get(SmsConsts.THREAD_ID));
        msg.setHeader("X-smssync-read", msgMap.get(SmsConsts.READ));
        msg.setHeader("X-smssync-status", msgMap.get(SmsConsts.STATUS));
        msg.setHeader("X-smssync-protocol", msgMap.get(SmsConsts.PROTOCOL));
        msg.setHeader("X-smssync-service_center", msgMap.get(SmsConsts.SERVICE_CENTER));
        msg.setHeader("X-smssync-backup_time", new Date().toGMTString());
        msg.setFlag(Flag.SEEN, mMarkAsRead);
        
        return msg;
    }

    private PersonRecord lookupPerson(String address) {
        if (!mPeopleCache.containsKey(address)) {
            // Look phone number
            Uri personUri = Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, address);
            Cursor phoneCursor = mContext.getContentResolver().query(personUri, PHONE_PROJECTION,
                    null, null, null);
            if (phoneCursor.moveToFirst()) {
                int indexPersonId = phoneCursor.getColumnIndex(Phones.PERSON_ID);
                int indexName = phoneCursor.getColumnIndex(People.NAME);
                int indexNumber = phoneCursor.getColumnIndex(Phones.NUMBER);
                long personId = phoneCursor.getLong(indexPersonId);
                String name = phoneCursor.getString(indexName);
                String number = phoneCursor.getString(indexNumber);
                phoneCursor.close();

                String primaryEmail = getEmail(number, personId);

                PersonRecord record = new PersonRecord();
                record._id = String.valueOf(personId);
                record.name = name;
                record.address = new Address(primaryEmail, name);

                mPeopleCache.put(address, record);
            } else {
                Log.v(Consts.TAG, "Looked up unknown address: " + address);
                return null;
            }
        }
        return mPeopleCache.get(address);
    }

    private String getEmail(String number, long personId) {
        String primaryEmail = null;
        String selection = ContactMethods.PERSON_ID + " = ?";
        String[] selectionArgs = new String[] { String.valueOf(personId) };
        if (personId > 0) {
            // Get all e-mail addresses for that person.
            Cursor emailCursor = mContext.getContentResolver().query(
                    ContactMethods.CONTENT_EMAIL_URI, EMAIL_PROJECTION,
                    selection, selectionArgs, null);
            int indexData = emailCursor.getColumnIndex(ContactMethods.DATA);
            
            // Loop over cursor and find a Gmail address for that person.
            // If there is none, pick first e-mail address.
            String firstEmail = null;
            String gmailEmail = null;
            while (emailCursor.moveToNext()) {
                String tmpEmail = emailCursor.getString(indexData);
                if (firstEmail == null) {
                    firstEmail = tmpEmail;
                }
                if (isGmailAddress(tmpEmail)) {
                    gmailEmail = tmpEmail;
                    break;
                }
            }
            emailCursor.close();
            primaryEmail = (gmailEmail != null) ? gmailEmail : firstEmail;
        }
        // Return found e-mail address or a dummy "unknown e-mail address"
        // if there is none.
        if (primaryEmail == null) {
            primaryEmail = getUnknownEmail(number);
        }
        return primaryEmail;
    }

    private static String getUnknownEmail(String number) {
        String no = (number == null) ? UNKNOWN_NUMBER : number;
        return no + "@" + UNKNOWN_EMAIL;
    }
    
    /** Returns whether the given e-mail address is a Gmail address or not. */
    private static boolean isGmailAddress(String email) {
        return email.endsWith("gmail.com") || email.endsWith("googlemail.com");
    }
    
    private static String generateReferenceValue() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 24; i++) {
            sb.append(Integer.toString((int)(Math.random() * 35), 36));
        }
        return sb.toString();
    }

    public static class ConversionResult {
        public long maxDate;

        public List<Message> messageList;
    }

    private static class PersonRecord {
        String _id;

        String name;

        Address address;
    }
}
