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

package com.zegoggles.smssync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static com.zegoggles.smssync.App.*;

public class Alarms {

    /**
     * Schedule a sync right after an SMS arrived.
     */
    static void scheduleIncomingSync(Context ctx) {
        scheduleSync(ctx, PrefStore.getIncomingTimeoutSecs(ctx), false);
    }

    /**
     * Schedule a sync at default rate for syncing outgoing SMS.
     */
    static void scheduleRegularSync(Context ctx) {
        scheduleSync(ctx, PrefStore.getRegularTimeoutSecs(ctx), false);
    }

    /**
     * Schedule a sync ASAP
     */
    static void scheduleImmediateSync(Context ctx) {
        scheduleSync(ctx, -1, true);
    }

    static void cancel(Context ctx) {
        getAlarmManager(ctx).cancel(createPendingIntent(ctx));
    }

    private static void scheduleSync(Context ctx, int inSeconds, boolean force) {
        if (LOCAL_LOGV) Log.v(TAG, "scheduleSync("+ctx+", "+inSeconds+")");

        if ((PrefStore.isEnableAutoSync(ctx) && inSeconds > 0) || force) {
          final long atTime = System.currentTimeMillis() + inSeconds * 1000l;
          getAlarmManager(ctx).set(AlarmManager.RTC_WAKEUP, atTime, createPendingIntent(ctx));
          Log.d(TAG, "Scheduled sync due " + (inSeconds > 0 ? "in "+ inSeconds + " seconds" : "now"));
        } else {
          Log.d(TAG, "Not scheduling sync because auto sync is disabled.");
        }
    }

    private static AlarmManager getAlarmManager(Context ctx) {
      return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent createPendingIntent(Context ctx) {
        Intent serviceIntent = new Intent(ctx, SmsBackupService.class);
        serviceIntent.putExtra(Consts.KEY_NUM_RETRIES, Consts.NUM_AUTO_RETRIES);
        return PendingIntent.getService(ctx, 0, serviceIntent, 0);
    }
}
