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

package com.zegoggles.smssync.preferences;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.text.TextUtils;
import android.util.Log;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.activity.MainActivity;
import com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity;
import com.zegoggles.smssync.auth.XOAuthConsumer;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.DataType;
import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.DataType.*;

public class PrefStore {

    /**
     * Preference key containing the Google account username.
     */
    public static final String PREF_LOGIN_USER = "login_user";
    /**
     * Preference key containing the Google account password.
     */
    public static final String PREF_LOGIN_PASSWORD = "login_password";
    public static final String PREF_SERVER_AUTHENTICATION = "server_authentication";
    /**
     * Preference key containing the IMAP folder name where SMS should be backed up to.
     */
    public static final String PREF_IMAP_FOLDER = "imap_folder";
    /**
     * Preference key containing the IMAP folder name where call logs should be backed up to.
     */
    public static final String PREF_IMAP_FOLDER_CALLLOG = "imap_folder_calllog";
    /**
     * Preference key for storing whether to enable auto sync or not.
     */
    public static final String PREF_ENABLE_AUTO_SYNC = "enable_auto_sync";
    /**
     * Preference key for the timeout between an SMS is received and the scheduled sync.
     */
    public static final String PREF_INCOMING_TIMEOUT_SECONDS = "auto_backup_incoming_schedule";
    /**
     * Preference key for the interval between backup of outgoing SMS.
     */
    public static final String PREF_REGULAR_TIMEOUT_SECONDS = "auto_backup_schedule";
    /**
     * Preference for storing the maximum items per sync.
     */
    public static final String PREF_MAX_ITEMS_PER_SYNC = "max_items_per_sync";
    /**
     * Preference for storing the maximum items per restore.
     */
    public static final String PREF_MAX_ITEMS_PER_RESTORE = "max_items_per_restore";
    public static final String PREF_BACKUP_SMS = "backup_sms";
    public static final String PREF_BACKUP_MMS = "backup_mms";
    public static final String PREF_BACKUP_CALLLOG = "backup_calllog";
    public static final String PREF_CALLLOG_SYNC_CALENDAR = "backup_calllog_sync_calendar";
    public static final String PREF_CALLLOG_SYNC_CALENDAR_ENABLED = "backup_calllog_sync_calendar_enabled";
    public static final String PREF_BACKUP_CONTACT_GROUP = "backup_contact_group";
    public static final String PREF_CONNECTED = "connected";
    public static final String PREF_WIFI_ONLY = "wifi_only";
    /**
     * Default value for {@link PrefStore#PREF_MAX_SYNCED_DATE_SMS}.
     */
    public static final long DEFAULT_MAX_SYNCED_DATE = -1;
    /**
     * Preference key containing the maximum date of messages that were
     * successfully synced.
     */
    private static final String PREF_MAX_SYNCED_DATE_SMS = "max_synced_date";
    private static final String PREF_MAX_SYNCED_DATE_MMS = "max_synced_date_mms";
    private static final String PREF_MAX_SYNCED_DATE_CALLLOG = "max_synced_date_calllog";
    private static final String PREF_MAX_SYNCED_DATE_WHATSAPP = "max_synced_date_whatsapp";
    /**
     * Preference key containing a UID used for the threading reference header.
     */
    private static final String PREF_REFERENCE_UID = "reference_uid";
    /**
     * Preference key containing the server address
     */
    private static final String PREF_SERVER_ADDRESS = "server_address";
    /**
     * Preference key containing the server protocol
     */
    private static final String PREF_SERVER_PROTOCOL = "server_protocol";
    private static final String PREF_OAUTH_TOKEN = "oauth_token";
    private static final String PREF_OAUTH2_TOKEN = "oauth2_token";
    private static final String PREF_OAUTH_TOKEN_SECRET = "oauth_token_secret";
    private static final String PREF_OAUTH_USER = "oauth_user";
    private static final String PREF_OAUTH2_USER = "oauth2_user";
    /**
     * Preference key containing the IMAP folder name where WhatsApp messages should be backed up to.
     */
    private static final String PREF_IMAP_FOLDER_WHATSAPP = "imap_folder_whatsapp";
    /**
     * Preference key containing the IMAP folder name where SMS should be backed up to.
     */
    private static final String PREF_MAIL_SUBJECT_PREFIX = "mail_subject_prefix";
    /**
     * Preference for storing the maximum items per restore.
     */
    private static final String PREF_RESTORE_STARRED_ONLY = "restore_starred_only";
    /**
     * Preference for storing whether backed up messages should be marked as read on Gmail.
     */
    private static final String PREF_MARK_AS_READ = "mark_as_read";
    /**
     * Preference for storing whether restored messages should be marked as read.
     */
    private static final String PREF_MARK_AS_READ_ON_RESTORE = "mark_as_read_on_restore";
    private static final String PREF_EMAIL_ADDRESS_STYLE = "email_address_style";
    private static final String PREF_RESTORE_SMS = "restore_sms";
    private static final String PREF_BACKUP_WHATSAPP = "backup_whatsapp";
    private static final String PREF_RESTORE_CALLLOG = "restore_calllog";
    private static final String PREF_CALLLOG_TYPES = "backup_calllog_types";
    private static final String PREF_THIRD_PARTY_INTEGRATION = "third_party_integration";
    private static final String PREF_APP_LOG = "app_log";
    /**
     * Default value for {@link PrefStore#PREF_IMAP_FOLDER}.
     */
    private static final String DEFAULT_IMAP_FOLDER = "SMS";
    /**
     * Default value for {@link PrefStore#PREF_IMAP_FOLDER_CALLLOG}.
     */
    private static final String DEFAULT_IMAP_FOLDER_CALLLOG = "Call log";
    /**
     * Default value for {@link PrefStore#PREF_IMAP_FOLDER_WHATSAPP}.
     */
    private static final String DEFAULT_IMAP_FOLDER_WHATSAPP = "WhatsApp";
    /**
     * Default value for {@link PrefStore#PREF_MAIL_SUBJECT_PREFIX}.
     */
    private static final boolean DEFAULT_MAIL_SUBJECT_PREFIX = false;
    /**
     * Default value for {@link PrefStore#PREF_ENABLE_AUTO_SYNC}.
     */
    private static final boolean DEFAULT_ENABLE_AUTO_SYNC = false;
    /**
     * Default value for {@link PrefStore#PREF_INCOMING_TIMEOUT_SECONDS}.
     */
    private static final int DEFAULT_INCOMING_TIMEOUT_SECONDS = 60 * 3;
    /**
     * Default value for {@link PrefStore#PREF_REGULAR_TIMEOUT_SECONDS}.
     */
    private static final int DEFAULT_REGULAR_TIMEOUT_SECONDS = 2 * 60 * 60; // 2h
    /**
     * Default value for {@link #PREF_MAX_ITEMS_PER_SYNC}.
     */
    private static final int DEFAULT_MAX_ITEMS_PER_SYNC = -1;
    private static final int DEFAULT_MAX_ITEMS_PER_RESTORE = -1;
    /**
     * Default value for {@link #PREF_MARK_AS_READ}.
     */
    private static final boolean DEFAULT_MARK_AS_READ = true;
    private static final boolean DEFAULT_MARK_AS_READ_ON_RESTORE = true;
    /**
     * Default value for {@link #PREF_SERVER_ADDRESS}.
     */
    private static final String DEFAULT_SERVER_ADDRESS = "imap.gmail.com:993";
    /**
     * Default value for {@link #PREF_SERVER_PROTOCOL}.
     */
    private static final String DEFAULT_SERVER_PROTOCOL = "+ssl+";
    private static final String LAST_VERSION_CODE = "last_version_code";

