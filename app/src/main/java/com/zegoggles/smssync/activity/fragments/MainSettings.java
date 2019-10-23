package com.zegoggles.smssync.activity.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.donation.DonationActivity;
import com.zegoggles.smssync.activity.donation.DonationActivity.DonationStatusListener;
import com.zegoggles.smssync.activity.events.AccountAddedEvent;
import com.zegoggles.smssync.activity.events.AccountRemovedEvent;
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;
import com.zegoggles.smssync.activity.events.SettingsResetEvent;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthPreferences;

import java.util.ArrayList;
import java.util.List;

import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.preferences.Preferences.Keys.BACKUP_SETTINGS_SCREEN;
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
        findPreference(AdvancedSettings.Server.class.getName()).setSummaryProvider(new Preference.SummaryProvider() {
            @Override
            public CharSequence provideSummary(Preference preference) {
                if (authPreferences.usePlain() && authPreferences.isLoginInformationSet()) {
                    return authPreferences.toString();
                } else {
                    return getString(R.string.custom_imap_not_configured);
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
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
        updateAutoBackupPreferences();
        addPreferenceListener(ENABLE_AUTO_BACKUP.key);
    }

    @Subscribe public void onAccountAdded(AccountAddedEvent event) {
        updateAutoBackupPreferences();
    }

    @Subscribe public void onAccountRemoved(AccountRemovedEvent event) {
        authPreferences.clearOauth2Data();
        preferences.getDataTypePreferences().clearLastSyncData();
        findAutoBackupPreference().setChecked(false);
        updateAutoBackupPreferences();
    }

    @Subscribe public void onAutoBackupSettingsChanged(final AutoBackupSettingsChangedEvent event) {
        updateAutoBackupPreferences();
    }

    @Subscribe public void onSettingsReset(SettingsResetEvent event) {
        preferences.getDataTypePreferences().clearLastSyncData();
        preferences.reset();
    }

    private void updateAutoBackupPreferences() {
        final CheckBoxPreference autoBackup = findAutoBackupPreference();
        autoBackup.setSummary(summarizeAutoBackupSettings());
        autoBackup.setEnabled(!authPreferences.useXOAuth() || authPreferences.hasOAuth2Tokens());

        final Preference autoBackupSettings = findPreference(BACKUP_SETTINGS_SCREEN.key);
        autoBackupSettings.setSummary(summarizeBackupScheduleSettings(autoBackup.isChecked()));
        autoBackupSettings.setEnabled(autoBackup.isEnabled() && autoBackup.isChecked());
    }

    private String summarizeAutoBackupSettings() {
        final List<String> enabled = new ArrayList<String>();
        for (DataType dataType : preferences.getDataTypePreferences().enabled()) {
            enabled.add(getString(dataType.resId));
        }
        StringBuilder summary = new StringBuilder();
        if (!enabled.isEmpty()) {
            summary.append(getString(R.string.ui_enable_auto_sync_summary, TextUtils.join(", ", enabled)));
            if (App.isInstalledOnSDCard(getContext())) {
                summary.append(' ').append(getString(R.string.sd_card_disclaimer));
            }
        } else {
            summary.append(getString(R.string.ui_enable_auto_sync_no_enabled_summary));
        }
        return summary.toString();
    }

    private String summarizeBackupScheduleSettings(boolean isEnabled) {
        if (!isEnabled) {
            return null;
        }

        final StringBuilder summary = new StringBuilder();
        final ListPreference regSchedule = findPreference(REGULAR_TIMEOUT_SECONDS.key);
        final ListPreference incomingSchedule = findPreference(INCOMING_TIMEOUT_SECONDS.key);

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

    private CheckBoxPreference findAutoBackupPreference() {
        return (CheckBoxPreference) findPreference(ENABLE_AUTO_BACKUP.key);
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
