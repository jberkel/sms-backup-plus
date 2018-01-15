package com.zegoggles.smssync.preferences;

import android.app.backup.BackupManager;

public class BackupManagerWrapper {
    private static Boolean available = null;

    private BackupManagerWrapper() {}

    private static boolean available() {
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
            new BackupManager(context).dataChanged();
        }
    }
}
