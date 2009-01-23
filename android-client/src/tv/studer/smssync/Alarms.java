/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
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

package tv.studer.smssync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Alarms {
    /**
     * Schedule a sync right after an SMS arrived.
     */
    static void scheduleIncomingSync(Context ctx) {
        scheduleSync(ctx, PrefStore.getIncomingTimeoutSecs(ctx));
    }
    
    /**
     * Schedule a sync at default rate for syncing outgoing SMS.
     */
    static void scheduleRegularSync(Context ctx) {
        scheduleSync(ctx, PrefStore.getRegularTimeoutSecs(ctx));
    }
    
    private static void scheduleSync(Context ctx, int inSeconds) {
        long atTime = System.currentTimeMillis() + inSeconds * 1000l;
        
        Intent serviceIntent = new Intent(ctx, SmsSyncService.class);
        serviceIntent.putExtra(Consts.KEY_NUM_RETRIES, Consts.NUM_AUTO_RETRIES);
        PendingIntent pi = PendingIntent.getService(ctx, 0, serviceIntent, 0);
        
        AlarmManager aMgr = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        aMgr.set(AlarmManager.RTC_WAKEUP, atTime, pi);
        Log.d(Consts.TAG, "Scheduled sync due in " + inSeconds + " seconds.");
    }
}
