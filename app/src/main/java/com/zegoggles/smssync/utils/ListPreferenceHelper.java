/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zegoggles.smssync.utils;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ListPreferenceHelper {
    private ListPreferenceHelper() {
    }

    public static boolean initListPreference(final ListPreference pref,
                                             final Map<?, ?> fields, boolean keepExisting) {
        if (fields.size() > 0) {
            final List<CharSequence> e = new ArrayList<CharSequence>();
            final List<CharSequence> ev = new ArrayList<CharSequence>();

            if (keepExisting) {
                if (pref.getEntries() != null) e.addAll(Arrays.asList(pref.getEntries()));
                if (pref.getEntryValues() != null) ev.addAll(Arrays.asList(pref.getEntryValues()));
            }

            for (Map.Entry<?, ?> entry : fields.entrySet()) {
                if (entry.getValue() != null && entry.getKey() != null) {
                    e.add(entry.getValue().toString());
                    ev.add(entry.getKey().toString());
                }
            }

            pref.setEntries(e.toArray(new CharSequence[e.size()]));
            pref.setEntryValues(ev.toArray(new CharSequence[ev.size()]));
        }

        pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, final Object newValue) {
                pref.setTitle(
                        pref.getEntries()[
                                pref.findIndexOfValue(newValue.toString())
                                ]);
                return true;
            }
        });

        CharSequence[] entries = pref.getEntries();
        boolean enabled = entries != null && entries.length > 0;
        pref.setEnabled(enabled);

        return enabled;
    }
}
