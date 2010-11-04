/*
 * Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
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
package com.zegoggles.smssync;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

import com.zegoggles.smssync.PrefStore;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Message.RecipientType;
import com.fsck.k9.mail.filter.Base64OutputStream;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.TextBody;
import com.fsck.k9.mail.store.LocalStore.LocalAttachmentBody;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.codec.EncoderUtil;
import static com.zegoggles.smssync.App.*;
import static com.zegoggles.smssync.Consts.TAG;

public class CursorToMessage {
    //ContactsContract.CommonDataKinds.Email.CONTENT_URI
    public static final Uri ECLAIR_CONTENT_URI = Uri.parse("content://com.android.contacts/data/emails");

    // PhoneLookup.CONTENT_FILTER_URI
    public static final Uri ECLAIR_CONTENT_FILTER_URI = Uri.parse("content://com.android.contacts/phone_lookup");

    private static final String REFERENCE_UID_TEMPLATE = "<%s.%s@sms-backup-plus.local>";
    private static final String MSG_ID_TEMPLATE = "<%s@sms-backup-plus.local>";

    private static final boolean NEW_CONTACT_API = Integer.parseInt(Build.VERSION.SDK) >=
                                                   Build.VERSION_CODES.ECLAIR;

    private static final String[] PHONE_PROJECTION = NEW_CONTACT_API  ?
          new String[] { Contacts._ID, Contacts.DISPLAY_NAME } :
          new String[] { Phones.PERSON_ID, People.NAME, Phones.NUMBER };


    private static final String UNKNOWN_NUMBER = "unknown_number";
    private static final String UNKNOWN_EMAIL = "unknown.email";
    private static final String UNKNOWN_PERSON = "unknown.person";

    private static final int MAX_PEOPLE_CACHE_SIZE = 500;

    private Context mContext;
    private Address mUserAddress;

    private Map<String, PersonRecord> mPeopleCache;

    private String mReferenceValue;
    private boolean mMarkAsRead = false;

    private enum Style { NAME, NAME_AND_NUMBER, NUMBER };
    private static Style mStyle = Style.NAME;

    public static interface Headers {
        String ID = "X-smssync-id";
        String ADDRESS = "X-smssync-address";
        String TYPE  = "X-smssync-type";
        String DATE =  "X-smssync-date";
        String THREAD_ID = "X-smssync-thread";
        String READ = "X-smssync-read";
        String STATUS = "X-smssync-status";
        String PROTOCOL = "X-smssync-protocol";
        String SERVICE_CENTER = "X-smssync-service_center";
        String BACKUP_TIME = "X-smssync-backup-time";
        String VERSION = "X-smssync-version";
    }

    public CursorToMessage(Context ctx, String userEmail) {
        mContext = ctx;
        // simple LRU cache
        mPeopleCache = new LinkedHashMap<String, PersonRecord>(MAX_PEOPLE_CACHE_SIZE+1, .75F, true) {
            @Override
            public boolean removeEldestEntry(Map.Entry eldest) {
              return size() > MAX_PEOPLE_CACHE_SIZE;
            }
        };

        mUserAddress = new Address(userEmail);

        mReferenceValue = PrefStore.getReferenceUid(ctx);
        if (mReferenceValue == null) {
          mReferenceValue = generateReferenceValue(userEmail);
          PrefStore.setReferenceUid(ctx, mReferenceValue);
        }

        mMarkAsRead = PrefStore.getMarkAsRead(ctx);

        if (PrefStore.getEmailAddressStyle(ctx) != null) {
          mStyle = Style.valueOf(PrefStore.getEmailAddressStyle(ctx).toUpperCase());
        }

        Log.d(TAG, String.format("using %s contacts API", NEW_CONTACT_API ? "new" : "old"));
    }

    public ConversionResult cursorToMessages(final Cursor cursor, final int maxEntries, boolean isMms) throws MessagingException {
        List<Message> messageList = new ArrayList<Message>(maxEntries);
        long maxDate = PrefStore.DEFAULT_MAX_SYNCED_DATE;
        final String[] columns = cursor.getColumnNames();

        while (messageList.size() < maxEntries && cursor.moveToNext()) {
            final long date = cursor.getLong(cursor.getColumnIndex(SmsConsts.DATE));
            if (date > maxDate) {
              maxDate = date;
            }

            Map<String, String> msgMap = new HashMap<String, String>(columns.length);
            for (int i = 0; i < columns.length; i++) {
                msgMap.put(columns[i], cursor.getString(i));
            }

            Message m;
            if (isMms) {
                m = messageFromMapMms(msgMap);
            } else {
                m = messageFromMapSms(msgMap);
            }

            if (m != null) messageList.add(m);
        }

        ConversionResult result = new ConversionResult();
        result.maxDate = maxDate;
        result.messageList = messageList;
        return result;
    }

    private Message messageFromMapSms(Map<String, String> msgMap) throws MessagingException {
        Message msg = new MimeMessage();

        String address = msgMap.get(SmsConsts.ADDRESS);
        if (address == null || address.trim().length() == 0) {
           return null;
        }

        PersonRecord record = lookupPerson(address);
        if (PrefStore.getMailSubjectPrefix(mContext))
          msg.setSubject("[" + PrefStore.getImapFolder(mContext) + "] " + record.getName());
        else
          msg.setSubject("SMS with " + record.getName());

        TextBody body = new TextBody(msgMap.get(SmsConsts.BODY));

        int messageType = Integer.valueOf(msgMap.get(SmsConsts.TYPE));
        if (SmsConsts.MESSAGE_TYPE_INBOX == messageType) {
            // Received message
            msg.setFrom(record.getAddress());
            msg.setRecipient(RecipientType.TO, mUserAddress);
        } else {
            // Sent message
            msg.setRecipient(RecipientType.TO, record.getAddress());
            msg.setFrom(mUserAddress);
        }

        msg.setBody(body);

        try {
          Date then = new Date(Long.valueOf(msgMap.get(SmsConsts.DATE)));
          msg.setSentDate(then);
          msg.setInternalDate(then);
          msg.setHeader("Message-ID", createMessageId(then, address, messageType));
        } catch (NumberFormatException n) {
          Log.e(TAG, "error parsing date", n);
        }

        // Threading by person ID, not by thread ID. I think this value is more stable.
        msg.setHeader("References",
                      String.format(REFERENCE_UID_TEMPLATE, mReferenceValue, sanitize(record._id)));
        msg.setHeader(Headers.ID, msgMap.get(SmsConsts.ID));
        msg.setHeader(Headers.ADDRESS, sanitize(address));
        msg.setHeader(Headers.TYPE, msgMap.get(SmsConsts.TYPE));
        msg.setHeader(Headers.DATE, msgMap.get(SmsConsts.DATE));
        msg.setHeader(Headers.THREAD_ID, msgMap.get(SmsConsts.THREAD_ID));
        msg.setHeader(Headers.READ, msgMap.get(SmsConsts.READ));
        msg.setHeader(Headers.STATUS, msgMap.get(SmsConsts.STATUS));
        msg.setHeader(Headers.PROTOCOL, msgMap.get(SmsConsts.PROTOCOL));
        msg.setHeader(Headers.SERVICE_CENTER, msgMap.get(SmsConsts.SERVICE_CENTER));
        msg.setHeader(Headers.BACKUP_TIME, new Date().toGMTString());
        msg.setHeader(Headers.VERSION, PrefStore.getVersion(mContext, true));
        msg.setFlag(Flag.SEEN, mMarkAsRead);

        return msg;
    }

    private Message messageFromMapMms(Map<String, String> msgMap) throws MessagingException {
        Message msg = new MimeMessage();
        String address = null;
        boolean inbound = true;

        Uri msgRef = Uri.withAppendedPath(ServiceBase.MMS_PROVIDER, msgMap.get(MmsConsts.ID));
        Uri uriAddr = Uri.withAppendedPath(msgRef, "addr");
        Cursor curAddr = mContext.getContentResolver().query(uriAddr, null, null, null, null);

        if (curAddr == null) {
          Log.w(TAG, "Cursor == null");
          return null;
        }

        // TODO: this is probably not the best way to determine if a message is inbound or outbound.
        // Also, messages can have multiple recipients (more than 2 addresses)
        if (curAddr.getCount() > 1) {
          curAddr.moveToNext();
            address = curAddr.getString(curAddr.getColumnIndex("address"));

            if (MmsConsts.INSERT_ADDRESS_TOKEN.equals(address)) {
              inbound = false;
              curAddr.moveToNext();
              address = curAddr.getString(curAddr.getColumnIndex("address"));
            }
        }

        if (address == null || address.trim().length() == 0) {
           return null;
        }

        MimeMultipart body = new MimeMultipart();
        Uri uriPart = Uri.withAppendedPath(msgRef, "part");
        Cursor curPart = mContext.getContentResolver().query(uriPart, null, null, null, null);

        // _id, mid, seq, ct, name, chset, cd, fn, cid, cl, ctt_s, ctt_t, _data, text
        while(curPart.moveToNext()) {
          String id = curPart.getString(curPart.getColumnIndex("_id"));
          String contentType = curPart.getString(curPart.getColumnIndex("ct"));
          if (contentType.equals("image/jpeg")) {
            String name = "attachment.jpg";
            Uri partUri = Uri.withAppendedPath(ServiceBase.MMS_PROVIDER, "part/" + id);
            MmsAttachmentBody attachment = new MmsAttachmentBody(partUri, mContext);

            BodyPart imagePart = new MimeBodyPart(attachment, contentType);
            imagePart.setHeader(MimeHeader.HEADER_CONTENT_TYPE,
                        String.format("%s;\n name=\"%s\"",
                            contentType, name));
            imagePart.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, "base64");
            //imagePart.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION,
                //      String.format("attachment;\n filename=\"%s\";\n size=%d", name, size);
            body.addBodyPart(imagePart);
          } else if (contentType.equals("text/plain")) {
            Body textBody = new TextBody(curPart.getString(curPart.getColumnIndex("text")));
            BodyPart textPart = new MimeBodyPart(textBody);
            body.addBodyPart(textPart);
          }
      }

        PersonRecord record = lookupPerson(address);
        if (PrefStore.getMailSubjectPrefix(mContext))
          msg.setSubject("[" + PrefStore.getImapFolder(mContext) + "] " + record.getName());
        else
          msg.setSubject("SMS with " + record.getName());

        if (inbound) {
            // Received message
            msg.setFrom(record.getAddress());
            msg.setRecipient(RecipientType.TO, mUserAddress);
        } else {
            // Sent message
            msg.setRecipient(RecipientType.TO, record.getAddress());
            msg.setFrom(mUserAddress);
        }

        msg.setBody(body);

        try {
          Date then = new Date(1000 * Long.valueOf(msgMap.get(MmsConsts.DATE)));
          msg.setSentDate(then);
          msg.setInternalDate(then);
          msg.setHeader("Message-ID", createMessageId(then, address, 1));
        } catch (NumberFormatException n) {
          Log.e(TAG, "error parsing date", n);
        }

        // Threading by person ID, not by thread ID. I think this value is more stable.
        msg.setHeader("References",
                      String.format(REFERENCE_UID_TEMPLATE, mReferenceValue, sanitize(record._id)));
        msg.setHeader(Headers.ID, msgMap.get(MmsConsts.ID));
        msg.setHeader(Headers.ADDRESS, sanitize(address));
        msg.setHeader(Headers.TYPE, "mms");
        msg.setHeader(Headers.DATE, msgMap.get(MmsConsts.DATE));
        msg.setHeader(Headers.THREAD_ID, msgMap.get(MmsConsts.THREAD_ID));
        msg.setHeader(Headers.READ, msgMap.get(MmsConsts.READ));
        //msg.setHeader(Headers.STATUS, msgMap.get(SmsConsts.STATUS));
        //msg.setHeader(Headers.PROTOCOL, msgMap.get(SmsConsts.PROTOCOL));
        //msg.setHeader(Headers.SERVICE_CENTER, msgMap.get(SmsConsts.SERVICE_CENTER));
        msg.setHeader(Headers.BACKUP_TIME, new Date().toGMTString());
        msg.setHeader(Headers.VERSION, PrefStore.getVersion(mContext, true));
        msg.setFlag(Flag.SEEN, mMarkAsRead);

        return msg;
    }

    /**
      * Create a message-id based on message date, phone number and message
      * type.
      */
    private String createMessageId(Date sent, String address, int type) {
      try {
        MessageDigest digest = java.security.MessageDigest.getInstance("MD5");

        digest.update(Long.toString(sent.getTime()).getBytes("UTF-8"));
        digest.update(address.getBytes("UTF-8"));
        digest.update(Integer.toString(type).getBytes("UTF-8"));

        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
          sb.append(String.format("%02x", b));
        }
        return String.format(MSG_ID_TEMPLATE, sb.toString());
      } catch (java.io.UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      } catch (java.security.NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    }

    /* Look up a person */
    public PersonRecord lookupPerson(final String address) {
        if (!mPeopleCache.containsKey(address)) {
            Uri personUri = Uri.withAppendedPath(NEW_CONTACT_API ? ECLAIR_CONTENT_FILTER_URI :
                                                 Phones.CONTENT_FILTER_URL, Uri.encode(address));

            Cursor c = mContext.getContentResolver().query(personUri, PHONE_PROJECTION, null, null, null);
            final PersonRecord record = new PersonRecord();
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(c.getColumnIndex(PHONE_PROJECTION[0]));
                record._id    = String.valueOf(id);
                record.name   = sanitize(c.getString(c.getColumnIndex(PHONE_PROJECTION[1])));
                record.email  = getPrimaryEmail(id, record.number);
                record.number = sanitize(NEW_CONTACT_API ? address :
                                                  c.getString(c.getColumnIndex(PHONE_PROJECTION[2])));
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "Looked up unknown address: " + address);

                record._id    = sanitize(address);
                record.number = sanitize(address);
                record.email  = encodeLocal(address) + "@" + UNKNOWN_PERSON;
                record.unknown = true;
            }
            mPeopleCache.put(address, record);

            if (c != null) c.close();
        }
        return mPeopleCache.get(address);
    }

    private String getPrimaryEmail(final long personId, final String number) {
        if (personId <= 0) {
          return getUnknownEmail(number);
        }
        String primaryEmail = null;

        // Get all e-mail addresses for that person.
        Cursor c;
        int columnIndex;
        if (NEW_CONTACT_API) {
          c = mContext.getContentResolver().query(
              ECLAIR_CONTENT_URI,
              new String[] { ContactsContract.CommonDataKinds.Email.DATA },
              ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", new String[] { String.valueOf(personId) },
              ContactsContract.CommonDataKinds.Email.IS_PRIMARY + " DESC");
          columnIndex = c != null ? c.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA) : -1;
        } else {
          c = mContext.getContentResolver().query(
              ContactMethods.CONTENT_EMAIL_URI,
              new String[] { ContactMethods.DATA },
              ContactMethods.PERSON_ID + " = ?", new String[] { String.valueOf(personId) },
              ContactMethods.ISPRIMARY + " DESC");
          columnIndex = c!= null ? c.getColumnIndex(ContactMethods.DATA) : -1;
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

        // Return found e-mail address or a dummy "unknown e-mail address"
        // if there is none.
        if (primaryEmail == null) {
          return getUnknownEmail(number);
        } else {
          return primaryEmail;
        }
    }

    private static String sanitize(String s) {
        return s != null ? s.replaceAll("\\p{Cntrl}", "") : null;
    }

    private static String encodeLocal(String s) {
      return (s != null ? EncoderUtil.encodeAddressLocalPart(sanitize(s)) : null);
    }

    private static String encodeDisplayName(String s) {
      return (s != null ? EncoderUtil.encodeAddressDisplayName(sanitize(s)) : null);
    }

    private static String getUnknownEmail(String number) {
        String no = (number == null) ? UNKNOWN_NUMBER : number;
        return encodeLocal(no.trim()) + "@" + UNKNOWN_EMAIL;
    }

    /** Returns whether the given e-mail address is a Gmail address or not. */
    private static boolean isGmailAddress(String email) {
        return email.endsWith("gmail.com") || email.endsWith("googlemail.com");
    }

    private static String generateReferenceValue(String email) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 24; i++) {
        sb.append(Integer.toString((int)(Math.random() * 35), 36));
      }
      return sb.toString();
    }

    public class ConversionResult {
        public long maxDate;
        public List<Message> messageList;
    }

    public static class PersonRecord {
        public String _id, name, email, number;
        public boolean unknown = false;
        private Address mAddress;

        public Address getAddress() {
          if (mAddress == null) {
            switch(mStyle) {
              case NUMBER:
                  mAddress = new Address(email, number);
                  break;
              case NAME_AND_NUMBER:
                  mAddress = new Address(email,
                                         name == null ? number : String.format("%s (%s)", name, number));
                  break;
              case NAME:
                  mAddress = new Address(email, name);
                  break;
              default:
                  mAddress = new Address(email);
            }
          }
          return mAddress;
        }
        public String getName() {
          return sanitize(name != null ? name : number);
        }
    }

    public static class MmsAttachmentBody implements Body
    {
        private Context mContext;
        private Uri mUri;

        public MmsAttachmentBody(Uri uri, Context context)
        {
            mContext = context;
            mUri = uri;
        }

        public InputStream getInputStream() throws MessagingException
        {
            try
            {
                return mContext.getContentResolver().openInputStream(mUri);
            }
            catch (FileNotFoundException fnfe)
            {
                /*
                 * Since it's completely normal for us to try to serve up attachments that
                 * have been blown away, we just return an empty stream.
                 */
                return new ByteArrayInputStream(new byte[0]);
            }
        }

        public void writeTo(OutputStream out) throws IOException, MessagingException
        {
            InputStream in = getInputStream();
            Base64OutputStream base64Out = new Base64OutputStream(out);
            IOUtils.copy(in, base64Out);
            base64Out.close();
        }

        public Uri getContentUri()
        {
            return mUri;
        }
    }
}
