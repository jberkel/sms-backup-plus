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

package com.zegoggles.smssync.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.zegoggles.smssync.preferences.Preferences;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.service.BackupType.*;


public class Alarms {
    private Context mContext;

    public Alarms(Context context) {
        mContext = context.getApplicationContext();
    }

    public long scheduleIncomingBackup() {
        return scheduleBackup(Preferences.getIncomingTimeoutSecs(mContext), INCOMING, false);
    }

    public long scheduleRegularBackup() {
        return scheduleBackup(Preferences.getRegularTimeoutSecs(mContext), REGULAR, false);
    }

    public long scheduleImmediateBackup() {
        return scheduleBackup(-1, BROADCAST_INTENT, true);
    }

    public void cancel() {
        getAlarmManager(mContext).cancel(createPendingIntent(mContext, UNKNOWN));
    }

    private long scheduleBackup(int inSeconds, BackupType backupType, boolean force) {
        if (LOCAL_LOGV)
            Log.v(TAG, "scheduleBackup(" + mContext + ", " + inSeconds + ", " + backupType + ", " + force + ")");

        if (force || (Preferences.isEnableAutoSync(mContext) && inSeconds > 0)) {
            final long atTime = System.currentTimeMillis() + (inSeconds * 1000l);
            getAlarmManager(mContext).set(AlarmManager.RTC_WAKEUP, atTime, createPendingIntent(mContext, backupType));
            if (LOCAL_LOGV)
                Log.v(TAG, "Scheduled backup due " + (inSeconds > 0 ? "in " + inSeconds + " seconds" : "now"));
            return atTime;
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "Not scheduling sync because auto sync is disabled.");
            return -1;
        }
    }

    private static AlarmManager getAlarmManager(Context ctx) {
        return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent createPendingIntent(Context ctx, BackupType backupType) {
        final Intent intent = (new Intent(ctx, SmsBackupService.class))
                .putExtra(BackupType.EXTRA, backupType.name());
        return PendingIntent.getService(ctx, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
