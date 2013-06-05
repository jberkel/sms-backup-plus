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

import android.app.Notification;
import android.content.Intent;
import android.text.format.DateFormat;
import android.util.Log;
import com.fsck.k9.mail.MessagingException;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.preferences.PrefStore;
import com.zegoggles.smssync.service.state.BackupState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.service.BackupType.MANUAL;
import static com.zegoggles.smssync.service.state.SmsSyncState.ERROR;
import static com.zegoggles.smssync.service.state.SmsSyncState.FINISHED_BACKUP;

public class SmsBackupService extends ServiceBase {
    private static final int BACKUP_ID = 1;

    /**
     * Number of messages sent per sync request.
     * Changing this value will cause mms/sms messages to thread out of order.
     */
    private static final int MAX_MSG_PER_REQUEST = 1;

    @Nullable private static SmsBackupService service;
    @NotNull private BackupState mState = new BackupState();

    @Override @NotNull
    public BackupState getState() {
        return mState;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        service = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (LOCAL_LOGV) Log.v(TAG, "SmsBackupService#onDestroy(state=" + getState() + ")");
        service = null;
    }

    @Override
    protected void handleIntent(final Intent intent) {
        if (intent == null) return; // NB: should not happen with START_NOT_STICKY
        final BackupType backupType = BackupType.fromIntent(intent);
        if (LOCAL_LOGV) Log.v(TAG, "handleIntent(" + intent +
                ", " + (intent.getExtras() == null ? "null" : intent.getExtras().keySet()) +
                ", type="+backupType+")");

        appLog(R.string.app_log_backup_requested, getString(backupType.resId));

        if (backupType.isBackground() && !getConnectivityManager().getBackgroundDataSetting()) {
            appLog(R.string.app_log_skip_backup_background_data);

            App.bus.post(mState.transition(FINISHED_BACKUP, new RequiresBackgroundDataException()));
        } else if (!isWorking()) {
            // Only start a backup if there's no other operation going on at this time.
            if (!SmsRestoreService.isServiceWorking()) {
                try {
                    BackupConfig config = new BackupConfig(
                            getBackupImapStore(), 0,
                            intent.getBooleanExtra(Consts.KEY_SKIP_MESSAGES, false),
                            PrefStore.getMaxItemsPerSync(service),
                            PrefStore.getBackupContactGroup(service),
                            MAX_MSG_PER_REQUEST);

                    new BackupTask(this, backupType).execute(config);
                } catch (MessagingException e) {
                    App.bus.post(mState.transition(ERROR, e));
                }
            } else {
                // restore is already running
                App.bus.post(mState.transition(ERROR, null));
            }
        } else {
            appLog(R.string.app_log_skip_backup_already_running);
        }
    }

    @Produce public BackupState produceLastState() {
        return mState;
    }

    @Subscribe public void backupStateChanged(BackupState state) {
        mState = state;
        if (mState.isInitialState()) return;

        if (state.isError() &&
           (state.backupType == MANUAL || PrefStore.isNotificationEnabled(this))) {
            if (state.isAuthException()) {
                int details = PrefStore.useXOAuth(this) ? R.string.status_auth_failure_details_xoauth :
                        R.string.status_auth_failure_details_plain;
                notifyUser(android.R.drawable.stat_sys_warning, TAG,
                        getString(R.string.notification_auth_failure), getString(details));
            } else {
                notifyUser(android.R.drawable.stat_sys_warning, TAG,
                        getString(R.string.notification_unknown_error), state.getErrorMessage(getResources()));
            }
        }

        if (state.isRunning()) {
            if (state.backupType == MANUAL) {
                notifyAboutBackup(state);
            }
        } else {
            if (state.backupType == BackupType.REGULAR) {
                Log.d(TAG, "scheduling next backup");
                scheduleNextBackup();
            }
            stopForeground(true);
            stopSelf();
        }
    }

    private void notifyAboutBackup(BackupState state) {
        if (notification == null) {
            notification = createNotification(R.string.status_backup);
        }
        notification.setLatestEventInfo(this,
            getString(R.string.status_backup),
            state.getNotificationLabel(getResources()),
            getPendingIntent());

        startForeground(BACKUP_ID, notification);
    }

    private void scheduleNextBackup() {
        final long nextSync = Alarms.scheduleRegularBackup(service);
        if (nextSync >= 0) {
            appLog(R.string.app_log_scheduled_next_sync,
                    DateFormat.format("kk:mm", new Date(nextSync)));
        } else {
            appLog(R.string.app_log_no_next_sync);
        }
    }

    protected void notifyUser(int icon, String shortText, String title, String text) {
        Notification n = new Notification(icon, shortText, System.currentTimeMillis());
        n.flags = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
        n.setLatestEventInfo(this,
                title,
                text,
                getPendingIntent());

        getNotifier().notify(0, n);
    }

    public static boolean isServiceWorking() {
        return service != null && service.isWorking();
    }
}
