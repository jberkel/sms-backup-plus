package com.zegoggles.smssync.service;

import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.provider.CallLog;
import android.text.TextUtils;
import android.util.Log;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.XOAuth2AuthenticationFailedException;
import com.github.jberkel.whassup.Whassup;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.SmsConsts;
import com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.ConversionResult;
import com.zegoggles.smssync.mail.CursorToMessage;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.PrefStore;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.zegoggles.smssync.App.*;
import static com.zegoggles.smssync.preferences.PrefStore.getMaxSyncedDateSms;
import static com.zegoggles.smssync.service.state.SmsSyncState.*;

/**
 * BackupTask does all the work
 */
class BackupTask extends AsyncTask<Intent, BackupState, BackupState> {
    private final int maxItemsPerSync;
    private final ContactAccessor.ContactGroup groupToBackup;
    private final BackupType backupType;
    private final SmsBackupService service;
    private final int maxMessagePerRequest;
    private final BackupImapStore imapStore;

    BackupTask(@NotNull SmsBackupService service,
               @NotNull BackupImapStore imapStore,
               int maxMessagePerRequest,
               int maxItemsPerSync,
               ContactAccessor.ContactGroup groupToBackup,
               BackupType backupType) {
        this.backupType = backupType;
        this.maxItemsPerSync = maxItemsPerSync;
        this.service = service;
        this.imapStore = imapStore;
        this.groupToBackup = groupToBackup;
        this.maxMessagePerRequest = maxMessagePerRequest;
    }

    @Override
    protected void onPreExecute() {
        App.bus.register(this);
    }

    @Subscribe public void userCanceled(UserCanceled canceled) {
        cancel(false);
    }

    @Override
    protected BackupState doInBackground(Intent... params) {
        final Intent intent = params[0];
        if (intent.getBooleanExtra(Consts.KEY_SKIP_MESSAGES, false)) {
            appLog(R.string.app_log_skip_backup_skip_messages);
            return skip();
        }

        appLog(R.string.app_log_start_backup, backupType);

        Cursor smsItems = null;
        Cursor mmsItems = null;
        Cursor callLogItems = null;
        Cursor whatsAppItems = null;
        final int smsCount, mmsCount, callLogCount, whatsAppItemsCount;
        try {
            service.acquireLocks(backupType.isBackground());
            smsItems = getSmsItemsToSync(maxItemsPerSync, groupToBackup);
            smsCount = smsItems != null ? smsItems.getCount() : 0;

            mmsItems = getMmsItemsToSync(maxItemsPerSync - smsCount, groupToBackup);
            mmsCount = mmsItems != null ? mmsItems.getCount() : 0;

            callLogItems = getCallLogItemsToSync(maxItemsPerSync - smsCount - mmsCount);
            callLogCount = callLogItems != null ? callLogItems.getCount() : 0;

            whatsAppItems = getWhatsAppItemsToSync(maxItemsPerSync - smsCount - mmsCount - callLogCount);
            whatsAppItemsCount = whatsAppItems != null ? whatsAppItems.getCount() : 0;

            final int itemsToSync = smsCount + mmsCount + callLogCount + whatsAppItemsCount;

            if (itemsToSync > 0) {
                if (!PrefStore.isLoginInformationSet(service)) {
                    appLog(R.string.app_log_missing_credentials);
                    return transition(ERROR, new RequiresLoginException());
                } else {
                    appLog(R.string.app_log_backup_messages, smsCount, mmsCount, callLogCount);
                    return backup(smsItems, mmsItems, callLogItems, whatsAppItems, itemsToSync);
                }
            } else {
                appLog(R.string.app_log_skip_backup_no_items);

                if (PrefStore.isFirstBackup(service)) {
                    // If this is the first backup we need to write something to PREF_MAX_SYNCED_DATE
                    // such that we know that we've performed a backup before.
                    PrefStore.setMaxSyncedDateSms(service, PrefStore.DEFAULT_MAX_SYNCED_DATE);
                    PrefStore.setMaxSyncedDateMms(service, PrefStore.DEFAULT_MAX_SYNCED_DATE);
                }
                Log.i(TAG, "Nothing to do.");
                return transition(FINISHED_BACKUP, null);
            }
        } catch (XOAuth2AuthenticationFailedException e) {
            if (e.getStatus() == 400) {
                Log.d(TAG, "need to perform xoauth2 token refresh");
                if (!intent.hasExtra("refresh_retried") &&
                        AccountManagerAuthActivity.refreshOAuth2Token(service)) {

                    // we got a new token, let's retry one more time
                    intent.putExtra("refresh_retried", true);
                    return doInBackground(intent);
                } else {
                    Log.w(TAG, "no new token obtained, giving up");
                }
            } else {
                Log.w(TAG, "unexpected xoauth status code " + e.getStatus());
            }
            appLog(R.string.app_log_backup_failed_authentication, e.getLocalizedMessage());
            return transition(ERROR, e);
        } catch (AuthenticationFailedException e) {
            appLog(R.string.app_log_backup_failed_authentication, e.getLocalizedMessage());
            return transition(ERROR, e);
        } catch (MessagingException e) {
            appLog(R.string.app_log_backup_failed_messaging, e.getLocalizedMessage());
            return transition(ERROR, e);
        } catch (ConnectivityErrorException e) {
            appLog(R.string.app_log_backup_failed_connectivity, e.getLocalizedMessage());
            return transition(ERROR, e);
        } finally {
            service.releaseLocks();
            try {
                if (smsItems != null) smsItems.close();
                if (mmsItems != null) mmsItems.close();
                if (callLogItems != null) callLogItems.close();
                if (whatsAppItems != null) whatsAppItems.close();
            } catch (Exception ignore) {
                Log.e(TAG, "error", ignore);
            }
        }
    }

