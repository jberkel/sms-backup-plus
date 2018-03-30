package com.zegoggles.smssync.activity.fragments;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;
import com.zegoggles.smssync.activity.events.ThemeChangedEvent;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.contacts.Group;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthMode;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.DataTypePreferences;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.WRITE_CALENDAR;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.activity.AppPermission.allGranted;
import static com.zegoggles.smssync.activity.Dialogs.Type.INVALID_IMAP_FOLDER;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.BACKUP_CONTACT_GROUP;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_BACKUP_AFTER_CALL;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR_ENABLED;
import static com.zegoggles.smssync.preferences.Preferences.Keys.DARK_THEME;
import static com.zegoggles.smssync.preferences.Preferences.Keys.IMAP_SETTINGS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_RESTORE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_SYNC;
import static com.zegoggles.smssync.utils.ListPreferenceHelper.initListPreference;

public class AdvancedSettings extends SMSBackupPreferenceFragment {

    public static class Main extends AdvancedSettings {
        @Override
        public void onResume() {
            super.onResume();
            addPreferenceListener(new ThemeChangedEvent(), DARK_THEME.key);
        }
    }

    public static class Backup extends AdvancedSettings {
        private static final int REQUEST_CALL_LOG_PERMISSIONS = 0;
        private CheckBoxPreference callLogPreference;

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            callLogPreference = (CheckBoxPreference) findPreference(CALLLOG.backupEnabledPreference);
        }

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

            addPreferenceListener(DataType.SMS.backupEnabledPreference, DataType.MMS.backupEnabledPreference);

            callLogPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    return !(Boolean) newValue || checkCallLogPermissions();
                }
            });

            preferences.getDataTypePreferences().registerDataTypeListener(new DataTypePreferences.DataTypeListener() {
                @Override
                public void onChanged(DataType dataType, DataTypePreferences preferences) {
                    if (dataType == DataType.CALLLOG) {
                        findPreference(AdvancedSettings.Backup.CallLog.class.getName())
                                .setEnabled(preferences.isBackupEnabled(dataType));
                    }
                }
            });
        }

        @Override
        public void onStop() {
            super.onStop();
            preferences.getDataTypePreferences().registerDataTypeListener(null);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == REQUEST_CALL_LOG_PERMISSIONS && allGranted(grantResults)) {
                callLogPreference.setChecked(true);
                App.post(new AutoBackupSettingsChangedEvent());
            }
        }

        private boolean checkCallLogPermissions() {
            final Set<String> requiredPermissions = CALLLOG.checkPermissions(getContext());

            if (requiredPermissions.isEmpty()) {
                return true;
            } else {
                requestPermissions(requiredPermissions.toArray(new String[requiredPermissions.size()]),
                    REQUEST_CALL_LOG_PERMISSIONS);
                return false;
            }
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
            final ListPreference preference = (ListPreference) findPreference(BACKUP_CONTACT_GROUP.key);
            if (ContextCompat.checkSelfPermission(getContext(), READ_CONTACTS) == PERMISSION_GRANTED) {
                ContactAccessor contacts = new ContactAccessor();
                final Map<Integer, Group> groups = contacts.getGroups(getContext().getContentResolver(), getResources());
                initListPreference(preference, groups, false);
            } else {
                preference.setEnabled(false);
            }
        }

        public static class CallLog extends AdvancedSettings {
            private static final int REQUEST_CALENDAR_ACCESS = 0;
            private CheckBoxPreference enabledPreference;
            private ListPreference calendarPreference;
            private Preference folderPreference;

            @Override
            public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
                super.onViewCreated(view, savedInstanceState);
                enabledPreference = (CheckBoxPreference) findPreference(CALLLOG_SYNC_CALENDAR_ENABLED.key);
                calendarPreference = (ListPreference) findPreference(CALLLOG_SYNC_CALENDAR.key);
                folderPreference = findPreference(CALLLOG.folderPreference);
            }

            @Override
            public void onResume() {
                super.onResume();
                initCalendars();

                updateCallLogCalendarLabelFromPref();
                registerValidCallLogFolderCheck();
                registerCalendarSyncEnabledCallback();

                addPreferenceListener(CALLLOG_BACKUP_AFTER_CALL.key);

                if (needCalendarPermission() && enabledPreference.isChecked()) {
                    // user revoked calendar permission manually
                    enabledPreference.setChecked(false);
                }
            }

            private void registerCalendarSyncEnabledCallback() {
                enabledPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        if (newValue == Boolean.TRUE && needCalendarPermission()) {
                            requestPermissions(new String[] {WRITE_CALENDAR}, REQUEST_CALENDAR_ACCESS);
                            return false;
                        } else {
                            return true;
                        }
                    }
                });
            }

            @Override
            public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                Log.v(TAG, "onRequestPermissionsResult("+requestCode+ ","+ Arrays.toString(permissions) +","+ Arrays.toString(grantResults));

                if (requestCode == REQUEST_CALENDAR_ACCESS) {
                    enabledPreference.setChecked(allGranted(grantResults));
                }
            }

            private boolean needCalendarPermission() {
                return ContextCompat.checkSelfPermission(getContext(), WRITE_CALENDAR) != PERMISSION_GRANTED;
            }

            private void updateCallLogCalendarLabelFromPref() {
                calendarPreference.setTitle(calendarPreference.getEntry() != null ? calendarPreference.getEntry() :
                        getString(R.string.ui_backup_calllog_sync_calendar_label));
            }

            private void initCalendars() {
                if (needCalendarPermission()) return;

                CalendarAccessor calendars = CalendarAccessor.Get.instance(getContext().getContentResolver());
                initListPreference(calendarPreference, calendars.getCalendars(), false);
            }

            private void registerValidCallLogFolderCheck() {
                folderPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
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
            updateUsernameLabel(authPreferences.getImapUsername());
            updateServerNameLabel(authPreferences.getServername());
            updateImapSettings(!authPreferences.useXOAuth());

            findPreference(AuthPreferences.SERVER_AUTHENTICATION)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            final boolean plain = AuthMode.valueOf(newValue.toString().toUpperCase(Locale.ENGLISH))
                                    == AuthMode.PLAIN;
                            updateImapSettings(plain);
                            return true;
                        }
                    });


            findPreference(AuthPreferences.SERVER_ADDRESS)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            updateServerNameLabel(newValue.toString());
                            return true;
                        }
                    });

            findPreference(AuthPreferences.IMAP_USER)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            updateUsernameLabel(newValue.toString());
                            return true;
                        }
                    });

            findPreference(AuthPreferences.IMAP_PASSWORD)
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
            if (TextUtils.isEmpty(username)) {
                username = getString(R.string.ui_login_label);
            }
            findPreference(AuthPreferences.IMAP_USER).setTitle(username);
        }

        private void updateServerNameLabel(String servername) {
            if (TextUtils.isEmpty(servername)) {
                servername = getString(R.string.ui_server_label);
            }
            findPreference(AuthPreferences.SERVER_ADDRESS).setTitle(servername);
        }
    }

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
}
