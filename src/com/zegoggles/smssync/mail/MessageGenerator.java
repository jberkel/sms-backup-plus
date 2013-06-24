package com.zegoggles.smssync.mail;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.text.TextUtils;
import android.util.Log;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.TextBody;
import com.github.jberkel.whassup.model.WhatsAppMessage;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.SmsConsts;
import com.zegoggles.smssync.contacts.GroupContactIds;
import com.zegoggles.smssync.preferences.AddressStyle;
import com.zegoggles.smssync.preferences.CallLogTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.Attachment.*;

class MessageGenerator {
    private final Context mContext;
    private final HeaderGenerator mHeaderGenerator;
    private final Address mUserAddress;
    private final PersonLookup mPersonLookup;
    private final boolean mPrefix;
    private final GroupContactIds mAllowedIds;
    private final CallFormatter mCallFormatter;
    private final AddressStyle mAddressStyle;

    public MessageGenerator(Context context,
                            Address userAddress,
                            AddressStyle addressStyle,
                            HeaderGenerator headerGenerator,
                            PersonLookup personLookup,
                            boolean mailSubjectPrefix,
                            GroupContactIds allowedIds) {
        mHeaderGenerator = headerGenerator;
        mUserAddress = userAddress;
        mAddressStyle = addressStyle;
        mContext = context;
        mPersonLookup = personLookup;
        mPrefix = mailSubjectPrefix;
        mAllowedIds = allowedIds;
        mCallFormatter = new CallFormatter(mContext.getResources());
    }

    public  @Nullable Message messageForDataType(Map<String, String> msgMap, DataType dataType) throws MessagingException {
        switch (dataType) {
            case SMS: return messageFromMapSms(msgMap);
            case MMS: return messageFromMapMms(msgMap);
            case CALLLOG: return messageFromMapCallLog(msgMap);
            default: return null;
        }
    }

    public @Nullable Message messageFromMapSms(Map<String, String> msgMap) throws MessagingException {
        final String address = msgMap.get(SmsConsts.ADDRESS);
        if (TextUtils.isEmpty(address)) return null;

        PersonRecord record = mPersonLookup.lookupPerson(address);
        if (!includePersonInBackup(record, DataType.SMS)) return null;

        final Message msg = new MimeMessage();
        msg.setSubject(getSubject(DataType.SMS, record));
        msg.setBody(new TextBody(msgMap.get(SmsConsts.BODY)));

        final int messageType = toInt(msgMap.get(SmsConsts.TYPE));
        if (SmsConsts.MESSAGE_TYPE_INBOX == messageType) {
            // Received message
            msg.setFrom(record.getAddress(mAddressStyle));
            msg.setRecipient(Message.RecipientType.TO, mUserAddress);
        } else {
            // Sent message
            msg.setRecipient(Message.RecipientType.TO, record.getAddress(mAddressStyle));
            msg.setFrom(mUserAddress);
        }

        Date sentDate;
        try {
            sentDate = new Date(Long.valueOf(msgMap.get(SmsConsts.DATE)));
        } catch (NumberFormatException n) {
            Log.e(TAG, "error parsing date", n);
            sentDate = new Date();
        }
        mHeaderGenerator.setHeaders(msg, msgMap, DataType.SMS, address, record, sentDate, messageType);
        return msg;
    }

