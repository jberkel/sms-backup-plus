package com.zegoggles.smssync.mail;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.filter.Base64OutputStream;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import static com.zegoggles.smssync.App.TAG;

class Attachment {
    // RFC2231 encoding from geronimo-javamail
    private static final String MIME_SPECIALS = "()<>@,;:\\\"/[]?=" + "\t ";
    private static final String RFC2231_SPECIALS = "*'%" + MIME_SPECIALS;
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private Attachment() {}

    static MimeBodyPart createPartFromUri(@NonNull ContentResolver resolver, @NonNull Uri uri, String filename, String contentType) throws MessagingException {
        return createPart(new ResolverBody(resolver, uri), filename, contentType);
    }

    private static MimeBodyPart createPart(Body body, final String filename, final String contentType) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart(body, contentType);

        String contentTypeHeader = TextUtils.isEmpty(contentType) ? "application/octet-stream" : contentType;
        String disposition = "attachment";
        if (!TextUtils.isEmpty(filename)) {
            // should set both name and filename parameters
            // http://www.imc.org/ietf-smtp/mail-archive/msg05023.html
            disposition += encodeRFC2231("filename", filename);
            contentTypeHeader += encodeRFC2231("name", filename);
        }
        part.setHeader(MimeHeader.HEADER_CONTENT_TYPE, contentTypeHeader);
        part.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, disposition);
        part.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, "base64");
        return part;
    }

    private static abstract class Base64Body implements Body {
        @Override
        public void writeTo(OutputStream outputStream) throws IOException, MessagingException {
            InputStream in = getInputStream();
            if (in != null)  {
                Base64OutputStream base64Out = new Base64OutputStream(outputStream);
                IOUtils.copy(in, base64Out);
                base64Out.close();
            } else {
                Log.w(TAG, "input stream is null");
            }
        }
    }

    private static class ResolverBody extends Base64Body {
        private ContentResolver resolver;
        private Uri uri;

        ResolverBody(@NonNull ContentResolver contentResolver, @NonNull Uri uri) {
            resolver = contentResolver;
            this.uri = uri;
        }

        public InputStream getInputStream() {
            try {
                return resolver.openInputStream(uri);
            } catch (FileNotFoundException fnfe) {
                /*
                 * Since it's completely normal for us to try to serve up attachments that
                 * have been blown away, we just return an empty stream.
                 */
                return new ByteArrayInputStream(new byte[0]);
            }
        }

        @Override
        public void setEncoding(String s) {
        }
    }

    static String encodeRFC2231(String key, String value) {
        StringBuilder buf = new StringBuilder();
        boolean encoded = encodeRFC2231value(value, buf);
        if (encoded) {
            return "; " + key + "*=" + buf.toString();
        } else {
            return "; " + key + "=" + value;
        }
    }

    private static boolean encodeRFC2231value(String value, StringBuilder buf) {
        String charset = "UTF-8";
        buf.append(charset);
        buf.append("''"); // no language
        byte[] bytes;
        try {
            bytes = value.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            return true;
        }
        boolean encoded = false;
        for (byte aByte : bytes) {
            int ch = aByte & 0xff;
            if (ch <= 32 || ch >= 127 || RFC2231_SPECIALS.indexOf(ch) != -1) {
                buf.append('%');
                buf.append(HEX_DIGITS[ch >> 4]);
                buf.append(HEX_DIGITS[ch & 0xf]);
                encoded = true;
            } else {
                buf.append((char) ch);
            }
        }
        return encoded;
    }
}
