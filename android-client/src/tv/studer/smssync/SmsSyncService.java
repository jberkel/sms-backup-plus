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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.google.gdata.client.GoogleAuthTokenFactory;
import com.google.gdata.client.GoogleService.CaptchaRequiredException;
import com.google.gdata.util.AuthenticationException;

public class SmsSyncService extends Service {
    private static final String SHARED_PREFS_NAME = "syncprefs";

    private static final int MESSAGE_TYPE_DRAFT = 3;

    private static final String PREF_MAX_SYNCED_ID = "max_synced_id";

    private static final int MAX_MSG_PER_SYNC = 50;

    private static final String PREF_SYNC_CLIENT_ID = "sync_client_id";

    private static boolean isRunning = false;

    // State information
    private static SmsSyncState state = SmsSyncState.IDLE;

    private static int itemsToSync;

    private static int currentSyncedItems;

    private static StateChangeListener stateChangeListener;

    public enum SmsSyncState {
        IDLE, REG, CALC, LOGIN, SYNC, AUTH_FAILED, GENERAL_ERROR, CAPTCHA_REQUIRED;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        synchronized (this.getClass()) {
            if (!isRunning) {
                isRunning = true;
                new Thread() {
                    public void run() {
                        try {
                            sync();
                        } catch (CaptchaRequiredException e) {
                            Log.i(Consts.TAG, "", e);
                            updateState(SmsSyncState.CAPTCHA_REQUIRED);
                        } catch (AuthenticationException e) {
                            Log.i(Consts.TAG, "", e);
                            updateState(SmsSyncState.AUTH_FAILED);
                        } catch (GeneralErrorException e) {
                            Log.i(Consts.TAG, "", e);
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

    private void sync() throws AuthenticationException, GeneralErrorException {
        Log.i(Consts.TAG, "Starting sync...");

        itemsToSync = 0;
        currentSyncedItems = 0;
        updateState(SmsSyncState.CALC);

        Cursor items = getItemsToSync();
        itemsToSync = items.getCount();
        Log.d(Consts.TAG, "Total messages to sync: " + itemsToSync);
        if (itemsToSync == 0) {
            updateState(SmsSyncState.IDLE);
            Log.d(Consts.TAG, "Nothing to do.");
            return;
        }

        // Splitting the items into batches of 'MAX_MSG_PER_SYNC' messages.
        HttpClient client = new DefaultHttpClient();
        // TODO(chstuder): Retrieve login from shared prefs.
        login(Consts.LOGIN_USER, Consts.LOGIN_PASSWORD, client);
        while (true) {
            updateState(SmsSyncState.SYNC);
            try {
                JSONObject request = new JSONObject();
                addStaticProperties(request);
                request.put("syncClientId", getSyncClientId(client));
                JSONArray messages = CursorToJson.cursorToJsonArray(items, MAX_MSG_PER_SYNC);
                if (messages.length() == 0) {
                    Log.i(Consts.TAG, "Sync done: " + currentSyncedItems + " items uploaded.");
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
                currentSyncedItems += messages.length();
                updateState(SmsSyncState.SYNC);
            } catch (JSONException e) {
                throw new GeneralErrorException("Could not decode server response.", e);
            } catch (UnsupportedEncodingException e) {
                throw new GeneralErrorException("General communication error.", e);
            } catch (ClientProtocolException e) {
                throw new GeneralErrorException("General communication error.", e);
            } catch (IOException e) {
                throw new GeneralErrorException("Connection error.", e);
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
            throw new GeneralErrorException("UnsupportedEncodingException problem.", e);
        } catch (ClientProtocolException e) {
            throw new GeneralErrorException("ClientProtocolException problem.", e);
        } catch (IOException e) {
            throw new GeneralErrorException("Connection problem.", e);
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
        } catch (Exception e) {
            throw new GeneralErrorException("Device registration failed.", null);
        }
    }

    /**
     * Returns a cursor of SMS messages that have not yet been synced with the
     * server.
     * 
     * @return
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
    public static SmsSyncState getState() {
        return state;
    }

    public static int getItemsToSyncCount() {
        return (state == SmsSyncState.SYNC) ? itemsToSync : 0;
    }

    public static int getCurrentSyncedItems() {
        return (state == SmsSyncState.SYNC) ? currentSyncedItems : 0;
    }

    private static void updateState(SmsSyncState newState) {
        SmsSyncState old = state;
        state = newState;
        if (stateChangeListener != null) {
            Log.d(Consts.TAG, "Calling stateChanged()");
            stateChangeListener.stateChanged(old, newState);
        }
    }

    public static void setStateChangeListener(StateChangeListener listener) {
        if (stateChangeListener != null) {
            throw new IllegalStateException("setStateChangeListener called when there"
                    + " was still some other listener "
                    + "registered. Use unsetStateChangeListener() first.");
        }
        stateChangeListener = listener;
    }

    public static void unsetStateChangeListener() {
        stateChangeListener = null;
    }

    public interface PropertiesProvider {
        public String getAid();

        public String getInstallationId();

        public long getSmsSyncVersion();
    }

    public static class GeneralErrorException extends Exception {
        private static final long serialVersionUID = 1L;

        public GeneralErrorException(String msg, Throwable t) {
            super(msg, t);
        }
    }

    public interface StateChangeListener {
        /**
         * Called whenever the state of the service changed.
         */
        public void stateChanged(SmsSyncState oldState, SmsSyncState newState);
    }
}
