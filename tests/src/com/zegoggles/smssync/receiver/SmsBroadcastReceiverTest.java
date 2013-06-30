package com.zegoggles.smssync.receiver;

import android.content.Context;
import android.content.Intent;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.Alarms;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class SmsBroadcastReceiverTest {
    Context context;
    @Mock Alarms alarms;
    @Mock Preferences preferences;
    @Mock AuthPreferences authPreferences;
    SmsBroadcastReceiver receiver;

    @Before public void before() {
        initMocks(this);
        context = Robolectric.application;
        receiver = new SmsBroadcastReceiver() {
            @Override protected Alarms getAlarms(Context context) {
                return alarms;
            }

            @Override protected Preferences getPreferences(Context context) {
                return preferences;
            }

            @Override protected AuthPreferences getAuthPreferences(Context context) {
                return authPreferences;
            }
        };
    }

    @Test public void shouldScheduleBootupBackupAfterBootup() throws Exception {
        mockScheduled();
        receiver.onReceive(context, new Intent().setAction(Intent.ACTION_BOOT_COMPLETED));
        verify(alarms, times(1)).scheduleBootupBackup();
    }

    @Test public void shouldScheduleIncomingBackupAfterIncomingMessage() throws Exception {
        mockScheduled();
        receiver.onReceive(context, new Intent().setAction("android.provider.Telephony.SMS_RECEIVED"));
        verify(alarms, times(1)).scheduleIncomingBackup();
    }

    @Test public void shouldNotScheduleIfAutoSyncIsDisabled() throws Exception {
        mockScheduled();
        when(preferences.isEnableAutoSync()).thenReturn(false);
        receiver.onReceive(context, new Intent().setAction("android.provider.Telephony.SMS_RECEIVED"));
        verifyZeroInteractions(alarms);
    }

    @Test public void shouldNotScheduleIfLoginInformationIsNotSet() throws Exception {
        mockScheduled();
        when(authPreferences.isLoginInformationSet()).thenReturn(false);
        receiver.onReceive(context, new Intent().setAction("android.provider.Telephony.SMS_RECEIVED"));
        verifyZeroInteractions(alarms);
    }

    @Test public void shouldNotScheduleIfFirstBackupHasNotBeenRun() throws Exception {
        mockScheduled();
        when(preferences.isFirstBackup()).thenReturn(true);
        receiver.onReceive(context, new Intent().setAction("android.provider.Telephony.SMS_RECEIVED"));
        verifyZeroInteractions(alarms);
    }

    private void mockScheduled() {
        when(authPreferences.isLoginInformationSet()).thenReturn(true);
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.isFirstBackup()).thenReturn(false);
    }
}
