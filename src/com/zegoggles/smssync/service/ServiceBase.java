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
package com.zegoggles.smssync.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.DateFormat;
import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.MainActivity;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.state.State;
import com.zegoggles.smssync.utils.AppLog;
import org.jetbrains.annotations.NotNull;

import static com.zegoggles.smssync.App.LOG;
import static com.zegoggles.smssync.App.TAG;

public abstract class ServiceBase extends Service {
    /**
     * A wakelock held while this service is working.
     */
    private PowerManager.WakeLock sWakeLock;
    /**
     * A wifilock held while this service is working.
     */
    private WifiManager.WifiLock sWifiLock;

    private AppLog appLog;
    protected Notification notification;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Preferences.isAppLogEnabled(this)) {
            this.appLog = new AppLog(LOG, DateFormat.getDateFormatOrder(this));
        }
        App.bus.register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (appLog != null) appLog.close();
        App.bus.unregister(this);
        notification = null;
    }

    protected BackupImapStore getBackupImapStore() throws MessagingException {
        final String uri = AuthPreferences.getStoreUri(this);
        if (!BackupImapStore.isValidUri(uri)) {
            throw new MessagingException("No valid IMAP URI: "+uri);
        }
        return new BackupImapStore(this, uri);
    }

    /**
     * Acquire locks
     *
     * @param background if service is running in background (no UI)
     * @throws ConnectivityErrorException
     *          when unable to connect
     */
    protected void acquireLocks(boolean background) throws ConnectivityErrorException {
        if (sWakeLock == null) {
            PowerManager pMgr = (PowerManager) getSystemService(POWER_SERVICE);
            sWakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }
        sWakeLock.acquire();

        WifiManager wMgr = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wMgr.isWifiEnabled() &&
                getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI) != null &&
                getConnectivityManager().getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {

            // we have Wifi, lock it
            if (sWifiLock == null) {
                sWifiLock = wMgr.createWifiLock(TAG);
            }
            sWifiLock.acquire();
        } else if (background && Preferences.isWifiOnly(this)) {
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
    @Override
    public void onStart(final Intent intent, int startId) {
        handleIntent(intent);
    }

    // Android api level >= 5
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        handleIntent(intent);
        return START_NOT_STICKY;
    }

    protected void appLog(int id, Object... args) {
        if (appLog != null) appLog.append(getString(id, args));
    }

    protected NotificationManager getNotifier() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    protected ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public abstract @NotNull
    State getState();

    public boolean isWorking() {
        return getState().isRunning();
    }

    protected CalendarAccessor getCalendars() {
        return CalendarAccessor.Get.instance();
    }

    protected ContactAccessor getContacts() {
        return ContactAccessor.Get.instance();
    }

    protected Notification createNotification(int resId) {
        Notification n = new Notification(R.drawable.ic_notification,
                getString(resId),
                System.currentTimeMillis());
        n.flags = Notification.FLAG_ONGOING_EVENT;
        return n;
    }

    protected PendingIntent getPendingIntent() {
        return PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
