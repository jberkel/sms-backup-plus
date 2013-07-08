package com.zegoggles.smssync.service;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.XOAuth2AuthenticationFailedException;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.BuildConfig;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.auth.TokenRefresher;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.CallFormatter;
import com.zegoggles.smssync.mail.ConversionResult;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.mail.MessageConverter;
import com.zegoggles.smssync.mail.PersonLookup;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.exception.ConnectivityException;
import com.zegoggles.smssync.service.exception.RequiresLoginException;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.DataType.*;
import static com.zegoggles.smssync.service.state.SmsSyncState.*;

/**
 * BackupTask does all the work
 */
class BackupTask extends AsyncTask<BackupConfig, BackupState, BackupState> {
    private final SmsBackupService service;
    private final BackupItemsFetcher fetcher;
    private final MessageConverter converter;
    private final CalendarSyncer calendarSyncer;
    private final AuthPreferences authPreferences;
    private final Preferences preferences;
    private final ContactAccessor contactAccessor;
    private final TokenRefresher tokenRefresher;

    private Map<DataType, BackupImapStore.BackupFolder> backupFolders = new HashMap<DataType, BackupImapStore.BackupFolder>();


    BackupTask(@NotNull SmsBackupService service) {
        final Context context = service.getApplicationContext();
        this.service = service;
        this.authPreferences = service.getAuthPreferences();
        this.preferences = service.getPreferences();

        this.fetcher = new BackupItemsFetcher(context,
                context.getContentResolver(),
                new BackupQueryBuilder(context));

        PersonLookup personLookup = new PersonLookup(service.getContentResolver());

        this.converter = new MessageConverter(context, preferences, authPreferences.getUserEmail(), personLookup);
        this.contactAccessor = ContactAccessor.Get.instance();

        if (preferences.isCallLogCalendarSyncEnabled()) {
            calendarSyncer = new CalendarSyncer(
                CalendarAccessor.Get.instance(service.getContentResolver()),
                preferences.getCallLogCalendarId(),
                personLookup,
                new CallFormatter(context.getResources())
            );

        } else {
            calendarSyncer = null;
        }
        this.tokenRefresher = new TokenRefresher(service, authPreferences);
    }

    BackupTask(SmsBackupService service,
               BackupItemsFetcher fetcher,
               MessageConverter messageConverter,
               CalendarSyncer syncer,
               AuthPreferences authPreferences,
               Preferences preferences,
               ContactAccessor accessor,
               TokenRefresher refresher) {
        this.service = service;
        this.fetcher = fetcher;
        this.converter = messageConverter;
        this.calendarSyncer = syncer;
        this.authPreferences = authPreferences;
        this.preferences = preferences;
        this.contactAccessor = accessor;
        this.tokenRefresher = refresher;
    }

    @Override
    protected void onPreExecute() {
        App.bus.register(this);
    }

    @Subscribe public void userCanceled(UserCanceled canceled) {
        cancel(false);
    }

    @Override protected BackupState doInBackground(BackupConfig... params) {
        if (params == null || params.length == 0) throw new IllegalArgumentException("No config passed");
        final BackupConfig config = params[0];
        if (config.skip) {
            return skip(config.typesToBackup);
        } else if (!authPreferences.isLoginInformationSet()) {
            appLog(R.string.app_log_missing_credentials);
            return transition(ERROR, new RequiresLoginException());
        }

        BackupCursors cursors = null;
        try {
            final ContactGroupIds groupIds = contactAccessor.getGroupContactIds(service.getContentResolver(), config.groupToBackup);

            service.acquireLocks();
            cursors = new BulkFetcher(fetcher).fetch(config.typesToBackup, groupIds, config.maxItemsPerSync);
            final int itemsToSync = cursors.count();

            if (itemsToSync > 0) {
                appLog(R.string.app_log_backup_messages, cursors.count(SMS), cursors.count(MMS), cursors.count(CALLLOG));
                if (config.debug) {
                    appLog(R.string.app_log_backup_messages_with_config, config);
                }

                return backup(config, cursors, itemsToSync);
            } else {
                appLog(R.string.app_log_skip_backup_no_items);

                if (preferences.isFirstBackup()) {
                    // If this is the first backup we need to write something to MAX_SYNCED_DATE
                    // such that we know that we've performed a backup before.
                    SMS.setMaxSyncedDate(service, Defaults.MAX_SYNCED_DATE);
                    MMS.setMaxSyncedDate(service, Defaults.MAX_SYNCED_DATE);
                }
                Log.i(TAG, "Nothing to do.");
                return transition(FINISHED_BACKUP, null);
            }
        } catch (XOAuth2AuthenticationFailedException e) {
            return handleAuthError(config, e);
        } catch (AuthenticationFailedException e) {
            return transition(ERROR, e);
        } catch (MessagingException e) {
            return transition(ERROR, e);
        } catch (ConnectivityException e) {
            return transition(ERROR, e);
        } finally {
            if (cursors != null) {
                cursors.close();
            }
            service.releaseLocks();
        }
    }

