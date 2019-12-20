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

import android.Manifest;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.StrictMode;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import com.fsck.k9.mail.K9MailLib;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;
import com.zegoggles.smssync.compat.GooglePlayServices;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.receiver.BootReceiver;
import com.zegoggles.smssync.receiver.SmsBroadcastReceiver;
import com.zegoggles.smssync.service.BackupJobs;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

public class App extends Application {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    public static final boolean LOCAL_LOGV = DEBUG;
    public static final String TAG = "SMSBackup+";
    public static final String LOG = "sms_backup_plus.log";
    public static final String CHANNEL_ID = "sms_backup_plus";

    private static final Bus bus = new Bus();
    /** Google Play Services present on this device? */
    public static boolean gcmAvailable;

    private Preferences preferences;
    private BackupJobs backupJobs;

    @Override
    public void onCreate() {
        super.onCreate();
        setupStrictMode();
        gcmAvailable = GooglePlayServices.isAvailable(this);
        preferences = new Preferences(this);
        preferences.migrate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        backupJobs = new BackupJobs(this);

        if (gcmAvailable) {
            setBroadcastReceiversEnabled(false);
        } else {
            Log.v(TAG, "Google Play Services not available, forcing use of old scheduler");
            preferences.setUseOldScheduler(true);
        }

        K9MailLib.setDebugStatus(new K9MailLib.DebugStatus() {
            @Override
            public boolean enabled() {
                return preferences.isAppLogDebug();
            }

            @Override
            public boolean debugSensitive() {
                return false;
            }
        });

        if (gcmAvailable && DEBUG) {
            getContentResolver().registerContentObserver(Consts.SMS_PROVIDER, true, new LoggingContentObserver());
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                getContentResolver().registerContentObserver(Consts.CALLLOG_PROVIDER, true, new LoggingContentObserver());
            }
        }
        register(this);
    }

    @Subscribe public void autoBackupSettingsChanged(final AutoBackupSettingsChangedEvent event) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "autoBackupSettingsChanged("+event+")");
        }
        setBroadcastReceiversEnabled(preferences.isUseOldScheduler() && preferences.isAutoBackupEnabled());
        rescheduleJobs();
    }

    public static void register(Object listener) {
        try {
            bus.register(listener);
        } catch (IllegalArgumentException ignored) {
            Log.w(TAG, ignored);
        }
     }

    public static void unregister(Object listener) {
        try {
            bus.unregister(listener);
        } catch (IllegalArgumentException ignored) {
            Log.w(TAG, ignored);
        }
    }

    public static void post(Object event) {
        bus.post(event);
    }

    @Nullable
    public static String getVersionName(Context context) {
        PackageInfo pInfo;
        try {
            pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, null, e);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public static int getVersionCode(Context context) {
        PackageInfo pInfo;
        try {
            pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, null, e);
            return -1;
        }
    }

    public static boolean isInstalledOnSDCard(Context context) {
        PackageInfo pInfo;
        try {
            pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            return (pInfo.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "error", e);
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "default",
                NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManagerCompat.from(this).createNotificationChannel(channel);
    }

    private void setBroadcastReceiversEnabled(boolean enabled) {
        enableOrDisableComponent(enabled, SmsBroadcastReceiver.class);
        enableOrDisableComponent(enabled, BootReceiver.class);
    }

    private void enableOrDisableComponent(boolean enabled, Class<?> component) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "enableOrDisableComponent("+enabled+", "+component.getSimpleName()+")");
        }
        // NB: changes made via setComponentEnabledSetting are persisted across reboots
        getPackageManager().setComponentEnabledSetting(
            new ComponentName(this, component),
            enabled ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED,
            DONT_KILL_APP /* apply setting without restart */);
    }

    private void rescheduleJobs() {
        backupJobs.cancelAll();

        if (preferences.isAutoBackupEnabled()) {
            backupJobs.scheduleRegular();

            if (preferences.getIncomingTimeoutSecs() > 0 && !preferences.isUseOldScheduler()) {
                backupJobs.scheduleContentTriggerJob();
            }
        }
    }

    private void setupStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
//                    .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyFlashScreen()
            .build());
    }

    private static class LoggingContentObserver extends ContentObserver {
        LoggingContentObserver() {
            super(new Handler());
        }
        @Override public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }
        @Override public void onChange(boolean selfChange, Uri uri) {
            Log.v(TAG, "onChange("+selfChange+", " + uri+")");
        }
    }
}
