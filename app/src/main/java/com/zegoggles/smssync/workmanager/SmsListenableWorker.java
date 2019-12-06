package com.zegoggles.smssync.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

public class SmsListenableWorker extends ListenableWorker {
    /**
     * @param appContext   The application {@link Context}
     * @param workerParams Parameters to setup the internal state of this worker
     */
    public SmsListenableWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    /**
     * Override this method to start your actual background processing. This method is called on
     * the main thread.
     * <p>
     * A ListenableWorker is given a maximum of ten minutes to finish its execution and return a
     * {@link Result}.  After this time has expired, the worker will be signalled to stop and its
     * {@link ListenableFuture} will be cancelled.
     *
     * @return A {@link ListenableFuture} with the {@link Result} of the computation.  If you
     *         cancel this Future, WorkManager will treat this unit of work as failed.
     */
    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        Data input = getInputData();

        // Return a ListenableFuture<>
        return null;
    }

    @Override
    public void onStopped() {
        super.onStopped();
        // Cleanup because you are being stopped.
    }
}
