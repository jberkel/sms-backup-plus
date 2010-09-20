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

package com.zegoggles.smssync;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Process;
import android.util.Log;
import android.os.AsyncTask;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.CursorToMessage.ConversionResult;
import com.zegoggles.smssync.ServiceBase.SmsSyncState;
import com.zegoggles.smssync.R;

import java.util.List;

import static com.zegoggles.smssync.ServiceBase.SmsSyncState.*;

public class SmsBackupService extends ServiceBase {
    public static final String TAG = SmsBackupService.class.getName();

    /** Number of messages sent per sync request. */
    private static final int MAX_MSG_PER_REQUEST = 1;

    /** Flag indicating whether this service is already running. */
    private static boolean sIsRunning = false;

    private static SmsSyncState sState = SmsSyncState.IDLE;
    public static SmsSyncState getState() { return sState; }

    /** Number of messages that currently need a sync. */
    private static int sItemsToSync;

    /** Number of messages already synced during this cycle.  */
    private static int sCurrentSyncedItems;

    /**
     * Indicates that the user canceled the current backup and that this service
     * should finish working ASAP.
     */
    private static boolean sCanceled;

    @Override
    public void onStart(final Intent intent, int startId) {
        super.onStart(intent, startId);
        boolean background = intent.hasExtra(Consts.KEY_NUM_RETRIES);

        if (background && !getConnectivityManager().getBackgroundDataSetting()) {
            Log.d(TAG, "onStart(): Background data disabled");

            stopSelf();
            return;
        }

        synchronized(ServiceBase.class) {
          // Only start a sync if there's no other sync / restore going on at this time.
          if (!sIsRunning && !SmsRestoreService.isWorking()) {
            sIsRunning = true;
            new BackupTask().execute(intent);
          }
        }
    }


    /** BackupTask does all the work */
    class BackupTask extends AsyncTask<Intent, SmsSyncState, Integer>
    {
      private Exception ex;
      private android.content.Context context = SmsBackupService.this;

         protected java.lang.Integer doInBackground(Intent... params) {
            Intent intent = params[0];

            if (intent.getBooleanExtra(Consts.KEY_SKIP_MESSAGES, false)) {
               return skip();
            }

            try {
              if (!PrefStore.isLoginInformationSet(context)) {
                 throw new GeneralErrorException(getString(R.string.err_sync_requires_login_info));
              }

              acquireLocks();

              sCanceled = false;
              publishProgress(LOGIN);
              Folder folder = getBackupFolder();

              return backup(folder);

            } catch (AuthenticationErrorException authError) {
              publishProgress(AUTH_FAILED);
              return null;
            } catch (GeneralErrorException e) {
              Log.e(TAG, "error during backup", e);
              lastError = e.getMessage();
              publishProgress(GENERAL_ERROR);
              this.ex = e;
              return null;
            } finally {
              releaseLocks();
              stopSelf();
              Alarms.scheduleRegularSync(context);
              sIsRunning = false;
              sCanceled = false;
            }
          }

        @Override
        protected void onProgressUpdate(SmsSyncState... progress) {
          smsSync.statusPref.stateChanged(sState, progress[0]);
          sState = progress[0];
        }

        @Override
        protected void onPostExecute(Integer result) {
          if (result != null) {
            Log.d(TAG, result + " items backed up");
          }
        }

      /**
       * @throws GeneralErrorException Thrown when there there was an error during sync.
       * @throws FolderErrorException Thrown when there was an error accessing or creating the folder
       */
      private int backup(final Folder folder) throws GeneralErrorException {
          Log.i(TAG, "Starting backup...");

          publishProgress(CALC);

          sItemsToSync = 0;
          sCurrentSyncedItems = 0;

          Cursor items = getItemsToSync();
          int maxItemsPerSync = PrefStore.getMaxItemsPerSync(context);
          sItemsToSync = maxItemsPerSync > 0 ? Math.min(items.getCount(), maxItemsPerSync) : items.getCount();

          if (sItemsToSync <= 0) {
              PrefStore.setLastSync(context);
              if (PrefStore.isFirstSync(context)) {
                  // If this is the first backup we need to write something to PREF_MAX_SYNCED_DATE
                  // such that we know that we've performed a backup before.
                  PrefStore.setMaxSyncedDate(context, PrefStore.DEFAULT_MAX_SYNCED_DATE);
              }
              publishProgress(IDLE);
              Log.d(TAG, "Nothing to do.");
              return 0;
          }

          Log.d(TAG, "Total messages to backup: " + sItemsToSync);

          CursorToMessage converter = new CursorToMessage(context, PrefStore.getLoginUsername(context));
          try {
              while (true) {
                  // Cancel sync if requested by the user.
                  if (sCanceled) {
                      Log.i(TAG, "Backup canceled by user.");
                      // TODO: close IMAP ?
                      sCanceled = false;
                      publishProgress(CANCELED);
                      break;
                  }
                  publishProgress(SYNC);
                  ConversionResult result = converter.cursorToMessageArray(items, MAX_MSG_PER_REQUEST);
                  List<Message> messages = result.messageList;
                  // Stop the sync if all items where uploaded or if the maximum number
                  // of messages per sync was uploaded.
                  if (messages.isEmpty() || sCurrentSyncedItems >= sItemsToSync) {
                      Log.i(TAG, "Sync done: " + sCurrentSyncedItems + " items uploaded.");
                      PrefStore.setLastSync(context);
                      publishProgress(IDLE);
                      folder.close();
                      break;
                  }

                  Log.d(TAG, "Sending " + messages.size() + " messages to server.");
                  folder.appendMessages(messages.toArray(new Message[messages.size()]));
                  sCurrentSyncedItems += messages.size();
                  publishProgress(SYNC);
                  updateMaxSyncedDate(result.maxDate);
                  result = null;
                  messages = null;
              }
              return sCurrentSyncedItems;
          } catch (MessagingException e) {
              throw new GeneralErrorException(getString(R.string.err_communication_error));
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
          if (PrefStore.getMaxItemsPerSync(context) > 0) {
            sortOrder += " LIMIT " + PrefStore.getMaxItemsPerSync(context);
          }
          return getContentResolver().query(SMS_PROVIDER, null,
                String.format("%s > ? AND %s <> ?", SmsConsts.DATE, SmsConsts.TYPE),
                new String[] { String.valueOf(getMaxSyncedDate()), String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT) },
                sortOrder);
      }

      public int skip() {
          // Only update the max synced ID, do not really sync.
          updateMaxSyncedDate(getMaxItemDate());
          PrefStore.setLastSync(context);
          sItemsToSync = 0;
          sCurrentSyncedItems = 0;
          sIsRunning = false;
          publishProgress(IDLE);
          Log.i(TAG, "All messages skipped.");
          return 0;
      }
    }
    /**
     * Cancels the current ongoing backup.
    */
    static void cancel() {
        if (SmsBackupService.sIsRunning) {
            SmsBackupService.sCanceled = true;
        }
    }

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
}
