package com.zegoggles.smssync.service;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CallLog;
import android.provider.Telephony;
import android.util.Log;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.store.XOAuth2AuthenticationFailedException;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.auth.TokenRefreshException;
import com.zegoggles.smssync.auth.TokenRefresher;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.mail.MessageConverter;
import com.zegoggles.smssync.service.state.RestoreState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static com.zegoggles.smssync.service.state.SmsSyncState.CALC;
import static com.zegoggles.smssync.service.state.SmsSyncState.CANCELED_RESTORE;
import static com.zegoggles.smssync.service.state.SmsSyncState.ERROR;
import static com.zegoggles.smssync.service.state.SmsSyncState.FINISHED_RESTORE;
import static com.zegoggles.smssync.service.state.SmsSyncState.LOGIN;
import static com.zegoggles.smssync.service.state.SmsSyncState.RESTORE;
import static com.zegoggles.smssync.service.state.SmsSyncState.UPDATING_THREADS;

class RestoreTask extends AsyncTask<RestoreConfig, RestoreState, RestoreState> {
    private Set<String> smsIds = new HashSet<String>();
    private Set<String> callLogIds = new HashSet<String>();
    private Set<String> uids = new HashSet<String>();

    private final SmsRestoreService service;
    private final ContentResolver resolver;
    private final MessageConverter converter;
    private final TokenRefresher tokenRefresher;

    public RestoreTask(SmsRestoreService service,
                       MessageConverter converter,
                       ContentResolver resolver,
                       TokenRefresher tokenRefresher) {
        this.service = service;
        this.converter = converter;
        this.resolver = resolver;
        this.tokenRefresher = tokenRefresher;
    }

    @Override
    protected void onPreExecute() {
        App.bus.register(this);
    }

    @Subscribe public void userCanceled(UserCanceled canceled) {
        cancel(false);
    }

    @NotNull protected RestoreState doInBackground(RestoreConfig... params) {
        if (params == null || params.length == 0) throw new IllegalArgumentException("No config passed");
        RestoreConfig config = params[0];

        if (!config.restoreSms && !config.restoreCallLog) {
            return new RestoreState(FINISHED_RESTORE, 0, 0, 0, 0, null, null);
        } else {
            try {
                service.acquireLocks();
                return restore(config);
            } finally {
                service.releaseLocks();
            }
        }
    }

    private RestoreState restore(RestoreConfig config) {
        final BackupImapStore imapStore = config.imapStore;

        int currentRestoredItem = config.currentRestoredItem;
        try {
            publishProgress(LOGIN);
            imapStore.checkSettings();

            publishProgress(CALC);

            final List<Message> msgs = new ArrayList<Message>();

            if (config.restoreSms) {
                msgs.addAll(imapStore.getFolder(SMS).getMessages(config.maxRestore, config.restoreOnlyStarred, null));
            }
            if (config.restoreCallLog) {
                msgs.addAll(imapStore.getFolder(CALLLOG).getMessages(config.maxRestore, config.restoreOnlyStarred, null));
            }

            final int itemsToRestoreCount = config.maxRestore <= 0 ? msgs.size() : Math.min(msgs.size(), config.maxRestore);

            if (itemsToRestoreCount > 0) {
                for (; currentRestoredItem < itemsToRestoreCount && !isCancelled(); currentRestoredItem++) {
                    DataType dataType = importMessage(msgs.get(currentRestoredItem));

                    msgs.set(currentRestoredItem, null); // help gc
                    publishProgress(new RestoreState(RESTORE, currentRestoredItem, itemsToRestoreCount, 0, 0, dataType, null));
                    if (currentRestoredItem % 50 == 0) {
                        //clear cache periodically otherwise SD card fills up
                        service.clearCache();
                    }
                }
                updateAllThreadsIfAnySmsRestored();
            } else {
                Log.d(TAG, "nothing to restore");
            }

            final int restoredCount = smsIds.size() + callLogIds.size();
            return new RestoreState(isCancelled() ? CANCELED_RESTORE : FINISHED_RESTORE,
                    currentRestoredItem,
                    itemsToRestoreCount,
                    restoredCount,
                    uids.size() - restoredCount, null, null);
        } catch (XOAuth2AuthenticationFailedException e) {
            return handleAuthError(config, currentRestoredItem, e);
        } catch (AuthenticationFailedException e) {
            return transition(ERROR, e);
        } catch (MessagingException e) {
            Log.e(TAG, "error", e);
            updateAllThreadsIfAnySmsRestored();
            return transition(ERROR, e);
        } catch (IllegalStateException e) {
            // usually memory problems (Couldn't init cursor window)
            return transition(ERROR, e);
        } finally {
            imapStore.closeFolders();
        }
    }

    private RestoreState handleAuthError(RestoreConfig config, int currentRestoredItem, XOAuth2AuthenticationFailedException e) {
        if (e.getStatus() == 400) {
            Log.d(TAG, "need to perform xoauth2 token refresh");
            if (config.tries < 1) {
                try {
                    tokenRefresher.refreshOAuth2Token();
                    // we got a new token, let's retry one more time - we need to pass in a new store object
                    // since the auth params on it are immutable
                    return restore(config.retryWithStore(currentRestoredItem, service.getBackupImapStore()));
                } catch (MessagingException ignored) {
                    Log.w(TAG, ignored);
                } catch (TokenRefreshException refreshException) {
                    Log.w(TAG, refreshException);
                }
            } else {
                Log.w(TAG, "no new token obtained, giving up");
            }
        } else {
            Log.w(TAG, "unexpected xoauth status code " + e.getStatus());
        }
        return transition(ERROR, e);
    }

