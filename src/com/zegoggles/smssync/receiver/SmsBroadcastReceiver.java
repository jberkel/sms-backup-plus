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
    public static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (LOCAL_LOGV) Log.v(TAG, "onReceive(" + ctx + "," + intent + ")");

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            bootup(ctx);
        } else if (SMS_RECEIVED.equals(intent.getAction())) {
            incomingSMS(ctx);
        }
    }

    private void bootup(Context ctx) {
        if (Preferences.isEnableAutoSync(ctx) &&
                new AuthPreferences(ctx).isLoginInformationSet() &&
                !Preferences.isFirstBackup(ctx)) {

            new Alarms(ctx).scheduleRegularBackup();
        } else {
            Log.i(TAG, "Received bootup but not set up to sync.");
        }
    }

    private void incomingSMS(Context ctx) {
        final boolean autoSync = Preferences.isEnableAutoSync(ctx);
        final boolean loginInformationSet = new AuthPreferences(ctx).isLoginInformationSet();
        final boolean firstBackup = Preferences.isFirstBackup(ctx);
        if (autoSync && loginInformationSet && !firstBackup) {
            new Alarms(ctx).scheduleIncomingBackup();
        } else {
            Log.i(TAG, "Received SMS but not set up to back up.");

            if (Preferences.isAppLogEnabled(ctx)) {
                new AppLog(LOG, DateFormat.getDateFormatOrder(ctx))
                    .appendAndClose("Received SMS but not set up to back up. "+
                    "autoSync="+autoSync+", loginInfoSet="+loginInformationSet+", firstBackup="+firstBackup);
            }
        }
    }
}
