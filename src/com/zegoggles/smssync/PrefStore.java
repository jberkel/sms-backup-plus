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

import java.net.URLEncoder;

import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.CallLog;

import static com.zegoggles.smssync.ContactAccessor.ContactGroup;
import static com.zegoggles.smssync.App.*;

public class PrefStore {
    /**
     * Preference key containing the maximum date of messages that were
     * successfully synced.
     */
    static final String PREF_MAX_SYNCED_DATE_SMS = "max_synced_date";
    static final String PREF_MAX_SYNCED_DATE_MMS = "max_synced_date_mms";
    static final String PREF_MAX_SYNCED_DATE_CALLLOG = "max_synced_date_calllog";

    /** Preference key containing the Google account username. */
    static final String PREF_LOGIN_USER = "login_user";

    /** Preference key containing the Google account password. */
    static final String PREF_LOGIN_PASSWORD = "login_password";

    /** Preference key containing a UID used for the threading reference header. */
    static final String PREF_REFERENECE_UID = "reference_uid";

    /** Preference key containing the server address */
    static final String PREF_SERVER_ADDRESS = "server_address";

    /** Preference key containing the server protocol */
    static final String PREF_SERVER_PROTOCOL = "server_protocol";

    static final String PREF_SERVER_AUTHENTICATION = "server_authentication";

    static final String PREF_OAUTH_TOKEN = "oauth_token";
    static final String PREF_OAUTH_TOKEN_SECRET = "oauth_token_secret";
    static final String PREF_OAUTH_USER = "oauth_user";

    /** Preference key containing the IMAP folder name where SMS should be backed up to. */
    static final String PREF_IMAP_FOLDER = "imap_folder";

    /** Preference key containing the IMAP folder name where SMS should be backed up to. */
    static final String PREF_IMAP_FOLDER_CALLLOG = "imap_folder_calllog";

    /** Preference key containing the IMAP folder name where SMS should be backed up to. */
    static final String PREF_MAIL_SUBJECT_PREFIX = "mail_subject_prefix";

    /** Preference key for storing whether to enable auto sync or not. */
    static final String PREF_ENABLE_AUTO_SYNC = "enable_auto_sync";

    /** Preference key for the timeout between an SMS is received and the scheduled sync. */
    static final String PREF_INCOMING_TIMEOUT_SECONDS = "auto_backup_incoming_schedule";

    /** Preference key for the interval between backup of outgoing SMS. */
    static final String PREF_REGULAR_TIMEOUT_SECONDS = "auto_backup_schedule";

    /** Preference for storing the maximum items per sync. */
    static final String PREF_MAX_ITEMS_PER_SYNC = "max_items_per_sync";

    /** Preference for storing the maximum items per restore. */
    static final String PREF_MAX_ITEMS_PER_RESTORE = "max_items_per_restore";

    /** Preference for storing the maximum items per restore. */
    static final String PREF_RESTORE_STARRED_ONLY = "restore_starred_only";

    /** Preference for storing whether backed up messages should be marked as read on Gmail. */
    static final String PREF_MARK_AS_READ = "mark_as_read";

    /** Preference for storing whether restored messages should be marked as read. */
    static final String PREF_MARK_AS_READ_ON_RESTORE = "mark_as_read_on_restore";

    static final String PREF_EMAIL_ADDRESS_STYLE = "email_address_style";

    static final String PREF_BACKUP_SMS  = "backup_sms";
    static final String PREF_RESTORE_SMS  = "restore_sms";

    static final String PREF_BACKUP_MMS  = "backup_mms";
    static final String PREF_RESTORE_MMS  = "restore_mms";

    static final String PREF_BACKUP_CALLLOG  = "backup_calllog";
    static final String PREF_RESTORE_CALLLOG  = "restore_calllog";

    static final String PREF_CALLLOG_SYNC_CALENDAR  = "backup_calllog_sync_calendar";
    static final String PREF_CALLLOG_SYNC_CALENDAR_ENABLED  = "backup_calllog_sync_calendar_enabled";

    static final String PREF_CALLLOG_TYPES  = "backup_calllog_types";
    static final String PREF_BACKUP_CONTACT_GROUP  = "backup_contact_group";

    static final String PREF_CONNECTED  = "connected";
    static final String PREF_WIFI_ONLY  = "wifi_only";

    static final String PREF_THIRD_PARTY_INTEGRATION  = "third_party_integration";

    /** Default value for {@link PrefStore#PREF_MAX_SYNCED_DATE_SMS}. */
    static final long DEFAULT_MAX_SYNCED_DATE = -1;

