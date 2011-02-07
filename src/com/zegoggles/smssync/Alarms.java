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

    static final int BROADCAST_INTENT = 0;
    static final int INCOMING = 1;
    static final int REGULAR  = 2;
    static final int UNKNOWN  = 3;

    static long scheduleIncomingSync(Context ctx) {
        return scheduleSync(ctx, PrefStore.getIncomingTimeoutSecs(ctx), INCOMING, false);
    }

    static long scheduleRegularSync(Context ctx) {
        return scheduleSync(ctx, PrefStore.getRegularTimeoutSecs(ctx), REGULAR, false);
    }

    static long scheduleImmediateSync(Context ctx) {
        return scheduleSync(ctx, -1, BROADCAST_INTENT, true);
    }

    static void cancel(Context ctx) {
        getAlarmManager(ctx).cancel(createPendingIntent(ctx, UNKNOWN));
    }

    private static long scheduleSync(Context ctx, int inSeconds, int source, boolean force) {
        if (LOCAL_LOGV) Log.v(TAG, "scheduleSync("+ctx+", "+inSeconds+", "+source+", "+force+")");

        if (force || (PrefStore.isEnableAutoSync(ctx) && inSeconds > 0)) {
          final long atTime = System.currentTimeMillis() + (inSeconds * 1000l);
          getAlarmManager(ctx).set(AlarmManager.RTC_WAKEUP, atTime, createPendingIntent(ctx, source));
          if (LOCAL_LOGV) Log.v(TAG, "Scheduled sync due " + (inSeconds > 0 ? "in " + inSeconds + " seconds" : "now"));
          return atTime;
        } else {
          if (LOCAL_LOGV) Log.v(TAG, "Not scheduling sync because auto sync is disabled.");
          return -1;
        }
    }

    private static AlarmManager getAlarmManager(Context ctx) {
      return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent createPendingIntent(Context ctx, int source) {
        Intent intent = (new Intent(ctx, SmsBackupService.class))
              .putExtra(Consts.KEY_NUM_RETRIES, Consts.NUM_AUTO_RETRIES)
              .putExtra(Consts.SOURCE, source);
        return PendingIntent.getService(ctx, 0, intent, 0);
    }
}
