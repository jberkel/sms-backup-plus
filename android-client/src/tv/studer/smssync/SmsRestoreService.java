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

public class SmsRestoreService extends ServiceBase {

    public static final String TAG = "SmsRestoreService";

    public static void cancel() {
    }

    public static boolean isWorking() {
        return false;
    }

    private class RestoreTask extends AsyncTask<Integer, Integer, Integer> {
        private Set<String> ids = new HashSet<String>();
        private Set<String> uids = new HashSet<String>();
        private int max;

        protected java.lang.Integer doInBackground(Integer... params) {
            this.max = params.length > 0 ? params[0] : -1;

            try {
                acquireWakeLock();
                Message[] msgs = getBackupFolder().getMessages(null);

                for (int i = 0; i < msgs.length; i++) {
                    publishProgress(i, msgs.length);

                    if (max == -1 || i < max) {
                        importMessage(msgs[i]);
                    }
                }
                updateAllThreads();

                Log.d(TAG, "finished (" + ids.size() + "/" + uids.size()+")");
                return ids.size();
            } catch (Throwable e) {
                Log.e(TAG, "error", e);
                return -1;
            } finally {
                releaseWakeLock();
            }
        }

        protected void onProgressUpdate(Integer... progress) {
        }

        protected void onPostExecute(Integer result) {
        }

        private void importMessage(Message message) {
            uids.add(message.getUid());

            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.BODY);

            try {
                Log.d(TAG, "fetching message uid " + message.getUid());
                message.getFolder().fetch(new Message[]{message}, fp, null);
                ContentValues values = messageToContentValues(message);

                if (!smsExists(values)) {
                    Uri uri = getContentResolver().insert(SMS_PROVIDER, values);
                    ids.add(uri.getLastPathSegment());
                    Log.d(TAG, "inserted " + uri);

                } else {
                    Log.d(TAG, "sms already exists, ignoring");
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
        new RestoreTask().execute(10);
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

    private void updateAllThreads() {
        // thread dates + states might be wrong, we need to force a full update
        // unfortunately there's no direct way to do that in the SDK, but passing a negative conversation
        // id to delete will to the trick
        getContentResolver().delete(Uri.parse("content://sms/conversations/-1"), null, null);
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
}