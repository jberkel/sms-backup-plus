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
import com.android.email.mail.Message;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Message.RecipientType;
import com.android.email.mail.internet.MimeMessage;
import com.android.email.mail.internet.TextBody;

public class CursorToMessage {
    
    private static final String[] PHONE_PROJECTION = new String[] {
            Phones.PERSON_ID, People.NAME, Phones.NUMBER
    };

    private static final String[] EMAIL_PROJECTION = new String[] {
        ContactMethods.DATA
    };

    private static final String UNKNOWN_NUMBER = "unknown_number";

    private static final int MAX_PEOPLE_CACHE_SIZE = 100;

    private Context mContext;

    private Address mUserAddress;

    private Map<String, PersonRecord> mPeopleCache;
    
    private String mReferenceValue;

    public CursorToMessage(Context ctx, String userEmail) {
        mContext = ctx;
        mPeopleCache = new HashMap<String, PersonRecord>();
        mUserAddress = new Address(userEmail);
        
        mReferenceValue = PrefStore.getReferenceUid(ctx);
        if (mReferenceValue == null) {
            mReferenceValue = generateReferenceValue();
            PrefStore.setReferenceUid(ctx, mReferenceValue);
        }
        
    }

    public ConversionResult cursorToMessageArray(Cursor cursor, int maxEntries)
            throws MessagingException {
        List<Message> messageList = new ArrayList<Message>(maxEntries);
        long maxId = -1;

        String[] columns = cursor.getColumnNames();
        int indexId = cursor.getColumnIndex(SmsConsts.ID);
        while (cursor.moveToNext()) {
            HashMap<String, String> msgMap = new HashMap<String, String>(columns.length);

            long id = cursor.getLong(indexId);
            if (id > maxId) {
                maxId = id;
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
        result.maxId = String.valueOf(maxId);
        result.messageList = messageList;
        return result;
    }

    private Message messageFromHashMap(HashMap<String, String> msgMap) throws MessagingException {
        Message msg = new MimeMessage();

        PersonRecord record = null;
        String address = msgMap.get("address");
        if (address != null) {
            record = lookupPerson(address);
        }
        if (record == null) {
            record = new PersonRecord();
            record._id = address;
            record.name = address;
            record.address = new Address(address + "@unknown.person");
        }

        msg.setSubject("SMS with " + record.name);

        TextBody body = new TextBody(msgMap.get("body"));

        int messageType = Integer.valueOf(msgMap.get("type"));
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
        Date then = new Date(Long.valueOf(msgMap.get("date")));
        msg.setSentDate(then);
        msg.setInternalDate(then);
        String threadIdStr = msgMap.get("thread_id");
        long threadId = (threadIdStr != null) ? Long.valueOf(threadIdStr) : 0;
        if (threadId > 0) {
            msg.setHeader("References", String.format(mReferenceValue, threadId));
        }
        msg.setHeader("X-smssync-original-address", address);

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
        if (personId > 0) {
            Cursor emailCursor = mContext.getContentResolver().query(
                    ContactMethods.CONTENT_EMAIL_URI, EMAIL_PROJECTION,
                    ContactMethods.PERSON_ID + " = " + personId, null, null);
            if (emailCursor.moveToFirst()) {
                int indexData = emailCursor.getColumnIndex(ContactMethods.DATA);
                primaryEmail = emailCursor.getString(indexData);
            }
            emailCursor.close();
        }
        if (primaryEmail == null) {
            primaryEmail = getUnknownEmail(number);
        }
        return primaryEmail;
    }

    private static String getUnknownEmail(String number) {
        String no = (number == null) ? UNKNOWN_NUMBER : number;
        return no + "@unknown.email";
    }
    
    private static String generateReferenceValue() {
        StringBuffer sb = new StringBuffer();
        sb.append("<");
        for (int i = 0; i < 24; i++) {
            sb.append(Integer.toString((int)(Math.random() * 35), 36));
        }
        sb.append(".%s@smssync.studer.tv>");
        return sb.toString();
    }

    public static class ConversionResult {
        public String maxId;

        public List<Message> messageList;
    }

    private static class PersonRecord {
        String _id;

        String name;

        Address address;
    }
}
