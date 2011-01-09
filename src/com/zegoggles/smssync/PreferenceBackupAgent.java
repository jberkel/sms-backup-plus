package com.zegoggles.smssync;

import java.io.IOException;

import android.os.ParcelFileDescriptor;
import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.util.Log;

import static com.zegoggles.smssync.App.*;

public class PreferenceBackupAgent extends BackupAgentHelper {
    // A key to uniquely identify the set of backup data
    static final String PREFS_BACKUP_KEY = "prefs";

    @Override public void onCreate() {
      SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this,
            getPackageName() + "_preferences");

      addHelper(PREFS_BACKUP_KEY, helper);
    }

    @Override public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
                                        ParcelFileDescriptor newState) throws IOException {

      if (LOCAL_LOGV) Log.v(TAG, "onBackup()");
      super.onBackup(oldState, data, newState);
    }


    @Override public void onRestore(BackupDataInput data, int appVersionCode,
                                    ParcelFileDescriptor newState) throws IOException {
      if (LOCAL_LOGV) Log.v(TAG, "onRestore()");
      super.onRestore(data, appVersionCode, newState);
    }
}
