package com.zegoggles.smssync.service;

import android.content.Intent;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.R;

public enum BackupType {
    BROADCAST_INTENT(R.string.source_3rd_party),
    INCOMING(R.string.source_incoming),
    REGULAR(R.string.source_regular),
    UNKNOWN(R.string.source_unknown),
    MANUAL(R.string.source_manual);

    public final int resId;

    BackupType(int resId) {
        this.resId = resId;
    }

    public static BackupType fromIntent(Intent intent) {
        if (intent.hasExtra(Consts.BACKUP_TYPE)) {
            return (BackupType) intent.getSerializableExtra(Consts.BACKUP_TYPE);
        } else {
            return MANUAL;
        }
    }

    public boolean isBackground() {
        return this != MANUAL;
    }
}
