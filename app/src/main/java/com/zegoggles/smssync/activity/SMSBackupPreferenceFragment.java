package com.zegoggles.smssync.activity;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.annotation.Nullable;
import android.view.View;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.StatusPreference;
import com.zegoggles.smssync.preferences.Preferences;

public class SMSBackupPreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}
