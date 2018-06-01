/* Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
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
import android.util.Log;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.BackupJobs;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class BackupBroadcastReceiver extends BroadcastReceiver {
    public static final String BACKUP_ACTION = "com.zegoggles.smssync.BACKUP";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (LOCAL_LOGV) Log.v(TAG, "onReceive(" + context + "," + intent + ")");

        if (BACKUP_ACTION.equals(intent.getAction())) {
            backupRequested(context, intent);
        }
    }

    private void backupRequested(Context context, Intent intent) {
        if (new Preferences(context).isAllow3rdPartyIntegration()) {
            Log.d(TAG, "backup requested via broadcast intent");
            new BackupJobs(context).scheduleImmediate();
        } else {
            Log.d(TAG, "backup requested via broadcast intent but ignored");
        }
    }
}
