package com.zegoggles.smssync.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import com.zegoggles.smssync.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPendingIntent;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class AlarmsTest {
    Alarms alarms;
    @Mock Preferences preferences;

    @Before public void before() {
        initMocks(this);
        alarms = new Alarms(RuntimeEnvironment.application, preferences);
    }

    @Test public void shouldScheduleImmediateBackup() throws Exception {
        long scheduled = alarms.scheduleImmediateBackup();
        verifyAlarmScheduled(scheduled, "BROADCAST_INTENT");
    }

    @Test public void shouldScheduleRegularBackup() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.getRegularTimeoutSecs()).thenReturn(2000);
        long scheduled = alarms.scheduleRegularBackup();
        verifyAlarmScheduled(scheduled, "REGULAR");
    }

    @Test public void shouldScheduleBootBackup() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        long scheduled = alarms.scheduleBootupBackup();
        verifyAlarmScheduled(scheduled, "REGULAR");
    }

    @Test public void shouldScheduleIncomingBackup() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.getIncomingTimeoutSecs()).thenReturn(2000);
        long scheduled = alarms.scheduleIncomingBackup();
        verifyAlarmScheduled(scheduled, "INCOMING");
    }

    @Test public void shouldNotScheduleRegularBackupIfAutoBackupIsDisabled() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(false);
        assertThat(alarms.scheduleRegularBackup()).isEqualTo(-1);
    }

    @Test public void shouldNotScheduleIncomingBackupIfAutoBackupIsDisabled() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(false);
        assertThat(alarms.scheduleIncomingBackup()).isEqualTo(-1);
    }

    @Test public void shouldScheduleIntentsWithUniqueActions() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.getIncomingTimeoutSecs()).thenReturn(2000);

        long scheduled = alarms.scheduleIncomingBackup();
        Intent intent1 = verifyAlarmScheduled(scheduled, "INCOMING");

        long scheduled2 = alarms.scheduleIncomingBackup();
        Intent intent2 = verifyAlarmScheduled(scheduled2, "INCOMING");

        assertThat(intent1.getAction()).isNotEqualTo(intent2.getAction());
    }

    private Intent verifyAlarmScheduled(long scheduled, String expectedType) {
        final Context context = RuntimeEnvironment.application;

        assertThat(scheduled).isGreaterThan(0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = shadowOf(alarmManager);

        ShadowAlarmManager.ScheduledAlarm nextScheduledAlarm = shadow.getNextScheduledAlarm();

        assertThat(nextScheduledAlarm.type).isEqualTo(AlarmManager.RTC_WAKEUP);
        assertThat(nextScheduledAlarm.triggerAtTime).isEqualTo(scheduled);

        PendingIntent pendingIntent = nextScheduledAlarm.operation;
        ShadowPendingIntent shadowPendingIntent = shadowOf(pendingIntent);

        ComponentName component = shadowPendingIntent.getSavedIntent().getComponent();
        assertThat(component.getPackageName()).isEqualTo("com.zegoggles.smssync");
        assertThat(component.getClassName()).isEqualTo("com.zegoggles.smssync.service.SmsBackupService");
        assertThat(shadowPendingIntent.getFlags()).isEqualTo(0);
        assertThat(shadowPendingIntent.getSavedIntent().getAction()).isNotEmpty();

        assertThat(shadowPendingIntent.getSavedIntent().getStringExtra(BackupType.EXTRA))
                .isEqualTo(expectedType);

        return shadowPendingIntent.getSavedIntent();
    }
}
