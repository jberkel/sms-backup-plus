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
import android.preference.PreferenceManager;
import android.util.Log;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.DataType;

import java.util.Locale;

import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.preferences.Preferences.Keys.*;

public class Preferences {

    public enum Keys {
        ENABLE_AUTO_BACKUP("enable_auto_sync"),
        INCOMING_TIMEOUT_SECONDS("auto_backup_incoming_schedule"),
        REGULAR_TIMEOUT_SECONDS ("auto_backup_schedule"),
        MAX_ITEMS_PER_SYNC("max_items_per_sync"),
        MAX_ITEMS_PER_RESTORE ("max_items_per_restore"),
        CALLLOG_SYNC_CALENDAR ("backup_calllog_sync_calendar"),
        CALLLOG_SYNC_CALENDAR_ENABLED ("backup_calllog_sync_calendar_enabled"),
        BACKUP_CONTACT_GROUP("backup_contact_group"),
        CONNECTED("connected"),
        WIFI_ONLY("wifi_only"),
        REFERENCE_UID("reference_uid"),
        MAIL_SUBJECT_PREFIX("mail_subject_prefix"),
        RESTORE_STARRED_ONLY("restore_starred_only"),
        MARK_AS_READ("mark_as_read"),
        MARK_AS_READ_ON_RESTORE("mark_as_read_on_restore"),
        THIRD_PARTY_INTEGRATION("third_party_integration"),
        APP_LOG("app_log"),
        APP_LOG_DEBUG("app_log_debug"),
        LAST_VERSION_CODE("last_version_code"),
        CONFIRM_ACTION("confirm_action"),
        NOTIFICATIONS("notifications"),
        FIRST_USE("first_use"),
        IMAP_SETTINGS("imap_settings"),
        DONATE("donate"),
        BACKUP_SETTINGS_SCREEN("auto_backup_settings_screen"),
        ;

        public final String key;
        private Keys(String key) {
            this.key = key;
        }
    }

    public static boolean isAppLogEnabled(Context ctx) {
        return prefs(ctx).getBoolean(APP_LOG.key, false);
    }

    public static boolean isAppLogDebug(Context context) {
        return  isAppLogEnabled(context) &&
                prefs(context).getBoolean(APP_LOG_DEBUG.key, false);
    }

