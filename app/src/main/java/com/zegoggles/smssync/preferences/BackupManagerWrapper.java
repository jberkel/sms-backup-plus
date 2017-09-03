package com.zegoggles.smssync.preferences;

import android.annotation.TargetApi;
import android.app.backup.BackupManager;
import android.util.Log;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

@TargetApi(8)
public class BackupManagerWrapper {
    static Boolean available = null;

    private BackupManagerWrapper() {}

    static boolean available() {
        if (available == null) {
            try {
                Class.forName("android.app.backup.BackupManager");
                available = Boolean.TRUE;
            } catch (Exception ex) {
                available = Boolean.FALSE;
            }
        }
        return available;
    }

    public static void dataChanged(android.content.Context context) {
        if (available()) {
            if (LOCAL_LOGV) Log.v(TAG, "dataChanged()");
            new BackupManager(context).dataChanged();
        }
    }
}
