package com.zegoggles.smssync;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.content.Context;
import android.util.Log;
import android.provider.CallLog;
import com.fsck.k9.mail.*;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import com.zegoggles.smssync.CursorToMessage.DataType;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import static com.zegoggles.smssync.ServiceBase.SmsSyncState.*;
import static com.zegoggles.smssync.App.*;

public class SmsRestoreService extends ServiceBase {
    private static int sCurrentRestoredItems;
    private static int sItemsToRestoreCount;

    static int sRestoredCount, sDuplicateCount;

    private static boolean sIsRunning = false;
    private static boolean sCanceled = false;

    public static void cancel() {
        sCanceled = true;
    }

    public static boolean isWorking() {
        return sIsRunning;
    }

    public static int getCurrentRestoredItems() {
        return sCurrentRestoredItems;
    }

    public static int getItemsToRestoreCount() {
        return sItemsToRestoreCount;
    }

    class RestoreTask extends AsyncTask<Integer, SmsSyncState, Integer> {
        private Set<String> smsIds     = new HashSet<String>();
        private Set<String> callLogIds = new HashSet<String>();
        private Set<String> uids       = new HashSet<String>();
        private BackupImapStore.BackupFolder smsFolder, callFolder;
        private final Context context = SmsRestoreService.this;
        private CursorToMessage converter = new CursorToMessage(context, PrefStore.getUserEmail(context));
        private int max;

        protected java.lang.Integer doInBackground(Integer... params) {
            this.max = params.length > 0 ? params[0] : -1;
            final boolean starredOnly = PrefStore.isRestoreStarredOnly(context);
            final boolean restoreCallLog = PrefStore.isRestoreCallLog(context);
            final boolean restoreSms     = PrefStore.isRestoreSms(context);

            if (!restoreSms && !restoreCallLog) return null;

            try {
                acquireLocks(false);
                sIsRunning = true;

                publishProgress(LOGIN);
                smsFolder = getSMSBackupFolder();
                if (restoreCallLog) callFolder = getCallLogBackupFolder();

                publishProgress(CALC);

                final List<Message> msgs = new ArrayList<Message>();

                if (restoreSms) msgs.addAll(smsFolder.getMessages(max, starredOnly, null));
                if (restoreCallLog) msgs.addAll(callFolder.getMessages(max, starredOnly, null));

                sItemsToRestoreCount = max <= 0 ? msgs.size() : Math.min(msgs.size(), max);

                long lastPublished = System.currentTimeMillis();
                for (int i = 0; i < sItemsToRestoreCount && !sCanceled; i++) {

                    importMessage(msgs.get(i));
                    sCurrentRestoredItems = i;

                    msgs.set(i, null); // help gc

                    if (System.currentTimeMillis() - lastPublished > 1000) {
                        // don't publish too often or we get ANRs
                        publishProgress(RESTORE);
                        lastPublished = System.currentTimeMillis();
                    }

                    if (i % 50 == 0) {
                      //clear cache periodically otherwise SD card fills up
                      clearCache();
                    }
                }
                publishProgress(UPDATING_THREADS);
                updateAllThreads(false);

                return smsIds.size() + callLogIds.size();
            } catch (ConnectivityErrorException e) {
                lastError = translateException(e);
                publishProgress(CONNECTIVITY_ERROR);
                return null;
            } catch (AuthenticationFailedException e) {
                publishProgress(AUTH_FAILED);
                return null;
            } catch (MessagingException e) {
                Log.e(TAG, "error", e);
                lastError = translateException(e);
                publishProgress(GENERAL_ERROR);
                return null;
            } catch (IllegalStateException e) {
                // usually memory problems (Couldn't init cursor window)
                lastError = translateException(e);
                publishProgress(GENERAL_ERROR);
                return null;
            } finally {
                releaseLocks();
           }
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (sCanceled) {
                Log.d(TAG, "restore canceled by user");
                publishProgress(CANCELED_RESTORE);
            } else if (result != null) {
                Log.d(TAG, "finished (" + result + "/" + uids.size() + ")");
                sRestoredCount = result;
                sDuplicateCount = uids.size() - result;
                publishProgress(FINISHED_RESTORE);
            }
            sCanceled = false;
            sIsRunning = false;
        }

        @Override protected void onProgressUpdate(SmsSyncState... progress) {
          if (progress == null || progress.length == 0) return;
          if (smsSync != null) smsSync.statusPref.stateChanged(progress[0]);
          sState = progress[0];
        }

        private void updateAllThreads(final boolean async) {
            // thread dates + states might be wrong, we need to force a full update
            // unfortunately there's no direct way to do that in the SDK, but passing a
            // negative conversation id to delete should to the trick

            // execute in background, might take some time
            final Thread t = new Thread() {
                @Override public void run() {
                    Log.d(TAG, "updating threads");
                    getContentResolver().delete(Uri.parse("content://sms/conversations/-1"), null, null);
                    Log.d(TAG, "finished");
                }
            };
            t.start();
            try {
              if (!async) t.join();
            } catch (InterruptedException ignored) { }
        }

