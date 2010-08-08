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

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Process;
import android.util.Log;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import tv.studer.smssync.CursorToMessage.ConversionResult;
import tv.studer.smssync.ServiceBase.SmsSyncState;
import com.zegoggles.smssync.R;

import java.util.List;

public class SmsSyncService extends ServiceBase {

    /** Number of messages sent per sync request. */
    private static final int MAX_MSG_PER_REQUEST = 1;

    /** Flag indicating whether this service is already running. */
    // Should this be split into sIsRunning and sIsWorking? One for the
    // service, the other for the actual backing up?
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

    /**
     * Indicates that the user canceled the current backup and that this service
     * should finish working ASAP.
     */
    private static boolean sCanceled;


    @Override
    //TODO(chstuder): Clean this flow up a bit and split it into multiple
    // methods. Make clean distinction between onStart(...) and backup(...).
    public void onStart(final Intent intent, int startId) {
        super.onStart(intent, startId);
        boolean background = intent.hasExtra(Consts.KEY_NUM_RETRIES);

        if (background && !getConnectivityManager().getBackgroundDataSetting()) {
            Log.d(Consts.TAG, "SmsSyncService.onStart(): Background data disabled");
            return;
        }

        synchronized(ServiceBase.class) {
            // Only start a sync if there's no other sync / restore going on at this time.
            if (!sIsRunning && !SmsRestoreService.isWorking()) {
                acquireWakeLock();
                sIsRunning = true;
                // TODO use AsyncTask
                // Start sync in new thread
                new Thread() {
                    @Override
                    public void run() {
                        // Lower thread priority a little. We're not the UI.
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                        try {
                            // On first sync we need to know whether to skip or
                            // sync current messages.
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
                                    backup(skipMessages);
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
                        } catch (FolderErrorException e) {
                            Log.i(Consts.TAG, "", e);
                            sLastError = e.getLocalizedMessage();
                            updateState(SmsSyncState.FOLDER_ERROR);
                        } finally {
                            stopSelf();
                            Alarms.scheduleRegularSync(SmsSyncService.this);
                            sIsRunning = false;
                            sCanceled = false;
                            releaseWakeLock();
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
     * <li>{@link SmsSyncState#CANCELED}: If {@link #cancel()} was called during
     * backup, the backup will stop at the next possible occasion.</li>
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
     * Future backups will ignore these messages and only messages arrived
     * afterwards will be sent to the server.
     * </p>
     *
     * @param skipMessages whether to skip all messages on this device.
     * @throws GeneralErrorException Thrown when there there was an error during
     *             sync.
     * @throws FolderErrorException Thrown when there was an error accessing or creating the folder
     */
    private void backup(boolean skipMessages) throws GeneralErrorException,
            AuthenticationErrorException, FolderErrorException {
        Log.i(Consts.TAG, "Starting backup...");
        sCanceled = false;

        if (!PrefStore.isLoginInformationSet(this)) {
            throw new GeneralErrorException(this, R.string.err_sync_requires_login_info, null);
        }

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
        int maxItemsPerSync = PrefStore.getMaxItemsPerSync(this);
        sItemsToSync = maxItemsPerSync > 0 ? Math.min(items.getCount(), maxItemsPerSync) : items.getCount();

        if (sItemsToSync <= 0) {
            PrefStore.setLastSync(this);
            if (PrefStore.isFirstSync(this)) {
                // If this is the first backup we need to write something to PREF_MAX_SYNCED_DATE
                // such that we know that we've performed a backup before.
                PrefStore.setMaxSyncedDate(this, PrefStore.DEFAULT_MAX_SYNCED_DATE);
            }
            updateState(SmsSyncState.IDLE);
            Log.d(Consts.TAG, "Nothing to do.");
            return;
        }

        Log.d(Consts.TAG, "Total messages to backup: " + sItemsToSync);

        updateState(SmsSyncState.LOGIN);
        Folder folder = getBackupFolder();

        CursorToMessage converter = new CursorToMessage(this, PrefStore.getLoginUsername(this));
        try {
            while (true) {
                // Cancel sync if requested by the user.
                if (sCanceled) {
                    Log.i(Consts.TAG, "Backup canceled by user.");
                    // TODO: close IMAP ?
                    sCanceled = false;
                    updateState(SmsSyncState.CANCELED);
                    break;
                }
                updateState(SmsSyncState.SYNC);
                ConversionResult result = converter.cursorToMessageArray(items, MAX_MSG_PER_REQUEST);
                List<Message> messages = result.messageList;
                // Stop the sync if all items where uploaded or if the maximum number
                // of messages per sync was uploaded.
                if (messages.isEmpty() || sCurrentSyncedItems >= sItemsToSync) {
                    Log.i(Consts.TAG, "Sync done: " + sCurrentSyncedItems + " items uploaded.");
                    PrefStore.setLastSync(SmsSyncService.this);
                    updateState(SmsSyncState.IDLE);
                    folder.close();
                    break;
                }

                Log.d(Consts.TAG, "Sending " + messages.size() + " messages to server.");
                folder.appendMessages(messages.toArray(new Message[messages.size()]));
                sCurrentSyncedItems += messages.size();
                updateState(SmsSyncState.SYNC);
                updateMaxSyncedDate(result.maxDate);
                result = null;
                messages = null;
            }
        } catch (MessagingException e) {
            throw new GeneralErrorException(this, R.string.err_communication_error, e);
        } finally {
            items.close();
        }
    }




    /**
     * Returns a cursor of SMS messages that have not yet been synced with the
     * server. This includes all messages with
     * <code>date &lt; {@link #getMaxSyncedDate()}</code> which are no drafs.
     */
    private Cursor getItemsToSync() {
        String sortOrder = SmsConsts.DATE;
        if (PrefStore.getMaxItemsPerSync(this) > 0) {
          sortOrder += " LIMIT " + PrefStore.getMaxItemsPerSync(this);
        }
        return getContentResolver().query(SMS_PROVIDER, null,
              String.format("%s > ? AND %s <> ?", SmsConsts.DATE, SmsConsts.TYPE),
              new String[] { String.valueOf(getMaxSyncedDate()), String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT) },
              sortOrder);
    }

    /**
     * Cancels the current ongoing backup.
     *
     * TODO(chstuder): Clean up this interface a bit. It's strange the backup is
     * started by an intent but canceling is done through a static method.
     *
     * But all other alternatives seem strange too. An intent just to cancel a backup?
     */
    static void cancel() {
        if (SmsSyncService.sIsRunning) {
            SmsSyncService.sCanceled = true;
        }
    }

    // Statistics accessible from other classes.

    /**
     * Returns whether there is currently a backup going on or not.
     *
     */
    static boolean isWorking() {
        return sIsRunning;
    }

     public static boolean isCancelling() {
        return sCanceled;
    }
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



    public static class FolderErrorException extends Exception {
        private static final long serialVersionUID = 1L;

        public FolderErrorException(Throwable t) {
            super(t.getLocalizedMessage(), t);
        }
    }
}
