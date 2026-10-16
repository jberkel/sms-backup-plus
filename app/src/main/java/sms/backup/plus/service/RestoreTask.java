package sms.backup.plus.service;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CallLog;
import android.provider.Telephony;
import androidx.annotation.NonNull;
import android.util.Log;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.squareup.otto.Subscribe;
import sms.backup.plus.App;
import sms.backup.plus.Consts;
import sms.backup.plus.mail.BackupImapStore;
import sms.backup.plus.mail.DataType;
import sms.backup.plus.mail.MessageConverter;
import sms.backup.plus.preferences.Preferences;
import sms.backup.plus.service.state.RestoreState;
import sms.backup.plus.service.state.SmsSyncState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static sms.backup.plus.App.LOCAL_LOGV;
import static sms.backup.plus.App.TAG;
import static sms.backup.plus.mail.DataType.CALLLOG;
import static sms.backup.plus.mail.DataType.SMS;
import static sms.backup.plus.service.state.SmsSyncState.CALC;
import static sms.backup.plus.service.state.SmsSyncState.CANCELED_RESTORE;
import static sms.backup.plus.service.state.SmsSyncState.FINISHED_RESTORE;
import static sms.backup.plus.service.state.SmsSyncState.LOGIN;
import static sms.backup.plus.service.state.SmsSyncState.RESTORE;
import static sms.backup.plus.service.state.SmsSyncState.UPDATING_THREADS;

class RestoreTask extends AsyncTask<RestoreConfig, RestoreState, RestoreState> {
    private static final String ERROR = "error";
    private Set<String> smsIds = new HashSet<String>();
    private Set<String> callLogIds = new HashSet<String>();
    private Set<String> uids = new HashSet<String>();

    @SuppressLint("StaticFieldLeak")
    private final SmsRestoreService service;
    private final ContentResolver resolver;
    private final MessageConverter converter;
    private final Preferences preferences;

    RestoreTask(SmsRestoreService service,
                MessageConverter converter,
                ContentResolver resolver) {
        this.service = service;
        this.converter = converter;
        this.resolver = resolver;
        this.preferences = service.getPreferences();
    }

    @Override
    protected void onPreExecute() {
        App.register(this);
    }

    @Subscribe public void canceled(CancelEvent canceled) {
        cancel(canceled.mayInterruptIfRunning());
    }

    @NonNull protected RestoreState doInBackground(RestoreConfig... params) {
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
                msgs.addAll(imapStore.getFolder(SMS, preferences.getDataTypePreferences()).getMessages(config.maxRestore, config.restoreOnlyStarred, null));
            }
            if (config.restoreCallLog) {
                msgs.addAll(imapStore.getFolder(CALLLOG, preferences.getDataTypePreferences()).getMessages(config.maxRestore, config.restoreOnlyStarred, null));
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
                    Math.max(0, uids.size() - restoredCount),
                    null, null);
} catch (AuthenticationFailedException e) {
            return transition(SmsSyncState.ERROR, e);
        } catch (MessagingException e) {
            Log.e(TAG, ERROR, e);
            updateAllThreadsIfAnySmsRestored();
            return transition(SmsSyncState.ERROR, e);
        } catch (IllegalStateException e) {
            // usually memory problems (Couldn't init cursor window)
            return transition(SmsSyncState.ERROR, e);
        } finally {
            imapStore.closeFolders();
        }
    }


    private void publishProgress(SmsSyncState smsSyncState) {
        publishProgress(transition(smsSyncState, null));
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
        App.unregister(this);
    }

    @Override
    protected void onCancelled() {
        Log.d(TAG, "restore cancelled");
        post(transition(CANCELED_RESTORE, null));
        App.unregister(this);
    }

    @Override
    protected void onProgressUpdate(RestoreState... progress) {
        if (progress != null && progress.length > 0 && !isCancelled()) {
            post(progress[0]);
        }
    }

    private void post(RestoreState changed) {
        if (changed == null) return;
        App.post(changed);
    }

    @SuppressWarnings("unchecked")
    private DataType importMessage(Message message) {
        uids.add(message.getUid());

        FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.BODY);
        DataType dataType = null;
        try {
            if (LOCAL_LOGV) Log.v(TAG, "fetching message uid " + message.getUid());
            message.getFolder().fetch(Collections.singletonList(message), fp, null);
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
            Log.e(TAG, ERROR, e);
        } catch (IllegalArgumentException e) {
            // http://code.google.com/p/android/issues/detail?id=2916
            Log.e(TAG, ERROR, e);
        } catch (IOException e) {
            Log.e(TAG, ERROR, e);
        }
        return dataType;
    }

    private void importSms(final Message message) throws IOException, MessagingException {
        if (LOCAL_LOGV) Log.v(TAG, "importSms(" + message + ")");
        final ContentValues values = converter.messageToContentValues(message);
        final Integer type = values.getAsInteger(Telephony.TextBasedSmsColumns.TYPE);

        // only restore inbox messages and sent messages - otherwise sms might get sent on restore
        if (type != null &&
              (type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX ||
               type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT) &&
            !smsExists(values)) {

            final Uri uri = resolver.insert(Consts.SMS_PROVIDER, values);
            if (uri != null) {
                smsIds.add(uri.getLastPathSegment());
                Long timestamp = values.getAsLong(Telephony.TextBasedSmsColumns.DATE);

                if (timestamp != null && preferences.getDataTypePreferences().getMaxSyncedDate(SMS) < timestamp) {
                    preferences.getDataTypePreferences().setMaxSyncedDate(SMS, timestamp);
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
