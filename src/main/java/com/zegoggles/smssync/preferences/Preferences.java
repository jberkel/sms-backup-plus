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
import static com.zegoggles.smssync.preferences.Preferences.Keys.APP_LOG;
import static com.zegoggles.smssync.preferences.Preferences.Keys.APP_LOG_DEBUG;
import static com.zegoggles.smssync.preferences.Preferences.Keys.BACKUP_CONTACT_GROUP;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR_ENABLED;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CONFIRM_ACTION;
import static com.zegoggles.smssync.preferences.Preferences.Keys.ENABLE_AUTO_BACKUP;
import static com.zegoggles.smssync.preferences.Preferences.Keys.FIRST_USE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.INCOMING_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.LAST_VERSION_CODE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAIL_SUBJECT_PREFIX;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MARK_AS_READ;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MARK_AS_READ_ON_RESTORE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MARK_AS_READ_TYPES;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_RESTORE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_SYNC;
import static com.zegoggles.smssync.preferences.Preferences.Keys.NOTIFICATIONS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.REFERENCE_UID;
import static com.zegoggles.smssync.preferences.Preferences.Keys.REGULAR_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.RESTORE_STARRED_ONLY;
import static com.zegoggles.smssync.preferences.Preferences.Keys.SMS_DEFAULT_PACKAGE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.SMS_DEFAULT_PACKAGE_CHANGE_SEEN;
import static com.zegoggles.smssync.preferences.Preferences.Keys.THIRD_PARTY_INTEGRATION;
import static com.zegoggles.smssync.preferences.Preferences.Keys.WIFI_ONLY;

public class Preferences {
    private final Context context;
    private final SharedPreferences preferences;

    public Preferences(Context context) {
        this.context = context.getApplicationContext();
        this.preferences =  PreferenceManager.getDefaultSharedPreferences(this.context);
    }

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
        @Deprecated
        MARK_AS_READ("mark_as_read"),
        MARK_AS_READ_TYPES("mark_as_read_types"),
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
        SMS_DEFAULT_PACKAGE("sms_default_package"),
        SMS_DEFAULT_PACKAGE_CHANGE_SEEN("sms_default_package_change_seen"),
        ;

