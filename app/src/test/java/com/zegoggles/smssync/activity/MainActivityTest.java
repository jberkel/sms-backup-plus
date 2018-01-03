package com.zegoggles.smssync.activity;

import android.preference.PreferenceManager;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.mail.DataType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
@Ignore
public class MainActivityTest {
    private MainActivity activity;

    @Before public void before() {
        activity = Robolectric.setupActivity(MainActivity.class);
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.application).edit().clear().commit();
    }

    @Test public void shouldDisplaySummaryOfEnabledBackupTypesDefault() throws Exception {
        assertThat(activity.summarizeAutoBackupSettings()).isEqualTo("Automatically backup SMS, MMS. You will also need to enable background data.");
    }

    @Test public void shouldDisplaySummaryOfEnabledBackupTypesNothingSelected() throws Exception {
        for (DataType t : DataType.values()) {
            activity.preferences.getDataTypePreferences().setBackupEnabled(false, t);
        }
        assertThat(activity.summarizeAutoBackupSettings()).isEqualTo(
            activity.getString(R.string.ui_enable_auto_sync_no_enabled_summary)
        );
    }
}
