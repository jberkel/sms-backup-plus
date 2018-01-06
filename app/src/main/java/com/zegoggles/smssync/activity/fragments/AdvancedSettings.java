package com.zegoggles.smssync.activity.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.Dialogs;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthMode;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.utils.ListPreferenceHelper;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import static com.zegoggles.smssync.activity.Dialogs.Type.INVALID_IMAP_FOLDER;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.BACKUP_CONTACT_GROUP;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_BACKUP_AFTER_CALL;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR_ENABLED;
import static com.zegoggles.smssync.preferences.Preferences.Keys.IMAP_SETTINGS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_RESTORE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_SYNC;

public class AdvancedSettings extends SMSBackupPreferenceFragment {
    void updateMaxItems(String prefKey, int currentValue, String newValue) {
        Preference pref = findPreference(prefKey);
        if (newValue == null) {
            newValue = String.valueOf(currentValue);
        }
        // XXX
        pref.setTitle("-1".equals(newValue) ? getString(R.string.all_messages) : newValue);
    }

    boolean checkValidImapFolder(Preference preference, String imapFolder) {
        if (BackupImapStore.isValidImapFolder(imapFolder)) {
            preference.setTitle(imapFolder);
            return true;
        } else {
            INVALID_IMAP_FOLDER.instantiate(getActivity(), null).show(getFragmentManager(), null);
            return false;
        }
    }

    public static class Backup extends AdvancedSettings {
        @Override
        public void onResume() {
            super.onResume();
            updateMaxItemsPerSync(null);
            updateImapFolderLabelFromPref();
            updateImapCallogFolderLabelFromPref();
            updateBackupContactGroupLabelFromPref();
            updateLastBackupTimes();
            initGroups();
            registerValidImapFolderCheck();
            findPreference(MAX_ITEMS_PER_SYNC.key)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        updateMaxItemsPerSync(newValue.toString());
                        return true;
                    }
                });

            addPreferenceListener(
                DataType.SMS.backupEnabledPreference,
                DataType.MMS.backupEnabledPreference,
                DataType.CALLLOG.backupEnabledPreference);
        }

        private void updateImapFolderLabelFromPref() {
            String imapFolder = preferences.getDataTypePreferences().getFolder(SMS);
            findPreference(SMS.folderPreference).setTitle(imapFolder);
        }

        private void updateImapCallogFolderLabelFromPref() {
            String imapFolder = preferences.getDataTypePreferences().getFolder(CALLLOG);
            findPreference(CALLLOG.folderPreference).setTitle(imapFolder);
        }

        private void updateMaxItemsPerSync(String newValue) {
            updateMaxItems(MAX_ITEMS_PER_SYNC.key, preferences.getMaxItemsPerSync(), newValue);
        }

        private void updateLastBackupTimes() {
            for (DataType type : DataType.values()) {
                findPreference(type.backupEnabledPreference).setSummary(
                        getLastSyncText(preferences.getDataTypePreferences().getMaxSyncedDate(type))
                );
            }
        }

        private String getLastSyncText(final long lastSync) {
            return getString(R.string.status_idle_details,
                    lastSync < 0 ? getString(R.string.status_idle_details_never) :
                            DateFormat.getDateTimeInstance().format(new Date(lastSync)));
        }

        private void updateBackupContactGroupLabelFromPref() {
            final ListPreference groupPref = (ListPreference)
                    findPreference(BACKUP_CONTACT_GROUP.key);

            groupPref.setTitle(groupPref.getEntry() != null ? groupPref.getEntry() :
                    getString(R.string.ui_backup_contact_group_label));
        }

        private void registerValidImapFolderCheck() {
            findPreference(SMS.folderPreference)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, final Object newValue) {
                            return checkValidImapFolder(preference, newValue.toString());
                        }
                    });
        }

        private void initGroups() {
            ContactAccessor contacts = ContactAccessor.Get.instance();
            ListPreferenceHelper.initListPreference((ListPreference) findPreference(BACKUP_CONTACT_GROUP.key),
                    contacts.getGroups(getContext().getContentResolver(), getResources()), false);
        }

        public static class CallLog extends AdvancedSettings {
            @Override
            public void onResume() {
                super.onResume();
                updateCallLogCalendarLabelFromPref();
                initCalendars();
                registerValidCallLogFolderCheck();

                addPreferenceListener(CALLLOG_BACKUP_AFTER_CALL.key);
            }

            private void updateCallLogCalendarLabelFromPref() {
                final ListPreference calendarPref = (ListPreference)
                        findPreference(CALLLOG_SYNC_CALENDAR.key);

                calendarPref.setTitle(calendarPref.getEntry() != null ? calendarPref.getEntry() :
                        getString(R.string.ui_backup_calllog_sync_calendar_label));
            }

            private void initCalendars() {
                final ListPreference calendarPref = (ListPreference)
                        findPreference(CALLLOG_SYNC_CALENDAR.key);
                CalendarAccessor calendars = CalendarAccessor.Get.instance(getContext().getContentResolver());
                boolean enabled = ListPreferenceHelper.initListPreference(calendarPref, calendars.getCalendars(), false);

                findPreference(CALLLOG_SYNC_CALENDAR_ENABLED.key).setEnabled(enabled);
            }

            private void registerValidCallLogFolderCheck() {
                findPreference(CALLLOG.folderPreference)
                        .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                            public boolean onPreferenceChange(Preference preference, final Object newValue) {
                                return checkValidImapFolder(preference, newValue.toString());
                            }
                        });
            }
        }
    }

    public static class Restore extends AdvancedSettings {
        @Override
        public void onResume() {
            super.onResume();
            updateMaxItemsPerRestore(null);
            findPreference(MAX_ITEMS_PER_RESTORE.key)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            updateMaxItemsPerRestore(newValue.toString());
                            return true;
                        }
                    });
        }

        private void updateMaxItemsPerRestore(String newValue) {
            updateMaxItems(MAX_ITEMS_PER_RESTORE.key, preferences.getMaxItemsPerRestore(), newValue);
        }
    }

    public static class Server extends SMSBackupPreferenceFragment {
        private AuthPreferences authPreferences;

        @Override
        public void onCreatePreferences(Bundle bundle, String rootKey) {
            super.onCreatePreferences(bundle, rootKey);
            authPreferences = new AuthPreferences(getContext());
        }

        @Override
        public void onResume() {
            super.onResume();
            updateUsernameLabel(null);
            updateImapSettings(!authPreferences.useXOAuth());

            findPreference(AuthPreferences.SERVER_AUTHENTICATION)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            final boolean plain = (AuthMode.PLAIN) ==
                                    AuthMode.valueOf(newValue.toString().toUpperCase(Locale.ENGLISH));
//                            updateConnected().setEnabled(!plain);
                            updateImapSettings(plain);
                            return true;
                        }
                    });


            findPreference(AuthPreferences.LOGIN_USER)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            updateUsernameLabel(newValue.toString());
                            return true;
                        }
                    });

            findPreference(AuthPreferences.LOGIN_PASSWORD)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            authPreferences.setImapPassword(newValue.toString());
                            return true;
                        }
                    });
        }

        private void updateImapSettings(boolean enabled) {
            findPreference(IMAP_SETTINGS.key).setEnabled(enabled);
        }

        private void updateUsernameLabel(String username) {
            if (username == null) {
                username = authPreferences.getImapUsername();
                if (username == null) {
                    username = getString(R.string.ui_login_label);
                }
            }
            findPreference(AuthPreferences.LOGIN_USER).setTitle(username);
        }
    }
}