    private void publishProgress(SmsSyncState smsSyncState) {
        publishProgress(smsSyncState, null);
    }

    private void publishProgress(SmsSyncState smsSyncState, Exception exception) {
        publishProgress(transition(smsSyncState, exception));
    }

    private RestoreState transition(SmsSyncState smsSyncState, Exception exception) {
        return service.getState().transition(smsSyncState, exception);
    }

    @Override
    protected void onPostExecute(RestoreState result) {
        if (result != null) {
            Log.d(TAG, "finished (" + result + "/" + uids.size() + ")");
            post(result);
        }
        App.bus.unregister(this);
    }

    @Override
    protected void onCancelled() {
        Log.d(TAG, "restore canceled by user");
        post(transition(CANCELED_RESTORE, null));
        App.bus.unregister(this);
    }

    @Override
    protected void onProgressUpdate(RestoreState... progress) {
        if (progress != null && progress.length > 0 && !isCancelled()) {
            post(progress[0]);
        }
    }

    private void post(RestoreState changed) {
        if (changed == null) return;
        App.bus.post(changed);
    }

    private DataType importMessage(Message message) {
        uids.add(message.getUid());

        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.BODY);
        DataType dataType = null;
        try {
            if (LOCAL_LOGV) Log.v(TAG, "fetching message uid " + message.getUid());
            message.getFolder().fetch(Arrays.asList(message), fp, null);
            dataType = converter.getDataType(message);
            //only restore sms+call log for now
            switch (dataType) {
                case CALLLOG:
                    importCallLog(message);
                    break;
                case SMS:
                    importSms(message);
                    break;
                default:
                    if (LOCAL_LOGV) Log.d(TAG, "ignoring restore of type: " + dataType);
            }

        } catch (MessagingException e) {
            Log.e(TAG, "error", e);
        } catch (IllegalArgumentException e) {
            // http://code.google.com/p/android/issues/detail?id=2916
            Log.e(TAG, "error", e);
        } catch (IOException e) {
            Log.e(TAG, "error", e);
        }
        return dataType;
    }

    private void importSms(final Message message) throws IOException, MessagingException {
        if (LOCAL_LOGV) Log.v(TAG, "importSms(" + message + ")");
        final ContentValues values = converter.messageToContentValues(message);
        final Integer type = values.getAsInteger(Telephony.TextBasedSmsColumns.TYPE);

        // only restore inbox messages and sent messages - otherwise sms might get sent on restore
        if (type != null && (type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX || type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT) && !smsExists(values)) {

            final Uri uri = resolver.insert(Consts.SMS_PROVIDER, values);
            if (uri != null) {
                smsIds.add(uri.getLastPathSegment());
                Long timestamp = values.getAsLong(Telephony.TextBasedSmsColumns.DATE);

                if (timestamp != null && SMS.getMaxSyncedDate(service) < timestamp) {
                    SMS.setMaxSyncedDate(service, timestamp);
                }

                if (LOCAL_LOGV) Log.v(TAG, "inserted " + uri);
            }
        } else {
            if (LOCAL_LOGV) Log.d(TAG, "ignoring sms");
        }
    }

    private void importCallLog(final Message message) throws MessagingException, IOException {
        if (LOCAL_LOGV) Log.v(TAG, "importCallLog(" + message + ")");
        final ContentValues values = converter.messageToContentValues(message);
        if (!callLogExists(values)) {
            final Uri uri = resolver.insert(Consts.CALLLOG_PROVIDER, values);
            if (uri != null) callLogIds.add(uri.getLastPathSegment());
        } else {
            if (LOCAL_LOGV) Log.d(TAG, "ignoring call log");
        }
    }

    private boolean callLogExists(ContentValues values) {
        Cursor c = resolver.query(Consts.CALLLOG_PROVIDER,
            new String[] { "_id" },
            "date = ? AND number = ? AND duration = ? AND type = ?",
            new String[]{
                values.getAsString(CallLog.Calls.DATE),
                values.getAsString(CallLog.Calls.NUMBER),
                values.getAsString(CallLog.Calls.DURATION),
                values.getAsString(CallLog.Calls.TYPE)
            },
            null
        );
        boolean exists = false;
        if (c != null) {
            exists = c.getCount() > 0;
            c.close();
        }
        return exists;
    }

    private boolean smsExists(ContentValues values) {
        // just assume equality on date+address+type
        Cursor c = resolver.query(Consts.SMS_PROVIDER,
            new String[] {"_id" },
            "date = ? AND address = ? AND type = ?",
            new String[] {
                values.getAsString(Telephony.TextBasedSmsColumns.DATE),
                values.getAsString(Telephony.TextBasedSmsColumns.ADDRESS),
                values.getAsString(Telephony.TextBasedSmsColumns.TYPE)
            },
            null
        );

        boolean exists = false;
        if (c != null) {
            exists = c.getCount() > 0;
            c.close();
        }
        return exists;
    }

    private void updateAllThreadsIfAnySmsRestored() {
        if (smsIds.size() > 0) {
            updateAllThreads();
        }
    }

    private void updateAllThreads() {
        // thread dates + states might be wrong, we need to force a full update
        // unfortunately there's no direct way to do that in the SDK, but passing a
        // negative conversation id to delete should to the trick
        publishProgress(UPDATING_THREADS);
        Log.d(TAG, "updating threads");
        resolver.delete(Uri.parse("content://sms/conversations/-1"), null, null);
        Log.d(TAG, "finished");
    }

    protected Set<String> getSmsIds() {
        return smsIds;
    }
}
