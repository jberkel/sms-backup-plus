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
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Process;
import android.util.Log;
import android.os.AsyncTask;
import android.provider.CallLog;
import android.text.TextUtils;

import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.zegoggles.smssync.CursorToMessage.ConversionResult;
import com.zegoggles.smssync.CursorToMessage.DataType;
import com.zegoggles.smssync.ServiceBase.SmsSyncState;
import com.zegoggles.smssync.R;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.io.IOException;

import static com.zegoggles.smssync.ContactAccessor.ContactGroup;
import static com.zegoggles.smssync.ServiceBase.SmsSyncState.*;
import static com.zegoggles.smssync.App.*;

public class SmsBackupService extends ServiceBase {
    /** Number of messages sent per sync request. */
    /** Changing this value will cause mms/sms messages to thread out of order. */
    private static final int MAX_MSG_PER_REQUEST = 1;

    /** Flag indicating whether this service is already running. */
    private static boolean sIsRunning = false;

    /** Number of messages that currently need a sync. */
    private static int sItemsToSync, sItemsToSyncSms, sItemsToSyncMms, sItemsToSyncCallLog;

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

    @Override protected void handleIntent(final Intent intent) {
        if (LOCAL_LOGV) Log.v(TAG, "handleIntent("+intent+")");
        if (intent == null) return; // NB: should not happen with START_NOT_STICKY

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
        private final Context context = SmsBackupService.this;
        private final int maxItemsPerSync = PrefStore.getMaxItemsPerSync(context);
        private final ContactGroup groupToBackup = PrefStore.getBackupContactGroup(context);
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
            Cursor callLogItems = null;
            final int smsCount, mmsCount, callLogCount;
            try {
              acquireLocks(background);
              smsItems = getSmsItemsToSync(maxItemsPerSync, groupToBackup);
              smsCount = smsItems != null ? smsItems.getCount() : 0;
              mmsCount = mmsItems != null ? mmsItems.getCount() : 0;

              mmsItems = getMmsItemsToSync(maxItemsPerSync - smsCount, groupToBackup);
              callLogItems = getCallLogItemsToSync(maxItemsPerSync - smsCount - mmsCount);
              callLogCount = callLogItems != null ? callLogItems.getCount() : 0;

              sCurrentSyncedItems = 0;
              sItemsToSyncSms = smsCount;
              sItemsToSyncMms = mmsCount;
              sItemsToSyncCallLog = callLogCount;
              sItemsToSync = sItemsToSyncSms + sItemsToSyncMms + sItemsToSyncCallLog;

              if (LOCAL_LOGV) {
                Log.v(TAG, String.format("items to backup:  %d SMS, %d MMS, %d calls, %d total",
                                         sItemsToSyncSms, sItemsToSyncMms, sItemsToSyncCallLog,
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
                   return backup(smsItems, mmsItems, callLogItems);
                }
              }
            } catch (AuthenticationFailedException e) {
              Log.e(TAG, "authentication failed", e);
              publish(AUTH_FAILED);
              return null;
            } catch (MessagingException e) {
              Log.e(TAG, "error during backup", e);
              lastError = translateException(e);
              publish(GENERAL_ERROR);
              return null;
            } catch (ConnectivityErrorException e) {
              lastError = translateException(e);
              publish(CONNECTIVITY_ERROR);
              return null;
            } finally {
              releaseLocks();

              try {
                if (smsItems != null) smsItems.close();
                if (mmsItems != null) mmsItems.close();
                if (callLogItems != null) callLogItems.close();
              } catch (Exception e) {
                Log.e(TAG, "error", e);
                /* ignore */
              }

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
       * @param callLogItems
       * @throws MessagingException Thrown when there was an error accessing or creating the folder
       */
      private int backup(Cursor smsItems, Cursor mmsItems, Cursor callLogItems)
        throws MessagingException {
          Log.i(TAG, String.format("Starting backup (%d messages)", sItemsToSync));
          final CursorToMessage converter = new CursorToMessage(context, PrefStore.getUserEmail(context));

          publish(LOGIN);
          Folder smsmmsfolder  = getSMSBackupFolder();
          Folder callLogfolder = null;
          if (PrefStore.isCallLogBackupEnabled(context)) {
            callLogfolder = getCallLogBackupFolder();
          }

          try {
           Cursor curCursor = null;
           DataType dataType = null;
           publish(CALC);
           while (!sCanceled && (sCurrentSyncedItems < sItemsToSync)) {
                if (smsItems != null && smsItems.moveToNext()) {
                  dataType = DataType.SMS;
                  curCursor = smsItems;
                } else if (mmsItems != null && mmsItems.moveToNext()) {
                  dataType = DataType.MMS;
                  curCursor = mmsItems;
                } else if (callLogItems != null && callLogItems.moveToNext()) {
                  dataType = DataType.CALLLOG;
                  curCursor = callLogItems;
                } else break;

                if (LOCAL_LOGV) Log.v(TAG, "backing up: " + dataType);
                ConversionResult result = converter.cursorToMessages(curCursor, MAX_MSG_PER_REQUEST,
                                                                     dataType);
                List<Message> messages = result.messageList;
                if (!messages.isEmpty()) {
                  if (LOCAL_LOGV) Log.v(TAG, String.format("sending %d %s message(s) to server.",
                                             messages.size(), dataType));
                  switch (dataType) {
                    case MMS:
                      updateMaxSyncedDateMms(result.maxDate);
                      smsmmsfolder.appendMessages(messages.toArray(new Message[messages.size()]));
                      break;
                    case SMS:
                      updateMaxSyncedDateSms(result.maxDate);
                      smsmmsfolder.appendMessages(messages.toArray(new Message[messages.size()]));
                      break;
                    case CALLLOG:
                      updateMaxSyncedDateCallLog(result.maxDate);
                      if (callLogfolder != null) {
                        callLogfolder.appendMessages(messages.toArray(new Message[messages.size()]));
                      }
                      if (PrefStore.isCallLogCalendarSyncEnabled(context)) {
                        syncCalendar(converter, result);
                      }
                      break;
                  }
                }

                sCurrentSyncedItems += messages.size();
                publish(BACKUP);

                result = null;
                messages = null;
            }

            return sCurrentSyncedItems;

          } finally {
              if (smsmmsfolder != null)  smsmmsfolder.close();
              if (callLogfolder != null) callLogfolder.close();
          }
      }

      private void syncCalendar(CursorToMessage converter, ConversionResult result) {
        if (result.type == DataType.CALLLOG) {
          for (Map<String, String> m : result.mapList) {
            try {
              final int duration = Integer.parseInt(m.get(CallLog.Calls.DURATION));
              final int callType = Integer.parseInt(m.get(CallLog.Calls.TYPE));
              final String number= m.get(CallLog.Calls.NUMBER);
              final Date then    = new Date(Long.valueOf(m.get(CallLog.Calls.DATE)));
              final CursorToMessage.PersonRecord record = converter.lookupPerson(number);

              StringBuilder description = new StringBuilder();
              description.append(getString(R.string.call_number_field, record.getNumber()))
                         .append(" (")
                         .append(converter.callTypeString(callType, null))
                         .append(" )")
                         .append("\n");

              if (callType != CallLog.Calls.MISSED_TYPE) {
                description.append(getString(R.string.call_duration_field,
                                             converter.formattedDuration(duration)));
              }

              // insert into calendar
              CalendarApi.addEntry(context,
                                   PrefStore.getCallLogCalendarId(context),
                                   then, duration,
                                   converter.callTypeString(callType, record.getName()),
                                   description.toString());
            } catch (NumberFormatException e) {
              Log.w(TAG, "error", e);
            }
          }
        }
      }

      /**
       * Returns a cursor of SMS messages that have not yet been synced with the
       * server. This includes all messages with
       * <code>date &lt; {@link #getMaxSyncedDateSms()}</code> which are not drafts.
       */
      private Cursor getSmsItemsToSync(int max, ContactGroup group) {
         if (LOCAL_LOGV) {
            Log.v(TAG, String.format("getSmsItemToSync(max=%d),  maxSyncedDate=%d", max,
                       PrefStore.getMaxSyncedDateSms(context)));
         }
         String sortOrder = SmsConsts.DATE;
         if (max > 0) sortOrder += " LIMIT " + max;

         return getContentResolver().query(SMS_PROVIDER, null,
                String.format("%s > ? AND %s <> ? %s", SmsConsts.DATE, SmsConsts.TYPE,
                                                       groupSelection(DataType.SMS, group)),
                new String[] { String.valueOf(PrefStore.getMaxSyncedDateSms(context)),
                               String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT) },
                sortOrder);
      }

      /**
       * Returns a cursor of MMS messages that have not yet been synced with the
       * server. This includes all messages with
       * <code>date &lt; {@link #getMaxSyncedDateSms()}</code> which are not drafts.
       */
      private Cursor getMmsItemsToSync(int max, ContactGroup group) {
          if (LOCAL_LOGV) Log.v(TAG, "getMmsItemsToSync(max=" + max+")");

          if (!PrefStore.isMmsBackupEnabled(context)) {
            // return empty cursor if we don't have MMS
            if (LOCAL_LOGV) Log.v(TAG, "MMS backup disabled, returning empty cursor");
            return new MatrixCursor(new String[0], 0);
          }
          String sortOrder = SmsConsts.DATE;
          if (max > 0) sortOrder += " LIMIT " + max;

          return getContentResolver().query(MMS_PROVIDER, null,
                String.format("%s > ? AND %s <> ? %s", SmsConsts.DATE, MmsConsts.TYPE,
                                                       groupSelection(DataType.MMS, group)),
                new String[] { String.valueOf(PrefStore.getMaxSyncedDateMms(context)),
                               MmsConsts.DELIVERY_REPORT },
                sortOrder);
      }

      /**
       * Returns a cursor of call log entries that have not yet been synced with the
       * server. This includes all entries with
       * <code>date &lt; {@link #getMaxSyncedDateCallLog()}</code>.
       */
      private Cursor getCallLogItemsToSync(int max) {
          if (LOCAL_LOGV) Log.v(TAG, "getCallLogItemsToSync(max=" + max+")");

          if (!PrefStore.isCallLogBackupEnabled(context)) {
            if (LOCAL_LOGV) Log.v(TAG, "CallLog backup disabled, returning empty cursor");
            return new MatrixCursor(new String[0], 0);
          }
          String sortOrder = SmsConsts.DATE;
          if (max > 0) sortOrder += " LIMIT " + max;

          return getContentResolver().query(CALLLOG_PROVIDER, null,
                String.format("%s > ?", CallLog.Calls.DATE),
                new String[] { String.valueOf(PrefStore.getMaxSyncedDateCallLog(context)) },
                sortOrder);
      }

      private String groupSelection(DataType type, ContactGroup group) {
         /* MMS group selection not supported at the moment */
         if (type != DataType.SMS || group.type == ContactGroup.Type.EVERYBODY) return "";

         final Set<Long> ids = App.contacts().getGroupContactIds(context, group).rawIds;
         if (LOCAL_LOGV) Log.v(TAG, "only selecting contacts matching " + ids);
         return String.format(" AND (%s = %d OR %s IN (%s))",
                          SmsConsts.TYPE,
                          SmsConsts.MESSAGE_TYPE_SENT,
                          SmsConsts.PERSON,
                          TextUtils.join(",", ids.toArray(new Long[ids.size()])));

      }

      protected void publish(SmsSyncState s) {
        if (!background) {
           publishProgress(s);
        } else {
           if (!PrefStore.isNotificationEnabled(context)) return;

           switch(s) {
            case AUTH_FAILED:
                int details = PrefStore.useXOAuth(context) ? R.string.status_auth_failure_details_xoauth :
                                                             R.string.status_auth_failure_details_plain;
                notifyUser(android.R.drawable.stat_sys_warning, TAG,
                           getString(R.string.notification_auth_failure), getString(details));
                break;
            case GENERAL_ERROR:
                notifyUser(android.R.drawable.stat_sys_warning, TAG,
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
          updateMaxSyncedDateCallLog(getMaxItemDateCallLog());

          sItemsToSync = sCurrentSyncedItems = 0;
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
