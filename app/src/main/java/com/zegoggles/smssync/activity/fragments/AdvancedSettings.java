package com.zegoggles.smssync.activity.fragments;

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
import androidx.preference.PreferenceScreen;
import androidx.preference.EditTextPreference;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.ActionBar;

import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.events.AccountAddedEvent;
import com.zegoggles.smssync.activity.events.AccountConnectionChangedEvent;
import com.zegoggles.smssync.activity.events.AccountRemovedEvent;
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;
import com.zegoggles.smssync.activity.events.SettingsResetEvent;
import com.zegoggles.smssync.activity.events.ThemeChangedEvent;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.contacts.Group;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.DataTypePreferences;
import com.zegoggles.smssync.utils.SimCardHelper;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import static com.zegoggles.smssync.preferences.Preferences.Keys.CONNECTED;
import static com.zegoggles.smssync.preferences.Preferences.Keys.DARK_THEME;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_RESTORE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_SYNC;
import static com.zegoggles.smssync.utils.ListPreferenceHelper.initListPreference;

public abstract class AdvancedSettings extends SMSBackupPreferenceFragment {
    public static class Main extends SMSBackupPreferenceFragment {
        private AuthPreferences authPreferences;
        private TwoStatePreference connected;

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
            updateConnected();
            addPreferenceListener(new ThemeChangedEvent(), DARK_THEME.key);
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String rootKey) {
            super.onCreatePreferences(bundle, rootKey);

            //XOAuth2 is legacy and therefore not considered for multi-sim (authPreferences only used in this context here)
            authPreferences = new AuthPreferences(getContext(), 0);
            connected = findPreference(CONNECTED.key);
            assert connected != null;
            connected.setSummaryProvider(new Preference.SummaryProvider<TwoStatePreference>() {
                @Override
                public CharSequence provideSummary(TwoStatePreference preference) {
                    final String username = authPreferences.getOauth2Username();
                    if (preference.isEnabled()) {
                        return preference.isChecked() && !TextUtils.isEmpty(username) ?
                                getString(R.string.gmail_already_connected, username) :
                                getString(R.string.gmail_needs_connecting);
                    } else {
                        return null;
                    }
                }
            });
            connected.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object change) {
                    App.post(new AccountConnectionChangedEvent((Boolean) change));
                    return false; // will be set later
                }
            });
        }

        @Subscribe public void onAccountAdded(AccountAddedEvent event) {
            updateConnected();
        }

        @Subscribe public void onAccountRemoved(AccountRemovedEvent event) {
            updateConnected();
        }

        @Subscribe public void onSettingsReset(SettingsResetEvent event) {
            updateConnected();
        }

        private void updateConnected() {
            connected.setChecked(authPreferences.hasOAuth2Tokens());
        }
    }

    public static class Backup extends SMSBackupPreferenceFragment {
        private static final int REQUEST_CALL_LOG_PERMISSIONS = 0;
        private CheckBoxPreference callLogPreference;

        public void onCreatePreferences(Bundle bundle, String rootKey) {
            super.onCreatePreferences(bundle, rootKey);

            PreferenceScreen mainSettings = getPreferenceScreen();
            for (Integer settingsId = 0; settingsId < App.SimCards.length; settingsId++) {
                if (settingsId>0) {
                    EditTextPreference imapFolder = findPreference(DataType.PreferenceKeys.IMAP_FOLDER);
                    EditTextPreference cloneImapFolder = new EditTextPreference(getContext());
                    cloneImapFolder.setTitle(SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.ui_imap_folder_label), settingsId));
                    cloneImapFolder.setKey(SimCardHelper.addSettingsId(DataType.PreferenceKeys.IMAP_FOLDER, settingsId));
                    cloneImapFolder.setDialogTitle(SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.ui_imap_folder_label), settingsId));
                    cloneImapFolder.setDialogMessage(getString(R.string.ui_imap_folder_label_dialog_msg));
                    cloneImapFolder.setDefaultValue(getString(R.string.imap_folder_default));
                    cloneImapFolder.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
                    insertPreference(imapFolder.getOrder()+1, mainSettings, cloneImapFolder);
                }
                registerValidImapFolderCheck(settingsId);
            }

            EditTextPreference imapFolder = findPreference(DataType.PreferenceKeys.IMAP_FOLDER);
            imapFolder.setTitle(SimCardHelper.addPhoneNumberIfMultiSim(imapFolder.getTitle().toString(), 0));
            imapFolder.setDialogTitle(SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.ui_imap_folder_label), 0));
        }

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
                long maxSyncedDate = 0;
                for (Integer settingsId = 0; settingsId < App.SimCards.length; settingsId++) {
                    long maxSyncedDateForSettingsId = preferences.getDataTypePreferences().getMaxSyncedDate(type, settingsId);
                    if (maxSyncedDateForSettingsId>maxSyncedDate) maxSyncedDate = maxSyncedDateForSettingsId;
                }
                findPreference(type.backupEnabledPreference).setSummary(getLastSyncText(maxSyncedDate));
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

        private void registerValidImapFolderCheck(Integer settingsId) {
            findPreference(SimCardHelper.addSettingsId(SMS.folderPreference, settingsId))
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

            public void onCreatePreferences(Bundle bundle, String rootKey) {
                super.onCreatePreferences(bundle, rootKey);

                PreferenceScreen mainSettings = getPreferenceScreen();

                for (Integer settingsId = 0; settingsId < App.SimCards.length; settingsId++) {
                    if (settingsId>0) {
                        addImapFolder(mainSettings, settingsId);
                        addCalendar(mainSettings, settingsId);
                    }
                    registerValidCallLogFolderCheck(settingsId);
                }

                EditTextPreference imapFolder = findPreference(DataType.PreferenceKeys.IMAP_FOLDER_CALLLOG);
                imapFolder.setTitle(SimCardHelper.addPhoneNumberIfMultiSim(imapFolder.getTitle().toString(), 0));
                imapFolder.setDialogTitle(SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.ui_imap_folder_calllog_label), 0));
            }

            private void addImapFolder(PreferenceScreen mainSettings, Integer settingsId) {
                EditTextPreference imapFolder = findPreference(DataType.PreferenceKeys.IMAP_FOLDER_CALLLOG);
                EditTextPreference cloneImapFolder = new EditTextPreference(getContext());
                cloneImapFolder.setTitle(SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.ui_imap_folder_calllog_label), settingsId));
                cloneImapFolder.setKey(SimCardHelper.addSettingsId(DataType.PreferenceKeys.IMAP_FOLDER_CALLLOG, settingsId));
                cloneImapFolder.setDialogTitle(SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.ui_imap_folder_calllog_label), settingsId));
                cloneImapFolder.setDialogMessage(getString(R.string.ui_imap_folder_calllog_label_dialog_msg));
                cloneImapFolder.setDefaultValue(getString(R.string.imap_folder_calllog_default));
                cloneImapFolder.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
                insertPreference(imapFolder.getOrder()+1, mainSettings, cloneImapFolder);
            }

            private void addCalendar(PreferenceScreen mainSettings, Integer settingsId) {
                ListPreference calendarPreference = findPreference(CALLLOG_SYNC_CALENDAR.key);
                ListPreference cloneCalendarPreference = new ListPreference(getContext());
                cloneCalendarPreference.setTitle(SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.ui_backup_calllog_sync_calendar_label), settingsId));
                cloneCalendarPreference.setSummary(calendarPreference.getSummary());
                cloneCalendarPreference.setKey(SimCardHelper.addSettingsId(CALLLOG_SYNC_CALENDAR.key, settingsId));
                cloneCalendarPreference.setDefaultValue("-1");
                insertPreference(calendarPreference.getOrder()+1, mainSettings, cloneCalendarPreference);
            }

            @Override
            public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
                super.onViewCreated(view, savedInstanceState);
                enabledPreference = findPreference(CALLLOG_SYNC_CALENDAR_ENABLED.key);
            }

            @Override
            public void onResume() {
                super.onResume();
                initCalendars();

                updateCallLogCalendarLabelFromPref();
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
                for (Integer settingsId = 0; settingsId < App.SimCards.length; settingsId++) {
                    ListPreference calendarPreference = findPreference(SimCardHelper.addSettingsId(CALLLOG_SYNC_CALENDAR.key,settingsId));
                    calendarPreference.setTitle(calendarPreference.getEntry() != null ? calendarPreference.getEntry() : SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.ui_backup_calllog_sync_calendar_label), settingsId));
                }
            }

            private void initCalendars() {
                if (needCalendarPermission()) return;

                CalendarAccessor calendars = CalendarAccessor.Get.instance(getContext().getContentResolver());
                for (Integer settingsId = 0; settingsId < App.SimCards.length; settingsId++) {
                    ListPreference calendarPreference = findPreference(SimCardHelper.addSettingsId(CALLLOG_SYNC_CALENDAR.key,settingsId));
                    initListPreference(calendarPreference, calendars.getCalendars(), false);
                }
            }

            private void registerValidCallLogFolderCheck(Integer settingsId) {
                findPreference(SimCardHelper.addSettingsId(CALLLOG.folderPreference, settingsId))
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
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
        private Integer settingsId;

        @Override
        public void onCreatePreferences(Bundle bundle, String rootKey) {
            settingsId = 0;
            Pattern p = Pattern.compile("^(.*)_(\\d*)$");
            Matcher m = p.matcher(rootKey);
            if (m.find()) {
                settingsId=Integer.parseInt(m.group(2));
                rootKey=m.group(1);
            }

            super.onCreatePreferences(bundle, rootKey);

            authPreferences = new AuthPreferences(getContext(), settingsId);

            if (settingsId > 0) {
                setPreferenceScreen(cloneSettings());
                insertTakeOverCheckBox();

                setServerState(authPreferences.getTakeOver());

                findPreference(SimCardHelper.addSettingsId(AuthPreferences.SERVER_TAKEOVER, settingsId))
                        .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                            public boolean onPreferenceChange(Preference preference, Object newValue) {
                                setServerState((Boolean)newValue);
                                return true;
                            }
                        });
            }
        }

        private void setServerState(Boolean takeOver) {
            Boolean state = !takeOver;
            findPreference(SimCardHelper.addSettingsId(AuthPreferences.SERVER_ADDRESS, settingsId)).setEnabled(state);
            findPreference(SimCardHelper.addSettingsId(AuthPreferences.IMAP_USER, settingsId)).setEnabled(state);
            findPreference(SimCardHelper.addSettingsId(AuthPreferences.IMAP_PASSWORD, settingsId)).setEnabled(state);
            findPreference(SimCardHelper.addSettingsId(AuthPreferences.SERVER_PROTOCOL, settingsId)).setEnabled(state);
            findPreference(SimCardHelper.addSettingsId(AuthPreferences.SERVER_TRUST_ALL_CERTIFICATES, settingsId)).setEnabled(state);
        }

        private void insertTakeOverCheckBox() {
            PreferenceScreen preferenceScreen = getPreferenceScreen();

            CheckBoxPreference checkbox = new CheckBoxPreference(getContext());
            checkbox.setTitle(R.string.ui_server_takeover);
            checkbox.setSummary(R.string.ui_server_takeover_summary);
            checkbox.setDefaultValue(true);
            checkbox.setKey(SimCardHelper.addSettingsId(AuthPreferences.SERVER_TAKEOVER, settingsId));
            insertPreference(0, preferenceScreen, checkbox);
        }

        private PreferenceScreen cloneSettings() {
            PreferenceScreen originalScreen = getPreferenceScreen();
            PreferenceScreen copyScreen = getPreferenceManager().createPreferenceScreen(getContext());

            while(originalScreen.getPreferenceCount() > 0) {
                Preference pref = originalScreen.getPreference(0);
                pref.setKey(pref.getKey()+"_"+settingsId.toString());

                originalScreen.removePreference(pref);
                copyScreen.addPreference(pref);
            }
            return copyScreen;
        }

        @Override
        public void onResume() {
            super.onResume();

            findPreference(SimCardHelper.addSettingsId(AuthPreferences.IMAP_PASSWORD, settingsId))
                    .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            authPreferences.setImapPassword(newValue.toString());
                            return true;
                        }
                    });
        }

        @Override public void onStart() {
            super.onStart();

            ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
            if ( actionBar == null) return;
            actionBar.setSubtitle(SimCardHelper.addPhoneNumberIfMultiSim(getString(R.string.imap_settings), settingsId));
            actionBar.setDisplayHomeAsUpEnabled(true);
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