    public @Nullable Message messageFromMapMms(Map<String, String> msgMap) throws MessagingException {
        if (LOCAL_LOGV) Log.v(TAG, "messageFromMapMms(" + msgMap + ")");

        final Uri msgRef = Uri.withAppendedPath(Consts.MMS_PROVIDER, msgMap.get(MmsConsts.ID));
        Cursor curAddr = mContext.getContentResolver().query(Uri.withAppendedPath(msgRef, "addr"),
                null, null, null, null);

        // TODO: this is probably not the best way to determine if a message is inbound or outbound
        boolean inbound = true;
        final List<String> recipients = new ArrayList<String>(); // MMS recipients
        while (curAddr != null && curAddr.moveToNext()) {
            final String address = curAddr.getString(curAddr.getColumnIndex("address"));
            //final int type       = curAddr.getInt(curAddr.getColumnIndex("type"));

            if (MmsConsts.INSERT_ADDRESS_TOKEN.equals(address)) {
                inbound = false;
            } else {
                recipients.add(address);
            }
        }
        if (curAddr != null) curAddr.close();
        if (recipients.isEmpty()) {
            Log.w(TAG, "no recipients found");
            return null;
        }

        final String address = recipients.get(0);
        final PersonRecord[] records = new PersonRecord[recipients.size()];
        final Address[] addresses = new Address[recipients.size()];
        for (int i = 0; i < recipients.size(); i++) {
            records[i] = mPersonLookup.lookupPerson(recipients.get(i));
            addresses[i] = records[i].getAddress(mAddressStyle);
        }

        boolean backup = false;
        for (PersonRecord r : records) {
            if (includePersonInBackup(r, DataType.MMS)) {
                backup = true;
                break;
            }
        }
        if (!backup) return null;

        final Message msg = new MimeMessage();
        msg.setSubject(getSubject(DataType.MMS, records[0]));
        final int msg_box = Integer.parseInt(msgMap.get("msg_box"));
        if (inbound) {
            // msg_box == MmsConsts.MESSAGE_BOX_INBOX does not work
            msg.setFrom(records[0].getAddress(mAddressStyle));
            msg.setRecipient(Message.RecipientType.TO, mUserAddress);
        } else {
            msg.setRecipients(Message.RecipientType.TO, addresses);
            msg.setFrom(mUserAddress);
        }

        Date sentDate;
        try {
            sentDate = new Date(1000 * Long.valueOf(msgMap.get(MmsConsts.DATE)));
        } catch (NumberFormatException n) {
            Log.e(TAG, "error parsing date", n);
            sentDate = new Date();
        }
        mHeaderGenerator.setHeaders(msg, msgMap, DataType.MMS, address, records[0], sentDate, msg_box);
        // deal with attachments
        MimeMultipart body = new MimeMultipart();
        for (BodyPart p : getBodyParts(Uri.withAppendedPath(msgRef, "part"))) {
            body.addBodyPart(p);
        }
        msg.setBody(body);
        return msg;
    }

    public @Nullable Message messageFromMapCallLog(Map<String, String> msgMap) throws MessagingException {
        final String address = msgMap.get(CallLog.Calls.NUMBER);
        final int callType = Integer.parseInt(msgMap.get(CallLog.Calls.TYPE));

        if (TextUtils.isEmpty(address) || !CallLogTypes.isTypeEnabled(mContext, callType)) {
            if (LOCAL_LOGV) Log.v(TAG, "ignoring call log entry: " + msgMap);
            return null;
        }
        PersonRecord record = mPersonLookup.lookupPerson(address);
        if (!includePersonInBackup(record, DataType.CALLLOG)) return null;

        final Message msg = new MimeMessage();
        msg.setSubject(getSubject(DataType.CALLLOG, record));

        switch (callType) {
            case CallLog.Calls.OUTGOING_TYPE:
                msg.setFrom(mUserAddress);
                msg.setRecipient(Message.RecipientType.TO, record.getAddress(mAddressStyle));
                break;
            case CallLog.Calls.MISSED_TYPE:
            case CallLog.Calls.INCOMING_TYPE:
                msg.setFrom(record.getAddress(mAddressStyle));
                msg.setRecipient(Message.RecipientType.TO, mUserAddress);
                break;
            default:
                // some weird phones seem to have SMS in their call logs, which is
                // not part of the official API.
                Log.i(TAG, "ignoring unknown call type: " + callType);
                return null;
        }

        final int duration = msgMap.get(CallLog.Calls.DURATION) == null ? 0 :
                Integer.parseInt(msgMap.get(CallLog.Calls.DURATION));
        final StringBuilder text = new StringBuilder();

        if (callType != CallLog.Calls.MISSED_TYPE) {
            text.append(duration)
                    .append("s")
                    .append(" (").append(mCallFormatter.formattedCallDuration(duration)).append(")")
                    .append("\n");
        }
        text.append(record.getNumber())
                .append(" (").append(mCallFormatter.callTypeString(callType, null)).append(")");

        msg.setBody(new TextBody(text.toString()));

        Date sentDate;
        try {
            sentDate = new Date(Long.valueOf(msgMap.get(CallLog.Calls.DATE)));
        } catch (NumberFormatException n) {
            Log.e(TAG, "error parsing date", n);
            sentDate = new Date();
        }
        mHeaderGenerator.setHeaders(msg, msgMap, DataType.CALLLOG, address, record, sentDate, callType);
        return msg;
    }

