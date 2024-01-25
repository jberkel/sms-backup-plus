package com.zegoggles.smssync.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import android.util.Log;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.store.imap.XOAuth2AuthenticationFailedException;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.auth.TokenRefreshException;
import com.zegoggles.smssync.service.exception.RequiresLoginException;
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

import java.util.List;
import java.util.Locale;
import java.util.HashMap;


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

class BackupTask extends AsyncTask<BackupConfig, BackupState, BackupState> {
    @SuppressLint("StaticFieldLeak")
    private final SmsBackupService service;
    private final BackupItemsFetcher fetcher;
    private final MessageConverter converter;
    private final HashMap<Integer, CalendarSyncer> calendarSyncerList = new HashMap<Integer, CalendarSyncer>();
    private final AuthPreferences authPreferences;
    private final Preferences preferences;
    private final ContactAccessor contactAccessor;
    private final TokenRefresher tokenRefresher;

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

        for (Integer settingsId = 0; settingsId < App.SimCards.length; settingsId++) {
            if (preferences.isCallLogCalendarSyncEnabled(settingsId)) {
                calendarSyncerList.put(settingsId, new CalendarSyncer(
                    CalendarAccessor.Get.instance(service.getContentResolver()),
                    preferences.getCallLogCalendarId(settingsId),
                    personLookup,
                    new CallFormatter(context.getResources())
                ));
            }
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
        this.calendarSyncerList.put(0, syncer);
        this.authPreferences = authPreferences;
        this.preferences = preferences;
        this.contactAccessor = accessor;
        this.tokenRefresher = refresher;
    }

    @Override
    protected void onPreExecute() {
        App.register(this);
    }

