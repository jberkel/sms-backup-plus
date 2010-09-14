package com.zegoggles.smssync;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import com.fsck.k9.mail.*;
import com.fsck.k9.mail.internet.BinaryTempFileBody;
import org.apache.commons.io.IOUtils;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.FilenameFilter;

import static com.zegoggles.smssync.CursorToMessage.Headers.*;
import static com.zegoggles.smssync.ServiceBase.SmsSyncState.*;

public class SmsRestoreService extends ServiceBase {

    public static final String TAG = "SmsRestoreService";
    private static int currentRestoredItems;
    private static int itemsToRestoreCount;

    private static boolean sIsRunning = false;
    private static SmsSyncState sState;
    private static boolean sCanceled = false;

    public static int restoredCount, duplicateCount;

    public static void cancel() {
        sCanceled = true;
    }

    public static boolean isWorking() {
        return sIsRunning;
    }

    public static boolean isCancelling() {
        return sCanceled;
    }

    public static int getCurrentRestoredItems() {
        return currentRestoredItems;
    }

    public static int getItemsToRestoreCount() {
        return itemsToRestoreCount;
    }

    class RestoreTask extends AsyncTask<Integer, Integer, Integer> {
        private Set<String> insertedIds = new HashSet<String>();
        private Set<String> uids = new HashSet<String>();
        private int max;

        protected java.lang.Integer doInBackground(Integer... params) {
            this.max = params.length > 0 ? params[0] : -1;

            try {
                acquireLocks();
                sIsRunning = true;

                updateState(LOGIN);
                ImapStore.BackupFolder folder = getBackupFolder();

                updateState(CALC);

                Message[] msgs;
                if (max > 0) {
                    msgs = folder.getMessagesSince(null, max);
                } else {
                    msgs = folder.getMessages(null);
                }

                itemsToRestoreCount = max == -1 ? msgs.length : Math.min(msgs.length, max);

                long lastPublished = System.currentTimeMillis();
                for (int i = 0; i < itemsToRestoreCount; i++) {
                    if (sCanceled) {
                        Log.i(TAG, "Restore canceled by user.");
                        updateState(CANCELED);

                        updateAllThreads();
                        return insertedIds.size();
                    }
                    importMessage(msgs[i]);

                    // help GC
                    msgs[i] = null;

                    if (System.currentTimeMillis() - lastPublished > 1000) {
                        // don't publish too often or we get ANRs
                        publishProgress(i);
                        lastPublished = System.currentTimeMillis();
                    }

                    if (i % 50 == 0) {
                      //clear cache periodically otherwise SD card fills up
                      clearCache();
                    }
                }
                publishProgress(itemsToRestoreCount);

                updateAllThreads();

                return insertedIds.size();
            } catch (GeneralErrorException error) {
                Log.e(TAG, "error", error);
                sLastError = error.getLocalizedMessage();
                updateState(GENERAL_ERROR);
                return -1;
            } catch (AuthenticationErrorException authError) {
                Log.e(TAG, "error", authError);
                updateState(AUTH_FAILED);
                return -1;
            } catch (MessagingException e) {
                Log.e(TAG, "error", e);
                updateState(GENERAL_ERROR);
                return -1;
            } finally {
                releaseLocks();
                sCanceled = false;
                sIsRunning = false;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            currentRestoredItems = progress[0];
            updateState(RESTORE);
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result != -1) {
                Log.d(TAG, "finished (" + result + "/" + uids.size() + ")");
                restoredCount = result;
                duplicateCount = uids.size() - result;
                updateState(IDLE);
            }
        }

        private void updateAllThreads() {
            // thread dates + states might be wrong, we need to force a full update
            // unfortunately there's no direct way to do that in the SDK, but passing a negative conversation
            // id to delete will to the trick

            if (insertedIds.isEmpty())
                return;

            // execute in background, might take some time
            new Thread() {
                @Override
                public void run() {
                    Log.d(TAG, "updating threads");
                    getContentResolver().delete(Uri.parse("content://sms/conversations/-1"), null, null);
                    Log.d(TAG, "finished");
                }
            }.start();
        }

