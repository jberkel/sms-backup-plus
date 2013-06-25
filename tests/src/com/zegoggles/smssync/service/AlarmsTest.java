package com.zegoggles.smssync.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import com.zegoggles.smssync.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPendingIntent;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class AlarmsTest {
    Alarms alarms;

    @Before public void before() {
        alarms = new Alarms(Robolectric.application);
    }

    @Test
    public void shouldScheduleImmediateBackup() throws Exception {
        long scheduled = alarms.scheduleImmediateBackup();
        verifyAlarmScheduled(scheduled, "BROADCAST_INTENT");
    }

    @Test
    public void shouldScheduleRegularBackup() throws Exception {
        Preferences.setEnableAutoSync(Robolectric.application, true);
        long scheduled = alarms.scheduleRegularBackup();
        verifyAlarmScheduled(scheduled, "REGULAR");
    }

    @Test
    public void shouldScheduleIncomingBackup() throws Exception {
        Preferences.setEnableAutoSync(Robolectric.application, true);
        long scheduled = alarms.scheduleIncomingBackup();
        verifyAlarmScheduled(scheduled, "INCOMING");
    }

    @Test
    public void shouldNotScheduleRegularBackupIfAutoBackupIsDisabled() throws Exception {
        Preferences.setEnableAutoSync(Robolectric.application, false);
        assertThat(alarms.scheduleRegularBackup()).isEqualTo(-1);
    }

    @Test
    public void shouldNotScheduleIncomingBackupIfAutoBackupIsDisabled() throws Exception {
        Preferences.setEnableAutoSync(Robolectric.application, false);
        assertThat(alarms.scheduleIncomingBackup()).isEqualTo(-1);
    }

    private void verifyAlarmScheduled(long scheduled, String expectedType) {
        final Context context = Robolectric.application;

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

        assertThat(shadowPendingIntent.getSavedIntent().getStringExtra(BackupType.EXTRA))
                .isEqualTo(expectedType);
    }
}
