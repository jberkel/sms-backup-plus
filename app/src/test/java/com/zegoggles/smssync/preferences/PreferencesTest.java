package com.zegoggles.smssync.preferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.MMS;
import static com.zegoggles.smssync.mail.DataType.SMS;

@RunWith(RobolectricTestRunner.class)
public class PreferencesTest {
    Preferences preferences;

    @Before public void before() {
        preferences = new Preferences(RuntimeEnvironment.application);
    }

    @Test public void shouldTestForFirstUse() throws Exception {
        assertThat(preferences.isFirstUse()).isTrue();
        assertThat(preferences.isFirstUse()).isFalse();
    }
    @Test public void shouldTestForFirstBackup() throws Exception {
        assertThat(preferences.isFirstBackup()).isTrue();
    }

    @Test public void shouldTestForFirstBackupSMS() throws Exception {
        preferences.getDataTypePreferences().setMaxSyncedDate(SMS, 1234);
        assertThat(preferences.isFirstBackup()).isFalse();
    }

    @Test public void shouldTestForFirstBackupMMS() throws Exception {
        preferences.getDataTypePreferences().setMaxSyncedDate(MMS, 1234);
        assertThat(preferences.isFirstBackup()).isFalse();
    }

    @Test public void shouldTestForFirstBackupCallLog() throws Exception {
        preferences.getDataTypePreferences().setMaxSyncedDate(CALLLOG, 1234);
        assertThat(preferences.isFirstBackup()).isFalse();
    }
}
