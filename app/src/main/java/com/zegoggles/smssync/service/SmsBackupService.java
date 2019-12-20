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

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.text.format.DateFormat;
import android.util.Log;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobTrigger;
import com.fsck.k9.mail.MessagingException;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.MainActivity;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.service.exception.BackupDisabledException;
import com.zegoggles.smssync.service.exception.ConnectivityException;
import com.zegoggles.smssync.service.exception.MissingPermissionException;
import com.zegoggles.smssync.service.exception.NoConnectionException;
import com.zegoggles.smssync.service.exception.RequiresLoginException;
import com.zegoggles.smssync.service.exception.RequiresWifiException;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.SmsSyncState;

import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static android.R.drawable.stat_sys_warning;
import static com.zegoggles.smssync.App.CHANNEL_ID;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.activity.AppPermission.formatMissingPermissionDetails;
import static com.zegoggles.smssync.service.BackupType.MANUAL;
import static com.zegoggles.smssync.service.BackupType.REGULAR;
import static com.zegoggles.smssync.service.BackupType.SKIP;
import static com.zegoggles.smssync.service.state.SmsSyncState.ERROR;
import static com.zegoggles.smssync.service.state.SmsSyncState.FINISHED_BACKUP;
import static com.zegoggles.smssync.service.state.SmsSyncState.INITIAL;

public class SmsBackupService extends ServiceBase {
    private static final int BACKUP_ID = 1;
    private static final int NOTIFICATION_ID_WARNING = 1;

    @Nullable private static SmsBackupService service;
    @NonNull private BackupState state = new BackupState();

    @Override @NonNull
    public BackupState getState() {
        return state;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (LOCAL_LOGV) Log.v(TAG, "SmsBackupService#onCreate");
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
        if (LOCAL_LOGV) {
            Log.v(TAG, "handleIntent(" + intent +
                    ", " + (intent.getExtras() == null ? "null" : intent.getExtras().keySet()) +
                    ", " + intent.getAction() +
                    ", type="+backupType+")");
        }

        appLog(R.string.app_log_backup_requested, getString(backupType.resId));
        // Only start a backup if there's no other operation going on at this time.
        if (!isWorking() && SmsRestoreService.isServiceIdle()) {
            backup(backupType);
        } else {
            appLog(R.string.app_log_skip_backup_already_running);
        }
    }

    private void backup(BackupType backupType) {
        getNotifier().cancel(NOTIFICATION_ID_WARNING);

        try {
            // set initial state
            state = new BackupState(INITIAL, 0, 0, backupType, null, null);
            EnumSet<DataType> enabledTypes = getEnabledBackupTypes();
            checkPermissions(enabledTypes);
            if (backupType != SKIP) {
                checkCredentials();
                if (getPreferences().isUseOldScheduler()) {
                    legacyCheckConnectivity();
                }
            }
            appLog(R.string.app_log_start_backup, backupType);
            getBackupTask().execute(getBackupConfig(backupType, enabledTypes, getBackupImapStore()));
        } catch (MessagingException e) {
            Log.w(TAG, e);
            moveToState(state.transition(ERROR, e));
        } catch (ConnectivityException e) {
            moveToState(state.transition(ERROR, e));
        } catch (RequiresLoginException e) {
            appLog(R.string.app_log_missing_credentials);
            moveToState(state.transition(ERROR, e));
        } catch (BackupDisabledException e) {
            moveToState(state.transition(FINISHED_BACKUP, e));
        } catch (MissingPermissionException e) {
            moveToState(state.transition(ERROR, e));
        }
    }

    private void checkPermissions(EnumSet<DataType> enabledTypes) throws MissingPermissionException {
        Set<String> missing = new HashSet<String>();
        for (DataType dataType : enabledTypes) {
            missing.addAll(dataType.checkPermissions(this));
        }
        if (!missing.isEmpty()) {
            throw new MissingPermissionException(missing);
        }
    }

    private BackupConfig getBackupConfig(BackupType backupType,
                                         EnumSet<DataType> enabledTypes,
                                         BackupImapStore imapStore) {
        return new BackupConfig(
            imapStore,
            0,
            getPreferences().getMaxItemsPerSync(),
            getPreferences().getBackupContactGroup(),
            backupType,
            enabledTypes,
            getPreferences().isAppLogDebug()
        );
    }

    private EnumSet<DataType> getEnabledBackupTypes() throws BackupDisabledException {
        EnumSet<DataType> dataTypes = getPreferences().getDataTypePreferences().enabled();
        if (dataTypes.isEmpty()) {
            throw new BackupDisabledException();
        }
        return dataTypes;
    }

    private void checkCredentials() throws RequiresLoginException {
        if (!getAuthPreferences().isLoginInformationSet()) {
            throw new RequiresLoginException();
        }
    }

