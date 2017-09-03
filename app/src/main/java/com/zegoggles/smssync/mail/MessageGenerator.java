package com.zegoggles.smssync.mail;

import android.content.Context;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.TextBody;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.preferences.AddressStyle;
import com.zegoggles.smssync.preferences.CallLogTypes;
import com.zegoggles.smssync.preferences.Preferences;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

import static com.fsck.k9.mail.internet.MimeMessageHelper.setBody;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.Consts.MMS_PART;

class MessageGenerator {
    private static final String ERROR_PARSING_DATE = "error parsing date";
    private final Context mContext;
    private final HeaderGenerator mHeaderGenerator;
    private final Address mUserAddress;
    private final PersonLookup mPersonLookup;
    private final boolean mPrefix;
    private final @Nullable ContactGroupIds mContactsToBackup;
    private final CallFormatter mCallFormatter;
    private final AddressStyle mAddressStyle;
    private final MmsSupport mMmsSupport;
    private final CallLogTypes mCallLogTypes;

    public MessageGenerator(Context context,
                            Address userAddress,
                            AddressStyle addressStyle,
                            HeaderGenerator headerGenerator,
                            PersonLookup personLookup,
                            boolean mailSubjectPrefix,
                            @Nullable ContactGroupIds contactsToBackup,
                            MmsSupport mmsSupport) {
        mHeaderGenerator = headerGenerator;
        mUserAddress = userAddress;
        mAddressStyle = addressStyle;
        mContext = context;
        mPersonLookup = personLookup;
        mPrefix = mailSubjectPrefix;
        mContactsToBackup = contactsToBackup;
        mCallFormatter = new CallFormatter(mContext.getResources());
        mMmsSupport = mmsSupport;
        mCallLogTypes = CallLogTypes.getCallLogType(new Preferences(context));
    }

    public  @Nullable Message messageForDataType(Map<String, String> msgMap, DataType dataType) throws MessagingException {
        switch (dataType) {
            case SMS: return messageFromMapSms(msgMap);
            case MMS: return messageFromMapMms(msgMap);
            case CALLLOG: return messageFromMapCallLog(msgMap);
            default: return null;
        }
    }

    private @Nullable Message messageFromMapSms(Map<String, String> msgMap) throws MessagingException {
        final String address = msgMap.get(Telephony.TextBasedSmsColumns.ADDRESS);
        if (TextUtils.isEmpty(address)) return null;

        PersonRecord record = mPersonLookup.lookupPerson(address);
        if (!includePersonInBackup(record, DataType.SMS)) return null;

        final Message msg = new MimeMessage();
        msg.setSubject(getSubject(DataType.SMS, record));
        setBody(msg, new TextBody(msgMap.get(Telephony.TextBasedSmsColumns.BODY)));

        final int messageType = toInt(msgMap.get(Telephony.TextBasedSmsColumns.TYPE));
        if (Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX == messageType) {
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
            sentDate = new Date(Long.valueOf(msgMap.get(Telephony.TextBasedSmsColumns.DATE)));
        } catch (NumberFormatException n) {
            Log.e(TAG, ERROR_PARSING_DATE, n);
            sentDate = new Date();
        }
        mHeaderGenerator.setHeaders(msg, msgMap, DataType.SMS, address, record, sentDate, messageType);
        return msg;
    }

    private @Nullable Message messageFromMapMms(Map<String, String> msgMap) throws MessagingException {
        if (LOCAL_LOGV) Log.v(TAG, "messageFromMapMms(" + msgMap + ")");

        final Uri mmsUri = Uri.withAppendedPath(Consts.MMS_PROVIDER, msgMap.get(Telephony.BaseMmsColumns._ID));
        MmsSupport.MmsDetails details = mMmsSupport.getDetails(mmsUri, mAddressStyle);

        if (details.isEmpty()) {
            Log.w(TAG, "no recipients found");
            return null;
        } else if (!includeInBackup(DataType.MMS, details.records)) {
            Log.w(TAG, "no recipients included");
            return null;
        }

        final Message msg = new MimeMessage();
        msg.setSubject(getSubject(DataType.MMS, details.getRecipient()));

        if (details.inbound) {
            // msg_box == MmsConsts.MESSAGE_BOX_INBOX does not work
            msg.setFrom(details.getRecipientAddress());
            msg.setRecipient(Message.RecipientType.TO, mUserAddress);
        } else {
            msg.setRecipients(Message.RecipientType.TO, details.getAddresses());
            msg.setFrom(mUserAddress);
        }

        Date sentDate;
        try {
            sentDate = new Date(1000 * Long.valueOf(msgMap.get(Telephony.BaseMmsColumns.DATE)));
        } catch (NumberFormatException n) {
            Log.e(TAG, ERROR_PARSING_DATE, n);
            sentDate = new Date();
        }
        final int msg_box = toInt(msgMap.get("msg_box"));
        mHeaderGenerator.setHeaders(msg, msgMap, DataType.MMS, details.address, details.getRecipient(), sentDate, msg_box);
        MimeMultipart body = MimeMultipart.newInstance();

        for (BodyPart p : mMmsSupport.getMMSBodyParts(Uri.withAppendedPath(mmsUri, MMS_PART))) {
            body.addBodyPart(p);
        }

        setBody(msg, body);
        return msg;
    }

    private  @Nullable Message messageFromMapCallLog(Map<String, String> msgMap) throws MessagingException {
        final String address = msgMap.get(CallLog.Calls.NUMBER);
        final int callType = toInt(msgMap.get(CallLog.Calls.TYPE));

        if (!mCallLogTypes.isTypeEnabled(callType)) {
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
                Log.w(TAG, "ignoring unknown call type: " + callType);
                return null;
        }

        final int duration = msgMap.get(CallLog.Calls.DURATION) == null ? 0 :
                toInt(msgMap.get(CallLog.Calls.DURATION));

        setBody(msg, new TextBody(mCallFormatter.format(callType, record.getNumber(), duration)));
        Date sentDate;
        try {
            sentDate = new Date(Long.valueOf(msgMap.get(CallLog.Calls.DATE)));
        } catch (NumberFormatException n) {
            Log.e(TAG, ERROR_PARSING_DATE, n);
            sentDate = new Date();
        }
        mHeaderGenerator.setHeaders(msg, msgMap, DataType.CALLLOG, address, record, sentDate, callType);
        return msg;
    }

    private String getSubject(@NotNull DataType type, @NotNull PersonRecord record) {
        return mPrefix ?
                String.format(Locale.ENGLISH, "[%s] %s", type.getFolder(mContext), record.getName()) :
                mContext.getString(type.withField, record.getName());
    }

    private boolean includeInBackup(DataType type, Iterable<PersonRecord> records) {
        for (PersonRecord r : records) {
            if (includePersonInBackup(r, type)) {
                return true;
            }
        }
        return false;
    }

    private boolean includePersonInBackup(PersonRecord record, DataType type) {
        final boolean backup = mContactsToBackup == null || mContactsToBackup.contains(record);
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (LOCAL_LOGV && !backup) Log.v(TAG, "not backing up " + type + " / " + record);
        return backup;
    }

    private static int toInt(String s) {
        try {
             return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