    /** Default value for {@link PrefStore#PREF_IMAP_FOLDER}. */
    static final String DEFAULT_IMAP_FOLDER = "SMS";

    /** Default value for {@link PrefStore#PREF_IMAP_FOLDER_CALLLOG}. */
    static final String DEFAULT_IMAP_FOLDER_CALLLOG = "Call log";

    /** Default value for {@link PrefStore#PREF_MAIL_SUBJECT_PREFIX}. */
    static final boolean DEFAULT_MAIL_SUBJECT_PREFIX = false;

    /** Default value for {@link PrefStore#PREF_ENABLE_AUTO_SYNC}. */
    static final boolean DEFAULT_ENABLE_AUTO_SYNC = false;

    /** Default value for {@link PrefStore#PREF_INCOMING_TIMEOUT_SECONDS}. */
    static final int DEFAULT_INCOMING_TIMEOUT_SECONDS = 60 * 3;

    /** Default value for {@link PrefStore#PREF_REGULAR_TIMEOUT_SECONDS}. */
    static final int DEFAULT_REGULAR_TIMEOUT_SECONDS = 2 * 60 * 60; // 2h

    /** Default value for {@link #PREF_LAST_SYNC}. */
    static final long DEFAULT_LAST_SYNC = -1;

    /** Default value for {@link #PREF_MAX_ITEMS_PER_SYNC}. */
    static final int DEFAULT_MAX_ITEMS_PER_SYNC = -1;

    static final int DEFAULT_MAX_ITEMS_PER_RESTORE = -1;

    /** Default value for {@link #PREF_MARK_AS_READ}. */
    static final boolean DEFAULT_MARK_AS_READ = true;

    static final boolean DEFAULT_MARK_AS_READ_ON_RESTORE = true;

    /** Default value for {@link #PREF_SERVER_ADDRESS}. */
    static final String DEFAULT_SERVER_ADDRESS = "imap.gmail.com:993";

    /** Default value for {@link #PREF_SERVER_PROTOCOL}. */
    static final String DEFAULT_SERVER_PROTOCOL = "+ssl+";

    enum AuthMode            { PLAIN, XOAUTH }
    enum CallLogTypes        { EVERYTHING, MISSED, INCOMING, OUTGOING, INCOMING_OUTGOING }
    public enum AddressStyle { NAME, NAME_AND_NUMBER, NUMBER }

    static SharedPreferences getSharedPreferences(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    static long getMostRecentSyncedDate(Context ctx) {
        return Math.max(Math.max(
            getMaxSyncedDateSms(ctx),
            getMaxSyncedDateMms(ctx) * 1000),
            getMaxSyncedDateCallLog(ctx));
    }

    static long getMaxSyncedDateSms(Context ctx) {
        return getSharedPreferences(ctx).getLong(PREF_MAX_SYNCED_DATE_SMS, DEFAULT_MAX_SYNCED_DATE);
    }

    static long getMaxSyncedDateMms(Context ctx) {
        return getSharedPreferences(ctx).getLong(PREF_MAX_SYNCED_DATE_MMS, DEFAULT_MAX_SYNCED_DATE);
    }

    static long getMaxSyncedDateCallLog(Context ctx) {
        return getSharedPreferences(ctx).getLong(PREF_MAX_SYNCED_DATE_CALLLOG, DEFAULT_MAX_SYNCED_DATE);
    }

    static boolean isMaxSyncedDateSet(Context ctx) {
        return getSharedPreferences(ctx).contains(PREF_MAX_SYNCED_DATE_SMS);
    }

    static void setMaxSyncedDateSms(Context ctx, long maxSyncedDate) {
        getSharedPreferences(ctx).edit()
          .putLong(PREF_MAX_SYNCED_DATE_SMS, maxSyncedDate)
          .commit();
    }

    static void setMaxSyncedDateMms(Context ctx, long maxSyncedDate) {
        getSharedPreferences(ctx).edit()
          .putLong(PREF_MAX_SYNCED_DATE_MMS, maxSyncedDate)
          .commit();
    }

    static void setMaxSyncedDateCallLog(Context ctx, long maxSyncedDate) {
        getSharedPreferences(ctx).edit()
          .putLong(PREF_MAX_SYNCED_DATE_CALLLOG, maxSyncedDate)
          .commit();
    }
    static String getImapUsername(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_LOGIN_USER, null);
    }

