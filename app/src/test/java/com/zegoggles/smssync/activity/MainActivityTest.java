package com.zegoggles.smssync.activity;

import android.preference.PreferenceManager;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.mail.DataType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class MainActivityTest {
    MainActivity activity;

    @Before public void before() {
        activity = Robolectric.buildActivity(MainActivity.class).create().get();
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.application).edit().clear().commit();
    }

    @Test public void shouldDisplaySummaryOfEnabledBackupTypesDefault() throws Exception {
        assertThat(activity.getEnabledBackupSummary()).isEqualTo("Automatically backup SMS, MMS. You will also need to enable background data.");
    }

    @Test public void shouldDisplaySummaryOfEnabledBackupTypesNothingSelected() throws Exception {
        for (DataType t : DataType.values()) t.setBackupEnabled(PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.application), false);
        assertThat(activity.getEnabledBackupSummary()).isEqualTo(
            activity.getString(R.string.ui_enable_auto_sync_no_enabled_summary)
        );
    }
}