    private void appLog(int id, Object... args) {
        service.appLog(id, args);
    }

    private BackupState transition(SmsSyncState smsSyncState, Exception exception) {
        return service.getState().transition(smsSyncState, exception);
    }

    @Override
    protected void onProgressUpdate(BackupState... progress) {
        if (progress != null && progress.length > 0) {
            post(progress[0]);
        }
    }

    @Override
    protected void onPostExecute(BackupState result) {
        appLog(R.string.app_log_backup_finished);
        if (result != null) {
            post(result);
        }
        App.bus.unregister(this);
    }

    @Override
    protected void onCancelled() {
        appLog(R.string.app_log_backup_canceled);
        post(transition(CANCELED_BACKUP, null));
        App.bus.unregister(this);
    }

    private void post(BackupState state) {
        App.bus.post(state);
    }

    private BackupState backup(Cursor smsItems, Cursor mmsItems, Cursor callLogItems, Cursor whatsAppItems, final int itemsToSync)
            throws MessagingException {
        Log.i(TAG, String.format(Locale.ENGLISH, "Starting backup (%d messages)", itemsToSync));

        publish(LOGIN);
        Folder smsmmsfolder = imapStore.getSMSBackupFolder();
        Folder callLogfolder = null;
        Folder whatsAppFolder = null;
        if (PrefStore.isCallLogBackupEnabled(service)) {
            callLogfolder = imapStore.getCallLogBackupFolder();
        }
        if (PrefStore.isWhatsAppBackupEnabled(service)) {
            whatsAppFolder = imapStore.getWhatsAppBackupFolder();
        }

        try {
            final CursorToMessage converter = new CursorToMessage(service, PrefStore.getUserEmail(service));
            Cursor curCursor;
            DataType dataType = null;
            publish(CALC);
            int backedUpItems = 0;
            while (!isCancelled() && backedUpItems < itemsToSync) {
                if (smsItems != null && smsItems.moveToNext()) {
                    dataType = DataType.SMS;
                    curCursor = smsItems;
                } else if (mmsItems != null && mmsItems.moveToNext()) {
                    dataType = DataType.MMS;
                    curCursor = mmsItems;
                } else if (callLogItems != null && callLogItems.moveToNext()) {
                    dataType = DataType.CALLLOG;
                    curCursor = callLogItems;
                } else if (whatsAppItems != null && whatsAppItems.moveToNext()) {
                    dataType = DataType.WHATSAPP;
                    curCursor = whatsAppItems;
                } else break; // no more items available

                if (LOCAL_LOGV) Log.v(TAG, "backing up: " + dataType);
                ConversionResult result = converter.cursorToMessages(curCursor, maxMessagePerRequest, dataType);
                List<Message> messages = result.messageList;
                if (!messages.isEmpty()) {
                    if (LOCAL_LOGV)
                        Log.v(TAG, String.format(Locale.ENGLISH, "sending %d %s message(s) to server.",
                                messages.size(), dataType));
                    switch (dataType) {
                        case MMS:
                            updateMaxSyncedDateMms(result.maxDate);
                            smsmmsfolder.appendMessages(messages.toArray(new Message[messages.size()]));
                            break;
                        case SMS:
                            service.updateMaxSyncedDateSms(result.maxDate);
                            smsmmsfolder.appendMessages(messages.toArray(new Message[messages.size()]));
                            break;
                        case CALLLOG:
                            updateMaxSyncedDateCallLog(result.maxDate);
                            if (callLogfolder != null) {
                                callLogfolder.appendMessages(messages.toArray(new Message[messages.size()]));
                            }
                            if (PrefStore.isCallLogCalendarSyncEnabled(service)) {
                                new CalendarSyncer(service, service.getCalendars(), PrefStore.getCallLogCalendarId(service)).syncCalendar(converter, result);
                            }
                            break;
                        case WHATSAPP:
                            updateMaxSyncedDateWhatsApp(result.maxDate);
                            if (whatsAppFolder != null) {
                                whatsAppFolder.appendMessages(messages.toArray(new Message[messages.size()]));
                            }
                            break;
                    }
                }
                backedUpItems += messages.size();
                publishProgress(new BackupState(BACKUP,
                        backedUpItems,
                        itemsToSync,
                        backupType,
                        dataType,
                        null));
            }
            return new BackupState(FINISHED_BACKUP,
                    backedUpItems,
                    itemsToSync,
                    backupType, dataType, null);
        } finally {
            if (smsmmsfolder != null) smsmmsfolder.close();
            if (callLogfolder != null) callLogfolder.close();
            if (whatsAppFolder != null) whatsAppFolder.close();
        }
    }

