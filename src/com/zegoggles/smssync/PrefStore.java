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

import android.text.TextUtils;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.CallLog;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

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
    static final String PREF_REFERENCE_UID = "reference_uid";

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

    static final String PREF_BACKUP_CALLLOG  = "backup_calllog";
    static final String PREF_RESTORE_CALLLOG  = "restore_calllog";

    static final String PREF_CALLLOG_SYNC_CALENDAR  = "backup_calllog_sync_calendar";
    static final String PREF_CALLLOG_SYNC_CALENDAR_ENABLED  = "backup_calllog_sync_calendar_enabled";

    static final String PREF_CALLLOG_TYPES  = "backup_calllog_types";
    static final String PREF_BACKUP_CONTACT_GROUP  = "backup_contact_group";

    static final String PREF_CONNECTED  = "connected";
    static final String PREF_WIFI_ONLY  = "wifi_only";

    static final String PREF_THIRD_PARTY_INTEGRATION  = "third_party_integration";

    static final String PREF_APP_LOG = "app_log";

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


    public static boolean isAppLogEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_APP_LOG, false);
    }

    enum AuthMode            { PLAIN, XOAUTH }
    enum CallLogTypes        { EVERYTHING, MISSED, INCOMING, OUTGOING, INCOMING_OUTGOING }
    public enum AddressStyle { NAME, NAME_AND_NUMBER, NUMBER }


    static SharedPreferences getPrefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    // All sensitive information is stored in a separate prefs file so we can
    // backup the rest without exposing sensitive data
    static SharedPreferences getCredentials(Context ctx) {
        return ctx.getSharedPreferences("credentials", Context.MODE_PRIVATE);
    }

    static long getMostRecentSyncedDate(Context ctx) {
        return Math.max(Math.max(
            getMaxSyncedDateSms(ctx),
            getMaxSyncedDateMms(ctx) * 1000),
            getMaxSyncedDateCallLog(ctx));
    }

    static long getMaxSyncedDateSms(Context ctx) {
        return getPrefs(ctx).getLong(PREF_MAX_SYNCED_DATE_SMS, DEFAULT_MAX_SYNCED_DATE);
    }

    static long getMaxSyncedDateMms(Context ctx) {
        return getPrefs(ctx).getLong(PREF_MAX_SYNCED_DATE_MMS, DEFAULT_MAX_SYNCED_DATE);
    }

    static long getMaxSyncedDateCallLog(Context ctx) {
        return getPrefs(ctx).getLong(PREF_MAX_SYNCED_DATE_CALLLOG, DEFAULT_MAX_SYNCED_DATE);
    }

    static void setMaxSyncedDateSms(Context ctx, long maxSyncedDate) {
        getPrefs(ctx).edit()
          .putLong(PREF_MAX_SYNCED_DATE_SMS, maxSyncedDate)
          .commit();
    }

    static void setMaxSyncedDateMms(Context ctx, long maxSyncedDate) {
        getPrefs(ctx).edit()
          .putLong(PREF_MAX_SYNCED_DATE_MMS, maxSyncedDate)
          .commit();
    }

    static void setMaxSyncedDateCallLog(Context ctx, long maxSyncedDate) {
        getPrefs(ctx).edit()
          .putLong(PREF_MAX_SYNCED_DATE_CALLLOG, maxSyncedDate)
          .commit();
    }
    static String getImapUsername(Context ctx) {
        return getPrefs(ctx).getString(PREF_LOGIN_USER, null);
    }

    static String getImapPassword(Context ctx) {
        return getCredentials(ctx).getString(PREF_LOGIN_PASSWORD, null);
    }

    static void setImapPassword(Context ctx, String s) {
        getCredentials(ctx).edit().putString(PREF_LOGIN_PASSWORD, s).commit();
    }

    static XOAuthConsumer getOAuthConsumer(Context ctx) {
        return new XOAuthConsumer(
            getOauthUsername(ctx),
            getOauthToken(ctx),
            getOauthTokenSecret(ctx));
    }

    static String getOauthToken(Context ctx) {
        return getCredentials(ctx).getString(PREF_OAUTH_TOKEN, null);
    }

    static String getOauthTokenSecret(Context ctx) {
        return getCredentials(ctx).getString(PREF_OAUTH_TOKEN_SECRET, null);
    }

    static boolean hasOauthTokens(Context ctx) {
        return getOauthUsername(ctx) != null &&
               getOauthToken(ctx) != null &&
               getOauthTokenSecret(ctx) != null;
    }

    static String getOauthUsername(Context ctx) {
        return getPrefs(ctx).getString(PREF_OAUTH_USER, null);
    }

    static void setOauthUsername(Context ctx, String s) {
        getPrefs(ctx).edit().putString(PREF_OAUTH_USER, s).commit();
    }

    static void setOauthTokens(Context ctx, String token, String secret) {
      getCredentials(ctx).edit()
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
        switch (getAuthMode(ctx)) {
            case PLAIN:  return !TextUtils.isEmpty(getImapPassword(ctx)) &&
                                !TextUtils.isEmpty(getImapUsername(ctx));
            case XOAUTH: return hasOauthTokens(ctx);
            default: return false;
        }
    }

    static boolean isSmsBackupEnabled(Context ctx) {
      return getPrefs(ctx).getBoolean(PREF_BACKUP_SMS, true);
    }

    static boolean isMmsBackupEnabled(Context ctx) {
       final int version = android.os.Build.VERSION.SDK_INT;
       return version >= SmsSync.MIN_VERSION_MMS && getPrefs(ctx).getBoolean(PREF_BACKUP_MMS, false);
    }

    static boolean isCallLogBackupEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_BACKUP_CALLLOG, false);
    }

    static boolean isCallLogCalendarSyncEnabled(Context ctx) {
        return
          getCallLogCalendarId(ctx) >= 0 &&
          getPrefs(ctx).getBoolean(PREF_CALLLOG_SYNC_CALENDAR_ENABLED, false);
    }

    static <T extends Enum<T>> T getDefaultType(Context ctx, String pref, Class<T> tClazz,
                                                T defaultType) {
        try {
          final String s = getPrefs(ctx).getString(pref, null);
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
        return getPrefs(ctx).getBoolean(PREF_RESTORE_STARRED_ONLY, false);
    }

    static boolean isRestoreSms(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_RESTORE_SMS, true);
    }

    static boolean isRestoreCallLog(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_RESTORE_CALLLOG, true);
    }

    static String getReferenceUid(Context ctx) {
        return getPrefs(ctx).getString(PREF_REFERENCE_UID, null);
    }

    static void setReferenceUid(Context ctx, String referenceUid) {
        getPrefs(ctx).edit()
          .putString(PREF_REFERENCE_UID, referenceUid)
          .commit();
    }

    static String getImapFolder(Context ctx) {
        return getPrefs(ctx).getString(PREF_IMAP_FOLDER, DEFAULT_IMAP_FOLDER);
    }

    static String getCallLogFolder(Context ctx) {
        return getPrefs(ctx).getString(PREF_IMAP_FOLDER_CALLLOG, DEFAULT_IMAP_FOLDER_CALLLOG);
    }

    static boolean getMailSubjectPrefix(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_MAIL_SUBJECT_PREFIX, DEFAULT_MAIL_SUBJECT_PREFIX);
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
      return (getPrefs(ctx).getBoolean(PREF_WIFI_ONLY, false));
    }

    static boolean isAllow3rdPartyIntegration(Context ctx) {
      return (getPrefs(ctx).getBoolean(PREF_THIRD_PARTY_INTEGRATION, false));
    }

    private static int getStringAsInt(Context ctx, String key, int def) {
        try {
          String s = getPrefs(ctx).getString(key, null);
          if (s == null) return def;

          return Integer.valueOf(s);
        } catch (NumberFormatException e) {
          return def;
        }
      }

    /**
     * @param imapFolder the folder
     * @return whether an IMAP folder is valid.
     */
    static boolean isValidImapFolder(String imapFolder) {
        return !(imapFolder == null || imapFolder.length() == 0) &&
               !(imapFolder.charAt(0) == ' ' || imapFolder.charAt(imapFolder.length() - 1) == ' ');

    }

    static boolean isEnableAutoSync(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_ENABLE_AUTO_SYNC,
                DEFAULT_ENABLE_AUTO_SYNC);
    }

    static int getIncomingTimeoutSecs(Context ctx) {
       return getStringAsInt(ctx, PREF_INCOMING_TIMEOUT_SECONDS, DEFAULT_INCOMING_TIMEOUT_SECONDS);
    }

    static int getRegularTimeoutSecs(Context ctx) {
        return getStringAsInt(ctx, PREF_REGULAR_TIMEOUT_SECONDS, DEFAULT_REGULAR_TIMEOUT_SECONDS);
    }

    static boolean getMarkAsRead(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_MARK_AS_READ, DEFAULT_MARK_AS_READ);
    }

    static boolean getMarkAsReadOnRestore(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_MARK_AS_READ_ON_RESTORE, DEFAULT_MARK_AS_READ_ON_RESTORE);
    }

    static boolean isFirstSync(Context ctx) {
        return !getPrefs(ctx).contains(PREF_MAX_SYNCED_DATE_SMS);
    }

    static boolean isFirstUse(Context ctx) {
        final String key = "first_use";

        if (isFirstSync(ctx) &&
            !getPrefs(ctx).contains(key)) {
            getPrefs(ctx).edit().putBoolean(key, false).commit();
            return true;
        } else {
            return false;
        }
    }

    static void clearOauthData(Context ctx) {
        getPrefs(ctx).edit()
          .remove(PREF_OAUTH_USER)
          .commit();

        getCredentials(ctx).edit()
          .remove(PREF_OAUTH_TOKEN)
          .remove(PREF_OAUTH_TOKEN_SECRET)
          .commit();
    }

    static void clearLastSyncData(Context ctx) {
        getPrefs(ctx).edit()
          .remove(PREF_MAX_SYNCED_DATE_SMS)
          .remove(PREF_MAX_SYNCED_DATE_MMS)
          .remove(PREF_MAX_SYNCED_DATE_CALLLOG)
          .commit();
    }

    static boolean isNotificationEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean("notifications", false);
    }
    
    static boolean confirmAction(Context ctx) {
    	return getPrefs(ctx).getBoolean("confirm_action", false);
    }

    static String getServerAddress(Context ctx) {
        return getPrefs(ctx).getString(PREF_SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS);
    }

    static String getServerProtocol(Context ctx) {
        return getPrefs(ctx).getString(PREF_SERVER_PROTOCOL, DEFAULT_SERVER_PROTOCOL);
    }

    static boolean isGmail(Context ctx) {
        return "imap.gmail.com:993".equalsIgnoreCase(getServerAddress(ctx));
    }

	static String encode(String s) {
      return s == null ? "" : URLEncoder.encode(s);
    }

    static String getStoreUri(Context ctx) {
        if (useXOAuth(ctx)) {
          XOAuthConsumer consumer = getOAuthConsumer(ctx);

          return String.format(Consts.IMAP_URI,
               DEFAULT_SERVER_PROTOCOL,
                "xoauth:" + encode(consumer.getUsername()),
               encode(consumer.generateXOAuthString()),
               getServerAddress(ctx));
        } else {
            return String.format(Consts.IMAP_URI,
               getServerProtocol(ctx),
               encode(getImapUsername(ctx)),
               encode(getImapPassword(ctx)).replace("+", "%20"),
               getServerAddress(ctx));
        }
    }

    static String getVersion(Context context, boolean code) {
      android.content.pm.PackageInfo pInfo;
      try {
        pInfo = context.getPackageManager().getPackageInfo(
                SmsSync.class.getPackage().getName(),
                PackageManager.GET_META_DATA);
        return ""+ (code ? pInfo.versionCode : pInfo.versionName);
      } catch (PackageManager.NameNotFoundException e) {
        Log.e(TAG, "error", e);
        return null;
      }
    }

    static boolean isInstalledOnSDCard(Context context) {
      android.content.pm.PackageInfo pInfo;
      try {
        pInfo = context.getPackageManager().getPackageInfo(
                SmsSync.class.getPackage().getName(),
                PackageManager.GET_META_DATA);

        return (pInfo.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
      } catch (PackageManager.NameNotFoundException e) {
        Log.e(TAG, "error", e);
        return false;
      }
    }

    static boolean showUpgradeMessage(Context ctx) {
      final String key = "upgrade_message_seen";
      boolean seen = getPrefs(ctx).getBoolean(key, false);
      if (!seen && isOldSmsBackupInstalled(ctx)) {
        getPrefs(ctx).edit().putBoolean(key, true).commit();
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

    // move credentials from default shared prefs to new separate prefs
    static boolean upgradeCredentials(Context ctx) {
      final String flag = "upgraded_credentials";
      SharedPreferences prefs = getPrefs(ctx);

      boolean upgraded = prefs.getBoolean(flag, false);
      if (!upgraded) {
        Log.d(TAG, "Upgrading credentials");

        SharedPreferences creds = getCredentials(ctx);
        SharedPreferences.Editor prefsEditor = prefs.edit();
        SharedPreferences.Editor credsEditor = creds.edit();

        for (String field : new String[] { PREF_OAUTH_TOKEN,
                                           PREF_OAUTH_TOKEN_SECRET,
                                           PREF_LOGIN_PASSWORD }) {

          if (prefs.getString(field, null) != null &&
              creds.getString(field, null) == null) {
              if (LOCAL_LOGV) Log.v(TAG, "Moving credential " + field);
              credsEditor.putString(field, prefs.getString(field, null));
              prefsEditor.remove(field);
          } else if (LOCAL_LOGV) Log.v(TAG, "Skipping field " + field);
        }
        boolean success = false;
        if (credsEditor.commit()) {
          prefsEditor.putBoolean(flag, true);
          success = prefsEditor.commit();
        }
        return success;
      } else {
        return false;
      }
    }
}
