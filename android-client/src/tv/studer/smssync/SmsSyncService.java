/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.studer.smssync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.google.gdata.client.GoogleAuthTokenFactory;
import com.google.gdata.client.GoogleService.CaptchaRequiredException;
import com.google.gdata.util.AuthenticationException;

public class SmsSyncService extends Service {
    // TODO(chstuder): Move to Consts.
    /** Name of shared preference bundle containing settings for this service. */
    static final String SHARED_PREFS_NAME = "syncprefs";

    /** Type of messages which are drafts. They are not synced. */
    private static final int MESSAGE_TYPE_DRAFT = 3;

    /**
     * Preference key containing the maximum ID of messages that were
     * successfully synced.
     */
    private static final String PREF_MAX_SYNCED_ID = "max_synced_id";

    /** Preference key containing the Google account username. */
    static final String PREF_LOGIN_USER = "login_user";

    /** Preference key containin gthe Google account password. */
    static final String PREF_LOGIN_PASSWORD = "login_password";

    /** Number of messages sent per sync request. */
    private static final int MAX_MSG_PER_SYNC = 50;

    /** Preference key containing the "sync client" ID of this installation. */
    private static final String PREF_SYNC_CLIENT_ID = "sync_client_id";

    /** Flag indicating whether this service is already running. */
    private static boolean isRunning = false;

    // State information
    /** Current state. See {@link #getState()}. */
    private static SmsSyncState sState = SmsSyncState.IDLE;

    /**
     * Number of messages that currently need a sync. Only valid when sState ==
     * SYNC.
     */
    private static int sItemsToSync;

    /**
     * Number of messages already synced during this cycle. Only valid when
     * sState == SYNC.
     */
    private static int sCurrentSyncedItems;

    /**
     * Field containing a description of the last error. See
     * {@link #getErrorDescription()}.
     */
    private static String sLastError;

    /**
     * This {@link StateChangeListener} is notified whenever {@link #sState} is
     * updated.
     */
    private static StateChangeListener sStateChangeListener;

    public enum SmsSyncState {
        IDLE, REG, CALC, LOGIN, SYNC, AUTH_FAILED, GENERAL_ERROR, CAPTCHA_REQUIRED;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onStart(final Intent intent, int startId) {
        super.onStart(intent, startId);
        synchronized (this.getClass()) {
            // Only start a sync if there's no other sync going on at this time.
            if (!isRunning) {
                isRunning = true;
                // Start sync in new thread.
                new Thread() {
                    public void run() {
                        // Lower thread priority a little. We're not the UI.
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        try {
                            // On first sync we need to know whether to skip or
                            // sync current
                            // messages.
                            if (isFirstSync(SmsSyncService.this)
                                    && !intent.hasExtra(Consts.KEY_SKIP_MESSAGES)) {
                                throw new GeneralErrorException(SmsSyncService.this,
                                        R.string.err_first_sync_needs_skip_flag, null);
                            }
                            boolean skipMessages = intent.getBooleanExtra(Consts.KEY_SKIP_MESSAGES,
                                    false);
                            // Do the sync.
                            sync(skipMessages);
                        } catch (CaptchaRequiredException e) {
                            Log.i(Consts.TAG, "", e);
                            updateState(SmsSyncState.CAPTCHA_REQUIRED);
                        } catch (AuthenticationException e) {
                            Log.i(Consts.TAG, "", e);
                            updateState(SmsSyncState.AUTH_FAILED);
                        } catch (GeneralErrorException e) {
                            Log.i(Consts.TAG, "", e);
                            sLastError = e.getMessage();
                            updateState(SmsSyncState.GENERAL_ERROR);
                        } finally {
                            isRunning = false;
                            stopSelf();
                        }
                    }
                }.start();
            } else {
                Log.d(Consts.TAG, "SmsSyncService.onStart(): Already running.");
            }
        }
    }

