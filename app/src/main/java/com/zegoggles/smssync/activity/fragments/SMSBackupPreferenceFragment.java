package com.zegoggles.smssync.activity.fragments;

import android.os.Bundle;
import android.os.Handler;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;

import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.events.AutoBackupSettingsChangedEvent;
import com.zegoggles.smssync.preferences.Preferences;

import java.util.HashMap;

public abstract class SMSBackupPreferenceFragment extends PreferenceFragmentCompat {
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
                    new Preference.OnPreferenceChangeListener() {
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

    protected void insertPreference(int position, PreferenceGroup preferenceList, Preference item) {
        HashMap<Integer, Preference> listItems = new HashMap<Integer, Preference>();
        int prefCount = preferenceList.getPreferenceCount();
        for(int i = 0; i < prefCount; i++) {
            Preference preference = preferenceList.getPreference(i);
            listItems.put(preference.getOrder(), preference);
        }

        Integer cnt = 0;
        for (Integer i : listItems.keySet()) {
            Preference preference = listItems.get(i);
            if (cnt<position) {
                preference.setOrder(cnt);
            }
            else {
                if (cnt == position) {
                    preferenceList.addPreference(item);
                    item.setOrder(position);
                }
                preference.setOrder(cnt+1);
            }
            cnt++;
        }
    }
}
