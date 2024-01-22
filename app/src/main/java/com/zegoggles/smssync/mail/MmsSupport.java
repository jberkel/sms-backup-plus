package com.zegoggles.smssync.mail;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.TextBody;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.preferences.AddressStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.Consts.MMS_PART;

class MmsSupport {

    private final PersonLookup personLookup;
    private final ContentResolver resolver;

    MmsSupport(@NonNull ContentResolver resolver, @NonNull PersonLookup personLookup) {
        this.resolver = resolver;
        this.personLookup = personLookup;
    }

    static class MmsDetails {
        public final boolean inbound;
        public final PersonRecord sender;
        public final List<PersonRecord> recipients;
        public final List<String> rawAddresses;

        public MmsDetails(boolean inbound,
                          PersonRecord sender,
                          @NonNull List<PersonRecord> recipients,
                          List<String> rawAddresses) {

            this.inbound = inbound;
            this.sender = sender;
            this.recipients = recipients;
            this.rawAddresses = rawAddresses;
        }

        public boolean isEmpty() {
            return recipients.isEmpty();
        }

        public PersonRecord getSender() {
            return sender;
        }

        public List<PersonRecord> getRecipients() {
            return recipients;
        }

        public Address[] getRecipientAddresses(AddressStyle style) {
            List<Address> recipientAddresses = new ArrayList<>(recipients.size());
            for (PersonRecord recipient : recipients) {
                recipientAddresses.add(recipient.getAddress(style));
            }

            return recipientAddresses.toArray(new Address[0]);
        }

        public PersonRecord getRecipient() {
            return recipients.get(0);
        }

        public String getFirstRawAddress() {
            if (rawAddresses.size() == 0) {
                return  "Unknown";
            }

            return rawAddresses.get(0);
        }
    }

    public MmsDetails getDetails(Uri mmsUri, AddressStyle style, Map<String, String> msgMap) {

        Cursor cursor = resolver.query(Uri.withAppendedPath(mmsUri, "addr"), null, null, null, null);

        boolean inbound = true;
        List<PersonRecord> recipients = new ArrayList<PersonRecord>();
        PersonRecord sender = null;

        List<String> rawAddresses = new ArrayList<>();

        while (cursor != null && cursor.moveToNext()) {
            final String address = cursor.getString(cursor.getColumnIndex("address"));

            rawAddresses.add(address);

            // https://stackoverflow.com/questions/52186442/how-to-get-phone-numbers-of-mms-group-conversation-participants
            String PduHeadersFROM  = "137";
            String PduHeadersTO  = "151";
            String PduHeadersCC  = "130"; // https://android.googlesource.com/platform/frameworks/opt/mms/+/4bfcd8501f09763c10255442c2b48fad0c796baa/src/java/com/google/android/mms/pdu/PduHeaders.java

            String type = cursor.getString(cursor.getColumnIndex("type"));
            if (type.equals(PduHeadersFROM)) {
                PersonRecord record = personLookup.lookupPerson(address);
                sender = record;
            } else if (type.equals(PduHeadersTO) || type.equals(PduHeadersCC)) {
                PersonRecord record = personLookup.lookupPerson(address);
                recipients.add(record);
            } else {
                Log.w(TAG, "New logic for to/from did not work, falling back to old logic");

                if (MmsConsts.INSERT_ADDRESS_TOKEN.equals(address)) {
                    inbound = false; // probably not the best way to determine if a message is inbound or outbound (legacy logic)
                } else {
                    PersonRecord record = personLookup.lookupPerson(address);
                    recipients.add(record);
                }
            }
        }
        if (cursor != null) cursor.close();

        // If neither of these are true, then the legacy logic will give us a fallback value.
        if (Integer.parseInt(msgMap.get(Telephony.BaseMmsColumns.MESSAGE_BOX)) == Telephony.BaseMmsColumns.MESSAGE_BOX_INBOX) {
            inbound = true;
        } else if (Integer.parseInt(msgMap.get(Telephony.BaseMmsColumns.MESSAGE_BOX)) == Telephony.BaseMmsColumns.MESSAGE_BOX_SENT) {
            inbound = false;
        }

        // Strip recipient if it's also the sender. Ensures that incoming messages in RCS threads
        // between 2 people don't include your own phone number, so that the thread doesn't get split.
        if (sender != null) {
            List<PersonRecord> recipientsWithoutSender = new ArrayList<>();

            for (PersonRecord recipient : recipients) {
                if (!recipient.getId().equals(sender.getId())) {
                    recipientsWithoutSender.add(recipient);
                }
            }

            recipients = recipientsWithoutSender;
        }

        return new MmsDetails(inbound, sender, recipients, rawAddresses);
    }

    public List<BodyPart> getMMSBodyParts(final Uri uriPart) throws MessagingException {
        final List<BodyPart> parts = new ArrayList<BodyPart>();
        Cursor curPart = resolver.query(uriPart, null, null, null, null);

        // _id, mid, seq, ct, name, chset, cd, fn, cid, cl, ctt_s, ctt_t, _data, text
        while (curPart != null && curPart.moveToNext()) {
            final String id = curPart.getString(curPart.getColumnIndex("_id"));
            final String contentType = curPart.getString(curPart.getColumnIndex("ct"));
            final String fileName = curPart.getString(curPart.getColumnIndex("cl"));
            final String text = curPart.getString(curPart.getColumnIndex("text"));

            if (LOCAL_LOGV) {
                Log.v(TAG, String.format(Locale.ENGLISH, "processing part %s, name=%s (%s)", id,
                        fileName, contentType));
            }

            if (!TextUtils.isEmpty(contentType) && contentType.startsWith("text/") && !TextUtils.isEmpty(text)) {
                // text
                parts.add(new MimeBodyPart(new TextBody(text), contentType));
            } else //noinspection StatementWithEmptyBody
                if ("application/smil".equalsIgnoreCase(contentType)) {
                    // silently ignore SMIL stuff
                } else {
                    // attach everything else
                    final Uri partUri = Uri.withAppendedPath(Consts.MMS_PROVIDER, MMS_PART + "/" + id);
                    parts.add(Attachment.createPartFromUri(resolver, partUri, fileName, contentType));
                }
        }

        if (curPart != null) curPart.close();
        return parts;
    }
}