    private BackupState handleAuthError(BackupConfig config, XOAuth2AuthenticationFailedException e) {
        if (e.getStatus() == 400) {
            Log.d(TAG, "need to perform xoauth2 token refresh");
            if (config.currentTry < 1 && tokenRefresher.refreshOAuth2Token()) {
                try {
                    // we got a new token, let's handleAuthError one more time - we need to pass in a new store object
                    // since the auth params on it are immutable
                    return doInBackground(config.retryWithStore(service.getBackupImapStore()));
                } catch (MessagingException ignored) {
                    Log.w(TAG, ignored);
                }
            } else {
                Log.w(TAG, "no new token obtained, giving up");
            }
        } else {
            Log.w(TAG, "unexpected xoauth status code " + e.getStatus());
        }
        return transition(ERROR, e);
    }

    private BackupState skip(Iterable<DataType> types) {
        appLog(R.string.app_log_skip_backup_skip_messages);
        for (DataType type : types) {
            type.setMaxSyncedDate(service, fetcher.getMostRecentTimestamp(type));
        }
        Log.i(TAG, "All messages skipped.");
        return new BackupState(FINISHED_BACKUP, 0, 0, BackupType.MANUAL, null, null);
    }

    private void appLog(int id, Object... args) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, service.getApplicationContext().getString(id, args));
        }

        service.appLog(id, args);
    }

    private BackupState transition(SmsSyncState smsSyncState, Exception exception) {
        return service.transition(smsSyncState, exception);
    }

    @Override
    protected void onProgressUpdate(BackupState... progress) {
        if (progress != null  && progress.length > 0 && !isCancelled()) {
            post(progress[0]);
        }
    }

    @Override
    protected void onPostExecute(BackupState result) {
        if (result != null) {
            post(result);
        }
        App.bus.unregister(this);
    }

    @Override
    protected void onCancelled() {
        post(transition(CANCELED_BACKUP, null));
        App.bus.unregister(this);
    }

    private void post(BackupState state) {
        if (state == null) return;
        App.bus.post(state);
    }

    private BackupState backup(BackupConfig config, BackupCursors cursors, final int itemsToSync)
            throws MessagingException {
        Log.i(TAG, String.format(Locale.ENGLISH, "Starting backup (%d messages)", itemsToSync));
        publish(LOGIN);

        try {
            publish(CALC);
            int backedUpItems = 0;
            while (!isCancelled() && cursors.hasNext()) {
                BackupCursors.CursorAndType cursor = cursors.next();
                if (LOCAL_LOGV) Log.v(TAG, "backing up: " + cursor);

                ConversionResult result = converter.convertMessages(cursor.cursor, config.maxMessagePerRequest, cursor.type);
                if (!result.isEmpty()) {
                    List<Message> messages = result.getMessages();

                    if (LOCAL_LOGV) {
                        Log.v(TAG, String.format(Locale.ENGLISH, "sending %d %s message(s) to server.",
                                messages.size(), cursor.type));
                    }

                    getFolder(cursor.type, config).appendMessages(messages.toArray(new Message[messages.size()]));

                    if (cursor.type == CALLLOG && calendarSyncer != null) {
                        calendarSyncer.syncCalendar(result);
                    }
                    cursor.type.setMaxSyncedDate(service, result.getMaxDate());
                    backedUpItems += messages.size();
                } else {
                    Log.w(TAG, "no messages converted");
                }

                publishProgress(new BackupState(BACKUP, backedUpItems, itemsToSync, config.backupType, cursor.type, null));
            }

            return new BackupState(FINISHED_BACKUP,
                    backedUpItems,
                    itemsToSync,
                    config.backupType, null, null);
        } finally {
            closeFolders();
        }
    }

    private BackupImapStore.BackupFolder getFolder(DataType type, BackupConfig config) throws MessagingException {
        BackupImapStore.BackupFolder folder = backupFolders.get(type);
        if (folder == null) {
            folder = config.getFolder(type);
            backupFolders.put(type, folder);
        }
        return folder;
    }

    private void closeFolders() {
        for (BackupImapStore.BackupFolder folder : backupFolders.values()) {
            try {
                folder.close();
            } catch (Exception e) {
                Log.w(TAG, e);
            }
        }
    }

    private void publish(SmsSyncState state) {
        publish(state, null);
    }

    private void publish(SmsSyncState state, Exception exception) {
        publishProgress(service.transition(state, exception));
    }
}