    static SharedPreferences prefs(Context ctx) {
        return PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    public static ContactGroup getBackupContactGroup(Context ctx) {
        return new ContactGroup(getStringAsInt(ctx, BACKUP_CONTACT_GROUP, -1));
    }

    public static boolean isCallLogCalendarSyncEnabled(Context ctx) {
        return getCallLogCalendarId(ctx) >= 0 &&
                        prefs(ctx).getBoolean(CALLLOG_SYNC_CALENDAR_ENABLED.key, false);
    }


    public static int getCallLogCalendarId(Context ctx) {
        return getStringAsInt(ctx, CALLLOG_SYNC_CALENDAR, -1);
    }

    public static boolean isRestoreStarredOnly(Context ctx) {
        return prefs(ctx).getBoolean(RESTORE_STARRED_ONLY.key, false);
    }

    public static String getReferenceUid(Context ctx) {
        return prefs(ctx).getString(REFERENCE_UID.key, null);
    }

    public static void setReferenceUid(Context ctx, String referenceUid) {
        prefs(ctx).edit()
                .putString(REFERENCE_UID.key, referenceUid)
                .commit();
    }

    public static boolean getMailSubjectPrefix(Context ctx) {
        return prefs(ctx).getBoolean(MAIL_SUBJECT_PREFIX.key, Defaults.MAIL_SUBJECT_PREFIX);
    }

    public static int getMaxItemsPerSync(Context ctx) {
        return getStringAsInt(ctx, MAX_ITEMS_PER_SYNC, Defaults.MAX_ITEMS_PER_SYNC);
    }

    public static int getMaxItemsPerRestore(Context ctx) {
        return getStringAsInt(ctx, MAX_ITEMS_PER_RESTORE, Defaults.MAX_ITEMS_PER_RESTORE);
    }

    public static boolean isWifiOnly(Context ctx) {
        return prefs(ctx).getBoolean(WIFI_ONLY.key, false);
    }

    public static boolean isAllow3rdPartyIntegration(Context ctx) {
        return prefs(ctx).getBoolean(THIRD_PARTY_INTEGRATION.key, false);
    }

    private static int getStringAsInt(Context ctx, Keys key, int def) {
        return getStringAsInt(ctx, key.key, def);
    }

    private static int getStringAsInt(Context ctx, String key, int def) {
        try {
            String s = prefs(ctx).getString(key, null);
            if (s == null) return def;

            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean isValidImapFolder(String imapFolder) {
        return !(imapFolder == null || imapFolder.length() == 0) &&
                !(imapFolder.charAt(0) == ' ' || imapFolder.charAt(imapFolder.length() - 1) == ' ');
    }

    public static boolean isEnableAutoSync(Context ctx) {
        return prefs(ctx).getBoolean(ENABLE_AUTO_BACKUP.key, Defaults.ENABLE_AUTO_SYNC);
    }

    public static boolean setEnableAutoSync(Context ctx, boolean enabled) {
        return prefs(ctx).edit().putBoolean(ENABLE_AUTO_BACKUP.key, enabled).commit();
    }


    public static int getIncomingTimeoutSecs(Context ctx) {
        return getStringAsInt(ctx, INCOMING_TIMEOUT_SECONDS, Defaults.INCOMING_TIMEOUT_SECONDS);
    }

    public static int getRegularTimeoutSecs(Context ctx) {
        return getStringAsInt(ctx, REGULAR_TIMEOUT_SECONDS, Defaults.REGULAR_TIMEOUT_SECONDS);
    }

    public static boolean getMarkAsRead(Context ctx) {
        return prefs(ctx).getBoolean(MARK_AS_READ.key, Defaults.MARK_AS_READ);
    }

    public static boolean getMarkAsReadOnRestore(Context ctx) {
        return prefs(ctx).getBoolean(MARK_AS_READ_ON_RESTORE.key, Defaults.MARK_AS_READ_ON_RESTORE);
    }

    public static boolean isFirstBackup(Context ctx) {
        SharedPreferences prefs = prefs(ctx);
        return !prefs.contains(DataType.PreferenceKeys.MAX_SYNCED_DATE_SMS);
    }

    public static boolean isFirstUse(Context ctx) {
        if (isFirstBackup(ctx) && !prefs(ctx).contains(FIRST_USE.key)) {
            prefs(ctx).edit().putBoolean(FIRST_USE.key, false).commit();
            return true;
        } else {
            return false;
        }
    }

    public static boolean isNotificationEnabled(Context ctx) {
        return prefs(ctx).getBoolean(NOTIFICATIONS.key, false);
    }

    public static boolean confirmAction(Context ctx) {
        return prefs(ctx).getBoolean(CONFIRM_ACTION.key, false);
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

    public static boolean shouldShowUpgradeMessage(Context ctx) {
        final String key = "upgrade_message_seen";
        boolean seen = prefs(ctx).getBoolean(key, false);
        if (!seen && isOldSmsBackupInstalled(ctx)) {
            prefs(ctx).edit().putBoolean(key, true).commit();
            return true;
        } else {
            return false;
        }
    }

    public static boolean shouldShowAboutDialog(Context ctx) {
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

        int lastSeenCode = prefs(ctx).getInt(LAST_VERSION_CODE.key, -1);
        if (lastSeenCode < code) {
            prefs(ctx).edit().putInt(LAST_VERSION_CODE.key, code).commit();
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

    @SuppressWarnings("UnusedDeclaration")
    public static boolean isWhatsAppInstalledAndPrefNotSet(Context context) {
        return isWhatsAppInstalled(context) && !prefs(context).contains(DataType.WHATSAPP.backupEnabledPreference);
    }

    static <T extends Enum<T>> T getDefaultType(Context ctx, String pref, Class<T> tClazz, T defaultType) {
        try {
            final String s = prefs(ctx).getString(pref, null);
            return s == null ? defaultType : Enum.valueOf(tClazz, s.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "getDefaultType(" + pref + ")", e);
            return defaultType;
        }
    }
}
