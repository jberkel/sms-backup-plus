package com.zegoggles.smssync.activity.fragments;

import android.os.Bundle;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.util.Log;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.donation.DonationActivity;
import com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener;
import com.zegoggles.smssync.activity.events.AccountAddedEvent;
import com.zegoggles.smssync.activity.events.AccountConnectionChangedEvent;
import com.zegoggles.smssync.activity.events.AccountRemovedEvent;
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;
import com.zegoggles.smssync.activity.events.SettingsResetEvent;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthPreferences;

import java.util.ArrayList;
import java.util.List;

import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.preferences.Preferences.Keys.BACKUP_SETTINGS_SCREEN;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CONNECTED;
import static com.zegoggles.smssync.preferences.Preferences.Keys.DONATE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.ENABLE_AUTO_BACKUP;
import static com.zegoggles.smssync.preferences.Preferences.Keys.INCOMING_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.REGULAR_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.WIFI_ONLY;

public class MainSettings extends SMSBackupPreferenceFragment {
    private AuthPreferences authPreferences;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        super.onCreatePreferences(bundle, rootKey);
        authPreferences = new AuthPreferences(getContext());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        App.unregister(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        checkUserDonationStatus();
        updateAutoBackupPreference();
        updateConnected().setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object change) {
                App.post(new AccountConnectionChangedEvent((Boolean) change));
                return false; // will be set later
            }
        });
        addPreferenceListener(ENABLE_AUTO_BACKUP.key);
    }

    @Subscribe public void onAccountAdded(AccountAddedEvent event) {
        updateConnected();
        updateAutoBackupPreference();
    }

    @Subscribe public void onAccountRemoved(AccountRemovedEvent event) {
        authPreferences.clearOauth2Data();
        preferences.getDataTypePreferences().clearLastSyncData();
        updateConnected();
        updateAutoBackupPreference();
    }

    @Subscribe public void onAutoBackupSettingsChanged(final AutoBackupSettingsChangedEvent event) {
        updateAutoBackupPreference();
    }

    @Subscribe public void onSettingsReset(SettingsResetEvent event) {
        updateConnected();
    }

    private CheckBoxPreference updateConnected() {
        CheckBoxPreference connected = (CheckBoxPreference) findPreference(CONNECTED.key);

        connected.setEnabled(authPreferences.useXOAuth());
        connected.setChecked(authPreferences.hasOAuth2Tokens());
        connected.setSummary(getConnectedSummary(connected));

        return connected;
    }

    private void updateAutoBackupPreference() {
        final Preference preference = findPreference(ENABLE_AUTO_BACKUP.key);
        preference.setSummary(summarizeAutoBackupSettings());
        preference.setEnabled(!authPreferences.useXOAuth() || authPreferences.hasOAuth2Tokens());

        updateAutoBackupSettingsScreen();
    }

    private void updateAutoBackupSettingsScreen() {
        final Preference preference = findPreference(BACKUP_SETTINGS_SCREEN.key);
        preference.setSummary(summarizeBackupScheduleSettings());
        preference.setEnabled(preferences.isEnableAutoSync());
    }

    private String getConnectedSummary(CheckBoxPreference connected) {
        final String username = authPreferences.getOauth2Username();
        final String imapUsername = authPreferences.getImapUsername();
        if (connected.isEnabled()) {
            return connected.isChecked() && !TextUtils.isEmpty(username) ?
                    getString(R.string.gmail_already_connected, username) :
                    getString(R.string.gmail_needs_connecting);
        } else {
            return getString(R.string.custom_imap, TextUtils.isEmpty(imapUsername)? "" : imapUsername);
        }
    }

    private String summarizeAutoBackupSettings() {
        final List<String> enabled = new ArrayList<String>();
        for (DataType dataType : preferences.getDataTypePreferences().enabled()) {
            enabled.add(getString(dataType.resId));
        }
        StringBuilder summary = new StringBuilder();
        if (!enabled.isEmpty()) {
            summary.append(getString(R.string.ui_enable_auto_sync_summary, TextUtils.join(", ", enabled)));
            if (preferences.isInstalledOnSDCard()) {
                summary.append(' ').append(getString(R.string.sd_card_disclaimer));
            }
        } else {
            summary.append(getString(R.string.ui_enable_auto_sync_no_enabled_summary));
        }
        return summary.toString();
    }

    private String summarizeBackupScheduleSettings() {
        final StringBuilder summary = new StringBuilder();

        final ListPreference regSchedule = (ListPreference)
                findPreference(REGULAR_TIMEOUT_SECONDS.key);

        final ListPreference incomingSchedule = (ListPreference)
                findPreference(INCOMING_TIMEOUT_SECONDS.key);

        // values are out-of sync
        regSchedule.setValue(String.valueOf(preferences.getRegularTimeoutSecs()));
        incomingSchedule.setValue(String.valueOf(preferences.getIncomingTimeoutSecs()));

        summary.append(regSchedule.getTitle())
                .append(": ")
                .append(regSchedule.getEntry())
                .append(", ")
                .append(incomingSchedule.getTitle())
                .append(": ")
                .append(incomingSchedule.getEntry());

        if (preferences.isWifiOnly()) {
            summary.append(" (")
                    .append(findPreference(WIFI_ONLY.key).getTitle())
                    .append(")");
        }
        return summary.toString();
    }

    private void checkUserDonationStatus() {
        try {
            DonationActivity.checkUserDonationStatus(getContext(), new DonationStatusListener() {
                @Override
                public void userDonationState(State state) {
                    switch (state) {
                        case NOT_AVAILABLE:
                        case DONATED:
                            Preference donate = findPreference(DONATE.key);
                            if (donate != null) {
                                getPreferenceScreen().removePreference(donate);
                            }
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }
}
