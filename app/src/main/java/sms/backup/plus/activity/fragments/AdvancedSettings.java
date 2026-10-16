package sms.backup.plus.activity.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.TwoStatePreference;

import com.squareup.otto.Subscribe;
import sms.backup.plus.App;
import sms.backup.plus.R;
import sms.backup.plus.activity.events.AccountAddedEvent;
import sms.backup.plus.activity.events.AccountRemovedEvent;
import sms.backup.plus.activity.events.AutoBackupSettingsChangedEvent;
import sms.backup.plus.activity.events.SettingsResetEvent;
import sms.backup.plus.activity.events.ThemeChangedEvent;
import sms.backup.plus.calendar.CalendarAccessor;
import sms.backup.plus.contacts.ContactAccessor;
import sms.backup.plus.contacts.Group;
import sms.backup.plus.mail.BackupImapStore;
import sms.backup.plus.mail.DataType;
import sms.backup.plus.preferences.AuthPreferences;
import sms.backup.plus.preferences.DataTypePreferences;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static android.Manifest.permission.READ_CALENDAR;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.WRITE_CALENDAR;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static sms.backup.plus.App.TAG;
import static sms.backup.plus.activity.AppPermission.allGranted;
import static sms.backup.plus.activity.Dialogs.Type.INVALID_IMAP_FOLDER;
import static sms.backup.plus.mail.DataType.CALLLOG;
import static sms.backup.plus.mail.DataType.SMS;
import static sms.backup.plus.preferences.Preferences.Keys.BACKUP_CONTACT_GROUP;
import static sms.backup.plus.preferences.Preferences.Keys.CALLLOG_BACKUP_AFTER_CALL;
import static sms.backup.plus.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR;
import static sms.backup.plus.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR_ENABLED;
import static sms.backup.plus.preferences.Preferences.Keys.CONNECTED;
import static sms.backup.plus.preferences.Preferences.Keys.DARK_THEME;
import static sms.backup.plus.preferences.Preferences.Keys.MAX_ITEMS_PER_RESTORE;
import static sms.backup.plus.preferences.Preferences.Keys.MAX_ITEMS_PER_SYNC;
import static sms.backup.plus.utils.ListPreferenceHelper.initListPreference;

public abstract class AdvancedSettings extends SMSBackupPreferenceFragment {
    public static class Main extends SMSBackupPreferenceFragment {
        private AuthPreferences authPreferences;

        @Override
        public void onStart() {
            super.onStart();
            App.register(this);

        }
        @Override
        public void onStop() {
            super.onStop();
            App.unregister(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            addPreferenceListener(new ThemeChangedEvent(), DARK_THEME.key);
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String rootKey) {
            super.onCreatePreferences(bundle, rootKey);
            authPreferences = new AuthPreferences(getContext());
        }

        @Subscribe public void onAccountAdded(AccountAddedEvent event) {
        }

        @Subscribe public void onAccountRemoved(AccountRemovedEvent event) {
        }

        @Subscribe public void onSettingsReset(SettingsResetEvent event) {
        }

        }

    public static class Backup extends SMSBackupPreferenceFragment {
        private static final int REQUEST_CALL_LOG_PERMISSIONS = 0;
        private CheckBoxPreference callLogPreference;

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            callLogPreference = findPreference(CALLLOG.backupEnabledPreference);
        }

        @Override
        public void onResume() {
            super.onResume();
            updateMaxItemsPerSync(null);

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

        private void updateMaxItemsPerSync(String newValue) {
            updateMaxItems(findPreference(MAX_ITEMS_PER_SYNC.key), preferences.getMaxItemsPerSync(), newValue);
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
                            return checkValidImapFolder(getFragmentManager(), newValue.toString());
                        }
                    });
        }

        private void initGroups() {
            final ListPreference preference = findPreference(BACKUP_CONTACT_GROUP.key);
            if (ContextCompat.checkSelfPermission(getContext(), READ_CONTACTS) == PERMISSION_GRANTED) {
                ContactAccessor contacts = new ContactAccessor();
                final Map<Integer, Group> groups = contacts.getGroups(getContext().getContentResolver(), getResources());
                initListPreference(preference, groups, false);
            } else {
                preference.setEnabled(false);
            }
        }

        public static class CallLog extends SMSBackupPreferenceFragment {
            private static final int REQUEST_CALENDAR_ACCESS = 0;
            private CheckBoxPreference enabledPreference;
            private ListPreference calendarPreference;
            private Preference folderPreference;

            @Override
            public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
                super.onViewCreated(view, savedInstanceState);
                enabledPreference = findPreference(CALLLOG_SYNC_CALENDAR_ENABLED.key);
                calendarPreference = findPreference(CALLLOG_SYNC_CALENDAR.key);
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
                            // READ_CALENDAR is needed to list calendars, WRITE_CALENDAR to add events.
                            requestPermissions(new String[] {READ_CALENDAR, WRITE_CALENDAR}, REQUEST_CALENDAR_ACCESS);
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
                return ContextCompat.checkSelfPermission(getContext(), READ_CALENDAR) != PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getContext(), WRITE_CALENDAR) != PERMISSION_GRANTED;
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
                        return checkValidImapFolder(getFragmentManager(), newValue.toString());
                    }
                });
            }
        }
    }

    public static class Restore extends SMSBackupPreferenceFragment {
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
            updateMaxItems(findPreference(MAX_ITEMS_PER_RESTORE.key), preferences.getMaxItemsPerRestore(), newValue);
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

            findPreference(AuthPreferences.IMAP_PASSWORD)
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            authPreferences.setImapPassword(newValue.toString());
                            return true;
                        }
                    });
        }
    }

    static void updateMaxItems(Preference preference, int currentValue, String newValue) {
        if (newValue == null) {
            newValue = String.valueOf(currentValue);
        }
        // XXX
        preference.setTitle("-1".equals(newValue) ? preference.getContext().getString(R.string.all_messages) : newValue);
    }

    static boolean checkValidImapFolder(FragmentManager fragmentManager, String imapFolder) {
        if (BackupImapStore.isValidImapFolder(imapFolder)) {
            return true;
        } else {
            INVALID_IMAP_FOLDER.instantiate(fragmentManager, null).show(fragmentManager, null);
            return false;
        }
    }
}
