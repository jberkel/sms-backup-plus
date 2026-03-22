package com.zegoggles.smssync.activity.fragments;

import static com.zegoggles.smssync.preferences.Preferences.Keys.INCOMING_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.REGULAR_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.USE_OLD_SCHEDULER;
import static com.zegoggles.smssync.preferences.Preferences.Keys.WIFI_ONLY;

public class AutoBackupSettings extends SMSBackupPreferenceFragment {
    @Override
    public void onResume() {
        super.onResume();

        addPreferenceListener(
            INCOMING_TIMEOUT_SECONDS.key,
            REGULAR_TIMEOUT_SECONDS.key,
            WIFI_ONLY.key,
            USE_OLD_SCHEDULER.key
        );
    }
}