    static void setImapUsername(Context ctx, String s) {
       getSharedPreferences(ctx).edit().putString(PREF_LOGIN_USER, s).commit();
    }

    static String getImapPassword(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_LOGIN_PASSWORD, null);
    }

    static XOAuthConsumer getOAuthConsumer(Context ctx) {
        return new XOAuthConsumer(
            getOauthUsername(ctx),
            getOauthToken(ctx),
            getOauthTokenSecret(ctx));
    }

    static String getOauthToken(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_OAUTH_TOKEN, null);
    }

    static String getOauthTokenSecret(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_OAUTH_TOKEN_SECRET, null);
    }

    static boolean hasOauthTokens(Context ctx) {
        return getOauthToken(ctx) != null &&
               getOauthTokenSecret(ctx) != null;
    }

    static String getOauthUsername(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_OAUTH_USER, getImapUsername(ctx) /* XXX remove */);
    }

    static void setOauthUsername(Context ctx, String s) {
        getSharedPreferences(ctx).edit().putString(PREF_OAUTH_USER, s).commit();
    }

    static void setOauthTokens(Context ctx, String token, String secret) {
      getSharedPreferences(ctx).edit()
        .putString(PREF_OAUTH_TOKEN, token)
        .putString(PREF_OAUTH_TOKEN_SECRET, secret)
        .commit();
    }

    static AuthMode getAuthMode(Context ctx) {
        return getDefaultType(ctx, PREF_SERVER_AUTHENTICATION, AuthMode.class, AuthMode.XOAUTH);
    }

    static ContactGroup getBackupContactGroup(Context ctx) {
        return new ContactGroup(getStringAsInt(ctx, PREF_BACKUP_CONTACT_GROUP, -1));
    }

    static boolean useXOAuth(Context ctx) {
        return getAuthMode(ctx) == AuthMode.XOAUTH && isGmail(ctx);
    }

    static String getUserEmail(Context ctx) {
      switch(getAuthMode(ctx)) {
        case XOAUTH: return getOauthUsername(ctx);
        default:     return getImapUsername(ctx);
      }
    }

    static boolean isLoginInformationSet(Context ctx) {
        if (getAuthMode(ctx) == AuthMode.PLAIN) {
            return getImapPassword(ctx) != null && getImapUsername(ctx) != null;
        } else {
            return hasOauthTokens(ctx) && getOauthUsername(ctx) != null;
        }
    }

    static boolean isSmsBackupEnabled(Context ctx) {
      return getSharedPreferences(ctx).getBoolean(PREF_BACKUP_SMS, true);
    }

    static boolean isMmsBackupEnabled(Context ctx) {
      return getSharedPreferences(ctx).getBoolean(PREF_BACKUP_MMS, false);
    }

    static boolean isCallLogBackupEnabled(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_BACKUP_CALLLOG, false);
    }

    static boolean isCallLogCalendarSyncEnabled(Context ctx) {
        return
          getCallLogCalendarId(ctx) >= 0 &&
          getSharedPreferences(ctx).getBoolean(PREF_CALLLOG_SYNC_CALENDAR_ENABLED, false);
    }

    static <T extends Enum<T>> T getDefaultType(Context ctx, String pref, Class<T> tClazz,
                                                T defaultType) {
        try {
          final String s = getSharedPreferences(ctx).getString(pref, null);
          return s == null ? defaultType : Enum.valueOf(tClazz, s.toUpperCase());
        } catch (IllegalArgumentException e) {
          Log.e(TAG, "getDefaultType("+pref+")", e);
          return defaultType;
        }
    }

    static CallLogTypes getCallLogType(Context ctx) {
      return getDefaultType(ctx, PREF_CALLLOG_TYPES, CallLogTypes.class, CallLogTypes.EVERYTHING);
    }

    static boolean isCallLogTypeEnabled(Context ctx, int type) {
      switch (getCallLogType(ctx)) {
        case OUTGOING: return type == CallLog.Calls.OUTGOING_TYPE;
        case INCOMING: return type == CallLog.Calls.INCOMING_TYPE;
        case MISSED:   return type == CallLog.Calls.MISSED_TYPE;
        case INCOMING_OUTGOING: return type != CallLog.Calls.MISSED_TYPE;

        default: return true;
      }
    }

    static int getCallLogCalendarId(Context ctx) {
        return getStringAsInt(ctx, PREF_CALLLOG_SYNC_CALENDAR, -1);
    }

    static boolean isRestoreStarredOnly(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_RESTORE_STARRED_ONLY, false);
    }

    static boolean isRestoreSms(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_RESTORE_SMS, true);
    }

    static boolean isRestoreMms(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_RESTORE_MMS, false);
    }

    static boolean isRestoreCallLog(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_RESTORE_CALLLOG, true);
    }

    static String getReferenceUid(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_REFERENECE_UID, null);
    }

    static void setReferenceUid(Context ctx, String referenceUid) {
        getSharedPreferences(ctx).edit()
          .putString(PREF_REFERENECE_UID, referenceUid)
          .commit();
    }

    static String getImapFolder(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_IMAP_FOLDER, DEFAULT_IMAP_FOLDER);
    }

    static String getCallLogFolder(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_IMAP_FOLDER_CALLLOG, DEFAULT_IMAP_FOLDER_CALLLOG);
    }

    static boolean getMailSubjectPrefix(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_MAIL_SUBJECT_PREFIX, DEFAULT_MAIL_SUBJECT_PREFIX);
    }

    static boolean isImapFolderSet(Context ctx) {
        return getSharedPreferences(ctx).contains(PREF_IMAP_FOLDER);
    }

    static boolean isCallLogFolderSet(Context ctx) {
        return getSharedPreferences(ctx).contains(PREF_IMAP_FOLDER_CALLLOG);
    }

    static int getMaxItemsPerSync(Context ctx) {
      return getStringAsInt(ctx, PREF_MAX_ITEMS_PER_SYNC, DEFAULT_MAX_ITEMS_PER_SYNC);
    }

    static int getMaxItemsPerRestore(Context ctx) {
      return getStringAsInt(ctx, PREF_MAX_ITEMS_PER_RESTORE, DEFAULT_MAX_ITEMS_PER_RESTORE);
    }

    static AddressStyle getEmailAddressStyle(Context ctx) {
      return getDefaultType(ctx, PREF_EMAIL_ADDRESS_STYLE, AddressStyle.class, AddressStyle.NAME);
    }

    static boolean isWifiOnly(Context ctx) {
      return (getSharedPreferences(ctx).getBoolean(PREF_WIFI_ONLY, false));
    }

    static boolean isAllow3rdPartyIntegration(Context ctx) {
      return (getSharedPreferences(ctx).getBoolean(PREF_THIRD_PARTY_INTEGRATION, false));
    }

    private static int getStringAsInt(Context ctx, String key, int def) {
        try {
          String s = getSharedPreferences(ctx).getString(key, null);
          if (s == null) return def;

          return Integer.valueOf(s);
        } catch (NumberFormatException e) {
          return def;
        }
      }

    /**
     * Returns whether an IMAP folder is valid.
     */
    static boolean isValidImapFolder(String imapFolder) {
      if (imapFolder == null || imapFolder.length() == 0) return false;
      if (imapFolder.charAt(0) == ' ' || imapFolder.charAt(imapFolder.length() - 1) == ' ')
          return false;

       return true;
    }

    static void setImapFolder(Context ctx, String imapFolder) {
        getSharedPreferences(ctx).edit()
          .putString(PREF_IMAP_FOLDER, imapFolder)
          .commit();
    }

    static boolean isEnableAutoSync(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_ENABLE_AUTO_SYNC,
                DEFAULT_ENABLE_AUTO_SYNC);
    }

    static boolean isEnableAutoSyncSet(Context ctx) {
        return getSharedPreferences(ctx).contains(PREF_ENABLE_AUTO_SYNC);
    }

    static void setEnableAutoSync(Context ctx, boolean enableAutoSync) {
        getSharedPreferences(ctx).edit()
          .putBoolean(PREF_ENABLE_AUTO_SYNC, enableAutoSync)
          .commit();
    }

    static int getIncomingTimeoutSecs(Context ctx) {
       return getStringAsInt(ctx, PREF_INCOMING_TIMEOUT_SECONDS, DEFAULT_INCOMING_TIMEOUT_SECONDS);
    }

    static int getRegularTimeoutSecs(Context ctx) {
        return getStringAsInt(ctx, PREF_REGULAR_TIMEOUT_SECONDS, DEFAULT_REGULAR_TIMEOUT_SECONDS);
    }

    static boolean getMarkAsRead(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_MARK_AS_READ, DEFAULT_MARK_AS_READ);
    }

    static void setMarkAsRead(Context ctx, boolean markAsRead) {
        getSharedPreferences(ctx).edit()
          .putBoolean(PREF_MARK_AS_READ, markAsRead)
          .commit();
    }

    static boolean getMarkAsReadOnRestore(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_MARK_AS_READ_ON_RESTORE, DEFAULT_MARK_AS_READ_ON_RESTORE);
    }

    static void setMarkAsReadOnRestore(Context ctx, boolean markAsRead) {
        getSharedPreferences(ctx).edit()
          .putBoolean(PREF_MARK_AS_READ_ON_RESTORE, markAsRead)
          .commit();
    }

    static boolean isFirstSync(Context ctx) {
        return !getSharedPreferences(ctx).contains(PREF_MAX_SYNCED_DATE_SMS);
    }

    static boolean isFirstUse(Context ctx) {
        final String key = "first_use";

        if (isFirstSync(ctx) &&
            !getSharedPreferences(ctx).contains(key)) {
            getSharedPreferences(ctx).edit().putBoolean(key, false).commit();
            return true;
        } else {
            return false;
        }
    }

    static void clearOauthData(Context ctx) {
        getSharedPreferences(ctx).edit()
          .remove(PREF_OAUTH_USER)
          .remove(PREF_OAUTH_TOKEN)
          .remove(PREF_OAUTH_TOKEN_SECRET)
          .commit();
    }

    static void clearLastSyncData(Context ctx) {
        getSharedPreferences(ctx).edit()
          .remove(PREF_MAX_SYNCED_DATE_SMS)
          .remove(PREF_MAX_SYNCED_DATE_MMS)
          .remove(PREF_MAX_SYNCED_DATE_CALLLOG)
          .commit();
    }

    static boolean isNotificationEnabled(Context ctx) {
        return getSharedPreferences(ctx).getBoolean("notifications", false);
    }

    static String getServerAddress(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS);
    }

    static void setServerAddress(Context ctx, String serverAddress) {
         getSharedPreferences(ctx).edit()
           .putString(PREF_SERVER_ADDRESS, serverAddress)
           .commit();
     }

     static String getServerProtocol(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_SERVER_PROTOCOL, DEFAULT_SERVER_PROTOCOL);
    }

    static boolean isGmail(Context ctx) {
        return "imap.gmail.com:993".equalsIgnoreCase(getServerAddress(ctx));
    }

    static String getStoreUri(Context ctx) {
        if (useXOAuth(ctx)) {
          XOAuthConsumer consumer = getOAuthConsumer(ctx);

          return String.format(Consts.IMAP_URI,
               DEFAULT_SERVER_PROTOCOL,
                "xoauth:" + URLEncoder.encode(consumer.getUsername()),
               URLEncoder.encode(consumer.generateXOAuthString()),
               getServerAddress(ctx));
        } else {
            return String.format(Consts.IMAP_URI,
               getServerProtocol(ctx),
               URLEncoder.encode(getImapUsername(ctx)),
               URLEncoder.encode(getImapPassword(ctx)).replace("+", "%20"),
               getServerAddress(ctx));
        }
    }

    static String getVersion(Context context, boolean code) {
      android.content.pm.PackageInfo pInfo = null;
      try {
        pInfo = context.getPackageManager().getPackageInfo(
                SmsSync.class.getPackage().getName(),
                android.content.pm.PackageManager.GET_META_DATA);
        return ""+ (code ? pInfo.versionCode : pInfo.versionName);
      } catch (android.content.pm.PackageManager.NameNotFoundException e) {
        Log.e(TAG, "error", e);
        return null;
      }
    }

    static boolean showUpgradeMessage(Context ctx) {
      final String key = "upgrade_message_seen";
      boolean seen = getSharedPreferences(ctx).getBoolean(key, false);
      if (!seen && isOldSmsBackupInstalled(ctx)) {
        getSharedPreferences(ctx).edit().putBoolean(key, true).commit();
        return true;
      } else {
        return false;
      }
    }

    static boolean isOldSmsBackupInstalled(Context context) {
      try {
        context.getPackageManager().getPackageInfo(
            "tv.studer.smssync",
            android.content.pm.PackageManager.GET_META_DATA);
        return true;
      } catch (android.content.pm.PackageManager.NameNotFoundException e) {
        return false;
      }
    }

    static void upgradeOAuthUsername(Context ctx) {
      if (useXOAuth(ctx) && getSharedPreferences(ctx).getString(PREF_OAUTH_USER, null) == null &&
                            getSharedPreferences(ctx).getString(PREF_LOGIN_USER, null) != null) {
        setOauthUsername(ctx, getImapUsername(ctx));
      }
    }
}
