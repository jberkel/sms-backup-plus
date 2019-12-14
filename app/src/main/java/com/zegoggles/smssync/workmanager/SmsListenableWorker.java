package com.zegoggles.smssync.workmanager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.zegoggles.smssync.App;

import static com.zegoggles.smssync.App.TAG;

public class SmsListenableWorker extends Worker {
    /**
     * @param appContext   The application {@link Context}
     * @param workerParams Parameters to setup the internal state of this worker
     */
    public SmsListenableWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        Log.d(TAG, "doWork(" + inputData + ")");
        return Result.success();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        // Cleanup because you are being stopped.
    }
}
