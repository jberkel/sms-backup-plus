package com.zegoggles.smssync.service;

import android.util.Log;
import com.firebase.jobdispatcher.*;

import java.util.List;

import static com.zegoggles.smssync.App.TAG;

public class AlarmManagerDriver implements Driver, JobValidator {
    @Override
    public int schedule(Job job) {
        Log.d(TAG, "schedule " +job);
        return 0;
    }

    @Override
    public int cancel(String tag) {
        Log.d(TAG, "cancel " +tag);
        return 0;
    }

    @Override
    public int cancelAll() {
        Log.d(TAG, "cancelAll");
        return 0;
    }

    @Override
    public JobValidator getValidator() {
        Log.d(TAG, "getValidator");
        return this;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<String> validate(JobParameters jobParameters) {
        Log.d(TAG, "validate: "+jobParameters);
        return null;
    }

    @Override
    public List<String> validate(JobTrigger jobTrigger) {
        Log.d(TAG, "validate "+jobTrigger);
        return null;
    }

    @Override
    public List<String> validate(RetryStrategy retryStrategy) {
        Log.d(TAG, "validate "+retryStrategy);
        return null;
    }
}
