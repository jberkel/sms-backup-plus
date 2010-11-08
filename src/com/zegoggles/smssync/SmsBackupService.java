/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
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
import android.database.MatrixCursor;
import android.os.Process;
import android.util.Log;
import android.os.AsyncTask;
import android.provider.CallLog;

import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.zegoggles.smssync.CursorToMessage.ConversionResult;
import com.zegoggles.smssync.CursorToMessage.DataType;
import com.zegoggles.smssync.ServiceBase.SmsSyncState;
import com.zegoggles.smssync.R;

import java.util.List;
import java.io.IOException;

import static com.zegoggles.smssync.ServiceBase.SmsSyncState.*;
import static com.zegoggles.smssync.App.*;

public class SmsBackupService extends ServiceBase {
    public static final String TAG = SmsBackupService.class.getSimpleName();

    /** Number of messages sent per sync request. */
    /** Changing this value will cause mms/sms messages to thread out of order. */
    private static final int MAX_MSG_PER_REQUEST = 1;

    /** Flag indicating whether this service is already running. */
    private static boolean sIsRunning = false;


    /** Number of messages that currently need a sync. */
    private static int sItemsToSync;
    private static int sItemsToSyncSms;
    private static int sItemsToSyncMms;
    private static int sItemsToSyncCalllog;

    /** Number of messages already synced during this cycle.  */
    private static int sCurrentSyncedItems;

    /**
     * Indicates that the user canceled the current backup and that this service
     * should finish working ASAP.
     */
    private static boolean sCanceled;

    private boolean isBackground(final Intent intent) {
      return intent.hasExtra(Consts.KEY_NUM_RETRIES);
    }

    @Override
    public void onStart(final Intent intent, int startId) {
        super.onStart(intent, startId);

        if (isBackground(intent) && !getConnectivityManager().getBackgroundDataSetting()) {
            Log.d(TAG, "background data disabled");
            stopSelf();
        } else {
          synchronized(ServiceBase.class) {
            // Only start a sync if there's no other sync / restore going on at this time.
            if (!sIsRunning && !SmsRestoreService.isWorking()) {
              sIsRunning = true;
              new BackupTask().execute(intent);
            }
          }
        }
    }


    /** BackupTask does all the work */
    class BackupTask extends AsyncTask<Intent, SmsSyncState, Integer>
    {
        private Exception ex;
        private android.content.Context context = SmsBackupService.this;
        private int maxItemsPerSync = PrefStore.getMaxItemsPerSync(context);
        private boolean background;

        @Override
        protected void onPreExecute () {
        }

        @Override
        protected java.lang.Integer doInBackground(Intent... params) {
            final Intent intent = params[0];
            this.background = isBackground(intent);

            if (intent.getBooleanExtra(Consts.KEY_SKIP_MESSAGES, false)) {
               return skip();
            }

            Cursor smsItems = null;
            Cursor mmsItems = null;
            Cursor calllogItems = null;
            try {
              acquireLocks(background);
              smsItems = getSmsItemsToSync(maxItemsPerSync);
              mmsItems = getMmsItemsToSync(maxItemsPerSync - smsItems.getCount());
              calllogItems = getCalllogItemsToSync(maxItemsPerSync - smsItems.getCount() - mmsItems.getCount());

              sCurrentSyncedItems = 0;
              sItemsToSyncSms = smsItems.getCount();
              sItemsToSyncMms = mmsItems.getCount();
              sItemsToSyncCalllog = calllogItems.getCount();
              sItemsToSync = sItemsToSyncSms + sItemsToSyncMms + sItemsToSyncCalllog;

              if (LOCAL_LOGV) {
                Log.v(TAG, String.format("items to backup:  %d SMS, %d MMS, %d calls, %d total",
                                         sItemsToSyncSms, sItemsToSyncMms, sItemsToSyncCalllog,
                                         sItemsToSync));
              }

              if (sItemsToSync <= 0) {
                  if (PrefStore.isFirstSync(context)) {
                      // If this is the first backup we need to write something to PREF_MAX_SYNCED_DATE
                      // such that we know that we've performed a backup before.
                      PrefStore.setMaxSyncedDateSms(context, PrefStore.DEFAULT_MAX_SYNCED_DATE);
                      PrefStore.setMaxSyncedDateMms(context, PrefStore.DEFAULT_MAX_SYNCED_DATE);
                  }
                  Log.i(TAG, "Nothing to do.");
                  return 0;
              } else {

                if (!PrefStore.isLoginInformationSet(context)) {
                   lastError = getString(R.string.err_sync_requires_login_info);
                   publish(GENERAL_ERROR);
                   return null;
                } else {
                   return backup(smsItems, mmsItems, calllogItems);
                }
              }
            } catch (AuthenticationFailedException e) {
              Log.e(TAG, "authentication failed", e);
              publish(AUTH_FAILED);
              return null;
            } catch (MessagingException e) {
              this.ex = e;
              Log.e(TAG, "error during backup", e);
              lastError = e.getLocalizedMessage();
              publish(GENERAL_ERROR);
              return null;
            } catch (ConnectivityErrorException e) {
              lastError = e.getLocalizedMessage();
              publish(CONNECTIVITY_ERROR);
              return null;
            } finally {
              releaseLocks();

              if (smsItems != null) smsItems.close();
              if (mmsItems != null) mmsItems.close();

              stopSelf();
              Alarms.scheduleRegularSync(context);
           }
        }

