package com.zegoggles.smssync;

import android.content.ContentResolver;
import android.net.Uri;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.filter.Base64OutputStream;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.TextBody;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class Attachment {

    public static MimeBodyPart createTextPart(String text) throws MessagingException {
        return new MimeBodyPart(new TextBody(text));
    }

    public static MimeBodyPart createPartFromUri(ContentResolver resolver, Uri uri, String filename, String contentType) throws MessagingException {
        return createPart(new ResolverBody(resolver, uri), filename, contentType);
    }

    public static MimeBodyPart createPartFromFile(File file, String contentType) throws MessagingException {
        return createPart(new FileBody(file), file.getName(), contentType);
    }

    private static MimeBodyPart createPart(Body body, String filename, String contentType) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart(body, contentType);
        part.setHeader(MimeHeader.HEADER_CONTENT_TYPE,
                String.format(Locale.ENGLISH, "%s;\n name=\"%s\"", contentType, filename != null ? filename : "attachment"));
        part.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, "base64");
        part.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION, "attachment");
        return part;
    }

    public static abstract class Base64Body implements Body {
        @Override
        public void writeTo(OutputStream outputStream) throws IOException, MessagingException {
            InputStream in = getInputStream();
            Base64OutputStream base64Out = new Base64OutputStream(outputStream);
            IOUtils.copy(in, base64Out);
            base64Out.close();
        }
    }

    public static class ResolverBody extends Base64Body {
        private ContentResolver mResolver;
        private Uri mUri;

        public ResolverBody(ContentResolver contentResolver, Uri uri) {
            mResolver = contentResolver;
            mUri = uri;
        }

        public InputStream getInputStream() throws MessagingException {
            try {
                return mResolver.openInputStream(mUri);
            } catch (FileNotFoundException fnfe) {
                /*
                 * Since it's completely normal for us to try to serve up attachments that
                 * have been blown away, we just return an empty stream.
                 */
                return new ByteArrayInputStream(new byte[0]);
            }
        }
    }

    public static class FileBody extends Base64Body {
        private final File file;

        public FileBody(File file) {
            this.file = file;
        }

        @Override
        public InputStream getInputStream() throws MessagingException {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                return new ByteArrayInputStream(new byte[0]);
            }
        }
    }
}
