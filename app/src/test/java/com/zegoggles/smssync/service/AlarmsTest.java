package com.zegoggles.smssync.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobTrigger;
import com.firebase.jobdispatcher.Trigger;
import com.zegoggles.smssync.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowPendingIntent;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class AlarmsTest {
    Alarms alarms;

    @Mock Preferences preferences;

    @Before public void before() {
        initMocks(this);
        ShadowPackageManager pm = shadowOf(RuntimeEnvironment.application.getPackageManager());

        Intent executeIntent = new Intent("com.firebase.jobdispatcher.ACTION_EXECUTE");
        executeIntent.setClassName(RuntimeEnvironment.application, "com.zegoggles.smssync.service.SmsJobService");

        ResolveInfo ri = new ResolveInfo();
        ServiceInfo si = new ServiceInfo();
        si.packageName = "com.zegoggles.smssync.service.SmsJobService";
        ri.serviceInfo = si;
        ri.isDefault = true;

        pm.addResolveInfoForIntent(executeIntent, ri);

            //new ResolveInfo().apply { serviceInfo = ServiceInfo().apply { enabled = true } });

        alarms = new Alarms(RuntimeEnvironment.application, preferences);
    }

    @Test public void shouldScheduleImmediateBackup() throws Exception {
        Job job = alarms.scheduleImmediateBackup();
        verifyJobScheduled(job, -1, "BROADCAST_INTENT");
    }

    @Test public void shouldScheduleRegularBackup() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.getRegularTimeoutSecs()).thenReturn(2000);
        Job job = alarms.scheduleRegularBackup();
        verifyJobScheduled(job, 2000, "REGULAR");
    }

    @Test public void shouldScheduleBootBackup() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        Job job = alarms.scheduleBootupBackup();
        verifyJobScheduled(job, 60, "REGULAR");
    }

    @Test public void shouldScheduleIncomingBackup() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.getIncomingTimeoutSecs()).thenReturn(2000);
        Job job = alarms.scheduleIncomingBackup();
        verifyJobScheduled(job, 2000, "INCOMING");
    }

    @Test public void shouldNotScheduleRegularBackupIfAutoBackupIsDisabled() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(false);
        assertThat(alarms.scheduleRegularBackup()).isEqualTo(null);
    }

    @Test public void shouldNotScheduleIncomingBackupIfAutoBackupIsDisabled() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(false);
        assertThat(alarms.scheduleIncomingBackup()).isEqualTo(null);
    }

    private void verifyJobScheduled(Job job, int scheduled, String expectedType)
    {
        if (scheduled <= 0)
        {
            assertThat(job.getTrigger() instanceof JobTrigger.ImmediateTrigger);
        }
        else
        {
            assertThat(job.getTrigger() instanceof JobTrigger.ExecutionWindowTrigger);
            JobTrigger.ExecutionWindowTrigger trigger = (JobTrigger.ExecutionWindowTrigger) job.getTrigger();
            JobTrigger.ExecutionWindowTrigger testTrigger = Trigger.executionWindow(scheduled, scheduled);
            assertThat(trigger.getWindowEnd()).isEqualTo(testTrigger.getWindowEnd());
            assertThat(trigger.getWindowStart()).isEqualTo(testTrigger.getWindowStart());
        }

        assertThat(job.getTag()).isEqualTo(expectedType);

    }
}
