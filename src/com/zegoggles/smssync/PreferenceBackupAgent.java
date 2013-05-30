package com.zegoggles.smssync;

import android.annotation.TargetApi;
import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

/**
 * @noinspection UnusedDeclaration
 */
@TargetApi(8)
public class PreferenceBackupAgent extends BackupAgentHelper {
    // A key to uniquely identify the set of backup data
    static final String PREFS_BACKUP_KEY = "prefs";

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this,
                getPackageName() + "_preferences");

        addHelper(PREFS_BACKUP_KEY, helper);
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
                         ParcelFileDescriptor newState) throws IOException {

        if (LOCAL_LOGV) Log.v(TAG, "onBackup()");
        super.onBackup(oldState, data, newState);
    }


    @Override
    public void onRestore(BackupDataInput data, int appVersionCode,
                          ParcelFileDescriptor newState) throws IOException {
        if (LOCAL_LOGV) Log.v(TAG, "onRestore()");
        super.onRestore(data, appVersionCode, newState);
    }
}
