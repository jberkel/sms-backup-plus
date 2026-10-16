/* Copyright (c) 2017 Jan Berkel <jan.berkel@gmail.com>
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
package sms.backup.plus.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;
import com.squareup.otto.Subscribe;
import sms.backup.plus.App;
import sms.backup.plus.preferences.Preferences;
import sms.backup.plus.service.state.BackupState;

import static sms.backup.plus.App.LOCAL_LOGV;
import static sms.backup.plus.App.TAG;
import static sms.backup.plus.service.BackupJobs.DATA_BACKUP_TYPE;
import static sms.backup.plus.service.BackupJobs.DATA_CONTENT_TRIGGER;
import static sms.backup.plus.service.BackupType.REGULAR;
import static sms.backup.plus.service.CancelEvent.Origin.SYSTEM;

/**
 * Runs a backup scheduled by {@link BackupJobs} via {@link androidx.work.WorkManager}.
 *
 * <p>The actual work is performed by {@link SmsBackupService}, which reports progress
 * asynchronously through the application event bus. We bridge that callback to the
 * {@link ListenableFuture} returned by {@link #startWork()} so WorkManager keeps the
 * worker alive until the backup has finished.
 */
public class SmsBackupWorker extends ListenableWorker {
    @Nullable private CallbackToFutureAdapter.Completer<Result> completer;
    @Nullable private String backupType;

    public SmsBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
        super(context, workerParameters);
    }

    @NonNull
    @Override
    public ListenableFuture<androidx.work.ForegroundInfo> getForegroundInfoAsync() {
        return CallbackToFutureAdapter.getFuture(completer -> {
            completer.set(createForegroundInfo(backupType));
            return "getForegroundInfoAsync";
        });
    }

    @NonNull @Override
    public ListenableFuture<Result> startWork() {
        backupType = getInputData().getString(DATA_BACKUP_TYPE);
        final boolean contentTrigger = getInputData().getBoolean(DATA_CONTENT_TRIGGER, false);
        if (LOCAL_LOGV) {
            Log.v(TAG, "startWork(backupType=" + backupType + ", contentTrigger=" + contentTrigger + ")");
        }

        return CallbackToFutureAdapter.getFuture(completer -> {
            this.completer = completer;
            if (contentTrigger) {
                // the content observer is single-shot, so re-arm it and request an incoming backup
                getBackupJobs().scheduleContentTriggerJob();
                getBackupJobs().scheduleIncoming();
                complete(Result.success());
            } else if (shouldRun()) {
                App.register(this);
                try {
                    setForegroundAsync(createForegroundInfo(backupType));
                } catch (Exception e) {
                    Log.w(TAG, "Failed to set foreground async", e);
                }
                startBackup(backupType);
            } else {
                Log.d(TAG, "skipping run");
                complete(Result.success());
            }
            return "SmsBackupWorker:" + backupType;
        });
    }

    @NonNull
    private androidx.work.ForegroundInfo createForegroundInfo(@Nullable String backupType) {
        Context context = getApplicationContext();
        String title = context.getString(sms.backup.plus.R.string.status_backup);
        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, App.CHANNEL_ID)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(context.getString(sms.backup.plus.R.string.app_name))
                .setSmallIcon(sms.backup.plus.R.drawable.ic_notification)
                .setOngoing(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return new androidx.work.ForegroundInfo(1, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new androidx.work.ForegroundInfo(1, builder.build());
    }

    /**
     * Starts the actual backup. Since API level 26, an app in the background cannot start a
     * background service, so the service is instantiated manually.
     * https://developer.android.com/about/versions/oreo/background.html#services
     */
    protected void startBackup(String backupType) {
        SmsBackupService service = new SmsBackupService();
        service.attachBaseContext(getApplicationContext());
        service.handleIntent(new Intent(backupType));
    }

    /**
     * Called when WorkManager interrupts the running work, most likely because the runtime
     * constraints are no longer satisfied. The backup must stop execution.
     */
    @Override
    public void onStopped() {
        if (LOCAL_LOGV) Log.v(TAG, "onStopped()");
        App.post(new CancelEvent(SYSTEM));
    }

    @Subscribe
    public void backupStateChanged(BackupState state) {
        if (!state.isFinished() || state.backupType == null || !state.backupType.name().equals(backupType)) {
            return;
        }
        final boolean needsReschedule = state.isError() && !state.isPermissionException();
        if (LOCAL_LOGV) {
            Log.v(TAG, "backupStateChanged finished(isError=" + state.isError() + ", needsReschedule=" + needsReschedule + ")");
        }
        complete(needsReschedule ? Result.retry() : Result.success());
    }

    private void complete(Result result) {
        App.unregister(this);
        if (completer != null) {
            completer.set(result);
            completer = null;
        }
    }

    private boolean shouldRun() {
        if (BackupType.fromName(backupType) == REGULAR) {
            final boolean autoBackupEnabled = new Preferences(getApplicationContext()).isAutoBackupEnabled();
            if (!autoBackupEnabled) {
                // was disabled in the meantime, cancel
                getBackupJobs().cancelRegular();
            }
            return autoBackupEnabled;
        }
        return true;
    }

    private BackupJobs getBackupJobs() {
        return new BackupJobs(getApplicationContext());
    }
}
