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
package com.zegoggles.smssync.service;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.zegoggles.smssync.App;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.state.BackupState;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.service.BackupType.REGULAR;
import static com.zegoggles.smssync.service.CancelEvent.Origin.SYSTEM;

public class SmsBackupWorker extends Worker {
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile boolean needsRetry = false;

    public SmsBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        final String backupTypeName = getInputData().getString(BackupJobs.KEY_BACKUP_TYPE);
        if (LOCAL_LOGV) {
            Log.v(TAG, "SmsBackupWorker.doWork(type=" + backupTypeName + ")");
        }

        if (!shouldRun(backupTypeName)) {
            Log.d(TAG, "skipping run");
            return Result.success();
        }

        App.register(this);
        try {
            // Since API level 26, an app in background cannot start a background service,
            // so just instantiate service manually
            SmsBackupService service = new SmsBackupService();
            service.attachBaseContext(getApplicationContext());
            service.handleIntent(new Intent(backupTypeName));

            // Wait for the backup to complete (up to 9 minutes to stay within WorkManager's 10-minute limit)
            latch.await(9, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Log.w(TAG, "Worker interrupted", e);
            return Result.retry();
        } finally {
            App.unregister(this);
        }

        return needsRetry ? Result.retry() : Result.success();
    }

    @Override
    public void onStopped() {
        super.onStopped();
        if (LOCAL_LOGV) {
            Log.v(TAG, "SmsBackupWorker.onStopped()");
        }
        App.post(new CancelEvent(SYSTEM));
        latch.countDown();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void backupStateChanged(BackupState state) {
        if (!state.isFinished()) {
            return;
        }

        needsRetry = state.isError() && !state.isPermissionException();
        if (LOCAL_LOGV) {
            Log.v(TAG, "backupStateChanged(isError=" + state.isError() + ", needsRetry=" + needsRetry + ")");
        }
        latch.countDown();
    }

    private boolean shouldRun(String backupTypeName) {
        if (BackupType.fromName(backupTypeName) == REGULAR) {
            final Preferences prefs = new Preferences(getApplicationContext());
            final boolean autoBackupEnabled = prefs.isAutoBackupEnabled();
            if (!autoBackupEnabled) {
                new BackupJobs(getApplicationContext()).cancelRegular();
            }
            return autoBackupEnabled;
        } else {
            return true;
        }
    }
}
