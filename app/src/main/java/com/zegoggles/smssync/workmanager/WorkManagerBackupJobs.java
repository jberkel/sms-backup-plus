package com.zegoggles.smssync.workmanager;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkRequest;

import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.BackupType;

import static androidx.work.BackoffPolicy.EXPONENTIAL;
import static androidx.work.NetworkType.CONNECTED;
import static androidx.work.NetworkType.UNMETERED;
import static com.zegoggles.smssync.Consts.CALLLOG_PROVIDER;
import static com.zegoggles.smssync.Consts.SMS_PROVIDER;
import static com.zegoggles.smssync.service.BackupType.INCOMING;
import static java.util.concurrent.TimeUnit.SECONDS;

public class WorkManagerBackupJobs {
    private final Preferences preferences;

    WorkManagerBackupJobs(Context context, Preferences preferences) {
        this.preferences = preferences;
    }

    public @Nullable WorkRequest createIncomingWorkRequest() {
        Data input = new Data.Builder().build();
        return new OneTimeWorkRequest.Builder(SmsListenableWorker.class)
            .setInputData(input)
            .addTag(INCOMING.name())
            .setBackoffCriteria(EXPONENTIAL, 30, SECONDS)
            .setInitialDelay(preferences.getIncomingTimeoutSecs(), SECONDS)
            .setConstraints(jobConstraints(INCOMING))
            .build();
    }

    private Constraints jobConstraints(BackupType backupType) {
        Constraints.Builder builder = new Constraints.Builder();
        builder.setRequiredNetworkType(preferences.isWifiOnly() ? UNMETERED : CONNECTED);
        return observeUris(builder).build();
    }

    private Constraints.Builder observeUris(Constraints.Builder builder) {
        builder.addContentUriTrigger(SMS_PROVIDER, true);
        if (preferences.getDataTypePreferences().isBackupEnabled(DataType.CALLLOG)
         && preferences.isCallLogBackupAfterCallEnabled()) {
            builder.addContentUriTrigger(CALLLOG_PROVIDER, true);
        }
        return builder;
    }
}
