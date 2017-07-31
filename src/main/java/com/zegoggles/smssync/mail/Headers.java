package com.zegoggles.smssync.mail;

import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;

public final class Headers {
    // private headers
    public static final String ID             = "X-smssync-id";
    public static final String ADDRESS        = "X-smssync-address";
    public static final String DATATYPE       = "X-smssync-datatype";
    public static final String TYPE           = "X-smssync-type";
    public static final String DATE           = "X-smssync-date";
    public static final String THREAD_ID      = "X-smssync-thread";
    public static final String READ           = "X-smssync-read";
    public static final String STATUS         = "X-smssync-status";
    public static final String PROTOCOL       = "X-smssync-protocol";
    public static final String SERVICE_CENTER = "X-smssync-service_center";
    public static final String BACKUP_TIME    = "X-smssync-backup-time";
    public static final String VERSION        = "X-smssync-version";
    public static final String DURATION       = "X-smssync-duration";

    // standard headers
    public static final String REFERENCES = "References";
    public static final String MESSAGE_ID = "Message-ID";

    private Headers() {}

    public static String get(Message msg, String header) {
        String[] hdrs = msg.getHeader(header);
        if (hdrs.length > 0) {
            return hdrs[0];
        } else {
            return null;
        }
    }
}