        @Override
        protected void onProgressUpdate(SmsSyncState... progress) {
          smsSync.statusPref.stateChanged(progress[0]);
          sState = progress[0];
        }

        @Override
        protected void onPostExecute(Integer result) {
           if (sCanceled) {
              Log.i(TAG, "backup canceled by user");
              publish(CANCELED_BACKUP);
           } else if (result != null) {
              Log.i(TAG, result + " items backed up");
              publish(FINISHED_BACKUP);
           }
           sIsRunning = false;
           sCanceled = false;
        }

      /**
       * @param calllogItems
     * @throws MessagingException Thrown when there was an error accessing or creating the folder
       */
      private int backup(Cursor smsItems, Cursor mmsItems, Cursor calllogItems) throws MessagingException {
          Log.i(TAG, String.format("Starting backup (%d messages)", sItemsToSync));
          final CursorToMessage converter = new CursorToMessage(context, PrefStore.getUserEmail(context));

          publish(LOGIN);
          Folder smsmmsfolder  = getSMSBackupFolder();
          Folder calllogfolder = null;
          if (PrefStore.isCalllogBackupEnabled(SmsBackupService.this)) {
            calllogfolder = getCalllogBackupFolder();
          }

          try {
            publish(CALC);

            while (!sCanceled && (sCurrentSyncedItems < sItemsToSync)) {
                long smsDate = Long.MAX_VALUE, mmsDate = Long.MAX_VALUE, calllogDate = Long.MAX_VALUE;
                Cursor curCursor;
                DataType dataType = DataType.SMS;
                if (smsItems.moveToNext()) {
                  smsDate = smsItems.getLong(smsItems.getColumnIndex(SmsConsts.DATE));
                  smsItems.moveToPrevious();
                }
                if (mmsItems.moveToNext()) {
                  // Mms date is in seconds, sms in millis.
                  mmsDate = 1000*mmsItems.getLong(mmsItems.getColumnIndex(MmsConsts.DATE));
                  mmsItems.moveToPrevious();
                }

                if (calllogItems.moveToNext()) {
                  // calllog entry date is in millis
                  calllogDate = calllogItems.getLong(calllogItems.getColumnIndex(CallLog.Calls.DATE));
                  calllogItems.moveToPrevious();
                }

                if (smsDate < mmsDate && smsDate < calllogDate) {
                  curCursor = smsItems;
                  dataType = DataType.SMS;
                } else if (mmsDate < smsDate && mmsDate < calllogDate) {
                  curCursor = mmsItems;
                  dataType = DataType.MMS;
                } else if (calllogDate < smsDate && calllogDate < mmsDate) {
                  curCursor = calllogItems;
                  dataType = DataType.CALLLOG;
                } else {
                  break;
                }

                ConversionResult result = converter.cursorToMessages(curCursor, MAX_MSG_PER_REQUEST,
                                                                     dataType);
                List<Message> messages = result.messageList;

                if (messages.isEmpty()) break;

                switch (dataType) {
                  case MMS:
                    updateMaxSyncedDateMms(result.maxDate);
                    if (LOCAL_LOGV) Log.v(TAG, "Sending " + messages.size() + " mms messages to server.");
                    break;
                  case SMS:
                    updateMaxSyncedDateSms(result.maxDate);
                    if (LOCAL_LOGV) Log.v(TAG, "Sending " + messages.size() + " sms messages to server.");
                    break;
                  case CALLLOG:
                    updateMaxSyncedDateCalllog(result.maxDate);
                    if (LOCAL_LOGV) Log.v(TAG, "Sending " + messages.size() +
                                          " calllog messages to server.");
                    break;
                }

                switch (dataType) {
                  case CALLLOG:
                    if (calllogfolder != null) {
                      calllogfolder.appendMessages(messages.toArray(new Message[messages.size()]));
                    }
                    break;
                  default:
                    smsmmsfolder.appendMessages(messages.toArray(new Message[messages.size()]));
                    break;
                }

                sCurrentSyncedItems += messages.size();
                publish(BACKUP);

                result = null;
                messages = null;
            }

            return sCurrentSyncedItems;

          } finally {
              if (smsmmsfolder != null)  smsmmsfolder.close();
              if (calllogfolder != null) calllogfolder.close();
          }
        }

