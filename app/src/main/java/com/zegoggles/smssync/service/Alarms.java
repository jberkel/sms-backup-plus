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
import android.os.Bundle;
import android.util.Log;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobTrigger;
import com.firebase.jobdispatcher.Trigger;
import com.zegoggles.smssync.preferences.Preferences;

import java.util.UUID;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.service.BackupType.*;


public class Alarms {
    private static final int BOOT_BACKUP_DELAY = 60;

    private final Preferences mPreferences;
    private Context mContext;
    private FirebaseJobDispatcher firebaseJobDispatcher;

    public Alarms(Context context) {
        this(context.getApplicationContext(), new Preferences(context));
    }

    Alarms(Context context, Preferences preferences) {
        mContext = context.getApplicationContext();
        mPreferences = preferences;
        firebaseJobDispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
    }

    public Job scheduleIncomingBackup() {
        return scheduleBackup(mPreferences.getIncomingTimeoutSecs(), INCOMING, false);
    }

    public Job scheduleRegularBackup() {
        return scheduleBackup(mPreferences.getRegularTimeoutSecs(), REGULAR, false);
    }

    public Job scheduleBootupBackup() {
        return scheduleBackup(BOOT_BACKUP_DELAY, REGULAR, false);
    }

    public Job scheduleImmediateBackup() {
        return scheduleBackup(-1, BROADCAST_INTENT, true);
    }

    public void cancel() {
        firebaseJobDispatcher.cancelAll();
    }

    private Job scheduleBackup(int inSeconds, BackupType backupType, boolean force) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "scheduleBackup(" + mContext + ", " + inSeconds + ", " + backupType + ", " + force + ")");
        }

        if (force || (mPreferences.isEnableAutoSync() && inSeconds > 0)) {

            Job job = getJob(firebaseJobDispatcher, inSeconds, backupType);
            firebaseJobDispatcher.schedule(job);

            if (LOCAL_LOGV) {
                Log.v(TAG, "Scheduled backup due " + (inSeconds > 0 ? "in " + inSeconds + " seconds" : "now"));
            }
            return job;
        } else {
            if (LOCAL_LOGV) Log.v(TAG, "Not scheduling backup because auto sync is disabled.");
            return null;
        }
    }

    private Job getJob(FirebaseJobDispatcher dispatcher, int inSeconds, BackupType backupType)
    {
        JobTrigger trigger;
        if (inSeconds <= 0)
        {
            trigger = Trigger.NOW;
        }
        else
        {
            trigger = Trigger.executionWindow(inSeconds, inSeconds);
        }

        int constraint = mPreferences.isWifiOnly() ? Constraint.ON_UNMETERED_NETWORK : Constraint.ON_ANY_NETWORK;
        Bundle extras = new Bundle();
        extras.putString(BackupType.EXTRA, backupType.name());
        Job.Builder job = dispatcher.newJobBuilder()
            .setReplaceCurrent(false)
            .setTrigger(trigger)
            .setConstraints(constraint)
            .setTag(backupType.name())
            .setExtras(extras)
            .setService(SmsJobService.class)
            ;

        return job.build();
    }
}