    /**
     * <p>
     * This is the main method that defines the general flow for a
     * synchronization.
     * </p>
     * <h2>Typical flow</h2>
     * <p>
     * This is a typical sync flow (for <code>skipMessages == false</code>):
     * </p>
     * <ol>
     * <li>{@link SmsSyncState#CALC}: The list of messages requiring a sync is
     * determined. This is done by querying the SMS content provider for
     * messages with
     * <code>ID > {@link #getMaxSyncedId()} AND type != {@link #MESSAGE_TYPE_DRAFT}</code>
     * .</li>
     * <li>{@link SmsSyncState#LOGIN}: An auth token is obtained using the
     * <code>ClientLogin</code> authentication provided by Google. This is
     * performed over HTTPS and the username and password is <em>NOT</em> sent
     * to the android-sms server.</li>
     * <li>{@link SmsSyncState#LOGIN}: The token obtained in step #2 is sent to
     * the android-sms AppEngine server and traded for a cookie which can then
     * be used for all subsequent requests.</li>
     * <li>{@link SmsSyncState#REG}: If this instance of SMS Sync client app has
     * never before performed a sync, a registration is performed. The obtained
     * "sync client ID" is then used to identify this device in the future. This
     * step is skipped if this is not the first sync.</li>
     * <li>{@link SmsSyncState#SYNC}: The messages determined in step #1 are
     * sent to the server, possibly in chunks of a maximum of
     * {@link #MAX_MSG_PER_SYNC} per request. After each successful sync
     * request, the maximum ID of synced messages is updated such that future
     * syncs will skip.</li>
     * </ol>
     * 
     * <h2>Preconditions</h2>
     * <p>
     * This method requires the login information to be set. If either username
     * or password are unset, a {@link GeneralErrorException} is thrown.
     * </p>
     * 
     * <h2>Sync or skip?</h2>
     * <p>
     * <code>skipMessages</code>: If this parameter is <code>true</code>, all
     * current messages stored on the device are skipped and marked as "synced".
     * Future syncs will ignore these messages and only messages arrived
     * afterwards will be sent to the server.
     * </p>
     * 
     * @param skipMessages whether to skip all messages on this device.
     * @throws AuthenticationException Thrown when there was an error during
     *             login.
     * @throws GeneralErrorException Thrown when there there was an error during
     *             sync.
     */
    private void sync(boolean skipMessages) throws AuthenticationException, GeneralErrorException {
        Log.i(Consts.TAG, "Starting sync...");

        updateState(SmsSyncState.CALC);

        if (!isLoginInformationSet(this)) {
            throw new GeneralErrorException(this, R.string.err_sync_requires_login_info, null);
        }

        if (skipMessages) {
            // Only update the max synced ID, do not really sync.
            updateMaxSyncedId(getMaxItemId());
            updateState(SmsSyncState.IDLE);
            Log.i(Consts.TAG, "All messages skipped.");
            return;
        }

        sItemsToSync = 0;
        sCurrentSyncedItems = 0;

        Cursor items = getItemsToSync();
        sItemsToSync = items.getCount();
        Log.d(Consts.TAG, "Total messages to sync: " + sItemsToSync);
        if (sItemsToSync == 0) {
            updateState(SmsSyncState.IDLE);
            Log.d(Consts.TAG, "Nothing to do.");
            return;
        }

        // Splitting the items into batches of 'MAX_MSG_PER_SYNC' messages.
        HttpClient client = new DefaultHttpClient();

        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);

        String loginUser = prefs.getString(PREF_LOGIN_USER, null);
        String loginPassword = prefs.getString(PREF_LOGIN_PASSWORD, null);
        login(loginUser, loginPassword, client);
        while (true) {
            updateState(SmsSyncState.SYNC);
            try {
                JSONObject request = new JSONObject();
                addStaticProperties(request);
                request.put("syncClientId", getSyncClientId(client));
                JSONArray messages = CursorToJson.cursorToJsonArray(items, MAX_MSG_PER_SYNC);
                if (messages.length() == 0) {
                    Log.i(Consts.TAG, "Sync done: " + sCurrentSyncedItems + " items uploaded.");
                    updateState(SmsSyncState.IDLE);
                    break;
                }

                request.put("messages", messages);

                String requestStr = request.toString();
                HttpPost httpReq = new HttpPost(Consts.SYNC_URI);
                StringEntity se = new StringEntity(requestStr, "UTF-8");
                httpReq.setEntity(se);

                Log.d(Consts.TAG, "Sending " + messages.length() + " messages to server.");
                Log.v(Consts.TAG, requestStr);
                HttpResponse response = client.execute(httpReq);
                String responseJson = extractEntityBody(response);
                JSONObject jsonResponse = new JSONObject(responseJson);
                String maxConfirmedId = jsonResponse.getString("maxConfirmedId");
                updateMaxSyncedId(maxConfirmedId);
                sCurrentSyncedItems += messages.length();
                updateState(SmsSyncState.SYNC);
            } catch (JSONException e) {
                throw new GeneralErrorException(this, R.string.err_json_error, e);
            } catch (UnsupportedEncodingException e) {
                throw new GeneralErrorException(this, R.string.err_communication_error, e);
            } catch (ClientProtocolException e) {
                throw new GeneralErrorException(this, R.string.err_communication_error, e);
            } catch (IOException e) {
                throw new GeneralErrorException(this, R.string.err_io_error, e);
            } finally {
                // Close the cursor
                items.close();
            }
        }
    }

