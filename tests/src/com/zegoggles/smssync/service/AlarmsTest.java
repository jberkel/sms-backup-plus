package com.zegoggles.smssync.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import com.zegoggles.smssync.preferences.Preferences;
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

    @Test
    public void shouldScheduleImmediateBackup() throws Exception {
        Context context = Robolectric.application;
        long scheduled = Alarms.scheduleImmediateBackup(context);
        verifyAlarm(context, scheduled, "BROADCAST_INTENT");
    }

    @Test
    public void shouldScheduleRegularBackup() throws Exception {
        Context context = Robolectric.application;
        Preferences.setEnableAutoSync(context, true);

        long scheduled = Alarms.scheduleRegularBackup(context);
        verifyAlarm(context, scheduled, "REGULAR");
    }

    @Test
    public void shouldScheduleIncomingBackup() throws Exception {
        Context context = Robolectric.application;
        Preferences.setEnableAutoSync(context, true);
        long scheduled = Alarms.scheduleIncomingBackup(context);
        verifyAlarm(context, scheduled, "INCOMING");
    }

    @Test
    public void shouldNotScheduleRegularBackupIfAutoBackupIsDisabled() throws Exception {
        Context context = Robolectric.application;
        Preferences.setEnableAutoSync(context, false);
        assertThat(Alarms.scheduleRegularBackup(context)).isEqualTo(-1);
    }

    @Test
    public void shouldNotScheduleIncomingBackupIfAutoBackupIsDisabled() throws Exception {
        Context context = Robolectric.application;
        Preferences.setEnableAutoSync(context, false);
        assertThat(Alarms.scheduleIncomingBackup(context)).isEqualTo(-1);
    }

    private void verifyAlarm(Context context, long scheduled, String expectedType) {
        assertThat(scheduled).isGreaterThan(0);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = shadowOf(alarmManager);

        ShadowAlarmManager.ScheduledAlarm nextScheduledAlarm = shadow.getNextScheduledAlarm();

        assertThat(nextScheduledAlarm.type).isEqualTo(AlarmManager.RTC_WAKEUP);
        assertThat(nextScheduledAlarm.triggerAtTime).isEqualTo(scheduled);

        PendingIntent pendingIntent = nextScheduledAlarm.operation;
        ShadowPendingIntent shadowPendingIntent = shadowOf(pendingIntent);

        assertThat(shadowPendingIntent.getSavedIntent().getComponent().getPackageName()).isEqualTo("com.zegoggles.smssync");
        assertThat(shadowPendingIntent.getSavedIntent().getComponent().getClassName()).isEqualTo("com.zegoggles.smssync.service.SmsBackupService");

        assertThat(shadowPendingIntent.getSavedIntent().getStringExtra(BackupType.EXTRA))
                .isEqualTo(expectedType);
    }
}
