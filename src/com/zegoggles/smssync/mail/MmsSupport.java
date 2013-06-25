package com.zegoggles.smssync.mail;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.Consts.MMS_PART;

class MmsSupport {

    static class MmsDetails {
        public final boolean inbound;
        public final List<String> recipients;
        public final PersonRecord[] records;
        public final Address[] addresses;
        public final String address;

        public MmsDetails(@NotNull List<String> recipients, boolean inbound,
                          PersonLookup lookup, AddressStyle style) {
            this.recipients = recipients;
            this.inbound = inbound;
            this.records   = new PersonRecord[recipients.size()];
            this.addresses = new Address[recipients.size()];

            if (!recipients.isEmpty()) {
                address = recipients.get(0);
                for (int i = 0; i < recipients.size(); i++) {
                    records[i]   = lookup.lookupPerson(recipients.get(i));
                    addresses[i] = records[i].getAddress(style);
                }
            } else {
                address = "Unknown";
            }
        }

        public boolean isEmpty() {
            return recipients.isEmpty();
        }
    }

    static MmsDetails getDetails(ContentResolver resolver,
                                 Uri mmsUri,
                                 PersonLookup lookup,
                                 AddressStyle style) {

        Cursor addresses = resolver.query(Uri.withAppendedPath(mmsUri, "addr"), null, null, null, null);

        // TODO: this is probably not the best way to determine if a message is inbound or outbound
        boolean inbound = true;
        final List<String> recipients = new ArrayList<String>();
        while (addresses != null && addresses.moveToNext()) {
            final String address = addresses.getString(addresses.getColumnIndex("address"));
            //final int type       = addresses.getInt(addresses.getColumnIndex("type"));
            if (MmsConsts.INSERT_ADDRESS_TOKEN.equals(address)) {
                inbound = false;
            } else {
                recipients.add(address);
            }
        }
        if (addresses != null) addresses.close();
        return new MmsDetails(recipients, inbound, lookup, style);
    }

    static List<BodyPart> getMMSBodyParts(ContentResolver resolver, final Uri uriPart) throws MessagingException {
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