    @SuppressWarnings("deprecation")
    private void legacyCheckConnectivity() throws ConnectivityException {
        android.net.NetworkInfo active = getConnectivityManager().getActiveNetworkInfo();
        if (active == null || !active.isConnectedOrConnecting()) {
            throw new NoConnectionException();
        }
        if (getPreferences().isWifiOnly() && isBackgroundTask() && !isConnectedViaWifi()) {
            throw new RequiresWifiException();
        }
    }

    protected BackupTask getBackupTask() {
        return new BackupTask(this);
    }

    private void moveToState(BackupState state) {
        backupStateChanged(state);
        App.post(state);
    }

    @Override
    protected boolean isBackgroundTask() {
        return state.backupType.isBackground();
    }

    @Produce public BackupState produceLastState() {
        return state;
    }

    @Subscribe public void backupStateChanged(BackupState state) {
        if (this.state == state) return;

        this.state = state;
        if (this.state.isInitialState()) return;

        if (state.isError()) {
            handleErrorState(state);
        }

        if (state.isRunning()) {
            if (state.backupType == MANUAL) {
                notifyAboutBackup(state);
            }
        } else {
            appLogDebug(state.toString());
            appLog(state.isCanceled() ? R.string.app_log_backup_canceled : R.string.app_log_backup_finished);
            scheduleNextBackup(state);
            stopForeground(true);
            stopSelf();
        }
    }

    private void handleErrorState(BackupState state) {
        if (state.isAuthException()) {
            appLog(R.string.app_log_backup_failed_authentication, state.getDetailedErrorMessage(getResources()));

            if (shouldNotifyUser(state)) {
                notifyUser(NOTIFICATION_ID_WARNING, notificationBuilder(stat_sys_warning,
                    getString(R.string.notification_auth_failure),
                    getString(getAuthPreferences().useXOAuth() ? R.string.status_auth_failure_details_xoauth : R.string.status_auth_failure_details_plain)));
            }
        } else if (state.isConnectivityError()) {
            appLog(R.string.app_log_backup_failed_connectivity, state.getDetailedErrorMessage(getResources()));
        } else if (state.isPermissionException()) {
            if (state.backupType != MANUAL) {
                Bundle extras = new Bundle();
                extras.putStringArray(MainActivity.EXTRA_PERMISSIONS, state.getMissingPermissions());

                notifyUser(NOTIFICATION_ID_WARNING, notificationBuilder(R.drawable.ic_notification,
                    getString(R.string.notification_missing_permission),
                    formatMissingPermissionDetails(getResources(), state.getMissingPermissions()))
                    .setContentIntent(getPendingIntent(extras)));
            }
        } else {
            appLog(R.string.app_log_backup_failed_general_error, state.getDetailedErrorMessage(getResources()));

            if (shouldNotifyUser(state)) {
                notifyUser(NOTIFICATION_ID_WARNING, notificationBuilder(stat_sys_warning,
                    getString(R.string.notification_general_error),
                    state.getErrorMessage(getResources())));
            }
        }
    }

    private boolean shouldNotifyUser(BackupState state) {
        return state.backupType == MANUAL ||
               (getPreferences().isNotificationEnabled() && !state.isConnectivityError());
    }

    private void notifyAboutBackup(BackupState state) {
        NotificationCompat.Builder builder = createNotification(R.string.status_backup);
        notification = builder.setContentTitle(getString(R.string.status_backup))
                .setContentText(state.getNotificationLabel(getResources()))
                .setContentIntent(getPendingIntent(null))
                .build();
        startForeground(BACKUP_ID, notification);
    }

    private void scheduleNextBackup(BackupState state) {
        if (state.backupType == REGULAR && getPreferences().isUseOldScheduler()) {
            final Job nextSync = getBackupJobs().scheduleRegular();
            if (nextSync != null) {
                JobTrigger.ExecutionWindowTrigger trigger = (JobTrigger.ExecutionWindowTrigger) nextSync.getTrigger();
                Date date = new Date(System.currentTimeMillis() + (trigger.getWindowStart() * 1000));
                appLog(R.string.app_log_scheduled_next_sync,
                        DateFormat.format("kk:mm", date));
            } else {
                appLog(R.string.app_log_no_next_sync);
            }
        } // else job already persisted
    }

    void notifyUser(int notificationId, NotificationCompat.Builder builder) {
        getNotifier().notify(notificationId, builder.build());
    }

    @SuppressWarnings("deprecation")
    private NotificationCompat.Builder notificationBuilder(int icon, String title, String text) {
        return new NotificationCompat.Builder(this)
            .setSmallIcon(icon)
            .setChannelId(CHANNEL_ID)
            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentText(text)
            .setTicker(getString(R.string.app_name))
            .setContentTitle(title)
            .setContentIntent(getPendingIntent(null));
    }

    protected BackupJobs getBackupJobs() {
        return new BackupJobs(this);
    }

    public static boolean isServiceWorking() {
        return service != null && service.isWorking();
    }

    public BackupState transition(SmsSyncState newState, Exception e) {
        return state.transition(newState, e);
    }
}