        private void importMessage(Message message) {
            uids.add(message.getUid());

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.BODY);

            try {
                Log.d(TAG, "fetching message uid " + message.getUid());
                message.getFolder().fetch(new Message[]{message}, fp, null);
                ContentValues values = messageToContentValues(message);

                Integer type = values.getAsInteger(SmsConsts.TYPE);
                if (type == null)
                    return;

                // only restore inbox messages and sent messages - otherwise sms might get sent on restore
                if ((type == SmsConsts.MESSAGE_TYPE_INBOX || type == SmsConsts.MESSAGE_TYPE_SENT) && !smsExists(values)) {
                    Uri uri = getContentResolver().insert(SMS_PROVIDER, values);
                    insertedIds.add(uri.getLastPathSegment());

                    long timestamp = values.getAsLong(SmsConsts.DATE);

                    if (getMaxSyncedDate() < timestamp) {
                        updateMaxSyncedDate(timestamp);
                    }
                    Log.d(TAG, "inserted " + uri);
                } else {
                    Log.d(TAG, "ignoring sms");
                }

            } catch (IllegalArgumentException e) {
                // http://code.google.com/p/android/issues/detail?id=2916
                Log.e(TAG, "error", e);
            } catch (java.io.IOException e) {
                Log.e(TAG, "error", e);
            } catch (MessagingException e) {
                Log.e(TAG, "error", e);
            }
        }
    }

    @Override
    public void onCreate() {
       clearCache();
       BinaryTempFileBody.setTempDirectory(getCacheDir());
    }

    @Override
    public void onStart(final Intent intent, int startId) {
        super.onStart(intent, startId);

        synchronized (ServiceBase.class) {
            if (!sIsRunning) {
                new RestoreTask().execute(PrefStore.getMaxItemsPerRestore(this));
            }
        }
    }

    private void clearCache() {
        File tmp = getCacheDir();
        Log.d(TAG, "clearing cache in " + tmp);
        for (File f : tmp.listFiles(new FilenameFilter() {
          public boolean accept(File dir, String name) {
            return name.startsWith("body");
          }
        })) {
          //Log.d(TAG, "deleting " + f);
          f.delete();
        }
    }

    private boolean smsExists(ContentValues values) {
        // just assume equality on date+address+type
        Cursor c = getContentResolver().query(SMS_PROVIDER,
                new String[]{"_id"},
                "date = ? AND address = ? AND type = ?",
                new String[]{values.getAsString(SmsConsts.DATE),
                        values.getAsString(SmsConsts.ADDRESS), values.getAsString(SmsConsts.TYPE)}, null
        );

        boolean exists = false;
        if (c != null) {
          exists = c.getCount() > 0;
          c.close();
        }
        return exists;
    }


    private ContentValues messageToContentValues(Message message)
            throws java.io.IOException, MessagingException {

        if (message == null) {
          throw new MessagingException("message is null");
        }

        java.io.InputStream is = message.getBody().getInputStream();

        if (is == null) {
          throw new MessagingException("body is null");
        }

        String body = IOUtils.toString(is);

        ContentValues values = new ContentValues();
        values.put(SmsConsts.BODY, body);
        values.put(SmsConsts.ADDRESS, getHeader(message, ADDRESS));
        values.put(SmsConsts.TYPE, getHeader(message, TYPE));
        values.put(SmsConsts.PROTOCOL, getHeader(message, PROTOCOL));
        values.put(SmsConsts.SERVICE_CENTER, getHeader(message, SERVICE_CENTER));
        values.put(SmsConsts.DATE, getHeader(message, DATE));
        values.put(SmsConsts.STATUS, getHeader(message, STATUS));
        values.put(SmsConsts.READ, PrefStore.getMarkAsReadOnRestore(this) ? "1" : getHeader(message, READ));
        return values;
    }

    private static void updateState(SmsSyncState newState) {
        SmsSyncState old = sState;
        sState = newState;
        smsSync.getStatusPreference().stateChanged(old, newState);
    }
}
