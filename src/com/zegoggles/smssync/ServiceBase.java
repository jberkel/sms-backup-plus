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
    public static SmsSync smsSync;

    /** Field containing a description of the last error. */
    public static String lastError;

    enum SmsSyncState {
        IDLE, CALC, LOGIN, BACKUP, RESTORE,
        AUTH_FAILED, CONNECTIVITY_ERROR, GENERAL_ERROR,
        CANCELED_BACKUP, CANCELED_RESTORE,
        FINISHED_BACKUP, FINISHED_RESTORE,
        UPDATING_THREADS
    }

    protected static SmsSyncState sState = SmsSyncState.IDLE;
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

    protected ImapStore.BackupFolder getSMSBackupFolder() throws MessagingException {
      return new ImapStore(this).getSMSBackupFolder();
    }

    protected ImapStore.BackupFolder getCalllogBackupFolder() throws MessagingException {
        return new ImapStore(this).getCalllogBackupFolder();
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

    /**
     * Returns the maximum date of all SMS messages (except for drafts).
     */
    protected long getMaxItemDateSms() {
        ContentResolver r = getContentResolver();
        String selection = SmsConsts.TYPE + " <> ?";
        String[] selectionArgs = new String[] {
            String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT)
        };
        String[] projection = new String[] {
            SmsConsts.DATE
        };
        Cursor result = r.query(SMS_PROVIDER, projection, selection, selectionArgs,
                SmsConsts.DATE + " DESC LIMIT 1");

        try
        {
            if (result.moveToFirst()) {
                return result.getLong(0);
            } else {
                return PrefStore.DEFAULT_MAX_SYNCED_DATE;
            }
        }
        catch (RuntimeException e)
        {
            result.close();
            throw e;
        }
    }
    
    /**
     * Returns the maximum date of all MMS messages
     */
    protected long getMaxItemDateMms() {
        ContentResolver r = getContentResolver();
        String[] projection = new String[] {
            SmsConsts.DATE
        };
        Cursor result = r.query(MMS_PROVIDER, projection, null, null,
                SmsConsts.DATE + " DESC LIMIT 1");

        try
        {
            if (result.moveToFirst()) {
                return result.getLong(0);
            } else {
                return PrefStore.DEFAULT_MAX_SYNCED_DATE;
            }
        }
        catch (RuntimeException e)
        {
            result.close();
            throw e;
        }
    }

    /**
     * Returns the largest date of all sms messages that have successfully been synced
     * with the server.
     */
    protected long getMaxSyncedDateSms() {
        return PrefStore.getMaxSyncedDateSms(this);
    }

    /**
     * Returns the largest date of all mms messages that have successfully been synced
     * with the server.
     */
    protected long getMaxSyncedDateMms() {
        return PrefStore.getMaxSyncedDateMms(this);
    }

    /**
     * Returns the largest date of all calllog entries that have successfully been synced
     * with the server.
     */
    protected long getMaxSyncedDateCalllog() {
        return PrefStore.getMaxSyncedDateCalllog(this);
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
          Log.v(Consts.TAG, "Max synced date for sms set to: " + maxSyncedDate);
        }
    }

    protected void updateMaxSyncedDateMms(long maxSyncedDate) {
        PrefStore.setMaxSyncedDateMms(this, maxSyncedDate);
        if (LOCAL_LOGV) {
          Log.v(Consts.TAG, "Max synced date for mms set to: " + maxSyncedDate);
        }
    }

    protected void updateMaxSyncedDateCalllog(long maxSyncedDate) {
        PrefStore.setMaxSyncedDateCalllog(this, maxSyncedDate);
        if (LOCAL_LOGV) {
          Log.v(Consts.TAG, "Max synced date for call log set to: " + maxSyncedDate);
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

    /**
     * Exception connecting.
     */
    public static class ConnectivityErrorException extends Exception {
        public ConnectivityErrorException(String msg) {
            super(msg);
        }
    }
}
