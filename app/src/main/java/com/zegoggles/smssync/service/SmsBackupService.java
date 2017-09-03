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
import android.net.NetworkInfo;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateFormat;
import android.util.Log;
import com.fsck.k9.mail.MessagingException;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.service.exception.BackupDisabledException;
import com.zegoggles.smssync.service.exception.ConnectivityException;
import com.zegoggles.smssync.service.exception.NoConnectionException;
import com.zegoggles.smssync.service.exception.RequiresBackgroundDataException;
import com.zegoggles.smssync.service.exception.RequiresLoginException;
import com.zegoggles.smssync.service.exception.RequiresWifiException;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import com.zegoggles.smssync.tasks.MigrateOAuth1TokenTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.EnumSet;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.service.BackupType.MANUAL;
import static com.zegoggles.smssync.service.state.SmsSyncState.ERROR;
import static com.zegoggles.smssync.service.state.SmsSyncState.FINISHED_BACKUP;
import static com.zegoggles.smssync.service.state.SmsSyncState.INITIAL;

public class SmsBackupService extends ServiceBase {
    private static final int BACKUP_ID = 1;
    private static final int NOTIFICATION_ID_WARNING = 1;

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
        // Only start a backup if there's no other operation going on at this time.
        if (!isWorking() && !SmsRestoreService.isServiceWorking()) {
            if (getAuthPreferences().needsMigration()) {
                runMigration();
            } else {
                backup(backupType, intent.getBooleanExtra(Consts.KEY_SKIP_MESSAGES, false));
            }
        } else {
            appLog(R.string.app_log_skip_backup_already_running);
        }
    }

    private void backup(BackupType backupType, boolean skip) {
        getNotifier().cancel(NOTIFICATION_ID_WARNING);

        try {
            // set initial state
            mState = new BackupState(INITIAL, 0, 0, backupType, null, null);
            EnumSet<DataType> enabledTypes = getEnabledBackupTypes();
            if (!skip) {
                checkCredentials();
                checkBackgroundDataSettings(backupType);
                checkConnectivity();
            }

            appLog(R.string.app_log_start_backup, backupType);
            getBackupTask().execute(getBackupConfig(backupType, enabledTypes, getBackupImapStore(), skip));
        } catch (MessagingException e) {
            Log.w(TAG, e);
            moveToState(mState.transition(ERROR, e));
        } catch (RequiresBackgroundDataException e) {
            moveToState(mState.transition(ERROR, e));
        } catch (ConnectivityException e) {
            moveToState(mState.transition(ERROR, e));
        } catch (RequiresLoginException e) {
            appLog(R.string.app_log_missing_credentials);
            moveToState(mState.transition(ERROR, e));
        } catch (BackupDisabledException e) {
            moveToState(mState.transition(FINISHED_BACKUP, e));
        }
    }

    private BackupConfig getBackupConfig(BackupType backupType,
                                         EnumSet<DataType> enabledTypes,
                                         BackupImapStore imapStore,
                                         boolean skip) {
        return new BackupConfig(
            imapStore,
            0,
            skip,
            getPreferences().getMaxItemsPerSync(),
            getPreferences().getBackupContactGroup(),
            backupType,
            enabledTypes,
            getPreferences().isAppLogDebug()
        );
    }

    private EnumSet<DataType> getEnabledBackupTypes() throws BackupDisabledException {
        EnumSet<DataType> dataTypes = DataType.enabled(this);
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

    private void checkBackgroundDataSettings(BackupType backupType) throws RequiresBackgroundDataException {
        if (backupType.isBackground() && !getConnectivityManager().getBackgroundDataSetting()) {
            throw new RequiresBackgroundDataException();
        }
    }

    private void checkConnectivity() throws ConnectivityException {
        NetworkInfo active = getConnectivityManager().getActiveNetworkInfo();
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
        App.bus.post(state);
    }

    @Override
    protected boolean isBackgroundTask() {
        return mState.backupType.isBackground();
    }

    @Produce public BackupState produceLastState() {
        return mState;
    }

    @Subscribe public void backupStateChanged(BackupState state) {
        if (mState == state) return;

        mState = state;
        if (mState.isInitialState()) return;

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

            if (state.backupType == BackupType.REGULAR) {
                Log.d(TAG, "scheduling next backup");
                scheduleNextBackup();
            }
            stopForeground(true);
            stopSelf();
        }
    }

    private void handleErrorState(BackupState state) {
        if (state.isAuthException()) {
            appLog(R.string.app_log_backup_failed_authentication, state.getDetailedErrorMessage(getResources()));

            if (shouldNotifyUser(state)) {
                notifyUser(android.R.drawable.stat_sys_warning,
                    NOTIFICATION_ID_WARNING,
                    getString(R.string.notification_auth_failure),
                    getString(getAuthPreferences().useXOAuth() ? R.string.status_auth_failure_details_xoauth : R.string.status_auth_failure_details_plain));
            }
        } else if (state.isConnectivityError()) {
            appLog(R.string.app_log_backup_failed_connectivity, state.getDetailedErrorMessage(getResources()));
        } else {
            appLog(R.string.app_log_backup_failed_general_error, state.getDetailedErrorMessage(getResources()));

            if (shouldNotifyUser(state)) {
                notifyUser(android.R.drawable.stat_sys_warning,
                    NOTIFICATION_ID_WARNING,
                    getString(R.string.notification_general_error),
                    state.getErrorMessage(getResources()));
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
                .setContentIntent(getPendingIntent())
                .getNotification();
        startForeground(BACKUP_ID, notification);
    }

    private void scheduleNextBackup() {
        final long nextSync = getAlarms().scheduleRegularBackup();
        if (nextSync >= 0) {
            appLog(R.string.app_log_scheduled_next_sync,
                    DateFormat.format("kk:mm", new Date(nextSync)));
        } else {
            appLog(R.string.app_log_no_next_sync);
        }
    }

    protected void notifyUser(int icon, int notificationId, String title, String text) {
        Notification n = new NotificationCompat.Builder(this)
                .setSmallIcon(icon)
                .setWhen(System.currentTimeMillis())
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentText(text)
                .setTicker(getString(R.string.app_name))
                .setContentTitle(title)
                .setContentIntent(getPendingIntent())
                .getNotification();
        getNotifier().notify(notificationId, n);
    }

    protected Alarms getAlarms() {
        return new Alarms(this);
    }

    public static boolean isServiceWorking() {
        return service != null && service.isWorking();
    }

    public BackupState transition(SmsSyncState newState, Exception e) {
        return mState.transition(newState, e);
    }

    @Deprecated
    private void runMigration() {
        appLogDebug("running OAuth1 migration");
        final MigrateOAuth1TokenTask migrationTask = new MigrateOAuth1TokenTask(
                getAuthPreferences().getOAuthConsumer(),
                new OAuth2Client(getAuthPreferences().getOAuth2ClientId()),
                getAuthPreferences());

        migrationTask.execute();
    }
}
