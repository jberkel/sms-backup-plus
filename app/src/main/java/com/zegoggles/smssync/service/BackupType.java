package com.zegoggles.smssync.service;

import android.content.Intent;
import com.zegoggles.smssync.R;

public enum BackupType {
    BROADCAST_INTENT(R.string.source_3rd_party),
    INCOMING(R.string.source_incoming),
    REGULAR(R.string.source_regular),
    UNKNOWN(R.string.source_unknown),
    MANUAL(R.string.source_manual);

    public static final String EXTRA = "com.zegoggles.smssync.BackupTypeAsString";

    public final int resId;

    BackupType(int resId) {
        this.resId = resId;
    }

    public static BackupType fromIntent(Intent intent) {
        if (intent.hasExtra(EXTRA)) {
            final String name = intent.getStringExtra(EXTRA);
            return fromName(name);
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
        return this != MANUAL;
    }
}
