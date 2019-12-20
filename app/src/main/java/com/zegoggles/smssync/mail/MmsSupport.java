package com.zegoggles.smssync.mail;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.TextBody;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.preferences.AddressStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
        public final List<String> recipients;
        public final List<PersonRecord> records;
        public final List<Address> addresses;

        public final String address;


        public MmsDetails(boolean inbound,
                          @NonNull List<String> recipients,
                          List<PersonRecord> records,
                          List<Address> addresses) {

            if (recipients.isEmpty()) {
                address = "Unknown";
            } else {
                address = recipients.get(0);
            }

            this.recipients = recipients;
            this.inbound = inbound;
            this.records = records;
            this.addresses = addresses;
        }

        public MmsDetails(boolean inbound,
                          String recipient,
                          PersonRecord record,
                          Address address) {
            this(inbound, Arrays.asList(recipient), Arrays.asList(record), Arrays.asList(address));
        }

        public boolean isEmpty() {
            return recipients.isEmpty();
        }

        public Address[] getAddresses() {
            return addresses.toArray(new Address[addresses.size()]);
        }

        public PersonRecord getRecipient() {
            return records.get(0);
        }

        public Address getRecipientAddress() {
            return addresses.get(0);
        }
    }

    public MmsDetails getDetails(Uri mmsUri, AddressStyle style) {

        Cursor cursor = resolver.query(Uri.withAppendedPath(mmsUri, "addr"), null, null, null, null);

        // TODO: this is probably not the best way to determine if a message is inbound or outbound
        boolean inbound = true;
        final List<String> recipients = new ArrayList<String>();
        while (cursor != null && cursor.moveToNext()) {
            final String address = cursor.getString(cursor.getColumnIndex("address"));
            //final int type       = addresses.getInt(addresses.getColumnIndex("type"));
            if (MmsConsts.INSERT_ADDRESS_TOKEN.equals(address)) {
                inbound = false;
            } else {
                recipients.add(address);
            }
        }
        if (cursor != null) cursor.close();

        List<PersonRecord> records = new ArrayList<PersonRecord>(recipients.size());
        List<Address> addresses = new ArrayList<Address>(recipients.size());
        if (!recipients.isEmpty()) {
            for (String s : recipients) {
                PersonRecord record = personLookup.lookupPerson(s);
                records.add(record);
                addresses.add(record.getAddress(style));
            }
        }
        return new MmsDetails(inbound, recipients, records, addresses);
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
