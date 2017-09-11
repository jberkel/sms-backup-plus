package com.zegoggles.smssync.preferences;

import com.zegoggles.smssync.mail.DataType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.fest.assertions.api.Assertions.assertThat;

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
        DataType.SMS.setMaxSyncedDate(preferences.preferences, 1234);
        assertThat(preferences.isFirstBackup()).isFalse();
    }

    @Test public void shouldTestForFirstBackupMMS() throws Exception {
        DataType.MMS.setMaxSyncedDate(preferences.preferences, 1234);
        assertThat(preferences.isFirstBackup()).isFalse();
    }

    @Test public void shouldTestForFirstBackupCallLog() throws Exception {
        DataType.CALLLOG.setMaxSyncedDate(preferences.preferences, 1234);
        assertThat(preferences.isFirstBackup()).isFalse();
    }

    @Test public void shouldGetVersion() throws Exception {
        assertThat(preferences.getVersion(false)).matches("\\d+\\.\\d+\\.\\d+(-\\w+)?");
    }

    @Test public void shouldGetVersionWithCode() throws Exception {
        assertThat(preferences.getVersion(true)).matches("\\d+");
    }

    @Test public void shouldTestOnSDCARD() throws Exception {
        assertThat(preferences.isInstalledOnSDCard()).isFalse();
    }
}