    private Cursor getSmsItemsToSync(int max, ContactAccessor.ContactGroup group) {
        if (LOCAL_LOGV) {
            Log.v(TAG, String.format("getSmsItemToSync(max=%d),  maxSyncedDate=%d", max,
                    getMaxSyncedDateSms(service)));
        }

        if (!PrefStore.isSmsBackupEnabled(service)) {
            if (LOCAL_LOGV) Log.v(TAG, "SMS backup disabled, returning empty cursor");
            return new MatrixCursor(new String[0], 0);
        }

        String sortOrder = SmsConsts.DATE;
        if (max > 0) sortOrder += " LIMIT " + max;

        return service.getContentResolver().query(Consts.SMS_PROVIDER, null,
                String.format(Locale.ENGLISH, "%s > ? AND %s <> ? %s", SmsConsts.DATE, SmsConsts.TYPE,
                        groupSelection(DataType.SMS, group)),
                new String[]{String.valueOf(getMaxSyncedDateSms(service)),
                        String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT)},
                sortOrder);
    }

    private Cursor getMmsItemsToSync(int max, ContactAccessor.ContactGroup group) {
        if (LOCAL_LOGV) Log.v(TAG, "getMmsItemsToSync(max=" + max + ")");

        if (!PrefStore.isMmsBackupEnabled(service)) {
            // return empty cursor if we don't have MMS
            if (LOCAL_LOGV) Log.v(TAG, "MMS backup disabled, returning empty cursor");
            return new MatrixCursor(new String[0], 0);
        }
        String sortOrder = SmsConsts.DATE;
        if (max > 0) sortOrder += " LIMIT " + max;

        return service.getContentResolver().query(Consts.MMS_PROVIDER, null,
                String.format(Locale.ENGLISH, "%s > ? AND %s <> ? %s", SmsConsts.DATE, MmsConsts.TYPE,
                        groupSelection(DataType.MMS, group)),
                new String[]{String.valueOf(PrefStore.getMaxSyncedDateMms(service)),
                        MmsConsts.DELIVERY_REPORT},
                sortOrder);
    }

    private Cursor getCallLogItemsToSync(int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getCallLogItemsToSync(max=" + max + ")");

        if (!PrefStore.isCallLogBackupEnabled(service)) {
            if (LOCAL_LOGV) Log.v(TAG, "CallLog backup disabled, returning empty cursor");
            return new MatrixCursor(new String[0], 0);
        }
        String sortOrder = SmsConsts.DATE;
        if (max > 0) sortOrder += " LIMIT " + max;

        return service.getContentResolver().query(Consts.CALLLOG_PROVIDER,
                CursorToMessage.CALLLOG_PROJECTION,
                String.format(Locale.ENGLISH, "%s > ?", CallLog.Calls.DATE),
                new String[]{String.valueOf(PrefStore.getMaxSyncedDateCallLog(service))},
                sortOrder);
    }

    private Cursor getWhatsAppItemsToSync(int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getWhatsAppItemsToSync(max=" + max + ")");

        if (!PrefStore.isWhatsAppBackupEnabled(service)) {
            if (LOCAL_LOGV) Log.v(TAG, "WhatsApp backup disabled, returning empty");
            return null;
        }

        Whassup whassup = new Whassup();
        if (!whassup.hasBackupDB()) {
            if (LOCAL_LOGV) Log.v(TAG, "No whatsapp backup DB found, returning empty");
            return null;
        }

        try {
            return whassup.queryMessages(PrefStore.getMaxSyncedDateWhatsApp(service), max);
        } catch (IOException e) {
            Log.w(LOG, "error fetching whatsapp messages", e);
            return null;
        }
    }

    private String groupSelection(DataType type, ContactAccessor.ContactGroup group) {
        /* MMS group selection not supported at the moment */
        if (type != DataType.SMS || group.type == ContactAccessor.ContactGroup.Type.EVERYBODY) return "";

        final Set<Long> ids = service.getContacts().getGroupContactIds(service, group).rawIds;
        if (LOCAL_LOGV) Log.v(TAG, "only selecting contacts matching " + ids);
        return String.format(Locale.ENGLISH, " AND (%s = %d OR %s IN (%s))",
                SmsConsts.TYPE,
                SmsConsts.MESSAGE_TYPE_SENT,
                SmsConsts.PERSON,
                TextUtils.join(",", ids.toArray(new Long[ids.size()])));

    }

    private void publish(SmsSyncState state) {
        publish(state, null);
    }

    private void publish(SmsSyncState state, Exception exception) {
        publishProgress(service.getState().transition(state, backupType, exception));
    }

    /** Only update the max synced ID, do not really sync. */
    private BackupState skip() {
        service.updateMaxSyncedDateSms(getMaxItemDateSms());
        updateMaxSyncedDateMms(getMaxItemDateMms());
        updateMaxSyncedDateCallLog(getMaxItemDateCallLog());
        Log.i(TAG, "All messages skipped.");
        return new BackupState(FINISHED_BACKUP, 0, 0, BackupType.MANUAL, null, null);
    }

    private void updateMaxSyncedDateCallLog(long maxSyncedDate) {
        PrefStore.setMaxSyncedDateCallLog(service, maxSyncedDate);
        if (LOCAL_LOGV) {
            Log.v(TAG, "Max synced date for call log set to: " + maxSyncedDate);
        }
    }

    private long getMaxItemDateCallLog() {
        Cursor result = service.getContentResolver().query(Consts.CALLLOG_PROVIDER,
                new String[]{CallLog.Calls.DATE}, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 1");
        try {
            return result.moveToFirst() ? result.getLong(0) : PrefStore.DEFAULT_MAX_SYNCED_DATE;
        } finally {
            if (result != null) result.close();
        }
    }

    private void updateMaxSyncedDateMms(long maxSyncedDate) {
        PrefStore.setMaxSyncedDateMms(service, maxSyncedDate);
        if (LOCAL_LOGV) {
            Log.v(TAG, "Max synced date for mms set to: " + maxSyncedDate);
        }
    }

    private void updateMaxSyncedDateWhatsApp(long maxSyncedDate) {
        PrefStore.setMaxSyncedDateWhatsApp(service, maxSyncedDate);
        if (LOCAL_LOGV) {
            Log.v(TAG, "Max synced date for whats app set to: " + maxSyncedDate);
        }
    }

    /** @return the maximum date of all MMS messages */
    private long getMaxItemDateMms() {
        Cursor result = service.getContentResolver().query(Consts.MMS_PROVIDER,
                new String[]{MmsConsts.DATE}, null, null,
                MmsConsts.DATE + " DESC LIMIT 1");
        try {
            return result.moveToFirst() ? result.getLong(0) : PrefStore.DEFAULT_MAX_SYNCED_DATE;
        } finally {
            if (result != null) result.close();
        }
    }

    /** @return the maximum date of all SMS messages (except for drafts). */
    private long getMaxItemDateSms() {
        Cursor result = service.getContentResolver().query(Consts.SMS_PROVIDER,
                new String[]{SmsConsts.DATE},
                SmsConsts.TYPE + " <> ?",
                new String[]{String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT)},
                SmsConsts.DATE + " DESC LIMIT 1");

        try {
            return result.moveToFirst() ? result.getLong(0) : PrefStore.DEFAULT_MAX_SYNCED_DATE;
        } finally {
            if (result != null) result.close();
        }
    }
}