    @Subscribe public void canceled(CancelEvent cancelEvent) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "canceled("+cancelEvent+")");
        }
        cancel(cancelEvent.mayInterruptIfRunning());
    }

    @Override protected BackupState doInBackground(BackupConfig... params) {
        if (params == null || params.length == 0) {
            throw new IllegalArgumentException("No config passed");
        }
        final BackupConfig config = params[0];
        if (config.backupType == SKIP) {
            return skip(config.typesToBackup);
        } else {
            return acquireLocksAndBackup(config);
        }
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
        HashMap<Integer, BackupCursors> cursorList = new HashMap<Integer, BackupCursors>();
        Exception lastException = null;
        try {
            final ContactGroupIds groupIds = contactAccessor.getGroupContactIds(service.getContentResolver(), config.groupToBackup);
            final Context context = service.getApplicationContext();

            int itemsToSync = 0;
            for (Integer settingsId = 0; settingsId < App.SimCards.length; settingsId++) {
                if (!(new AuthPreferences(context, settingsId).isLoginInformationSet())) {
                    lastException = new RequiresLoginException();
                    continue;
                }

                BackupCursors cursors = new BulkFetcher(fetcher).fetch(config.typesToBackup, groupIds, settingsId, config.maxItemsPerSync);
                itemsToSync += cursors.count();
                if (cursors.count() > 0) {
                    appLog(R.string.app_log_backup_messages, cursors.count(SMS), cursors.count(MMS), cursors.count(CALLLOG), App.SimCards[settingsId]);
                    if (config.debug) {
                        appLog(R.string.app_log_backup_messages_with_config, config);
                    }
                    cursorList.put(settingsId, cursors);
                }
            }

            int[] result = new int[2];
            result[0] = itemsToSync;
            result[1] = 0;
            for (int settingsId : cursorList.keySet()) {
                BackupCursors cursors = cursorList.get(settingsId);
                try {
                    if (cursors.count() > 0) {
                        result = backupCursors(settingsId, cursors, config.imapStores.get(settingsId), config.backupType, result[0], result[1]);
                    } else {
                        appLog(R.string.app_log_skip_backup_no_items);

                        if (preferences.isFirstBackup(settingsId)) {
                            // If this is the first backup we need to write something to MAX_SYNCED_DATE
                            // such that we know that we've performed a backup before.
                            preferences.getDataTypePreferences().setMaxSyncedDate(SMS, MAX_SYNCED_DATE, settingsId);
                            preferences.getDataTypePreferences().setMaxSyncedDate(MMS, MAX_SYNCED_DATE, settingsId);
                        }
                    }
                } catch (XOAuth2AuthenticationFailedException e) {
                    try {
                        return handleAuthError(config, e);
                    } catch (XOAuth2AuthenticationFailedException eInner) {
                        lastException = eInner;
                    }
                } catch (AuthenticationFailedException e) {
                    lastException = e;
                } catch (MessagingException e) {
                    lastException = e;
                } catch (SecurityException e) {
                    lastException = e;
                }
            }
            if (lastException != null) return transition(ERROR, lastException);

            if (itemsToSync > 0) {
                return new BackupState(FINISHED_BACKUP, result[1], result[0], config.backupType, null, null);
            } else {
                Log.i(TAG, "Nothing to do.");
                return transition(FINISHED_BACKUP, null);
            }
        } finally {
            if (cursorList != null) {
                for (BackupCursors cursors : cursorList.values()) {
                    cursors.close();
                }
            }
        }
    }

    private BackupState handleAuthError(BackupConfig config, XOAuth2AuthenticationFailedException e) throws XOAuth2AuthenticationFailedException {
        if (e.getStatus() == 400) {
            appLogDebug("need to perform xoauth2 token refresh");
            if (config.currentTry < 1) {
                try {
                    tokenRefresher.refreshOAuth2Token();
                    // we got a new token, let's handleAuthError one more time - we need to pass in a new store object
                    // since the auth params on it are immutable
                    appLogDebug("token refreshed, retrying");
                    return fetchAndBackupItems(config.retryWithStore(service.getBackupImapStores()));
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
        throw e;
    }

    private BackupState skip(Iterable<DataType> types) {
        appLog(R.string.app_log_skip_backup_skip_messages);
        for (DataType type : types) {
            try {
                for (Integer settingsId = 0; settingsId < App.SimCards.length; settingsId++) {
                    preferences.getDataTypePreferences().setMaxSyncedDate(type, fetcher.getMostRecentTimestamp(type), settingsId);
                }
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

    @Override
    protected void onProgressUpdate(BackupState... progress) {
        if (progress != null && progress.length > 0 && !isCancelled()) {
            post(progress[0]);
        }
    }

    @Override
    protected void onPostExecute(BackupState result) {
        if (result != null) {
            post(result);
        }
        App.unregister(this);
    }

    @Override
    protected void onCancelled() {
        post(transition(CANCELED_BACKUP, null));
        App.unregister(this);
    }

    private void post(BackupState state) {
        if (state == null) return;
        App.post(state);
    }

    private int[] backupCursors(Integer settingsId, BackupCursors cursors, BackupImapStore store, BackupType backupType, int itemsToSync, int backedUpItems)
            throws MessagingException {
        Log.i(TAG, String.format(Locale.ENGLISH, "Starting backup (%d messages)", itemsToSync));
        publish(LOGIN);
        store.checkSettings();

        try {
            publish(CALC);
            while (!isCancelled() && cursors.hasNext()) {
                BackupCursors.CursorAndType cursor = cursors.next();
                if (LOCAL_LOGV) Log.v(TAG, "backing up: " + cursor);

                ConversionResult result = converter.convertMessages(cursor.cursor, cursor.type, settingsId);
                if (!result.isEmpty()) {
                    List<Message> messages = result.getMessages();

                    if (LOCAL_LOGV) {
                        Log.v(TAG, String.format(Locale.ENGLISH, "sending %d %s message(s) to server.",
                                messages.size(), cursor.type));
                    }

                    store.getFolder(cursor.type, preferences.getDataTypePreferences(), settingsId).appendMessages(messages);

                    if (cursor.type == CALLLOG && calendarSyncerList.containsKey(settingsId)) {
                        calendarSyncerList.get(settingsId).syncCalendar(result);
                    }

                    preferences.getDataTypePreferences().setMaxSyncedDate(cursor.type, result.getMaxDate(), settingsId);
                    backedUpItems += messages.size();
                } else {
                    Log.w(TAG, "no messages converted");
                    itemsToSync -= 1;
                }

                publishProgress(new BackupState(BACKUP, backedUpItems, itemsToSync, backupType, cursor.type, null));
            }

            int[] retVal = new int[2];

            retVal[0] = itemsToSync;
            retVal[1] = backedUpItems;
            return retVal;
        } finally {
            store.closeFolders();
        }
    }

    private void publish(SmsSyncState state) {
        publishProgress(service.transition(state, null));
    }
}
