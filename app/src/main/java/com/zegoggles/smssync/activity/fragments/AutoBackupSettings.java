package com.zegoggles.smssync.activity.fragments;

import androidx.preference.Preference;

import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;

import static com.zegoggles.smssync.preferences.Preferences.Keys.INCOMING_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.REGULAR_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.USE_OLD_SCHEDULER;
import static com.zegoggles.smssync.preferences.Preferences.Keys.WIFI_ONLY;

public class AutoBackupSettings extends SMSBackupPreferenceFragment {
    @Override
    public void onResume() {
        super.onResume();
        checkGCM();

        addPreferenceListener(
            INCOMING_TIMEOUT_SECONDS.key,
            REGULAR_TIMEOUT_SECONDS.key,
            WIFI_ONLY.key,
            USE_OLD_SCHEDULER.key
        );
    }

    private void checkGCM() {
        if (!App.gcmAvailable) {
            final Preference preference = findPreference(USE_OLD_SCHEDULER.key);
            preference.setEnabled(false);
            preference.setSummary(R.string.pref_use_old_scheduler_no_gcm_summary);
        }
    }
}
