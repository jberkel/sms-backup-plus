package com.zegoggles.smssync.activity;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;
import com.zegoggles.smssync.R;

public class SMSBackupPreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }
}
