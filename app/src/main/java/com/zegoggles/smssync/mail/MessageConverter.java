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
import android.provider.CallLog;
import android.provider.Telephony;
import androidx.annotation.NonNull;
import android.util.Log;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.MimeUtility;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.preferences.MarkAsReadTypes;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.utils.ThreadHelper;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class MessageConverter {
    private final Context context;
    private final ThreadHelper threadHelper = new ThreadHelper();

    private final MarkAsReadTypes markAsReadType;
    private final PersonLookup personLookup;
    private final MessageGenerator messageGenerator;
    private final boolean markAsReadOnRestore;

    public MessageConverter(Context context,
                            Preferences preferences,
                            String userEmail,
                            PersonLookup personLookup,
                            ContactAccessor contactAccessor) {
        this.context = context;
        markAsReadType = preferences.getMarkAsReadType();
        this.personLookup = personLookup;
        markAsReadOnRestore = preferences.getMarkAsReadOnRestore();

        String referenceUid = preferences.getReferenceUid();
        if (referenceUid == null) {
            referenceUid = generateReferenceValue();
            preferences.setReferenceUid(referenceUid);
        }

        final ContactGroup backupContactGroup = preferences.getBackupContactGroup();
        ContactGroupIds allowedIds = contactAccessor.getGroupContactIds(context.getContentResolver(), backupContactGroup);
        if (LOCAL_LOGV) Log.v(TAG, "whitelisted ids for backup: " + allowedIds);

        messageGenerator = new MessageGenerator(context,
                new Address(userEmail),
                preferences.getEmailAddressStyle(),
                new HeaderGenerator(referenceUid, App.getVersionCode(context)),
                personLookup,
                preferences.getMailSubjectPrefix(),
                allowedIds,
                new MmsSupport(context.getContentResolver(), personLookup),
                preferences.getCallLogType(),
                preferences.getDataTypePreferences());
    }

    private boolean markAsSeen(DataType dataType, Map<String, String> msgMap) {
        switch (markAsReadType) {
            case MESSAGE_STATUS:
                switch (dataType) {
                    case SMS:
                        return "1".equals(msgMap.get(Telephony.TextBasedSmsColumns.READ));
                    case MMS:
                        return "1".equals(msgMap.get(Telephony.BaseMmsColumns.READ));
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

    public @NonNull ConversionResult convertMessages(final Cursor cursor, DataType dataType)
            throws MessagingException {

        final Map<String, String> msgMap = getMessageMap(cursor);
        final Message m = messageGenerator.messageForDataType(msgMap, dataType);
        final ConversionResult result = new ConversionResult(dataType);
        if (m != null) {
            m.setFlag(Flag.SEEN, markAsSeen(dataType, msgMap));
            result.add(m, msgMap);
        }

        return result;
    }


    public @NonNull ContentValues messageToContentValues(final Message message)
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
                values.put(Telephony.TextBasedSmsColumns.BODY, body);
                values.put(Telephony.TextBasedSmsColumns.ADDRESS, address);
                values.put(Telephony.TextBasedSmsColumns.TYPE, Headers.get(message, Headers.TYPE));
                values.put(Telephony.TextBasedSmsColumns.PROTOCOL, Headers.get(message, Headers.PROTOCOL));
                values.put(Telephony.TextBasedSmsColumns.SERVICE_CENTER, Headers.get(message, Headers.SERVICE_CENTER));
                values.put(Telephony.TextBasedSmsColumns.DATE, Headers.get(message, Headers.DATE));
                values.put(Telephony.TextBasedSmsColumns.STATUS, Headers.get(message, Headers.STATUS));
                values.put(Telephony.TextBasedSmsColumns.THREAD_ID, threadHelper.getThreadId(context, address));
                values.put(Telephony.TextBasedSmsColumns.READ,
                        markAsReadOnRestore ? "1" : Headers.get(message, Headers.READ));
                break;
            case CALLLOG:
                values.put(CallLog.Calls.NUMBER, Headers.get(message, Headers.ADDRESS));
                values.put(CallLog.Calls.TYPE, Integer.valueOf(Headers.get(message, Headers.TYPE)));
                values.put(CallLog.Calls.DATE, Headers.get(message, Headers.DATE));
                values.put(CallLog.Calls.DURATION, Long.valueOf(Headers.get(message, Headers.DURATION)));
                values.put(CallLog.Calls.NEW, 0);

                PersonRecord record = personLookup.lookupPerson(Headers.get(message, Headers.ADDRESS));
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

    public DataType getDataType(Message message) throws MessagingException {
        final String dataTypeHeader = Headers.get(message, Headers.DATATYPE);
        if (dataTypeHeader == null) {
            throw new MessagingException("Datatype header is missing");
        }
        try {
            return DataType.valueOf(dataTypeHeader.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new MessagingException("Invalid header: "+dataTypeHeader, e);
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