        private void importMessage(Message message) {
            uids.add(message.getUid());

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.BODY);

            try {
                if (LOCAL_LOGV) Log.v(TAG, "fetching message uid " + message.getUid());

                message.getFolder().fetch(new Message[] { message }, fp, null);
                final DataType dataType = converter.getDataType(message);
                //only restore sms+call log for now
                switch (dataType) {
                    case CALLLOG: importCallLog(message); break;
                    case SMS:     importSms(message); break;
                    default: if (LOCAL_LOGV) Log.d(TAG, "ignoring restore of type: " + dataType);
                }
            } catch (MessagingException e) {
                Log.e(TAG, "error", e);
            } catch (IllegalArgumentException e) {
                // http://code.google.com/p/android/issues/detail?id=2916
                Log.e(TAG, "error", e);
            } catch (java.io.IOException e) {
                Log.e(TAG, "error", e);
            }
        }

        private void importSms(final Message message) throws IOException, MessagingException {
            if (LOCAL_LOGV) Log.v(TAG, "importSms("+message+")");
            final ContentValues values = converter.messageToContentValues(message);
            final Integer type = values.getAsInteger(SmsConsts.TYPE);

            // only restore inbox messages and sent messages - otherwise sms might get sent on restore
            if (type != null && (type == SmsConsts.MESSAGE_TYPE_INBOX ||
                                 type == SmsConsts.MESSAGE_TYPE_SENT) &&
                                 !smsExists(values)) {
                final Uri uri = getContentResolver().insert(SMS_PROVIDER, values);
                if (uri != null) {
                  smsIds.add(uri.getLastPathSegment());
                  Long timestamp = values.getAsLong(SmsConsts.DATE);

                  if (timestamp != null &&
                      PrefStore.getMaxSyncedDateSms(context) < timestamp) {
                      updateMaxSyncedDateSms(timestamp);
                  }
                  if (LOCAL_LOGV) Log.v(TAG, "inserted " + uri);
                }
            } else {
                if (LOCAL_LOGV) Log.d(TAG, "ignoring sms");
            }
        }

        private void importCallLog(final Message message) throws MessagingException, IOException {
            if (LOCAL_LOGV) Log.v(TAG, "importCallLog("+message+")");
            final ContentValues values = converter.messageToContentValues(message);
            if (!callLogExists(values)) {
              final Uri uri = getContentResolver().insert(CALLLOG_PROVIDER, values);
              if (uri != null) callLogIds.add(uri.getLastPathSegment());
            } else {
              if (LOCAL_LOGV) Log.d(TAG, "ignoring call log");
            }
        }
    }

    @Override public void onCreate() {
       asyncClearCache();
       BinaryTempFileBody.setTempDirectory(getCacheDir());
    }

    @Override protected void handleIntent(final Intent intent) {
        synchronized (ServiceBase.class) {
            if (!sIsRunning) {
                new RestoreTask().execute(PrefStore.getMaxItemsPerRestore(this));
            }
        }
    }

   private synchronized void asyncClearCache() {
       new Thread("clearCache") {
          @Override public void run() { clearCache(); }
       }.start();
    }

    private void clearCache() {
        File tmp = getCacheDir();
        if (tmp == null) return; // not sure why this would return null

        Log.d(TAG, "clearing cache in " + tmp);
        for (File f : tmp.listFiles(new FilenameFilter() {
          public boolean accept(File dir, String name) {
            return name.startsWith("body");
          }
        })) {
          if (LOCAL_LOGV) Log.v(TAG, "deleting " + f);
          if (!f.delete()) Log.w(TAG, "error deleting " + f);
        }
    }

    private boolean callLogExists(ContentValues values) {
        Cursor c = getContentResolver().query(CALLLOG_PROVIDER,
                new String[] { "_id" },
                "number = ? AND duration = ? AND type = ?",
                new String[] { values.getAsString(CallLog.Calls.NUMBER),
                               values.getAsString(CallLog.Calls.DURATION),
                               values.getAsString(CallLog.Calls.TYPE) },
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
        Cursor c = getContentResolver().query(SMS_PROVIDER,
                new String[] { "_id" },
                "date = ? AND address = ? AND type = ?",
                new String[] { values.getAsString(SmsConsts.DATE),
                               values.getAsString(SmsConsts.ADDRESS),
                               values.getAsString(SmsConsts.TYPE)},
                               null
        );

        boolean exists = false;
        if (c != null) {
          exists = c.getCount() > 0;
          c.close();
        }
        return exists;
    }
}
