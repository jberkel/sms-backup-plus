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

import android.app.Application;
import android.content.ComponentName;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import com.fsck.k9.mail.K9MailLib;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.activity.MainActivity;
import com.zegoggles.smssync.compat.GooglePlayServices;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.receiver.SmsBroadcastReceiver;
import com.zegoggles.smssync.service.BackupJobs;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

public class App extends Application {
    public static final boolean DEBUG = BuildConfig.DEBUG;
    public static final boolean LOCAL_LOGV = DEBUG;
    public static final String TAG = "SMSBackup+";
    public static final String LOG = "sms_backup_plus.log";

    public static final Bus bus = new Bus();
    /** Google Play Services present on this device? */
    public static boolean gcmAvailable;

    private Preferences preferences;
    private BackupJobs backupJobs;

    @Override
    public void onCreate() {
        super.onCreate();
        gcmAvailable = GooglePlayServices.isAvailable(this);
        preferences = new Preferences(this);
        backupJobs = new BackupJobs(this);

        if (!gcmAvailable) {
            Log.v(TAG, "Google Play Services not available, forcing use of old scheduler");
            preferences.setUseOldScheduler(true);
        } else {
            setIncomingBroadcastReceiversEnabled(false);
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


        getContentResolver().registerContentObserver(Uri.parse("content://sms"), true, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                onChange(selfChange, null);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                System.err.println("onChange "  + uri);
            }
        });

        bus.register(this);
    }

    @Subscribe
    public void autoBackupEnabledChanged(final MainActivity.AutoBackupEnabledChangedEvent event) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "autoBackupChanged("+event+")");
        }
        setIncomingBroadcastReceiversEnabled(event.autoBackupEnabled);
        rescheduleJobs();
    }

    @Subscribe public void autoBackupSettingsChanged(final MainActivity.AutoBackupSettingsChangedEvent event) {
        setIncomingBroadcastReceiversEnabled(preferences.isUseOldScheduler() && preferences.isEnableAutoSync());
        rescheduleJobs();
    }

    private void setIncomingBroadcastReceiversEnabled(boolean enabled) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "setIncomingBroadcastReceiversEnabled("+enabled+")");
        }
        final ComponentName componentName = new ComponentName(this, SmsBroadcastReceiver.class);
        final int flagEnabled =  enabled ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED;
        getPackageManager().setComponentEnabledSetting(componentName,
            flagEnabled,
            DONT_KILL_APP /* apply setting without restart */);
    }

    private void rescheduleJobs() {
        backupJobs.cancelRegular();
        if (preferences.isEnableAutoSync()) {
            backupJobs.scheduleRegular();
        }
    }
}