    private void login(String userEmail, String password, HttpClient client)
            throws AuthenticationException, GeneralErrorException {
        updateState(SmsSyncState.LOGIN);
        GoogleAuthTokenFactory factory = new GoogleAuthTokenFactory("ah", "chstuder-androidsms-1",
                null);
        String token = null;
        try {
            // Retrieve token from Google
            token = factory.getAuthToken(userEmail, password, null, null, "ah", Consts.APP_ID);
            String loginUri = Consts.LOGIN_URI.replace("%auth%", token);
            Log.d(Consts.TAG, loginUri);
            HttpGet cookieRequest = new HttpGet(loginUri);
            client.execute(cookieRequest);
        } catch (UnsupportedEncodingException e) {
            throw new GeneralErrorException(this, R.string.err_communication_error, e);
        } catch (ClientProtocolException e) {
            throw new GeneralErrorException(this, R.string.err_communication_error, e);
        } catch (IOException e) {
            throw new GeneralErrorException(this, R.string.err_io_error, e);
        }
    }

    private Object getSyncClientId(HttpClient httpClient) throws GeneralErrorException {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        String syncClientId = prefs.getString(PREF_SYNC_CLIENT_ID, null);
        if (syncClientId == null) {
            syncClientId = registerSyncClient(httpClient);
            Editor editor = prefs.edit();
            editor.putString(PREF_SYNC_CLIENT_ID, syncClientId);
            editor.commit();
        }
        return syncClientId;
    }

    /**
     * Registers a new "sync client" with the server and returns the ID.
     */
    private String registerSyncClient(HttpClient httpClient) throws GeneralErrorException {
        Log.i(Consts.TAG, "Registering...");
        updateState(SmsSyncState.REG);
        HttpPost httpReq = new HttpPost(Consts.REG_URI);
        try {
            HttpResponse response = httpClient.execute(httpReq);
            String responseStr = extractEntityBody(response);
            JSONObject jsonResponse = new JSONObject(responseStr);
            updateState(SmsSyncState.SYNC);
            String syncClientId = jsonResponse.getString("syncClientId");
            Log.i(Consts.TAG, "Registration done: " + syncClientId);
            return syncClientId;
        } catch (ClientProtocolException e) {
            throw new GeneralErrorException(this, R.string.err_communication_error, e);
        } catch (IOException e) {
            throw new GeneralErrorException(this, R.string.err_io_error, e);
        } catch (JSONException e) {
            throw new GeneralErrorException(this, R.string.err_json_error, e);
        }
    }

    /**
     * Returns a cursor of SMS messages that have not yet been synced with the
     * server. This includes all messages with
     * <code>ID &lt; {@link #getMaxSyncedId()}</code> which are no drafs.
     */
    private Cursor getItemsToSync() {
        ContentResolver r = getContentResolver();
        String selection = "_id > ? AND " + "type <> ?";
        String[] selectionArgs = new String[] {
                getMaxSyncedId(), String.valueOf(MESSAGE_TYPE_DRAFT)
        };
        return r.query(Uri.parse("content://sms"), null, selection, selectionArgs, "_id");
    }

    /**
     * Returns the maximum ID of all SMS messages (except for drafts).
     */
    private String getMaxItemId() {
        ContentResolver r = getContentResolver();
        String selection = "type <> ?";
        String[] selectionArgs = new String[] {
            String.valueOf(MESSAGE_TYPE_DRAFT)
        };
        String[] projection = new String[] {
            "_id"
        };
        Cursor result = r.query(Uri.parse("content://sms"), projection, selection, selectionArgs,
                "_id DESC");
        if (result.moveToFirst()) {
            return result.getString(0);
        } else {
            return "-1";
        }
    }

    /**
     * Extracts the HTTPResponse's entity body as a String. This reads from the
     * {@link HttpEntity#getContent()} {@link InputStream} until EOF and returns
     * the contents.
     * 
     * @param response
     * @return
     * @throws IllegalStateException
     * @throws IOException
     */
    private String extractEntityBody(HttpResponse response) throws IllegalStateException,
            IOException {
        InputStream responseStream = response.getEntity().getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
        String line;
        StringBuilder builder = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    /**
     * Returns the largest ID of all messages that have successfully been synced
     * with the server.
     * 
     * @return
     */
    private String getMaxSyncedId() {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_MAX_SYNCED_ID, "-1");
    }

