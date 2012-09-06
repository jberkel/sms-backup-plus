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

import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.text.format.DateFormat;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;

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
    private static int sItemsToSync;

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

    private String getSource(final Intent intent) {
        switch (intent.getIntExtra(Consts.SOURCE, -1)) {
            case Alarms.INCOMING:           return getResources().getString(R.string.source_incoming);
            case Alarms.REGULAR:            return getResources().getString(R.string.source_regular);
            case Alarms.BROADCAST_INTENT:   return getResources().getString(R.string.source_3rd_party);
            case -1:                        return getResources().getString(R.string.source_manual);
            default:                        return getResources().getString(R.string.source_unknown);
        }
    }

    @Override protected void handleIntent(final Intent intent) {
        if (intent == null) return; // NB: should not happen with START_NOT_STICKY
        if (LOCAL_LOGV) Log.v(TAG, "handleIntent("+intent+
                ", "+(intent.getExtras() == null ?  "null" : intent.getExtras().keySet())+")");

        appLog(R.string.app_log_backup_requested, getSource(intent));

        if (isBackground(intent) && !getConnectivityManager().getBackgroundDataSetting()) {
            appLog(R.string.app_log_skip_backup_background_data);

            stopSelf();
        } else {
          synchronized(ServiceBase.class) {
            // Only start a sync if there's no other sync / restore going on at this time.
            if (!sIsRunning)
              if (!SmsRestoreService.isWorking()) {
                sIsRunning = true;
                new BackupTask().execute(intent);
              } else {
                appLog(R.string.app_log_skip_backup_already_running);
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
               appLog(R.string.app_log_skip_backup_skip_messages);
               return skip();
            }

            appLog(R.string.app_log_start_backup, getSource(intent));

            Cursor smsItems = null;
            Cursor mmsItems = null;
            Cursor callLogItems = null;
            final int smsCount, mmsCount, callLogCount;
            try {
              acquireLocks(background);
              smsItems = getSmsItemsToSync(maxItemsPerSync, groupToBackup);
              smsCount = smsItems != null ? smsItems.getCount() : 0;

              mmsItems = getMmsItemsToSync(maxItemsPerSync - smsCount, groupToBackup);
              mmsCount = mmsItems != null ? mmsItems.getCount() : 0;

              callLogItems = getCallLogItemsToSync(maxItemsPerSync - smsCount - mmsCount);
              callLogCount = callLogItems != null ? callLogItems.getCount() : 0;

              sCurrentSyncedItems = 0;
              sItemsToSync = smsCount + mmsCount + callLogCount;

              if (sItemsToSync > 0) {
                  if (!PrefStore.isLoginInformationSet(context)) {
                     appLog(R.string.app_log_missing_credentials);

                     lastError = getString(R.string.err_sync_requires_login_info);
                     publish(GENERAL_ERROR);
                     return null;
                  }

                  appLog(R.string.app_log_backup_messages, smsCount, mmsCount, callLogCount);
                  return backup(smsItems, mmsItems, callLogItems);
              } else {
                  appLog(R.string.app_log_skip_backup_no_items);

                  if (PrefStore.isFirstSync(context)) {
                      // If this is the first backup we need to write something to PREF_MAX_SYNCED_DATE
                      // such that we know that we've performed a backup before.
                      PrefStore.setMaxSyncedDateSms(context, PrefStore.DEFAULT_MAX_SYNCED_DATE);
                      PrefStore.setMaxSyncedDateMms(context, PrefStore.DEFAULT_MAX_SYNCED_DATE);
                      // XXX skip call log?
                  }

                  Log.i(TAG, "Nothing to do.");
                  return 0;
              }
            } catch (AuthenticationFailedException e) {
              appLog(R.string.app_log_backup_failed_authentication, translateException(e));
              publish(AUTH_FAILED);
              return null;
            } catch (MessagingException e) {
              appLog(R.string.app_log_backup_failed_messaging, translateException(e));
              lastError = translateException(e);
              publish(GENERAL_ERROR);
              return null;
            } catch (ConnectivityErrorException e) {
              appLog(R.string.app_log_backup_failed_connectivity, translateException(e));
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

              final long nextSync = Alarms.scheduleRegularSync(context);
              if (nextSync >= 0) {
                appLog(R.string.app_log_scheduled_next_sync,
                       DateFormat.format("kk:mm", new Date(nextSync)));
              } else {
                appLog(R.string.app_log_no_next_sync);
              }

              stopSelf();
           }
        }

        @Override
        protected void onProgressUpdate(SmsSyncState... progress) {
          if (progress != null && progress.length > 0) {
            if (smsSync != null) smsSync.statusPref.stateChanged(progress[0]);
            sState = progress[0];
          }
        }

        @Override
        protected void onPostExecute(Integer result) {
           if (sCanceled) {
              appLog(R.string.app_log_backup_canceled, result);
              publish(CANCELED_BACKUP);
           } else if (result != null) {
              appLog(R.string.app_log_backup_finished, result);
              Log.i(TAG, result + " items backed up");
              publish(FINISHED_BACKUP);
           }
           sIsRunning = false;
           sCanceled = false;
        }

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
           Cursor curCursor;
           DataType dataType;
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
                                             CursorToMessage.formattedDuration(duration)));
              }

              // insert into calendar
              App.calendarAccessor().addEntry(context,
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

      private Cursor getCallLogItemsToSync(int max) {
          if (LOCAL_LOGV) Log.v(TAG, "getCallLogItemsToSync(max=" + max+")");

          if (!PrefStore.isCallLogBackupEnabled(context)) {
            if (LOCAL_LOGV) Log.v(TAG, "CallLog backup disabled, returning empty cursor");
            return new MatrixCursor(new String[0], 0);
          }
          String sortOrder = SmsConsts.DATE;
          if (max > 0) sortOrder += " LIMIT " + max;

          return getContentResolver().query(CALLLOG_PROVIDER,
                CursorToMessage.CALLLOG_PROJECTION,
                String.format("%s > ?", CallLog.Calls.DATE),
                new String[] { String.valueOf(PrefStore.getMaxSyncedDateCallLog(context)) },
                sortOrder);
      }

      private String groupSelection(DataType type, ContactGroup group) {
         /* MMS group selection not supported at the moment */
         if (type != DataType.SMS || group.type == ContactGroup.Type.EVERYBODY) return "";

         final Set<Long> ids = App.contactAccessor().getGroupContactIds(context, group).rawIds;
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

    static boolean isWorking() {
        return sIsRunning;
    }

    static int getItemsToSyncCount() {
        return sItemsToSync;
    }

    static int getCurrentSyncedItems() {
        return sCurrentSyncedItems;
    }
}
