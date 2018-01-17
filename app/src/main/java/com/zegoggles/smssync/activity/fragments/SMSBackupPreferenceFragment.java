package com.zegoggles.smssync.activity.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.support.v7.preference.PreferenceFragmentCompat;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;
import com.zegoggles.smssync.preferences.Preferences;

public class SMSBackupPreferenceFragment extends PreferenceFragmentCompat {
    protected Preferences preferences;
    private Handler handler;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        preferences = new Preferences(getContext(), getPreferenceManager().getSharedPreferences());
        handler = new Handler();
    }

    void addPreferenceListener(String... prefKeys) {
        addPreferenceListener(new AutoBackupSettingsChangedEvent(), prefKeys);
    }

    void addPreferenceListener(final Object event, String... prefKeys) {
        for (String prefKey : prefKeys) {
            findPreference(prefKey).setOnPreferenceChangeListener(
                    new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, final Object newValue) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    App.post(event);
                                }
                            });
                            return true;
                        }
                    });
        }
    }
}
