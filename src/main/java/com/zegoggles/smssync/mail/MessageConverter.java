/*
 * Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
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
package com.zegoggles.smssync.mail;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.CallLog;
import android.util.Log;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.MimeUtility;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.SmsConsts;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.preferences.AddressStyle;
import com.zegoggles.smssync.preferences.MarkAsReadTypes;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.utils.ThreadHelper;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class MessageConverter {
    //ContactsContract.CommonDataKinds.Email.CONTENT_URI
    public static final Uri ECLAIR_CONTENT_URI =
            Uri.parse("content://com.android.contacts/data/emails");

    private final Context mContext;
    private final ThreadHelper threadHelper = new ThreadHelper();

    private final MarkAsReadTypes mMarkAsReadType;
    private final PersonLookup mPersonLookup;
    private final MessageGenerator mMessageGenerator;
    private final boolean mMarkAsReadOnRestore;

    public MessageConverter(Context context, Preferences preferences,
                            String userEmail,
                            PersonLookup personLookup,
                            ContactAccessor contactAccessor) {
        mContext = context;
        mMarkAsReadType = preferences.getMarkAsReadType();
        mPersonLookup = personLookup;
        mMarkAsReadOnRestore = preferences.getMarkAsReadOnRestore();

        String referenceUid = preferences.getReferenceUid();
        if (referenceUid == null) {
            referenceUid = generateReferenceValue();
            preferences.setReferenceUid(referenceUid);
        }

        final ContactGroup backupContactGroup = preferences.getBackupContactGroup();
        ContactGroupIds allowedIds = contactAccessor.getGroupContactIds(context.getContentResolver(), backupContactGroup);
        if (LOCAL_LOGV) Log.v(TAG, "whitelisted ids for backup: " + allowedIds);

        mMessageGenerator = new MessageGenerator(mContext,
                new Address(userEmail),
                AddressStyle.getEmailAddressStyle(preferences),
                new HeaderGenerator(referenceUid, preferences.getVersion(true)),
                mPersonLookup,
                preferences.getMailSubjectPrefix(),
                allowedIds,
                new MmsSupport(mContext.getContentResolver(), mPersonLookup));
    }

    private boolean markAsSeen(DataType dataType, Map<String, String> msgMap) {
        switch (mMarkAsReadType) {
            case MESSAGE_STATUS:
                switch (dataType) {
                    case SMS:
                        return "1".equals(msgMap.get(SmsConsts.READ));
                    case MMS:
                        return "1".equals(msgMap.get(MmsConsts.READ));
                    default:
                        return true;
                }
            case UNREAD:
                return false;
            case READ:
            default:
                return true;
        }
    }

    public @NotNull ConversionResult convertMessages(final Cursor cursor, DataType dataType)
            throws MessagingException {

        final Map<String, String> msgMap = getMessageMap(cursor);
        final Message m;
        switch (dataType) {
            case WHATSAPP:
                m = mMessageGenerator.messageFromMapWhatsApp(cursor);
                break;
            default:
                m = mMessageGenerator.messageForDataType(msgMap, dataType);
                break;
        }
        final ConversionResult result = new ConversionResult(dataType);
        if (m != null) {
            m.setFlag(Flag.SEEN, markAsSeen(dataType, msgMap));
            result.add(m, msgMap);
        }

        return result;
    }


    public @NotNull ContentValues messageToContentValues(final Message message)
            throws IOException, MessagingException {
        if (message == null) throw new MessagingException("message is null");

        final ContentValues values = new ContentValues();
        switch (getDataType(message)) {
            case SMS:
                if (message.getBody() == null) throw new MessagingException("body is null");

                InputStream is = MimeUtility.decodeBody(message.getBody());
                if (is == null) {
                    throw new MessagingException("body.getInputStream() is null for " + message.getBody());
                }
                final String body = IOUtils.toString(is);
                final String address = Headers.get(message, Headers.ADDRESS);
                values.put(SmsConsts.BODY, body);
                values.put(SmsConsts.ADDRESS, address);
                values.put(SmsConsts.TYPE, Headers.get(message, Headers.TYPE));
                values.put(SmsConsts.PROTOCOL, Headers.get(message, Headers.PROTOCOL));
                values.put(SmsConsts.SERVICE_CENTER, Headers.get(message, Headers.SERVICE_CENTER));
                values.put(SmsConsts.DATE, Headers.get(message, Headers.DATE));
                values.put(SmsConsts.STATUS, Headers.get(message, Headers.STATUS));
                values.put(SmsConsts.THREAD_ID, threadHelper.getThreadId(mContext, address));
                values.put(SmsConsts.READ,
                        mMarkAsReadOnRestore ? "1" : Headers.get(message, Headers.READ));
                break;
            case CALLLOG:
                values.put(CallLog.Calls.NUMBER, Headers.get(message, Headers.ADDRESS));
                values.put(CallLog.Calls.TYPE, Integer.valueOf(Headers.get(message, Headers.TYPE)));
                values.put(CallLog.Calls.DATE, Headers.get(message, Headers.DATE));
                values.put(CallLog.Calls.DURATION, Long.valueOf(Headers.get(message, Headers.DURATION)));
                values.put(CallLog.Calls.NEW, 0);

                PersonRecord record = mPersonLookup.lookupPerson(Headers.get(message, Headers.ADDRESS));
                if (!record.isUnknown()) {
                    values.put(CallLog.Calls.CACHED_NAME, record.getName());
                    values.put(CallLog.Calls.CACHED_NUMBER_TYPE, -2);
                }

                break;
            default:
                throw new MessagingException("don't know how to restore " + getDataType(message));
        }

        return values;
    }

    public DataType getDataType(Message message) {
        final String dataTypeHeader = Headers.get(message, Headers.DATATYPE);
        final String typeHeader = Headers.get(message, Headers.TYPE);
        //we have two possible header sets here
        //legacy:  there is Headers.DATATYPE .Headers.TYPE
        //         contains either the string "mms" or an integer which is the internal type of the sms
        //current: there IS a Headers.DATATYPE containing a string representation of Headers.DataType
        //         Headers.TYPE then contains the type of the sms, mms or calllog entry
        //The current header set was introduced in version 1.2.00
        if (dataTypeHeader == null) {
            return MmsConsts.LEGACY_HEADER.equalsIgnoreCase(typeHeader) ? DataType.MMS : DataType.SMS;
        } else {
            try {
                return DataType.valueOf(dataTypeHeader.toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                return DataType.SMS; // whateva
            }
        }
    }

    private Map<String, String> getMessageMap(Cursor cursor) {
        final String[] columns = cursor.getColumnNames();
        final Map<String, String> msgMap = new HashMap<String, String>(columns.length);
        for (String column : columns) {
            String value;
            try {
                final int index = cursor.getColumnIndex(column);
                if (index != -1) {
                    value = cursor.getString(index);
                } else {
                    continue;
                }
            } catch (SQLiteException ignored) {
                // this can happen in case of BLOBS in the DB
                // column type checking is API level >= 11
                value = "[BLOB]";
            }
            msgMap.put(column, value);
        }
        return msgMap;
    }

    private static String generateReferenceValue() {
        final StringBuilder sb = new StringBuilder();
        final Random random = new Random();
        for (int i = 0; i < 24; i++) {
            sb.append(Integer.toString(random.nextInt(35), 36));
        }
        return sb.toString();
    }
}
