package com.zegoggles.smssync.activity.fragments;

import android.net.ConnectivityManager;
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
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;
import com.zegoggles.smssync.activity.events.ConnectEvent;
import com.zegoggles.smssync.activity.events.SettingsResetEvent;
import com.zegoggles.smssync.activity.donation.DonationActivity;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.tasks.OAuth2CallbackTask;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.CONNECTIVITY_SERVICE;
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
        updateAutoBackupEnabledSummary();
        updateAutoBackupScheduleSummary();
        updateConnected().setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object change) {
                boolean newValue = (Boolean) change;
                App.post(new ConnectEvent(newValue));
                return false;
            }
        });


        addPreferenceListener(
            ENABLE_AUTO_BACKUP.key
        );
    }

    @Subscribe public void autoBackupSettingsChanged(final AutoBackupSettingsChangedEvent event) {
        updateAutoBackupEnabledSummary();
        updateAutoBackupScheduleSummary();
    }


    @Subscribe public void onOAuth2Callback(OAuth2CallbackTask.OAuth2CallbackEvent event) {
        if (event.valid()) {
            updateConnected();
        }
    }

    @Subscribe public void settingsReset(SettingsResetEvent event) {
        updateConnected();
    }

    private CheckBoxPreference updateConnected() {
        CheckBoxPreference connected = (CheckBoxPreference) findPreference(CONNECTED.key);

        connected.setEnabled(authPreferences.useXOAuth());
        connected.setChecked(authPreferences.hasOauthTokens() || authPreferences.hasOAuth2Tokens());


        String summary = getConnectedSummary(connected);
        connected.setSummary(summary);

        return connected;
    }

    private String getConnectedSummary(CheckBoxPreference connected) {
        final String username = authPreferences.getUsername();
        return connected.isChecked() && !TextUtils.isEmpty(username) ?
                getString(R.string.gmail_already_connected, username) :
                getString(R.string.gmail_needs_connecting);
    }


    private void updateAutoBackupEnabledSummary() {
        findPreference(ENABLE_AUTO_BACKUP.key).setSummary(summarizeAutoBackupSettings());
    }

    private void updateAutoBackupScheduleSummary() {
        findPreference(BACKUP_SETTINGS_SCREEN.key).setSummary(summarizeBackupScheduleSettings());
    }

    private String summarizeAutoBackupSettings() {
        final List<String> enabled = new ArrayList<String>();
        for (DataType dataType : preferences.getDataTypePreferences().enabled()) {
            enabled.add(getString(dataType.resId));
        }
        StringBuilder summary = new StringBuilder();
        if (!enabled.isEmpty()) {
            summary.append(getString(R.string.ui_enable_auto_sync_summary, TextUtils.join(", ", enabled)));
            if (!getConnectivityManager().getBackgroundDataSetting()) {
                summary.append(' ').append(getString(R.string.ui_enable_auto_sync_bg_data));
            }
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

    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) getContext().getSystemService(CONNECTIVITY_SERVICE);
    }

    private void checkUserDonationStatus() {
        try {
            DonationActivity.checkUserHasDonated(getContext(), new DonationActivity.DonationStatusListener() {
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
