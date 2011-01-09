package com.zegoggles.smssync;

import android.app.backup.BackupManager;
import android.util.Log;

import static com.zegoggles.smssync.App.*;

public class BackupManagerWrapper {
  static Boolean available = null;

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

  static void dataChanged(android.content.Context context) {
    if (available()) {
      if (LOCAL_LOGV) Log.v(TAG, "dataChanged()");
      new BackupManager(context).dataChanged();
    }
  }
}
