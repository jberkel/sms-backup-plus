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
import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.util.Log;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.Alarms;
import com.zegoggles.smssync.utils.AppLog;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.LOG;
import static com.zegoggles.smssync.App.TAG;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (LOCAL_LOGV) Log.v(TAG, "onReceive(" + context + "," + intent + ")");

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            bootup(context);
        } else if (SMS_RECEIVED.equals(intent.getAction())) {
            incomingSMS(context);
        }
    }

    private void bootup(Context context) {
        if (shouldSchedule(context)) {
            new Alarms(context).scheduleRegularBackup();
        } else {
            Log.i(TAG, "Received bootup but not set up to back up.");
        }
    }

    private void incomingSMS(Context context) {
        if (shouldSchedule(context)) {
            new Alarms(context).scheduleIncomingBackup();
        } else {
            Log.i(TAG, "Received SMS but not set up to back up.");
        }
    }

    private boolean shouldSchedule(Context context) {
        final boolean autoSync = Preferences.isEnableAutoSync(context);
        final boolean loginInformationSet = new AuthPreferences(context).isLoginInformationSet();
        final boolean firstBackup = Preferences.isFirstBackup(context);
        final boolean schedule = (autoSync && loginInformationSet && !firstBackup);

        if (!schedule && Preferences.isAppLogDebug(context)) {
            new AppLog(LOG, DateFormat.getDateFormatOrder(context))
                    .appendAndClose("Not set up to back up. "+
                            "autoSync="+autoSync+", loginInfoSet="+loginInformationSet+", firstBackup="+firstBackup);

        }
        return schedule;
    }
}
