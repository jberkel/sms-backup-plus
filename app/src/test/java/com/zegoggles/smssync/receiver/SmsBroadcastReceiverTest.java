package com.zegoggles.smssync.receiver;

import android.content.Context;
import android.content.Intent;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.BackupJobs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class SmsBroadcastReceiverTest {
    Context context;
    @Mock BackupJobs backupJobs;
    @Mock Preferences preferences;
    @Mock AuthPreferences authPreferences;
    SmsBroadcastReceiver receiver;

    @Before public void before() {
        initMocks(this);
        context = RuntimeEnvironment.application;
        receiver = new SmsBroadcastReceiver() {
            @Override protected BackupJobs getBackupJobs(Context context) {
                return backupJobs;
            }

            @Override protected Preferences getPreferences(Context context) {
                return preferences;
            }

            @Override protected AuthPreferences getAuthPreferences(Context context) {
                return authPreferences;
            }
        };
    }

    @Test public void shouldScheduleIncomingBackupAfterIncomingMessage() throws Exception {
        mockScheduled();
        receiver.onReceive(context, new Intent().setAction("android.provider.Telephony.SMS_RECEIVED"));
        verify(backupJobs, times(1)).scheduleIncoming();
    }

    @Test public void shouldNotScheduleIfAutoBackupIsDisabled() throws Exception {
        mockScheduled();
        when(preferences.isAutoBackupEnabled()).thenReturn(false);
        receiver.onReceive(context, new Intent().setAction("android.provider.Telephony.SMS_RECEIVED"));
        verifyZeroInteractions(backupJobs);
    }

    @Test public void shouldNotScheduleIfLoginInformationIsNotSet() throws Exception {
        mockScheduled();
        when(authPreferences.isLoginInformationSet()).thenReturn(false);
        receiver.onReceive(context, new Intent().setAction("android.provider.Telephony.SMS_RECEIVED"));
        verifyZeroInteractions(backupJobs);
    }

    @Test public void shouldNotScheduleIfFirstBackupHasNotBeenRun() throws Exception {
        mockScheduled();
        when(preferences.isFirstBackup()).thenReturn(true);
        receiver.onReceive(context, new Intent().setAction("android.provider.Telephony.SMS_RECEIVED"));
        verifyZeroInteractions(backupJobs);
    }

    private void mockScheduled() {
        when(authPreferences.isLoginInformationSet()).thenReturn(true);
        when(preferences.isAutoBackupEnabled()).thenReturn(true);
        when(preferences.isFirstBackup()).thenReturn(false);
        when(preferences.isUseOldScheduler()).thenReturn(true);
    }
}