    public @Nullable Message messageFromMapWhatsApp(Cursor cursor) throws MessagingException {
        WhatsAppMessage whatsapp = new WhatsAppMessage(cursor);
        // we don't deal with group messages (yet)
        if (whatsapp.isGroupMessage()) return null;
        final String address = whatsapp.getNumber();
        if (TextUtils.isEmpty(address)) {
            return null;
        }
        PersonRecord record = mPersonLookup.lookupPerson(address);
        if (!includePersonInBackup(record, DataType.WHATSAPP)) return null;

        final Message msg = new MimeMessage();

        if (whatsapp.hasMediaAttached()) {
            MimeMultipart body = new MimeMultipart();
            if (whatsapp.hasText()) {
                body.addBodyPart(createTextPart(whatsapp.getFilteredText()));
            }
            body.addBodyPart(createPartFromFile(whatsapp.getMedia().getFile(), whatsapp.getMedia().getMimeType()));
            msg.setBody(body);
        } else if (whatsapp.hasText()) {
            msg.setBody(new TextBody(whatsapp.getFilteredText()));
        } else {
            // no media / no text, pointless
            return null;
        }
        msg.setSubject(getSubject(DataType.WHATSAPP, record));

        if (whatsapp.isReceived()) {
            // Received message
            msg.setFrom(record.getAddress(mAddressStyle));
            msg.setRecipient(Message.RecipientType.TO, mUserAddress);
        } else {
            // Sent message
            msg.setRecipient(Message.RecipientType.TO, record.getAddress(mAddressStyle));
            msg.setFrom(mUserAddress);
        }
        mHeaderGenerator.setHeaders(msg, new HashMap<String, String>(), DataType.WHATSAPP, address, record,
                whatsapp.getTimestamp(), whatsapp.getStatus()
        );
        return msg;
    }

    private List<BodyPart> getBodyParts(final Uri uriPart) throws MessagingException {
        final List<BodyPart> parts = new ArrayList<BodyPart>();
        Cursor curPart = mContext.getContentResolver().query(uriPart, null, null, null, null);

        // _id, mid, seq, ct, name, chset, cd, fn, cid, cl, ctt_s, ctt_t, _data, text
        while (curPart != null && curPart.moveToNext()) {
            final String id = curPart.getString(curPart.getColumnIndex("_id"));
            final String contentType = curPart.getString(curPart.getColumnIndex("ct"));
            final String fileName = curPart.getString(curPart.getColumnIndex("cl"));
            final String text = curPart.getString(curPart.getColumnIndex("text"));

            if (LOCAL_LOGV) Log.v(TAG, String.format(Locale.ENGLISH, "processing part %s, name=%s (%s)", id,
                    fileName, contentType));

            if (!TextUtils.isEmpty(contentType) && contentType.startsWith("text/") && !TextUtils.isEmpty(text)) {
                // text
                parts.add(new MimeBodyPart(new TextBody(text), contentType));
            } else //noinspection StatementWithEmptyBody
                if ("application/smil".equalsIgnoreCase(contentType)) {
                    // silently ignore SMIL stuff
                } else {
                    // attach everything else
                    final Uri partUri = Uri.withAppendedPath(Consts.MMS_PROVIDER, "part/" + id);
                    parts.add(createPartFromUri(mContext.getContentResolver(), partUri, fileName, contentType));
                }
        }

        if (curPart != null) curPart.close();
        return parts;
    }

    private String getSubject(@NotNull DataType type, @NotNull PersonRecord record) {
        return mPrefix ?
                String.format(Locale.ENGLISH, "[%s] %s", type.getFolder(mContext), record.getName()) :
                mContext.getString(type.withField, record.getName());
    }

    private boolean includePersonInBackup(PersonRecord record, DataType type) {
        final boolean backup = (mAllowedIds == null || mAllowedIds.ids.contains(record.getContactId()));
        if (LOCAL_LOGV && !backup) Log.v(TAG, "not backing up " + type + " / " + record);
        return backup;
    }

    private int toInt(String s) {
        try {
             return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