    /**
     * Persists the provided ID so it can later on be retrieved using
     * {@link #getMaxSyncedId()}. This should be called when after each
     * successful sync request to a server.
     * 
     * @param maxSyncedId
     */
    private void updateMaxSyncedId(String maxSyncedId) {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        Editor editor = prefs.edit();
        editor.putString(PREF_MAX_SYNCED_ID, maxSyncedId);
        editor.commit();
        Log.d(Consts.TAG, "Max synced ID set to: " + maxSyncedId);
    }

    private static void addStaticProperties(JSONObject request) throws JSONException {
        request.put("appVersion", Consts.APP_VERSION);
    }

    // Statistics accessible from other classes.

    /**
     * Returns the current state of the service. Also see
     * {@link #setStateChangeListener(StateChangeListener)} to get notified when
     * the state changes.
     */
    static SmsSyncState getState() {
        return sState;
    }

    /**
     * Returns a description of the last error. Only valid if
     * <code>{@link #getState()} == {@link SmsSyncState#GENERAL_ERROR}</code>.
     */
    static String getErrorDescription() {
        return (sState == SmsSyncState.GENERAL_ERROR) ? sLastError : null;
    }

    /**
     * Returns the number of messages that require sync during the current
     * cycle. Only valid if
     * <code>{@link #getState()} == {@link SmsSyncState#SYNC}</code>.
     */
    static int getItemsToSyncCount() {
        return (sState == SmsSyncState.SYNC) ? sItemsToSync : 0;
    }

    /**
     * Returns the number of already synced messages during the current cycle.
     * Only valid if
     * <code>{@link #getState()} == {@link SmsSyncState#SYNC}</code>.
     */
    static int getCurrentSyncedItems() {
        return (sState == SmsSyncState.SYNC) ? sCurrentSyncedItems : 0;
    }

    /**
     * Returns <code>true</code> iff there were no previous syncs. This method
     * is handy if some special inputs are required for the first sync.
     */
    static boolean isFirstSync(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_MAX_SYNCED_ID, null) == null;
    }

    // Login information related methods.

    /**
     * Returns whether all required login information is available.
     */
    static boolean isLoginInformationSet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_LOGIN_USER, null) != null
                && prefs.getString(PREF_LOGIN_PASSWORD, null) != null;
    }
    
    /**
     * Resets all sync data. This is required after changing the username.
     */
    static void resetSyncData(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);
        Editor editor = prefs.edit();
        editor.remove(PREF_LOGIN_PASSWORD);
        editor.remove(PREF_MAX_SYNCED_ID);
        editor.remove(PREF_SYNC_CLIENT_ID);
        editor.commit();
    }

    /**
     * Registers a {@link StateChangeListener} that is notified whenever the
     * state of the service changes. Note that at most one listener can be
     * registered and you need to call {@link #unsetStateChangeListener()} in
     * between calls to this method.
     * 
     * @see #getState()
     * @see #unsetStateChangeListener()
     */
    static void setStateChangeListener(StateChangeListener listener) {
        if (sStateChangeListener != null) {
            throw new IllegalStateException("setStateChangeListener(...) called when there"
                    + " was still some other listener "
                    + "registered. Use unsetStateChangeListener() first.");
        }
        sStateChangeListener = listener;
    }

    /**
     * Unregisters the currently registered {@link StateChangeListener}.
     * 
     * @see #setStateChangeListener(StateChangeListener)
     */
    static void unsetStateChangeListener() {
        sStateChangeListener = null;
    }

    /**
     * Internal method that needs to be called whenever the state of the service
     * changes.
     */
    private static void updateState(SmsSyncState newState) {
        SmsSyncState old = sState;
        sState = newState;
        if (sStateChangeListener != null) {
            Log.d(Consts.TAG, "Calling stateChanged()");
            sStateChangeListener.stateChanged(old, newState);
        }
    }

    /**
     * A state change listener interface that provides a callback that is called
     * whenever the state of the {@link SmsSyncService} changes.
     * 
     * @see SmsSyncService#setStateChangeListener(StateChangeListener)
     */
    public interface StateChangeListener {
        /**
         * Called whenever the sync state of the service changed.
         */
        public void stateChanged(SmsSyncState oldState, SmsSyncState newState);
    }

    /**
     * Exception indicating an error while synchronizing.
     */
    public static class GeneralErrorException extends Exception {
        private static final long serialVersionUID = 1L;

        public GeneralErrorException(Context ctx, int msgId, Throwable t) {
            super(ctx.getString(msgId), t);
        }
    }

}
