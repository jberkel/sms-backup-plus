/*
 * Copyright (c) 2017 Jan Berkel <jan.berkel@gmail.com>
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
import androidx.annotation.NonNull;
import android.util.Log;
import com.firebase.jobdispatcher.Driver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobTrigger;
import com.firebase.jobdispatcher.JobValidator;
import com.firebase.jobdispatcher.RetryStrategy;

import java.util.List;

import static android.app.AlarmManager.RTC_WAKEUP;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static com.firebase.jobdispatcher.FirebaseJobDispatcher.CANCEL_RESULT_SUCCESS;
import static com.firebase.jobdispatcher.FirebaseJobDispatcher.SCHEDULE_RESULT_SUCCESS;
import static com.firebase.jobdispatcher.FirebaseJobDispatcher.SCHEDULE_RESULT_UNSUPPORTED_TRIGGER;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

/**
 * Simple driver to emulate old AlarmManager backup scheduling behaviour.
 */
class AlarmManagerDriver implements Driver, JobValidator {
    private final AlarmManager alarmManager;
    private final Context context;

    AlarmManagerDriver(Context context) {
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.context = context.getApplicationContext();
    }

    @Override
    public int schedule(@NonNull Job job) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "AlarmManagerDriver: schedule " +job);
        }

        final JobTrigger trigger = job.getTrigger();
        final long atTime = scheduleTime(trigger);

        if (atTime > 0) {
            alarmManager.set(RTC_WAKEUP, atTime, createPendingIntent(context, BackupType.fromName(job.getTag())));

            return SCHEDULE_RESULT_SUCCESS;
        } else {
            Log.w(TAG, "unsupported trigger for job "+job);
            return SCHEDULE_RESULT_UNSUPPORTED_TRIGGER;
        }
    }

    @Override
    public int cancel(@NonNull String tag) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "AlarmManagerDriver: cancel " +tag);
        }
        // Matching intents based on Intent#filterEquals():
        // That is, if their action, data, type, class, and categories are the same.
        alarmManager.cancel(createPendingIntent(context, BackupType.fromName(tag)));
        return CANCEL_RESULT_SUCCESS;
    }

    @Override
    public int cancelAll() {
        if (LOCAL_LOGV) {
            Log.v(TAG, "AlarmManagerDriver: cancelAll");
        }
        cancel(BackupType.REGULAR.name());
        return CANCEL_RESULT_SUCCESS;
    }

    @Override
    public @NonNull JobValidator getValidator() {
        return this;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<String> validate(JobParameters jobParameters) {
        return null;
    }

    /**
     * @return a List of error messages, or null if the Trigger is valid.
     */
    @Override
    public List<String> validate(JobTrigger jobTrigger) {
        return null;
    }

    /**
     * @return a List of error messages, or null if the Trigger is valid.
     */
    @Override
    public List<String> validate(RetryStrategy retryStrategy) {
        return null;
    }

    private static PendingIntent createPendingIntent(Context ctx, BackupType backupType) {
        final Intent intent = (new Intent(ctx, SmsBackupService.class))
            .setAction(backupType.name());

        return PendingIntent.getService(ctx, 0, intent, FLAG_UPDATE_CURRENT);
    }

    private static long scheduleTime(JobTrigger trigger) {
        if (trigger instanceof JobTrigger.ImmediateTrigger) {
            return System.currentTimeMillis();
        } else if (trigger instanceof JobTrigger.ExecutionWindowTrigger) {
            JobTrigger.ExecutionWindowTrigger executionWindowTrigger = (JobTrigger.ExecutionWindowTrigger) trigger;
            return System.currentTimeMillis() + (executionWindowTrigger.getWindowStart() * 1000L);
        } else {
            return -1;
        }
    }
}
