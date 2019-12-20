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
import androidx.annotation.Nullable;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.ObservedUri;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.Preferences;

import java.util.ArrayList;
import java.util.List;

import static com.firebase.jobdispatcher.Constraint.ON_ANY_NETWORK;
import static com.firebase.jobdispatcher.Constraint.ON_UNMETERED_NETWORK;
import static com.firebase.jobdispatcher.FirebaseJobDispatcher.CANCEL_RESULT_SUCCESS;
import static com.firebase.jobdispatcher.FirebaseJobDispatcher.SCHEDULE_RESULT_SUCCESS;
import static com.firebase.jobdispatcher.Lifetime.FOREVER;
import static com.firebase.jobdispatcher.Lifetime.UNTIL_NEXT_BOOT;
import static com.firebase.jobdispatcher.ObservedUri.Flags.FLAG_NOTIFY_FOR_DESCENDANTS;
import static com.firebase.jobdispatcher.RetryStrategy.RETRY_POLICY_EXPONENTIAL;
import static com.firebase.jobdispatcher.Trigger.NOW;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.Consts.CALLLOG_PROVIDER;
import static com.zegoggles.smssync.Consts.SMS_PROVIDER;
import static com.zegoggles.smssync.service.BackupType.BROADCAST_INTENT;
import static com.zegoggles.smssync.service.BackupType.INCOMING;
import static com.zegoggles.smssync.service.BackupType.REGULAR;


public class BackupJobs {
    private static final int BOOT_BACKUP_DELAY = 60;
    static final String CONTENT_TRIGGER_TAG = "contentTrigger";

    private final Preferences preferences;
    private final FirebaseJobDispatcher firebaseJobDispatcher;

    public BackupJobs(Context context) {
        this(context, new Preferences(context));
    }

    BackupJobs(Context context, Preferences preferences) {
        this.preferences = preferences;
        firebaseJobDispatcher = new FirebaseJobDispatcher(
            preferences.isUseOldScheduler() ?
            new AlarmManagerDriver(context) :
            new GooglePlayDriver(context));
    }

    public @Nullable Job scheduleIncoming() {
        return schedule(preferences.getIncomingTimeoutSecs(), INCOMING, false);
    }

    public Job scheduleRegular() {
        return schedule(preferences.getRegularTimeoutSecs(), REGULAR, false);
    }

    public @Nullable Job scheduleContentTriggerJob() {
        return schedule(createContentUriTriggerJob());
    }

    public @Nullable Job scheduleBootup() {
        if (!preferences.isAutoBackupEnabled()) {
            Log.d(TAG, "auto backup no longer enabled, canceling all jobs");
            cancelAll();
            return null;
        } else if (preferences.isUseOldScheduler()) {
            return schedule(BOOT_BACKUP_DELAY, REGULAR, false);
        } else {
            // everything else should be persistent by GCM
            return null;
        }
    }

    public @Nullable Job scheduleImmediate() {
        return schedule(-1, BROADCAST_INTENT, true);
    }

    public void cancelAll() {
        cancelRegular();
        cancelContentUriTrigger();
    }

    public void cancelRegular() {
        cancel(REGULAR.name());
    }

    private void cancelContentUriTrigger() {
        cancel(CONTENT_TRIGGER_TAG);
    }

    private void cancel(String tag) {
        final int result = firebaseJobDispatcher.cancel(tag);
        if (result == CANCEL_RESULT_SUCCESS) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "cancel("+tag+")");
            }
        } else {
            Log.w(TAG, "unable to cancel jobs: "+result);
        }
    }

    @Nullable private Job schedule(int inSeconds, BackupType backupType, boolean force) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "scheduleBackup(" + inSeconds + ", " + backupType + ", " + force + ")");
        }

        if (force || (preferences.isAutoBackupEnabled() && inSeconds > 0)) {
            final Job job = createJob(inSeconds, backupType);
            if (schedule(job) != null) {
                if (LOCAL_LOGV) {
                    Log.v(TAG, "Scheduled backup job " + job + ", tag: " + job.getTag() + " due " +
                            "" + (inSeconds > 0 ? "in " + inSeconds + " seconds" : "now"));
                }
            }
            return job;
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "Not scheduling backup because auto backup is disabled.");
            return null;
        }
    }

    private Job schedule(Job job) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "schedule job " + job.getTag());
        }
        final int result = firebaseJobDispatcher.schedule(job);
        if (result == SCHEDULE_RESULT_SUCCESS) {
            return job;
        } else {
            Log.w(TAG, "Error scheduling job: "+result);
            return null;
        }
    }

    private @NonNull Job createJob(int inSeconds, BackupType backupType) {
        return createBuilder(backupType)
            .setTrigger(inSeconds <= 0 ? NOW : Trigger.executionWindow(inSeconds, inSeconds))
            .setRecurring(backupType.isRecurring())
            .setLifetime(backupType.isRecurring() ? FOREVER : UNTIL_NEXT_BOOT)
            .build();
    }

    private @NonNull Job createContentUriTriggerJob() {
        return createBuilder(INCOMING)
            .setTrigger(Trigger.contentUriTrigger(observedUris()))
            .setRecurring(true)
            .setLifetime(FOREVER)
            .setTag(CONTENT_TRIGGER_TAG)
            .build();
    }

    private @NonNull List<ObservedUri> observedUris() {
        List<ObservedUri> observedUris = new ArrayList<ObservedUri>();
        observedUris.add(new ObservedUri(SMS_PROVIDER, FLAG_NOTIFY_FOR_DESCENDANTS));
        if (preferences.getDataTypePreferences().isBackupEnabled(DataType.CALLLOG) && preferences.isCallLogBackupAfterCallEnabled()) {
            observedUris.add(new ObservedUri(CALLLOG_PROVIDER, FLAG_NOTIFY_FOR_DESCENDANTS));
        }
        return observedUris;
    }

    private @NonNull Job.Builder createBuilder(BackupType backupType) {
        return firebaseJobDispatcher.newJobBuilder()
            .setReplaceCurrent(true)
            .setService(SmsJobService.class)
            .setTag(backupType.name())
            .setRetryStrategy(defaultRetryStrategy())
            .setConstraints(jobConstraints(backupType));
    }

    private int[] jobConstraints(BackupType backupType) {
        switch (backupType) {
            case BROADCAST_INTENT: return new int[0];
            default:
                return preferences.isWifiOnly() ? new int[] { ON_UNMETERED_NETWORK } : new int[] { ON_ANY_NETWORK };
        }
    }

    // initial_backoff * 2 ^ (num_failures - 1) = [ 30, 60, 120, 240, 480, ... ]
    private RetryStrategy defaultRetryStrategy() {
        return firebaseJobDispatcher.newRetryStrategy(RETRY_POLICY_EXPONENTIAL,  30, 300);
    }
}
