package com.zegoggles.smssync.service;

import android.content.Intent;

import android.util.Log;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;


public class SmsJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters job) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "onStartJob(" + job + ", extras=" + job.getExtras() + ")");
        }
        final Intent intent = new Intent(this, SmsBackupService.class);
        intent.putExtras(job.getExtras());
        startService(intent);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "onStopJob(" + job + ", extras=" + job.getExtras() + ")");
        }
        return false;
    }
}
