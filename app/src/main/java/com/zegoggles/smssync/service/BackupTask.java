package com.zegoggles.smssync.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import android.util.Log;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.store.imap.XOAuth2AuthenticationFailedException;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.auth.TokenRefreshException;
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
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.Defaults.MAX_SYNCED_DATE;
import static com.zegoggles.smssync.mail.DataType.MMS;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static com.zegoggles.smssync.service.BackupType.MANUAL;
import static com.zegoggles.smssync.service.BackupType.SKIP;
import static com.zegoggles.smssync.service.state.SmsSyncState.BACKUP;
import static com.zegoggles.smssync.service.state.SmsSyncState.CALC;
import static com.zegoggles.smssync.service.state.SmsSyncState.CANCELED_BACKUP;
import static com.zegoggles.smssync.service.state.SmsSyncState.ERROR;
import static com.zegoggles.smssync.service.state.SmsSyncState.FINISHED_BACKUP;
import static com.zegoggles.smssync.service.state.SmsSyncState.LOGIN;

class BackupTask {
    private final SmsBackupService service;
    private final BackupItemsFetcher fetcher;
    private final MessageConverter converter;
    private final CalendarSyncer calendarSyncer;
    private final AuthPreferences authPreferences;
    private final Preferences preferences;
    private final ContactAccessor contactAccessor;
    private final TokenRefresher tokenRefresher;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    BackupTask(@NonNull SmsBackupService service) {
        final Context context = service.getApplicationContext();
        this.service = service;
        this.authPreferences = service.getAuthPreferences();
        this.preferences = service.getPreferences();

        this.fetcher = new BackupItemsFetcher(
                context.getContentResolver(),
                new BackupQueryBuilder(preferences.getDataTypePreferences()));

        PersonLookup personLookup = new PersonLookup(service.getContentResolver());

        this.contactAccessor = new ContactAccessor();
        this.converter = new MessageConverter(context, service.getPreferences(), authPreferences.getUserEmail(), personLookup, contactAccessor);

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
        this.tokenRefresher = new TokenRefresher(service, new OAuth2Client(authPreferences.getOAuth2ClientId()), authPreferences);
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

    void execute(final BackupConfig config) {
        App.register(this);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final BackupState result;
                if (config.backupType == SKIP) {
                    result = skip(config.typesToBackup);
                } else {
                    result = acquireLocksAndBackup(config);
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) {
                            post(result);
                        }
                        App.unregister(BackupTask.this);
                    }
                });
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void canceled(CancelEvent cancelEvent) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "canceled("+cancelEvent+")");
        }
        cancelled.set(true);
    }

    private boolean isCancelled() {
        return cancelled.get();
    }

    private BackupState acquireLocksAndBackup(BackupConfig config) {
        try {
            service.acquireLocks();
            return fetchAndBackupItems(config);
        } finally {
            service.releaseLocks();
        }
    }

    private BackupState fetchAndBackupItems(BackupConfig config) {
        BackupCursors cursors = null;
        try {
            final ContactGroupIds groupIds = contactAccessor.getGroupContactIds(service.getContentResolver(), config.groupToBackup);

            cursors = new BulkFetcher(fetcher).fetch(config.typesToBackup, groupIds, config.maxItemsPerSync);
            final int itemsToSync = cursors.count();

            if (itemsToSync > 0) {
                appLog(R.string.app_log_backup_messages, cursors.count(SMS), cursors.count(MMS), cursors.count(CALLLOG));
                if (config.debug) {
                    appLog(R.string.app_log_backup_messages_with_config, config);
                }

                return backupCursors(cursors, config.imapStore, config.backupType, itemsToSync);
            } else {
                appLog(R.string.app_log_skip_backup_no_items);

                if (preferences.isFirstBackup()) {
                    // If this is the first backup we need to write something to MAX_SYNCED_DATE
                    // such that we know that we've performed a backup before.
                    preferences.getDataTypePreferences().setMaxSyncedDate(SMS, MAX_SYNCED_DATE);
                    preferences.getDataTypePreferences().setMaxSyncedDate(MMS, MAX_SYNCED_DATE);
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
        } catch (SecurityException e) {
            return transition(ERROR, e);
        } finally {
            if (cursors != null) {
                cursors.close();
            }
        }
    }

    private BackupState handleAuthError(BackupConfig config, XOAuth2AuthenticationFailedException e) {
        if (e.getStatus() == 400) {
            appLogDebug("need to perform xoauth2 token refresh");
            if (config.currentTry < 1) {
                try {
                    tokenRefresher.refreshOAuth2Token();
                    // we got a new token, let's retry one more time - we need to pass in a new store object
                    // since the auth params on it are immutable
                    appLogDebug("token refreshed, retrying");
                    return fetchAndBackupItems(config.retryWithStore(service.getBackupImapStore()));
                } catch (MessagingException ignored) {
                    Log.w(TAG, ignored);
                } catch (TokenRefreshException refreshException) {
                    appLogDebug("error refreshing token: "+refreshException+", cause="+refreshException.getCause());
                }
            } else {
                appLogDebug("no new token obtained, giving up");
            }
        } else {
            appLogDebug("unexpected xoauth status code " + e.getStatus());
        }
        return transition(ERROR, e);
    }

    private BackupState skip(Iterable<DataType> types) {
        appLog(R.string.app_log_skip_backup_skip_messages);
        for (DataType type : types) {
            try {
                preferences.getDataTypePreferences().setMaxSyncedDate(type, fetcher.getMostRecentTimestamp(type));
            } catch (SecurityException e ) {
                return new BackupState(ERROR, 0, 0, MANUAL, type, e);
            }
        }
        Log.i(TAG, "All messages skipped.");
        return new BackupState(FINISHED_BACKUP, 0, 0, MANUAL, null, null);
    }

    private void appLog(int id, Object... args) {
        service.appLog(id, args);
    }

    private void appLogDebug(String message, Object... args) {
        service.appLogDebug(message, args);
    }

    private BackupState transition(SmsSyncState smsSyncState, Exception exception) {
        return service.transition(smsSyncState, exception);
    }

    private void publishProgress(final BackupState progress) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isCancelled()) {
                    post(progress);
                }
            }
        });
    }

    private void post(BackupState state) {
        if (state == null) return;
        App.postSticky(state);
    }

    private BackupState backupCursors(BackupCursors cursors, BackupImapStore store, BackupType backupType, int itemsToSync)
            throws MessagingException {
        Log.i(TAG, String.format(Locale.ENGLISH, "Starting backup (%d messages)", itemsToSync));
        publishProgress(transition(LOGIN, null));
        store.checkSettings();

        try {
            publishProgress(transition(CALC, null));
            int backedUpItems = 0;
            while (!isCancelled() && cursors.hasNext()) {
                BackupCursors.CursorAndType cursor = cursors.next();
                if (LOCAL_LOGV) Log.v(TAG, "backing up: " + cursor);

                ConversionResult result = converter.convertMessages(cursor.cursor, cursor.type);
                if (!result.isEmpty()) {
                    List<Message> messages = result.getMessages();

                    if (LOCAL_LOGV) {
                        Log.v(TAG, String.format(Locale.ENGLISH, "sending %d %s message(s) to server.",
                                messages.size(), cursor.type));
                    }

                    store.getFolder(cursor.type, preferences.getDataTypePreferences()).appendMessages(messages);

                    if (cursor.type == CALLLOG && calendarSyncer != null) {
                        calendarSyncer.syncCalendar(result);
                    }
                    preferences.getDataTypePreferences().setMaxSyncedDate(cursor.type, result.getMaxDate());
                    backedUpItems += messages.size();
                } else {
                    Log.w(TAG, "no messages converted");
                    itemsToSync -= 1;
                }

                publishProgress(new BackupState(BACKUP, backedUpItems, itemsToSync, backupType, cursor.type, null));
            }

            if (isCancelled()) {
                return transition(CANCELED_BACKUP, null);
            }

            return new BackupState(FINISHED_BACKUP,
                    backedUpItems,
                    itemsToSync,
                    backupType, null, null);
        } finally {
            store.closeFolders();
        }
    }
}
