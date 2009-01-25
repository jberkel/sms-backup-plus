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

import java.net.URLEncoder;
import java.util.List;

import tv.studer.smssync.CursorToMessage.ConversionResult;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.android.email.mail.Folder;
import com.android.email.mail.Message;
import com.android.email.mail.MessagingException;
import com.android.email.mail.Folder.FolderType;
import com.android.email.mail.Folder.OpenMode;
import com.android.email.mail.store.ImapStore;

public class SmsSyncService extends Service {

    /** Number of messages sent per sync request. */
    private static final int MAX_MSG_PER_REQUEST = 1;
    
    /** Flag indicating whether this service is already running. */
    private static boolean sIsRunning = false;

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

    private static WakeLock sWakeLock;
    
    public enum SmsSyncState {
        IDLE, CALC, LOGIN, SYNC, AUTH_FAILED, GENERAL_ERROR;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    
    private static void acquireWakeLock(Context ctx) {
        if (sWakeLock == null) {
            PowerManager pMgr = (PowerManager) ctx.getSystemService(POWER_SERVICE);
            sWakeLock = pMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "SmsSyncService.sync() wakelock.");
        }
        sWakeLock.acquire();
    }
    
    private static void releaseWakeLock(Context ctx) {
        sWakeLock.release();
    }
    
