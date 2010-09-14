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
import android.util.Log;

import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;

public abstract class ServiceBase extends Service {

    // the activity
    public static SmsSync smsSync;

    /**
     * Field containing a description of the last error. See
     * {@link #getErrorDescription()}.
     */
    protected static String sLastError;

    enum SmsSyncState {
        IDLE, CALC, LOGIN, SYNC, RESTORE, AUTH_FAILED, GENERAL_ERROR, FOLDER_ERROR, CANCELED;
    }

    /**
     * A state change listener interface that provides a callback that is called
     * whenever the state of the {@link SmsSyncService} changes.
     *
     * @see SmsSyncService#setStateChangeListener(StateChangeListener)
     */
    public interface StateChangeListener {

        /**
         * Called whenever the sync state of the service changed.
         */
        void stateChanged(SmsSyncState oldState, SmsSyncState newState);
    }


    public static final Uri SMS_PROVIDER = Uri.parse("content://sms");

    /**
     * A wakelock held while this service is working.
     */
    protected PowerManager.WakeLock sWakeLock;
    /**
     * A wifilock held while this service is working.
     */
    protected WifiManager.WifiLock sWifiLock;

    /**
     * Returns a description of the last error. Only valid if
     * <code>{@link #getState()} == {@link SmsSyncState#GENERAL_ERROR}</code>.
     */
    static String getErrorDescription() {
        return sLastError;
    }

    public static String getHeader(Message msg, String header) {
        try {
            String[] hdrs = msg.getHeader(header);
            if (hdrs != null && hdrs.length > 0) {
                return hdrs[0];
            }
        } catch (MessagingException e) {
        }
        return null;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    protected ImapStore.BackupFolder getBackupFolder()
            throws AuthenticationErrorException {
        try {
            return new ImapStore(this).getBackupFolder();
        } catch (IllegalArgumentException e) {
            throw new AuthenticationErrorException(e);
        } catch (MessagingException e) {
            throw new AuthenticationErrorException(e);
        }
    }

    protected void acquireLocks() throws GeneralErrorException {
        if (sWakeLock == null) {
            PowerManager pMgr = (PowerManager) getSystemService(POWER_SERVICE);
            sWakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsSyncService wakelock.");
        }
        sWakeLock.acquire();

        WifiManager wMgr = (WifiManager) getSystemService(WIFI_SERVICE);

        if (wMgr.isWifiEnabled() &&
            getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {

          // we have Wifi, lock it
          if (sWifiLock == null)  {
            sWifiLock = wMgr.createWifiLock("SMS Backup+");
          }
          sWifiLock.acquire();
        } else if (PrefStore.isWifiOnly(this)) {
          throw new GeneralErrorException(R.string.error_wifi_only_no_connection, this, null);
        }
    }

    protected void releaseLocks() {
        sWakeLock.release();

        if (sWifiLock != null && sWifiLock.isHeld()) {
          sWifiLock.release();
        }
    }

    /**
     * Returns the maximum date of all SMS messages (except for drafts).
     */
    protected long getMaxItemDate() {
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
     * Returns the largest date of all messages that have successfully been synced
     * with the server.
     */
    protected long getMaxSyncedDate() {
        return PrefStore.getMaxSyncedDate(this);
    }

    /**
     * Persists the provided ID so it can later on be retrieved using
     * {@link #getMaxSyncedDate()}. This should be called when after each
     * successful sync request to a server.
     *
     * @param maxSyncedId
     */
    protected void updateMaxSyncedDate(long maxSyncedDate) {
        PrefStore.setMaxSyncedDate(this, maxSyncedDate);
        Log.d(Consts.TAG, "Max synced date set to: " + maxSyncedDate);
    }

    public ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Exception indicating an error while synchronizing.
     */
    public static class GeneralErrorException extends Exception {
        private static final long serialVersionUID = 1L;

        public GeneralErrorException(String msg) {
            super(msg);
        }

        public GeneralErrorException(String msg, Throwable t) {
            super(msg, t);
        }

        public GeneralErrorException(int msgId, Context ctx, Throwable t) {
            super(ctx.getString(msgId), t);
        }
    }

    public static class AuthenticationErrorException extends Exception {
        private static final long serialVersionUID = 1L;

        public AuthenticationErrorException(Throwable t) {
            super(t.getLocalizedMessage(), t);
        }
    }

}
