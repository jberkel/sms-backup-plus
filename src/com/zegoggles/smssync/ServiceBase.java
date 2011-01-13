/*
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zegoggles.smssync;

import android.app.Service;
import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.CallLog;
import android.util.Log;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;

import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.AuthenticationFailedException;

import static com.zegoggles.smssync.App.*;

public abstract class ServiceBase extends Service {
    // the activity
    static SmsSync smsSync;

    /** Field containing a description of the last error. */
    static String lastError;

    enum SmsSyncState {
        IDLE, CALC, LOGIN, BACKUP, RESTORE,
        AUTH_FAILED, CONNECTIVITY_ERROR, GENERAL_ERROR,
        CANCELED_BACKUP, CANCELED_RESTORE,
        FINISHED_BACKUP, FINISHED_RESTORE,
        UPDATING_THREADS
    }

    static SmsSyncState sState = SmsSyncState.IDLE;
    public static SmsSyncState getState() { return sState; }

    public static final Uri SMS_PROVIDER = Uri.parse("content://sms");
    public static final Uri MMS_PROVIDER = Uri.parse("content://mms");
    public static final Uri CALLLOG_PROVIDER = CallLog.Calls.CONTENT_URI;

    /**
     * A wakelock held while this service is working.
     */
    protected PowerManager.WakeLock sWakeLock;
    /**
     * A wifilock held while this service is working.
     */
    protected WifiManager.WifiLock sWifiLock;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    protected BackupImapStore.BackupFolder getSMSBackupFolder() throws MessagingException {
      return new BackupImapStore(this).getSMSBackupFolder();
    }

    protected BackupImapStore.BackupFolder getCallLogBackupFolder() throws MessagingException {
        return new BackupImapStore(this).getCallLogBackupFolder();
    }

    /**
     * Acquire locks
     * @params background if service is running in background (no UI)
     */
    protected void acquireLocks(boolean background) throws ConnectivityErrorException {
        if (sWakeLock == null) {
            PowerManager pMgr = (PowerManager) getSystemService(POWER_SERVICE);
            sWakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsBackup+");
        }
        sWakeLock.acquire();

        WifiManager wMgr = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wMgr.isWifiEnabled() &&
            getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI) != null &&
            getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {

          // we have Wifi, lock it
          if (sWifiLock == null) {
            sWifiLock = wMgr.createWifiLock("SMS Backup+");
          }
          sWifiLock.acquire();
        } else if (background && PrefStore.isWifiOnly(this)) {
          throw new ConnectivityErrorException(getString(R.string.error_wifi_only_no_connection));
        }

        NetworkInfo active = getConnectivityManager().getActiveNetworkInfo();

        if (active == null || !active.isConnectedOrConnecting()) {
          throw new ConnectivityErrorException(getString(R.string.error_no_connection));
        }
    }

    protected void releaseLocks() {
        if (sWakeLock != null && sWakeLock.isHeld()) sWakeLock.release();
        if (sWifiLock != null && sWifiLock.isHeld()) sWifiLock.release();
    }

    protected abstract void handleIntent(final Intent intent);

    // Android api level < 5
    @Override public void onStart(final Intent intent, int startId) {
        handleIntent(intent);
    }

    // Android api level >= 5
    @Override public int onStartCommand(final Intent intent, int flags, int startId) {
        handleIntent(intent);
        return START_NOT_STICKY;
    }

    /**
     * Returns the maximum date of all SMS messages (except for drafts).
     */
    protected long getMaxItemDateSms() {
        Cursor result = getContentResolver().query(SMS_PROVIDER,
                new String[] { SmsConsts.DATE },
                SmsConsts.TYPE + " <> ?",
                new String[] { String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT) },
                SmsConsts.DATE + " DESC LIMIT 1");

        try {
           return result.moveToFirst() ? result.getLong(0) : PrefStore.DEFAULT_MAX_SYNCED_DATE;
        } finally {
          if (result != null) result.close();
        }
    }

    /**
     * Returns the maximum date of all MMS messages
     */
    protected long getMaxItemDateMms() {
        Cursor result = getContentResolver().query(MMS_PROVIDER,
                new String[] { MmsConsts.DATE }, null, null,
                MmsConsts.DATE + " DESC LIMIT 1");
        try {
            return result.moveToFirst() ? result.getLong(0) : PrefStore.DEFAULT_MAX_SYNCED_DATE;
        } finally {
            if (result != null) result.close();
        }
    }

    protected long getMaxItemDateCallLog() {
        Cursor result = getContentResolver().query(CALLLOG_PROVIDER,
                new String[] { CallLog.Calls.DATE }, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 1");
        try {
            return result.moveToFirst() ? result.getLong(0) : PrefStore.DEFAULT_MAX_SYNCED_DATE;
        } finally {
            if (result != null) result.close();
        }
    }

    /**
     * Persists the provided ID so it can later on be retrieved using
     * {@link #getMaxSyncedDateSms()}. This should be called when after each
     * successful sync request to a server.
     *
     * @param maxSyncedId
     */
    protected void updateMaxSyncedDateSms(long maxSyncedDate) {
        PrefStore.setMaxSyncedDateSms(this, maxSyncedDate);
        if (LOCAL_LOGV) {
          Log.v(TAG, "Max synced date for sms set to: " + maxSyncedDate);
        }
    }

    protected void updateMaxSyncedDateMms(long maxSyncedDate) {
        PrefStore.setMaxSyncedDateMms(this, maxSyncedDate);
        if (LOCAL_LOGV) {
          Log.v(TAG, "Max synced date for mms set to: " + maxSyncedDate);
        }
    }

    protected void updateMaxSyncedDateCallLog(long maxSyncedDate) {
        PrefStore.setMaxSyncedDateCallLog(this, maxSyncedDate);
        if (LOCAL_LOGV) {
          Log.v(TAG, "Max synced date for call log set to: " + maxSyncedDate);
        }
    }

    protected void notifyUser(int icon, String shortText, String title, String text) {
        Notification n = new Notification(icon, shortText, System.currentTimeMillis());
        n.flags = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
        final Intent intent = new Intent(this, SmsSync.class);

        n.setLatestEventInfo(this,
            title,
            text,
            PendingIntent.getActivity(this, 0, intent,  0));

        getNotifier().notify(0, n);
    }

    protected NotificationManager getNotifier() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    public ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    protected String translateException(Exception e) {
       if (e instanceof MessagingException &&
           "Unable to get IMAP prefix".equals(e.getMessage())) {
        return getString(R.string.status_gmail_temp_error);
      } else {
        return e.getLocalizedMessage();
      }
    }

    /**
     * Exception connecting.
     */
    public static class ConnectivityErrorException extends Exception {
        public ConnectivityErrorException(String msg) {
            super(msg);
        }
    }
}
