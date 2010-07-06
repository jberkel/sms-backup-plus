package tv.studer.smssync;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import com.android.email.mail.*;
import com.android.email.mail.internet.BinaryTempFileBody;
import org.apache.commons.io.IOUtils;

import java.util.HashSet;
import java.util.Set;

import static tv.studer.smssync.CursorToMessage.Headers.*;
import static tv.studer.smssync.ServiceBase.SmsSyncState.*;

public class SmsRestoreService extends ServiceBase {

    public static final String TAG = "SmsRestoreService";
    private static int currentRestoredItems;
    private static int itemsToRestoreCount;

    private static boolean sIsRunning = false;
    private static SmsSyncState sState;
    private static boolean sCanceled = false;

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

    private class RestoreTask extends AsyncTask<Integer, Integer, Integer> {
        private Set<String> ids = new HashSet<String>();
        private Set<String> uids = new HashSet<String>();
        private int max;

        protected java.lang.Integer doInBackground(Integer... params) {
            this.max = params.length > 0 ? params[0] : -1;

            try {
                acquireWakeLock();
                sIsRunning = true;

                updateState(LOGIN);
                Folder folder = getBackupFolder();

                updateState(CALC);
                Message[] msgs = folder.getMessages(null);

                itemsToRestoreCount = msgs.length;

                long lastPublished = System.currentTimeMillis();
                for (int i = 0; i < msgs.length; i++) {
                    if (sCanceled) {
                        Log.i(TAG, "Restore canceled by user.");
                        updateState(CANCELED);

                        updateAllThreads();
                        return ids.size();
                    }
                    importMessage(msgs[i]);

                    // help GC
                    msgs[i] = null;

                    if (System.currentTimeMillis() - lastPublished > 1000) {
                        // don't publish too often or we get ANRs
                        publishProgress(i);
                        lastPublished = System.currentTimeMillis();
                    }
                }
                publishProgress(msgs.length);

                updateAllThreads();

                updateState(IDLE);

                Log.d(TAG, "finished (" + ids.size() + "/" + uids.size() + ")");
                return ids.size();
            } catch (AuthenticationErrorException authError) {
                Log.e(TAG, "error", authError);
                updateState(AUTH_FAILED);
                return -1;
            } catch (MessagingException e) {
                Log.e(TAG, "error", e);
                updateState(GENERAL_ERROR);
                return -1;
            } finally {
                releaseWakeLock();
                sCanceled = false;
                sIsRunning = false;
            }
        }

        protected void onProgressUpdate(Integer... progress) {
            currentRestoredItems = progress[0];
            updateState(RESTORE);
        }

        protected void onPostExecute(Integer result) {
        }

        private void updateAllThreads() {
            // thread dates + states might be wrong, we need to force a full update
            // unfortunately there's no direct way to do that in the SDK, but passing a negative conversation
            // id to delete will to the trick

            if (ids.isEmpty())
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
                    ids.add(uri.getLastPathSegment());
                    Log.d(TAG, "inserted " + uri);
                } else {
                    Log.d(TAG, "ignoring sms");
                }

            } catch (java.io.IOException e) {
                Log.e(TAG, "error", e);
            } catch (MessagingException e) {
                Log.e(TAG, "error", e);
            }
        }
    }

    @Override
    public void onCreate() {
        BinaryTempFileBody.setTempDirectory(getCacheDir());
    }

    @Override
    public void onStart(final Intent intent, int startId) {
        super.onStart(intent, startId);

        synchronized (this.getClass()) {
            if (!sIsRunning) {
                new RestoreTask().execute(-1);
            }
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

        boolean exists = c.getCount() > 0;
        c.close();
        return exists;
    }


    private ContentValues messageToContentValues(Message message)
            throws java.io.IOException, MessagingException {
        ContentValues values = new ContentValues();

        String body = IOUtils.toString(message.getBody().getInputStream());

        values.put(SmsConsts.BODY, body);
        values.put(SmsConsts.ADDRESS, getHeader(message, ADDRESS));
        values.put(SmsConsts.TYPE, getHeader(message, TYPE));
        values.put(SmsConsts.PROTOCOL, getHeader(message, PROTOCOL));
        values.put(SmsConsts.READ, getHeader(message, READ));
        values.put(SmsConsts.SERVICE_CENTER, getHeader(message, SERVICE_CENTER));
        values.put(SmsConsts.DATE, getHeader(message, DATE));
        values.put(SmsConsts.STATUS, getHeader(message, STATUS));

        return values;
    }

    private static void updateState(SmsSyncState newState) {
        SmsSyncState old = sState;
        sState = newState;
        smsSync.getStatusPreference().stateChanged(old, newState);
    }
}