    @Override
    public void onStart(final Intent intent, int startId) {
        super.onStart(intent, startId);
        
        synchronized (this.getClass()) {
            // Only start a sync if there's no other sync going on at this time.
            if (!sIsRunning) {
                acquireWakeLock(this);
                sIsRunning = true;
                // Start sync in new thread.
                new Thread() {
                    public void run() {
                        // Lower thread priority a little. We're not the UI.
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        try {
                            // On first sync we need to know whether to skip or
                            // sync current
                            // messages.
                            if (PrefStore.isFirstSync(SmsSyncService.this)
                                    && !intent.hasExtra(Consts.KEY_SKIP_MESSAGES)) {
                                throw new GeneralErrorException(SmsSyncService.this,
                                        R.string.err_first_sync_needs_skip_flag, null);
                            }
                            boolean skipMessages = intent.getBooleanExtra(Consts.KEY_SKIP_MESSAGES,
                                    false);
                            int numRetries = intent.getIntExtra(Consts.KEY_NUM_RETRIES, 0);
                            GeneralErrorException lastException = null;
                            
                            // Try sync numRetries + 1 times.
                            while (numRetries >= 0) {
                                try {
                                    sync(skipMessages);
                                    break;
                                } catch (GeneralErrorException e) {
                                    Log.w(Consts.TAG, e.getMessage());
                                    Log.i(Consts.TAG, "Retrying sync in 2 seconds. (" + (numRetries - 1) +  ")");
                                    lastException = e;
                                    if (numRetries > 1) {
                                        try {
                                            Thread.sleep(2000);
                                        } catch (InterruptedException e1) { /* ignore */ }
                                    }
                                }
                                numRetries--;
                            }
                            if (lastException != null) {
                                throw lastException;
                            }
                        } catch (GeneralErrorException e) {
                            Log.i(Consts.TAG, "", e);
                            sLastError = e.getLocalizedMessage();
                            updateState(SmsSyncState.GENERAL_ERROR);
                        } catch (AuthenticationErrorException e) {
                            Log.i(Consts.TAG, "", e);
                            sLastError = e.getLocalizedMessage();
                            updateState(SmsSyncState.AUTH_FAILED);
                        } finally {
                            stopSelf();
                            Alarms.scheduleRegularSync(SmsSyncService.this);
                            sIsRunning = false;
                            releaseWakeLock(SmsSyncService.this);
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
     * <code>ID > {@link #getMaxSyncedDate()} AND type != {@link #MESSAGE_TYPE_DRAFT}</code>
     * .</li>
     * <li>{@link SmsSyncState#LOGIN}: An SSL connection is opened to the Gmail IMAP
     * server using the user provided credentials.</li>
     * <li>{@link SmsSyncState#SYNC}: The messages determined in step #1 are
     * sent to the server, possibly in chunks of a maximum of
     * {@link #MAX_MSG_PER_REQUEST} per request. After each successful sync
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
     * @throws GeneralErrorException Thrown when there there was an error during
     *             sync.
     */
    private void sync(boolean skipMessages) throws GeneralErrorException,
            AuthenticationErrorException {
        Log.i(Consts.TAG, "Starting sync...");

        if (!PrefStore.isLoginInformationSet(this)) {
            throw new GeneralErrorException(this, R.string.err_sync_requires_login_info, null);
        }

        String username = PrefStore.getLoginUsername(this);
        String password = PrefStore.getLoginPassword(this);

        updateState(SmsSyncState.CALC);

        sItemsToSync = 0;
        sCurrentSyncedItems = 0;
        
        if (skipMessages) {
            // Only update the max synced ID, do not really sync.
            updateMaxSyncedDate(getMaxItemDate());
            PrefStore.setLastSync(this);
            sItemsToSync = 0;
            sCurrentSyncedItems = 0;
            updateState(SmsSyncState.IDLE);
            Log.i(Consts.TAG, "All messages skipped.");
            return;
        }

        Cursor items = getItemsToSync();
        sItemsToSync = Math.min(items.getCount(), Consts.MAX_MSG_PER_SYNC);
        Log.d(Consts.TAG, "Total messages to sync: " + sItemsToSync);
        if (sItemsToSync == 0) {
            PrefStore.setLastSync(this);
            updateState(SmsSyncState.IDLE);
            Log.d(Consts.TAG, "Nothing to do.");
            return;
        }

        updateState(SmsSyncState.LOGIN);

        ImapStore imapStore;
        Folder folder;
        boolean folderExists;
        String label = PrefStore.getImapFolder(this);
        try {
            imapStore = new ImapStore(String.format(Consts.IMAP_URI, URLEncoder.encode(username),
                    URLEncoder.encode(password)));
            folder = imapStore.getFolder(label);
            folderExists = folder.exists();
            if (!folderExists) {
                Log.i(Consts.TAG, "Label '" + label + "' does not exist yet. Creating.");
                folder.create(FolderType.HOLDS_MESSAGES);
            }
            folder.open(OpenMode.READ_WRITE);
        } catch (MessagingException e) {
            throw new AuthenticationErrorException(e);
        }
        
        CursorToMessage converter = new CursorToMessage(this, username);
        while (true) {
            updateState(SmsSyncState.SYNC);
            try {
                ConversionResult result = converter.cursorToMessageArray(items,
                        MAX_MSG_PER_REQUEST);
                List<Message> messages = result.messageList;
                // Stop the sync if all items where uploaded or if the maximum number
                // of messages per sync was uploaded.
                if (messages.size() == 0
                        || sCurrentSyncedItems >= Consts.MAX_MSG_PER_SYNC) {
                    Log.i(Consts.TAG, "Sync done: " + sCurrentSyncedItems + " items uploaded.");
                    PrefStore.setLastSync(SmsSyncService.this);
                    updateState(SmsSyncState.IDLE);
                    folder.close(true);
                    break;
                }

                Log.d(Consts.TAG, "Sending " + messages.size() + " messages to server.");
                folder.appendMessages(messages.toArray(new Message[messages.size()]));
                sCurrentSyncedItems += messages.size();
                updateState(SmsSyncState.SYNC);
                updateMaxSyncedDate(result.maxDate);
                result = null;
                messages = null;
            } catch (MessagingException e) {
                throw new GeneralErrorException(this, R.string.err_communication_error, e);
            } finally {
                // Close the cursor
                items.close();
            }
        }
    }

    /**
     * Returns a cursor of SMS messages that have not yet been synced with the
     * server. This includes all messages with
     * <code>ID &lt; {@link #getMaxSyncedDate()}</code> which are no drafs.
     */
    private Cursor getItemsToSync() {
        ContentResolver r = getContentResolver();
        String selection = String.format("%s > ? AND %s <> ?",
                SmsConsts.DATE, SmsConsts.TYPE);
        String[] selectionArgs = new String[] {
                String.valueOf(getMaxSyncedDate()), String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT)
        };
        String sortOrder = SmsConsts.DATE;
        return r.query(Uri.parse("content://sms"), null, selection, selectionArgs, sortOrder);
    }

    /**
     * Returns the maximum date of all SMS messages (except for drafts).
     */
    private long getMaxItemDate() {
        ContentResolver r = getContentResolver();
        String selection = SmsConsts.TYPE + " <> ?";
        String[] selectionArgs = new String[] {
            String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT)
        };
        String[] projection = new String[] {
            SmsConsts.DATE
        };
        Cursor result = r.query(Uri.parse("content://sms"), projection, selection, selectionArgs,
                SmsConsts.DATE + " DESC");
        if (result.moveToFirst()) {
            return result.getLong(0);
        } else {
            return PrefStore.DEFAULT_MAX_SYNCED_DATE;
        }
    }

    /**
     * Returns the largest date of all messages that have successfully been synced
     * with the server.
     */
    private long getMaxSyncedDate() {
        return PrefStore.getMaxSyncedDate(this);
    }

    /**
     * Persists the provided ID so it can later on be retrieved using
     * {@link #getMaxSyncedDate()}. This should be called when after each
     * successful sync request to a server.
     * 
     * @param maxSyncedId
     */
    private void updateMaxSyncedDate(long maxSyncedDate) {
        PrefStore.setMaxSyncedDate(this, maxSyncedDate);
        Log.d(Consts.TAG, "Max synced date set to: " + maxSyncedDate);
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
     * cycle.
     */
    static int getItemsToSyncCount() {
        return sItemsToSync;
    }

    /**
     * Returns the number of already synced messages during the current cycle.
     */
    static int getCurrentSyncedItems() {
        return sCurrentSyncedItems;
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

        public GeneralErrorException(String msg, Throwable t) {
            super(msg, t);
        }
        
        public GeneralErrorException(Context ctx, int msgId, Throwable t) {
            super(ctx.getString(msgId), t);
        }
    }
    
    public static class AuthenticationErrorException extends Exception {
        private static final long serialVersionUID = 1L;

        public AuthenticationErrorException(Throwable t) {
            super(t.getLocalizedMessage(), t);
        }
    }

}
