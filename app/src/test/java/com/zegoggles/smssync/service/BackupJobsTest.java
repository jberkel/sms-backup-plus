package com.zegoggles.smssync.service;

import com.zegoggles.smssync.preferences.DataTypePreferences;
import com.zegoggles.smssync.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class BackupJobsTest {
    private BackupJobs subject;

    @Mock private Preferences preferences;
    @Mock private DataTypePreferences dataTypePreferences;

    @Before public void before() {
        initMocks(this);
        subject = new BackupJobs(RuntimeEnvironment.application, preferences);
        when(preferences.getDataTypePreferences()).thenReturn(dataTypePreferences);
    }

    @Test public void shouldScheduleImmediate() {
        subject.scheduleImmediate();
        // WorkManager enqueues work; verify no exception is thrown
    }

    @Test public void shouldScheduleRegular() {
        when(preferences.isAutoBackupEnabled()).thenReturn(true);
        when(preferences.getRegularTimeoutSecs()).thenReturn(2000);
        long delayMs = subject.scheduleRegular();
        assertThat(delayMs).isEqualTo(2000 * 1000L);
    }

    @Test public void shouldScheduleBootupForOldScheduler() {
        when(preferences.isAutoBackupEnabled()).thenReturn(true);
        when(preferences.isUseOldScheduler()).thenReturn(true);
        // Should not throw
        subject.scheduleBootup();
    }

    @Test public void shouldScheduleIncoming() {
        when(preferences.isAutoBackupEnabled()).thenReturn(true);
        when(preferences.getIncomingTimeoutSecs()).thenReturn(2000);
        long delayMs = subject.scheduleIncoming();
        assertThat(delayMs).isEqualTo(2000 * 1000L);
    }

    @Test public void shouldNotScheduleRegularBackupIfAutoBackupIsDisabled() {
        when(preferences.isAutoBackupEnabled()).thenReturn(false);
        assertThat(subject.scheduleRegular()).isEqualTo(0);
    }

    @Test public void shouldNotScheduleIncomingBackupIfAutoBackupIsDisabled() {
        when(preferences.isAutoBackupEnabled()).thenReturn(false);
        assertThat(subject.scheduleIncoming()).isEqualTo(0);
    }
}