        public final String key;
        private Keys(String key) {
            this.key = key;
        }
    }

    public boolean isAppLogEnabled() {
        return preferences.getBoolean(APP_LOG.key, false);
    }

    public boolean isAppLogDebug() {
        return  isAppLogEnabled() &&
                preferences.getBoolean(APP_LOG_DEBUG.key, false);
    }

    public ContactGroup getBackupContactGroup() {
        return new ContactGroup(getStringAsInt(BACKUP_CONTACT_GROUP, -1));
    }

    public boolean isCallLogCalendarSyncEnabled() {
        return getCallLogCalendarId() >= 0 &&
                    preferences.getBoolean(CALLLOG_SYNC_CALENDAR_ENABLED.key, false);
    }


    public int getCallLogCalendarId() {
        return getStringAsInt(CALLLOG_SYNC_CALENDAR, -1);
    }

    public boolean isRestoreStarredOnly() {
        return preferences.getBoolean(RESTORE_STARRED_ONLY.key, false);
    }

    public String getReferenceUid() {
        return preferences.getString(REFERENCE_UID.key, null);
    }

    public void setReferenceUid(String referenceUid) {
        preferences.edit()
                .putString(REFERENCE_UID.key, referenceUid)
                .commit();
    }

    public boolean getMailSubjectPrefix() {
        return preferences.getBoolean(MAIL_SUBJECT_PREFIX.key, Defaults.MAIL_SUBJECT_PREFIX);
    }

    public int getMaxItemsPerSync() {
        return getStringAsInt(MAX_ITEMS_PER_SYNC, Defaults.MAX_ITEMS_PER_SYNC);
    }

    public int getMaxItemsPerRestore() {
        return getStringAsInt(MAX_ITEMS_PER_RESTORE, Defaults.MAX_ITEMS_PER_RESTORE);
    }

    public boolean isWifiOnly() {
        return preferences.getBoolean(WIFI_ONLY.key, false);
    }

    public boolean isAllow3rdPartyIntegration() {
        return preferences.getBoolean(THIRD_PARTY_INTEGRATION.key, false);
    }

    private int getStringAsInt(Keys key, int def) {
        return getStringAsInt(key.key, def);
    }

    private int getStringAsInt(String key, int def) {
        try {
            String s = preferences.getString(key, null);
            if (s == null) return def;

            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean isEnableAutoSync() {
        return preferences.getBoolean(ENABLE_AUTO_BACKUP.key, Defaults.ENABLE_AUTO_SYNC);
    }

    public int getIncomingTimeoutSecs() {
        return getStringAsInt(INCOMING_TIMEOUT_SECONDS, Defaults.INCOMING_TIMEOUT_SECONDS);
    }

    public int getRegularTimeoutSecs() {
        return getStringAsInt(REGULAR_TIMEOUT_SECONDS, Defaults.REGULAR_TIMEOUT_SECONDS);
    }

    public void migrateMarkAsRead() {
        if (preferences.contains(MARK_AS_READ.key)) {
            SharedPreferences.Editor editor = preferences.edit();
            boolean markAsRead = preferences.getBoolean(MARK_AS_READ.key, true);
            editor.putString(MARK_AS_READ_TYPES.key, markAsRead ? MarkAsReadTypes.READ.name() : MarkAsReadTypes.UNREAD.name());
            editor.remove(MARK_AS_READ.key);
            editor.commit();
        }
    }

    public MarkAsReadTypes getMarkAsReadType() {
        return getDefaultType(MARK_AS_READ_TYPES.key, MarkAsReadTypes.class, MarkAsReadTypes.READ);
    }

    public boolean getMarkAsReadOnRestore() {
        return preferences.getBoolean(MARK_AS_READ_ON_RESTORE.key, Defaults.MARK_AS_READ_ON_RESTORE);
    }

    public boolean isFirstBackup() {
        for (DataType type : DataType.values()) {
            if (preferences.contains(type.maxSyncedPreference)) {
                return false;
            }
        }
        return true;
    }

    public boolean isFirstUse() {
        if (isFirstBackup() && !preferences.contains(FIRST_USE.key)) {
            preferences.edit().putBoolean(FIRST_USE.key, false).commit();
            return true;
        } else {
            return false;
        }
    }

    public boolean setSmsDefaultPackage(String smsPackage) {
        return preferences.edit().putString(SMS_DEFAULT_PACKAGE.key, smsPackage).commit();
    }

    public String getSmsDefaultPackage() {
        return preferences.getString(SMS_DEFAULT_PACKAGE.key, null);
    }

    public boolean hasSeenSmsDefaultPackageChangeDialog() {
        return preferences.contains(SMS_DEFAULT_PACKAGE_CHANGE_SEEN.key);
    }

    public boolean setSeenSmsDefaultPackageChangeDialog() {
        return preferences.edit().putBoolean(SMS_DEFAULT_PACKAGE_CHANGE_SEEN.key, true).commit();
    }

    public void reset() {
        preferences.edit()
                .remove(SMS_DEFAULT_PACKAGE_CHANGE_SEEN.key)
                .remove(SMS_DEFAULT_PACKAGE.key)
                .commit();
    }

    public boolean isNotificationEnabled() {
        return preferences.getBoolean(NOTIFICATIONS.key, false);
    }

    public boolean confirmAction() {
        return preferences.getBoolean(CONFIRM_ACTION.key, false);
    }

    public String getVersion(boolean code) {
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
    public boolean isInstalledOnSDCard() {
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

    public boolean shouldShowUpgradeMessage() {
        final String key = "upgrade_message_seen";
        boolean seen = preferences.getBoolean(key, false);
        if (!seen && isOldSmsBackupInstalled()) {
            preferences.edit().putBoolean(key, true).commit();
            return true;
        } else {
            return false;
        }
    }

    public boolean shouldShowAboutDialog() {
        int code;
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA);
            code = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "error", e);
            code = -1;
        }

        int lastSeenCode = preferences.getInt(LAST_VERSION_CODE.key, -1);
        if (lastSeenCode < code) {
            preferences.edit().putInt(LAST_VERSION_CODE.key, code).commit();
            return true;
        } else {
            return false;
        }
    }

    boolean isOldSmsBackupInstalled() {
        try {
            context.getPackageManager().getPackageInfo(
                    "tv.studer.smssync",
                    PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    boolean isWhatsAppInstalled() {
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
    public boolean isWhatsAppInstalledAndPrefNotSet() {
        return isWhatsAppInstalled() && !preferences.contains(DataType.WHATSAPP.backupEnabledPreference);
    }

    <T extends Enum<T>> T getDefaultType(String pref, Class<T> tClazz, T defaultType) {
        try {
            final String s = preferences.getString(pref, null);
            return s == null ? defaultType : Enum.valueOf(tClazz, s.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "getDefaultType(" + pref + ")", e);
            return defaultType;
        }
    }
}
