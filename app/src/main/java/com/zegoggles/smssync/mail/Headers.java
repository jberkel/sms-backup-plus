package com.zegoggles.smssync.mail;

import androidx.annotation.Nullable;
import com.fsck.k9.mail.Message;

public final class Headers {
    // private headers
    static final String ID             = "X-smssync-id";
    static final String ADDRESS        = "X-smssync-address";
    /** {@link DataType} SMS, MMS, CALLLOG */
    static final String DATATYPE       = "X-smssync-datatype";
    /** Subtype, value is datatype specific  */
    public static final String TYPE           = "X-smssync-type";
    public static final String DATE           = "X-smssync-date";
    static final String THREAD_ID      = "X-smssync-thread";
    static final String READ           = "X-smssync-read";
    static final String STATUS         = "X-smssync-status";
    static final String PROTOCOL       = "X-smssync-protocol";
    static final String SERVICE_CENTER = "X-smssync-service_center";
    static final String BACKUP_TIME    = "X-smssync-backup-time";
    public static final String VERSION        = "X-smssync-version";
    public static final String DURATION       = "X-smssync-duration";

    // standard headers
    static final String REFERENCES = "References";
    static final String MESSAGE_ID = "Message-ID";

    private Headers() {}

    public static @Nullable String get(Message msg, String header) {
        final String[] headers = msg.getHeader(header);
        if (headers.length > 0) {
            return headers[0];
        } else {
            return null;
        }
    }
}