      /**
       * Returns a cursor of SMS messages that have not yet been synced with the
       * server. This includes all messages with
       * <code>date &lt; {@link #getMaxSyncedDateSms()}</code> which are not drafts.
       */
      private Cursor getSmsItemsToSync(int max) {
         if (LOCAL_LOGV) {
            Log.v(TAG, String.format("getSmsItemToSync(max=%d),  maxSyncedDate=%d", max, getMaxSyncedDateSms()));
         }
         String sortOrder = SmsConsts.DATE;
          if (max > 0) sortOrder += " LIMIT " + max;

          return getContentResolver().query(SMS_PROVIDER, null,
                String.format("%s > ? AND %s <> ?", SmsConsts.DATE, SmsConsts.TYPE),
                new String[] { String.valueOf(getMaxSyncedDateSms()),
                               String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT) },
                sortOrder);
      }

      /**
       * Returns a cursor of MMS messages that have not yet been synced with the
       * server. This includes all messages with
       * <code>date &lt; {@link #getMaxSyncedDateSms()}</code> which are not drafts.
       */
      private Cursor getMmsItemsToSync(int max) {
          if (LOCAL_LOGV) Log.v(TAG, "getMmsItemsToSync(max=" + max+")");

          if (!PrefStore.isMmsBackupEnabled(SmsBackupService.this)) {
            // return empty cursor if we don't have MMS
            if (LOCAL_LOGV) Log.v(TAG, "MMS backup disabled, returning empty cursor");
            return new MatrixCursor(new String[0], 0);
          }
          String sortOrder = SmsConsts.DATE;

          if (max > 0) {
            sortOrder += " LIMIT " + max;
          }
          return getContentResolver().query(MMS_PROVIDER, null,
                String.format("%s > ? AND %s <> ?", SmsConsts.DATE, MmsConsts.TYPE),
                new String[] { String.valueOf(getMaxSyncedDateMms()),
                               MmsConsts.DELIVERY_REPORT },
                sortOrder);
      }

      /**
       * Returns a cursor of call log entries that have not yet been synced with the
       * server. This includes all entries with
       * <code>date &lt; {@link #getMaxSyncedDateCalllog()}</code>.
       */
      private Cursor getCalllogItemsToSync(int max) {
          if (LOCAL_LOGV) Log.v(TAG, "getCalllogItemsToSync(max=" + max+")");

          if (!PrefStore.isCalllogBackupEnabled(SmsBackupService.this)) {
            if (LOCAL_LOGV) Log.v(TAG, "Calllog backup disabled, returning empty cursor");
            return new MatrixCursor(new String[0], 0);
          }
          String sortOrder = SmsConsts.DATE;

          if (max > 0) {
            sortOrder += " LIMIT " + max;
          }

          return getContentResolver().query(CALLLOG_PROVIDER, null,
                String.format("%s > ?", CallLog.Calls.DATE),
                new String[] { String.valueOf(getMaxSyncedDateCalllog())},
                sortOrder);
      }

      protected void publish(SmsSyncState s) {
        if (!background) {
           publishProgress(s);
        } else {
           if (!PrefStore.isNotificationEnabled(SmsBackupService.this)) return;

           switch(s) {
            case AUTH_FAILED:
                int details = PrefStore.useXOAuth(context) ? R.string.status_auth_failure_details_xoauth :
                                                             R.string.status_auth_failure_details_plain;
                notifyUser(android.R.drawable.stat_sys_warning, "SmsBackup+",
                           getString(R.string.notification_auth_failure), getString(details));
                break;
            case GENERAL_ERROR:
                notifyUser(android.R.drawable.stat_sys_warning, "SmsBackup+",
                           getString(R.string.notification_unknown_error), lastError);
                break;
            default:
           }
        }
      }


      /* Only update the max synced ID, do not really sync. */
      private int skip() {
          updateMaxSyncedDateSms(getMaxItemDateSms());
          updateMaxSyncedDateMms(getMaxItemDateMms());
          updateMaxSyncedDateCalllog(getMaxSyncedDateCalllog());

          sItemsToSync = 0;
          sCurrentSyncedItems = 0;
          sIsRunning = false;
          publish(IDLE);
          Log.i(TAG, "All messages skipped.");
          return 0;
      }
    }

    /**
     * Cancels the current ongoing backup.
    */
    static void cancel() {
        if (sIsRunning) {
          sCanceled = true;
        }
    }

    /**
     * Returns whether there is currently a backup going on or not.
     *
     */
    static boolean isWorking() {
        return sIsRunning;
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
