package com.zegoggles.smssync.service;

import android.content.Intent;
import com.zegoggles.smssync.R;

public enum BackupType {
    BROADCAST_INTENT(R.string.source_3rd_party),
    INCOMING(R.string.source_incoming),
    REGULAR(R.string.source_regular),
    UNKNOWN(R.string.source_unknown),
    MANUAL(R.string.source_manual),
    SKIP(R.string.source_manual);

    public final int resId;

    BackupType(int resId) {
        this.resId = resId;
    }

    public static BackupType fromIntent(Intent intent) {
        if (intent.getAction() != null) {
            return fromName(intent.getAction());
        } else {
            return UNKNOWN;
        }
    }

    public static BackupType fromName(String name) {
        for (BackupType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public boolean isBackground() {
        return this != MANUAL && this != SKIP;
    }

    public boolean isRecurring() { return this == REGULAR; }
}
