package com.zegoggles.smssync.mail;

import android.provider.CallLog;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.SmsConsts;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.zegoggles.smssync.utils.Sanitizer.sanitize;

/**
 * Generates headers for SMS, MMS, Call logs and WhatsApp messages.
 */
class HeaderGenerator {
    private static final String REFERENCE_UID_TEMPLATE = "<%s.%s@sms-backup-plus.local>";
    private static final String MSG_ID_TEMPLATE        = "<%s@sms-backup-plus.local>";

    private final String reference;
    private final String version;

    public HeaderGenerator(String reference, String version) {
        this.version = version;
        this.reference = reference;
    }

    public void setHeaders(final Message message,
                           final Map<String, String> msgMap,
                           final DataType dataType,
                           final String address,
                           final @NotNull PersonRecord contact,
                           final Date sentDate,
                           final int status) throws MessagingException {

        // Threading by contact ID, not by thread ID. I think this value is more stable.
        message.setHeader(Headers.REFERENCES, String.format(REFERENCE_UID_TEMPLATE, reference, contact.getId()));
        message.setHeader(Headers.MESSAGE_ID, createMessageId(sentDate, address, status));
        message.setHeader(Headers.ADDRESS,  sanitize(address));
        message.setHeader(Headers.DATATYPE, dataType.toString());
        message.setHeader(Headers.BACKUP_TIME, toGMTString(new Date()));
        message.setHeader(Headers.VERSION, version);
        message.setSentDate(sentDate, false);
        message.setInternalDate(sentDate);
        switch (dataType) {
            case SMS: setSmsHeaders(message, msgMap); break;
            case MMS: setMmsHeaders(message, msgMap); break;
            case CALLLOG: setCallLogHeaders(message, msgMap); break;
            case WHATSAPP: setWhatsAppHeaders(message, sentDate, status); break;
        }
    }

    private void setSmsHeaders(Message message, Map<String,String> msgMap) throws MessagingException {
        message.setHeader(Headers.ID, msgMap.get(SmsConsts.ID));
        message.setHeader(Headers.TYPE, msgMap.get(SmsConsts.TYPE));
        message.setHeader(Headers.DATE, msgMap.get(SmsConsts.DATE));
        message.setHeader(Headers.THREAD_ID, msgMap.get(SmsConsts.THREAD_ID));
        message.setHeader(Headers.READ, msgMap.get(SmsConsts.READ));
        message.setHeader(Headers.STATUS, msgMap.get(SmsConsts.STATUS));
        message.setHeader(Headers.PROTOCOL, msgMap.get(SmsConsts.PROTOCOL));
        message.setHeader(Headers.SERVICE_CENTER, msgMap.get(SmsConsts.SERVICE_CENTER));
    }

    private void setMmsHeaders(Message message, Map<String,String> msgMap) throws MessagingException {
        message.setHeader(Headers.ID, msgMap.get(MmsConsts.ID));
        message.setHeader(Headers.TYPE, msgMap.get(MmsConsts.TYPE));
        message.setHeader(Headers.DATE, msgMap.get(MmsConsts.DATE));
        message.setHeader(Headers.THREAD_ID, msgMap.get(MmsConsts.THREAD_ID));
        message.setHeader(Headers.READ, msgMap.get(MmsConsts.READ));
    }

    private void setCallLogHeaders(Message message, Map<String,String> msgMap) throws MessagingException {
        message.setHeader(Headers.ID, msgMap.get(CallLog.Calls._ID));
        message.setHeader(Headers.TYPE, msgMap.get(CallLog.Calls.TYPE));
        message.setHeader(Headers.DATE, msgMap.get(CallLog.Calls.DATE));
        message.setHeader(Headers.DURATION, msgMap.get(CallLog.Calls.DURATION));
    }

    private void setWhatsAppHeaders(Message message, Date sentDate, int status) throws MessagingException {
        message.setHeader(Headers.DATE, String.valueOf(sentDate.getTime()));
        message.setHeader(Headers.TYPE, String.valueOf(status));
        message.setHeader(Headers.STATUS, String.valueOf(status));
    }

    private static String toGMTString(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM y HH:mm:ss 'GMT'", Locale.US);
        TimeZone gmtZone = TimeZone.getTimeZone("GMT");
        sdf.setTimeZone(gmtZone);
        GregorianCalendar gc = new GregorianCalendar(gmtZone);
        gc.setTimeInMillis(date.getTime());
        return sdf.format(date);
    }

    /**
     * Create a message-id based on message date, phone number and message
     * type.
     *
     * @param sent    email send date
     * @param address the email address
     * @param type    the type
     * @return the message-id
     */
    private static String createMessageId(Date sent, String address, int type) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");

            digest.update(Long.toString(sent.getTime()).getBytes("UTF-8"));
            if (address != null) {
                digest.update(address.getBytes("UTF-8"));
            }
            digest.update(Integer.toString(type).getBytes("UTF-8"));

            final StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format(Locale.ENGLISH, "%02x", b));
            }
            return String.format(Locale.ENGLISH, MSG_ID_TEMPLATE, sb.toString());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
