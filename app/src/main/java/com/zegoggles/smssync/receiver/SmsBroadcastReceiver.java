/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
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
package com.zegoggles.smssync.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.format.DateFormat;
import android.util.Log;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.BackupJobs;
import com.zegoggles.smssync.utils.AppLog;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (LOCAL_LOGV) Log.v(TAG, "onReceive(" + context + "," + intent + ")");

        if (SMS_RECEIVED.equals(intent.getAction())) {
            incomingSMS(context);
        } else {
            Log.w(TAG, "unhandled intent: "+intent);
        }
    }

    private void incomingSMS(Context context) {
        if (shouldSchedule(context)) {
            getBackupJobs(context).scheduleIncoming();
        } else {
            Log.i(TAG, "Received SMS but not set up to back up.");
        }
    }

    private boolean shouldSchedule(Context context) {
        final Preferences preferences = getPreferences(context);

        final boolean autoBackupEnabled = preferences.isAutoBackupEnabled();
        final boolean loginInformationSet = getAuthPreferences(context).isLoginInformationSet();
        final boolean firstBackup = preferences.isFirstBackup();

        final boolean schedule = (autoBackupEnabled && loginInformationSet && !firstBackup);

        if (!schedule) {
            final String message = "Not set up to back up. " +
                    "autoBackup=" + autoBackupEnabled +
                    ", loginInfoSet=" + loginInformationSet +
                    ", firstBackup=" + firstBackup;

            log(context, message, preferences.isAppLogDebug());
        }
        return schedule;
    }

    private void log(Context context, String message, boolean appLog) {
        Log.d(TAG, message);
        if (appLog) {
            new AppLog(context).appendAndClose(message);
        }
    }

    protected BackupJobs getBackupJobs(Context context) {
        return new BackupJobs(context);
    }

    protected Preferences getPreferences(Context context) {
        return new Preferences(context);
    }

    protected AuthPreferences getAuthPreferences(Context context) {
        return new AuthPreferences(context);
    }
}
