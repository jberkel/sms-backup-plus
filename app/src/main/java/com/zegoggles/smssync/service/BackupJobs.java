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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.zegoggles.smssync.preferences.Preferences;

import java.util.concurrent.TimeUnit;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.service.BackupType.BROADCAST_INTENT;
import static com.zegoggles.smssync.service.BackupType.INCOMING;
import static com.zegoggles.smssync.service.BackupType.REGULAR;

public class BackupJobs {
    private static final int BOOT_BACKUP_DELAY = 60;
    static final String CONTENT_TRIGGER_TAG = "contentTrigger";
    static final String KEY_BACKUP_TYPE = "backup_type";

    private final Preferences preferences;
    private final WorkManager workManager;

    public BackupJobs(Context context) {
        this(context, new Preferences(context));
    }

    BackupJobs(Context context, Preferences preferences) {
        this.preferences = preferences;
        this.workManager = WorkManager.getInstance(context);
    }

    public long scheduleIncoming() {
        int delaySecs = preferences.getIncomingTimeoutSecs();
        return schedule(delaySecs, INCOMING, false);
    }

    /**
     * @return delay in milliseconds if scheduled, 0 otherwise
     */
    public long scheduleRegular() {
        int delaySecs = preferences.getRegularTimeoutSecs();
        return schedule(delaySecs, REGULAR, false);
    }

    public void scheduleContentTriggerJob() {
        // WorkManager does not support content URI triggers for OneTimeWorkRequest
        // in the same way Firebase JobDispatcher did. Instead, we rely on the
        // SmsBroadcastReceiver for incoming SMS and schedule incoming backups from there.
        // For call log changes, we schedule a periodic check.
        if (LOCAL_LOGV) {
            Log.v(TAG, "scheduleContentTriggerJob: relying on broadcast receivers for content triggers");
        }
    }

    public void scheduleBootup() {
        if (!preferences.isAutoBackupEnabled()) {
            Log.d(TAG, "auto backup no longer enabled, canceling all jobs");
            cancelAll();
        } else if (preferences.isUseOldScheduler()) {
            schedule(BOOT_BACKUP_DELAY, REGULAR, false);
        }
        // else: WorkManager persists jobs across reboots automatically
    }

    public void scheduleImmediate() {
        schedule(-1, BROADCAST_INTENT, true);
    }

    public void cancelAll() {
        cancelRegular();
        workManager.cancelUniqueWork(CONTENT_TRIGGER_TAG);
        workManager.cancelUniqueWork(INCOMING.name());
    }

    public void cancelRegular() {
        workManager.cancelUniqueWork(REGULAR.name());
    }

    /**
     * @return delay in milliseconds if scheduled, 0 otherwise
     */
    private long schedule(int inSeconds, BackupType backupType, boolean force) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "scheduleBackup(" + inSeconds + ", " + backupType + ", " + force + ")");
        }

        if (force || (preferences.isAutoBackupEnabled() && inSeconds > 0)) {
            Data inputData = new Data.Builder()
                    .putString(KEY_BACKUP_TYPE, backupType.name())
                    .build();

            Constraints constraints = buildConstraints(backupType);

            if (inSeconds <= 0) {
                // Immediate execution
                OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SmsBackupWorker.class)
                        .setInputData(inputData)
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .addTag(backupType.name())
                        .build();

                workManager.enqueueUniqueWork(
                        backupType.name(),
                        ExistingWorkPolicy.REPLACE,
                        request);
            } else {
                OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SmsBackupWorker.class)
                        .setInputData(inputData)
                        .setConstraints(constraints)
                        .setInitialDelay(inSeconds, TimeUnit.SECONDS)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .addTag(backupType.name())
                        .build();

                workManager.enqueueUniqueWork(
                        backupType.name(),
                        ExistingWorkPolicy.REPLACE,
                        request);
            }

            if (LOCAL_LOGV) {
                Log.v(TAG, "Scheduled backup job tag: " + backupType.name() + " due " +
                        (inSeconds > 0 ? "in " + inSeconds + " seconds" : "now"));
            }
            return inSeconds > 0 ? inSeconds * 1000L : 0;
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "Not scheduling backup because auto backup is disabled.");
            return 0;
        }
    }

    @NonNull
    private Constraints buildConstraints(BackupType backupType) {
        Constraints.Builder builder = new Constraints.Builder();
        if (backupType != BROADCAST_INTENT) {
            builder.setRequiredNetworkType(
                    preferences.isWifiOnly() ? NetworkType.UNMETERED : NetworkType.CONNECTED);
        }
        return builder.build();
    }
}