    public static boolean isAppLogEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_APP_LOG, false);
    }

    static SharedPreferences getPrefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    // All sensitive information is stored in a separate prefs file so we can
    // backup the rest without exposing sensitive data
    static SharedPreferences getCredentials(Context ctx) {
        return ctx.getSharedPreferences("credentials", Context.MODE_PRIVATE);
    }

    public static long getMostRecentSyncedDate(Context ctx) {
        return Math.max(Math.max(
                getMaxSyncedDate(ctx, SMS),
                getMaxSyncedDate(ctx, CALLLOG)),
            getMaxSyncedDate(ctx, MMS) * 1000);
    }

    @Deprecated
    private static long getMaxSyncedDateSms(Context ctx) {
        return getPrefs(ctx).getLong(PREF_MAX_SYNCED_DATE_SMS, DEFAULT_MAX_SYNCED_DATE);
    }

    @Deprecated
    private static long getMaxSyncedDateMms(Context ctx) {
        return getPrefs(ctx).getLong(PREF_MAX_SYNCED_DATE_MMS, DEFAULT_MAX_SYNCED_DATE);
    }

    @Deprecated
    private static long getMaxSyncedDateCallLog(Context ctx) {
        return getPrefs(ctx).getLong(PREF_MAX_SYNCED_DATE_CALLLOG, DEFAULT_MAX_SYNCED_DATE);
    }

    @Deprecated
    private static long getMaxSyncedDateWhatsApp(Context ctx) {
        return getPrefs(ctx).getLong(PREF_MAX_SYNCED_DATE_WHATSAPP, DEFAULT_MAX_SYNCED_DATE);
    }

    @Deprecated
    private static void setMaxSyncedDateSms(Context ctx, long maxSyncedDate) {
        getPrefs(ctx).edit()
                .putLong(PREF_MAX_SYNCED_DATE_SMS, maxSyncedDate)
                .commit();
    }

    @Deprecated
    private static void setMaxSyncedDateMms(Context ctx, long maxSyncedDate) {
        getPrefs(ctx).edit()
                .putLong(PREF_MAX_SYNCED_DATE_MMS, maxSyncedDate)
                .commit();
    }

    @Deprecated
    private static void setMaxSyncedDateCallLog(Context ctx, long maxSyncedDate) {
        getPrefs(ctx).edit()
                .putLong(PREF_MAX_SYNCED_DATE_CALLLOG, maxSyncedDate)
                .commit();
    }

    @Deprecated
    private static void setMaxSyncedDateWhatsApp(Context ctx, long maxSyncedDate) {
        getPrefs(ctx).edit()
                .putLong(PREF_MAX_SYNCED_DATE_WHATSAPP, maxSyncedDate)
                .commit();
    }

    @SuppressWarnings("deprecation")
    public static void setMaxSyncedDate(Context context, DataType dataType, long maxSyncedDate) {
        switch (dataType) {
            case MMS: setMaxSyncedDateMms(context, maxSyncedDate);
            case SMS: setMaxSyncedDateSms(context, maxSyncedDate);
            case CALLLOG: setMaxSyncedDateCallLog(context, maxSyncedDate);
            case WHATSAPP: setMaxSyncedDateWhatsApp(context, maxSyncedDate);
        }
    }

    @SuppressWarnings("deprecation")
    public static long getMaxSyncedDate(Context context, DataType dataType) {
        switch (dataType) {
            case MMS: return getMaxSyncedDateMms(context);
            case SMS: return getMaxSyncedDateSms(context);
            case CALLLOG: return getMaxSyncedDateCallLog(context);
            case WHATSAPP: return getMaxSyncedDateWhatsApp(context);
            default: return -1;
        }
    }


    static String getImapUsername(Context ctx) {
        return getPrefs(ctx).getString(PREF_LOGIN_USER, null);
    }

    static String getImapPassword(Context ctx) {
        return getCredentials(ctx).getString(PREF_LOGIN_PASSWORD, null);
    }

    public static void setImapPassword(Context ctx, String s) {
        getCredentials(ctx).edit().putString(PREF_LOGIN_PASSWORD, s).commit();
    }

    public static XOAuthConsumer getOAuthConsumer(Context ctx) {
        return new XOAuthConsumer(
                getOauthUsername(ctx),
                getOauthToken(ctx),
                getOauthTokenSecret(ctx));
    }

    static String getOauthToken(Context ctx) {
        return getCredentials(ctx).getString(PREF_OAUTH_TOKEN, null);
    }

    public static String getOauth2Token(Context ctx) {
        return getCredentials(ctx).getString(PREF_OAUTH2_TOKEN, null);
    }

    static String getOauthTokenSecret(Context ctx) {
        return getCredentials(ctx).getString(PREF_OAUTH_TOKEN_SECRET, null);
    }

    public static boolean hasOauthTokens(Context ctx) {
        return getOauthUsername(ctx) != null &&
                getOauthToken(ctx) != null &&
                getOauthTokenSecret(ctx) != null;
    }

    public static boolean hasOAuth2Tokens(Context ctx) {
        return getOauth2Username(ctx) != null &&
                getOauth2Token(ctx) != null;
    }

    private static String getOauthUsername(Context ctx) {
        return getPrefs(ctx).getString(PREF_OAUTH_USER, null);
    }

    private static String getOauth2Username(Context ctx) {
        return getPrefs(ctx).getString(PREF_OAUTH2_USER, null);
    }

    public static String getUsername(Context ctx) {
        return getPrefs(ctx).getString(PREF_OAUTH_USER, getOauth2Username(ctx));
    }

    public static void setOauthUsername(Context ctx, String s) {
        getPrefs(ctx).edit().putString(PREF_OAUTH_USER, s).commit();
    }

    public static void setOauthTokens(Context ctx, String token, String secret) {
        getCredentials(ctx).edit()
                .putString(PREF_OAUTH_TOKEN, token)
                .putString(PREF_OAUTH_TOKEN_SECRET, secret)
                .commit();
    }

    public static void setOauth2Token(Context ctx, String username, String token) {
        getPrefs(ctx).edit()
                .putString(PREF_OAUTH2_USER, username)
                .commit();

        getCredentials(ctx).edit()
                .putString(PREF_OAUTH2_TOKEN, token)
                .commit();
    }

    static AuthMode getAuthMode(Context ctx) {
        return getDefaultType(ctx, PREF_SERVER_AUTHENTICATION, AuthMode.class, AuthMode.XOAUTH);
    }

    public static ContactGroup getBackupContactGroup(Context ctx) {
        return new ContactGroup(getStringAsInt(ctx, PREF_BACKUP_CONTACT_GROUP, -1));
    }

    public static boolean useXOAuth(Context ctx) {
        return getAuthMode(ctx) == AuthMode.XOAUTH && isGmail(ctx);
    }

    public static String getUserEmail(Context ctx) {
        switch (getAuthMode(ctx)) {
            case XOAUTH:
                return getUsername(ctx);
            default:
                return getImapUsername(ctx);
        }
    }

    public static boolean isLoginInformationSet(Context ctx) {
        switch (getAuthMode(ctx)) {
            case PLAIN:
                return !TextUtils.isEmpty(getImapPassword(ctx)) &&
                        !TextUtils.isEmpty(getImapUsername(ctx));
            case XOAUTH:
                return hasOauthTokens(ctx) || hasOAuth2Tokens(ctx);
            default:
                return false;
        }
    }

    @Deprecated
    private static boolean isSmsBackupEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_BACKUP_SMS, true);
    }

    @Deprecated
    private static boolean isMmsBackupEnabled(Context ctx) {
        final int version = android.os.Build.VERSION.SDK_INT;
        return version >= MainActivity.MIN_VERSION_MMS && getPrefs(ctx).getBoolean(PREF_BACKUP_MMS, false);
    }

    @Deprecated
    private static boolean isWhatsAppBackupEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_BACKUP_WHATSAPP, false);
    }

    @Deprecated
    private static boolean setWhatsAppBackupEnabled(Context ctx, boolean enabled) {
        return getPrefs(ctx).edit().putBoolean(PREF_BACKUP_WHATSAPP, enabled).commit();
    }

    @Deprecated
    private static boolean isCallLogBackupEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_BACKUP_CALLLOG, false);
    }

    @SuppressWarnings("deprecation")
    public static boolean isDataTypeBackupEnabled(Context context, DataType dataType) {
        switch (dataType) {
            case MMS: return isMmsBackupEnabled(context);
            case SMS: return isSmsBackupEnabled(context);
            case CALLLOG: return isCallLogBackupEnabled(context);
            case WHATSAPP: return isWhatsAppBackupEnabled(context);
            default: return false;
        }
    }

    public static void setBackupEnabled(Context context, DataType dataType, boolean enabled) {
        if (dataType == WHATSAPP) {
            //noinspection deprecation
            setWhatsAppBackupEnabled(context, enabled);
        }
    }

    public static boolean isCallLogCalendarSyncEnabled(Context ctx) {
        return
                getCallLogCalendarId(ctx) >= 0 &&
                        getPrefs(ctx).getBoolean(PREF_CALLLOG_SYNC_CALENDAR_ENABLED, false);
    }

    static <T extends Enum<T>> T getDefaultType(Context ctx, String pref, Class<T> tClazz,
                                                T defaultType) {
        try {
            final String s = getPrefs(ctx).getString(pref, null);
            return s == null ? defaultType : Enum.valueOf(tClazz, s.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "getDefaultType(" + pref + ")", e);
            return defaultType;
        }
    }

    static CallLogTypes getCallLogType(Context ctx) {
        return getDefaultType(ctx, PREF_CALLLOG_TYPES, CallLogTypes.class, CallLogTypes.EVERYTHING);
    }

    public static boolean isCallLogTypeEnabled(Context ctx, int type) {
        switch (getCallLogType(ctx)) {
            case OUTGOING:
                return type == CallLog.Calls.OUTGOING_TYPE;
            case INCOMING:
                return type == CallLog.Calls.INCOMING_TYPE;
            case MISSED:
                return type == CallLog.Calls.MISSED_TYPE;
            case INCOMING_OUTGOING:
                return type != CallLog.Calls.MISSED_TYPE;

            default:
                return true;
        }
    }

    public static int getCallLogCalendarId(Context ctx) {
        return getStringAsInt(ctx, PREF_CALLLOG_SYNC_CALENDAR, -1);
    }

    public static boolean isRestoreStarredOnly(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_RESTORE_STARRED_ONLY, false);
    }

    public static boolean isRestoreSms(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_RESTORE_SMS, true);
    }

    public static boolean isRestoreCallLog(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_RESTORE_CALLLOG, true);
    }

    public static String getReferenceUid(Context ctx) {
        return getPrefs(ctx).getString(PREF_REFERENCE_UID, null);
    }

    public static void setReferenceUid(Context ctx, String referenceUid) {
        getPrefs(ctx).edit()
                .putString(PREF_REFERENCE_UID, referenceUid)
                .commit();
    }

    public static String getImapFolder(Context ctx) {
        return getPrefs(ctx).getString(PREF_IMAP_FOLDER, DEFAULT_IMAP_FOLDER);
    }

    public static String getCallLogFolder(Context ctx) {
        return getPrefs(ctx).getString(PREF_IMAP_FOLDER_CALLLOG, DEFAULT_IMAP_FOLDER_CALLLOG);
    }

    public static String getWhatsAppFolder(Context ctx) {
        return getPrefs(ctx).getString(PREF_IMAP_FOLDER_WHATSAPP, DEFAULT_IMAP_FOLDER_WHATSAPP);
    }

    public static boolean getMailSubjectPrefix(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_MAIL_SUBJECT_PREFIX, DEFAULT_MAIL_SUBJECT_PREFIX);
    }

    public static int getMaxItemsPerSync(Context ctx) {
        return getStringAsInt(ctx, PREF_MAX_ITEMS_PER_SYNC, DEFAULT_MAX_ITEMS_PER_SYNC);
    }

    public static int getMaxItemsPerRestore(Context ctx) {
        return getStringAsInt(ctx, PREF_MAX_ITEMS_PER_RESTORE, DEFAULT_MAX_ITEMS_PER_RESTORE);
    }

    public static AddressStyle getEmailAddressStyle(Context ctx) {
        return getDefaultType(ctx, PREF_EMAIL_ADDRESS_STYLE, AddressStyle.class, AddressStyle.NAME);
    }

    public static boolean isWifiOnly(Context ctx) {
        return (getPrefs(ctx).getBoolean(PREF_WIFI_ONLY, false));
    }

    public static boolean isAllow3rdPartyIntegration(Context ctx) {
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
    public static boolean isValidImapFolder(String imapFolder) {
        return !(imapFolder == null || imapFolder.length() == 0) &&
                !(imapFolder.charAt(0) == ' ' || imapFolder.charAt(imapFolder.length() - 1) == ' ');

    }

    public static boolean isEnableAutoSync(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_ENABLE_AUTO_SYNC,
                DEFAULT_ENABLE_AUTO_SYNC);
    }

    public static int getIncomingTimeoutSecs(Context ctx) {
        return getStringAsInt(ctx, PREF_INCOMING_TIMEOUT_SECONDS, DEFAULT_INCOMING_TIMEOUT_SECONDS);
    }

    public static int getRegularTimeoutSecs(Context ctx) {
        return getStringAsInt(ctx, PREF_REGULAR_TIMEOUT_SECONDS, DEFAULT_REGULAR_TIMEOUT_SECONDS);
    }

    public static boolean getMarkAsRead(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_MARK_AS_READ, DEFAULT_MARK_AS_READ);
    }

    public static boolean getMarkAsReadOnRestore(Context ctx) {
        return getPrefs(ctx).getBoolean(PREF_MARK_AS_READ_ON_RESTORE, DEFAULT_MARK_AS_READ_ON_RESTORE);
    }

    public static boolean isFirstBackup(Context ctx) {
        SharedPreferences prefs = getPrefs(ctx);
        return !prefs.contains(PREF_MAX_SYNCED_DATE_SMS) &&
               !prefs.contains(PREF_MAX_SYNCED_DATE_MMS) &&
               !prefs.contains(PREF_MAX_SYNCED_DATE_CALLLOG) &&
               !prefs.contains(PREF_MAX_SYNCED_DATE_WHATSAPP);

    }

    public static boolean isFirstUse(Context ctx) {
        final String key = "first_use";

        if (isFirstBackup(ctx) &&
                !getPrefs(ctx).contains(key)) {
            getPrefs(ctx).edit().putBoolean(key, false).commit();
            return true;
        } else {
            return false;
        }
    }

    public static void clearOauthData(Context ctx) {
        final String oauth2token = getOauth2Token(ctx);

        getPrefs(ctx).edit()
                .remove(PREF_OAUTH_USER)
                .remove(PREF_OAUTH2_USER)
                .commit();

        getCredentials(ctx).edit()
                .remove(PREF_OAUTH_TOKEN)
                .remove(PREF_OAUTH_TOKEN_SECRET)
                .remove(PREF_OAUTH2_TOKEN)
                .commit();

        if (!TextUtils.isEmpty(oauth2token) && Integer.parseInt(Build.VERSION.SDK) >= 5) {
            AccountManagerAuthActivity.invalidateToken(ctx, oauth2token);
        }
    }

    public static void clearLastSyncData(Context ctx) {
        getPrefs(ctx).edit()
                .remove(PREF_MAX_SYNCED_DATE_SMS)
                .remove(PREF_MAX_SYNCED_DATE_MMS)
                .remove(PREF_MAX_SYNCED_DATE_CALLLOG)
                .remove(PREF_MAX_SYNCED_DATE_WHATSAPP)
                .commit();
    }

    public static boolean isNotificationEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean("notifications", false);
    }

    public static boolean confirmAction(Context ctx) {
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
        try {
            return s == null ? "" : URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getStoreUri(Context ctx) {
        if (useXOAuth(ctx)) {
            if (hasOauthTokens(ctx)) {
                XOAuthConsumer consumer = getOAuthConsumer(ctx);
                return String.format(Consts.IMAP_URI,
                        DEFAULT_SERVER_PROTOCOL,
                        "xoauth:" + encode(consumer.getUsername()),
                        encode(consumer.generateXOAuthString()),
                        getServerAddress(ctx));
            } else if (hasOAuth2Tokens(ctx)) {
                return String.format(Consts.IMAP_URI,
                        DEFAULT_SERVER_PROTOCOL,
                        "xoauth2:" + encode(getOauth2Username(ctx)),
                        encode(generateXOAuth2Token(ctx)),
                        getServerAddress(ctx));
            } else {
                Log.w(TAG, "No valid xoauth1/2 tokens");
                return null;
            }

        } else {
            return String.format(Consts.IMAP_URI,
                    getServerProtocol(ctx),
                    encode(getImapUsername(ctx)),
                    encode(getImapPassword(ctx)).replace("+", "%20"),
                    getServerAddress(ctx));
        }
    }

    /**
     * <p>
     * The SASL XOAUTH2 initial client response has the following format:
     * </p>
     * <code>base64("user="{User}"^Aauth=Bearer "{Access Token}"^A^A")</code>
     * <p>
     * For example, before base64-encoding, the initial client response might look like this:
     * </p>
     * <code>user=someuser@example.com^Aauth=Bearer vF9dft4qmTc2Nvb3RlckBhdHRhdmlzdGEuY29tCg==^A^A</code>
     * <p/>
     * <em>Note:</em> ^A represents a Control+A (\001).
     *
     * @see <a href="https://developers.google.com/google-apps/gmail/xoauth2_protocol#the_sasl_xoauth2_mechanism">
     *      The SASL XOAUTH2 Mechanism</a>
     */
    private static String generateXOAuth2Token(Context context) {
        final String username = getOauth2Username(context);
        final String token = getOauth2Token(context);
        final String formatted = "user=" + username + "\001auth=Bearer " + token + "\001\001";
        try {
            return new String(Base64.encodeBase64(formatted.getBytes("UTF-8")), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getVersion(Context context, boolean code) {
        PackageInfo pInfo;
        try {
            pInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA);
            return "" + (code ? pInfo.versionCode : pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "error", e);
            return null;
        }
    }

    @TargetApi(8)
    public static boolean isInstalledOnSDCard(Context context) {
        PackageInfo pInfo;
        try {
            pInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA);

            return (pInfo.applicationInfo.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "error", e);
            return false;
        }
    }

    public static boolean showUpgradeMessage(Context ctx) {
        final String key = "upgrade_message_seen";
        boolean seen = getPrefs(ctx).getBoolean(key, false);
        if (!seen && isOldSmsBackupInstalled(ctx)) {
            getPrefs(ctx).edit().putBoolean(key, true).commit();
            return true;
        } else {
            return false;
        }
    }

    public static boolean showAboutDialog(Context ctx) {
        int code;
        try {
            PackageInfo pInfo = ctx.getPackageManager().getPackageInfo(
                    ctx.getPackageName(),
                    PackageManager.GET_META_DATA);
            code = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "error", e);
            code = -1;
        }

        int lastSeenCode = getPrefs(ctx).getInt(LAST_VERSION_CODE, -1);
        if (lastSeenCode < code) {
            getPrefs(ctx).edit().putInt(LAST_VERSION_CODE, code).commit();
            return true;
        } else {
            return false;
        }
    }

    static boolean isOldSmsBackupInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(
                    "tv.studer.smssync",
                    PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean isWhatsAppInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(
                    "com.whatsapp",
                    PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isWhatsAppInstalledAndPrefNotSet(Context context) {
        return isWhatsAppInstalled(context) && !getPrefs(context).contains(PREF_BACKUP_WHATSAPP);
    }

    // move credentials from default shared prefs to new separate prefs
    public static boolean upgradeCredentials(Context ctx) {
        final String flag = "upgraded_credentials";
        SharedPreferences prefs = getPrefs(ctx);

        boolean upgraded = prefs.getBoolean(flag, false);
        if (!upgraded) {
            Log.d(TAG, "Upgrading credentials");

            SharedPreferences creds = getCredentials(ctx);
            SharedPreferences.Editor prefsEditor = prefs.edit();
            SharedPreferences.Editor credsEditor = creds.edit();

            for (String field : new String[]{PREF_OAUTH_TOKEN,
                    PREF_OAUTH_TOKEN_SECRET,
                    PREF_LOGIN_PASSWORD}) {

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
