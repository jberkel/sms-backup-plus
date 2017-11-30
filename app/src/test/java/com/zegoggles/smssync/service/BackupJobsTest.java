package com.zegoggles.smssync.service;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobTrigger;
import com.firebase.jobdispatcher.Trigger;
import com.zegoggles.smssync.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowPackageManager;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class BackupJobsTest {
    private BackupJobs subject;

    @Mock private Preferences preferences;

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
        subject = new BackupJobs(RuntimeEnvironment.application, preferences);
    }

    @Test public void shouldScheduleImmediate() throws Exception {
        Job job = subject.scheduleImmediate();
        verifyJobScheduled(job, -1, "BROADCAST_INTENT");
    }

    @Test public void shouldScheduleRegular() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.getRegularTimeoutSecs()).thenReturn(2000);
        Job job = subject.scheduleRegular();
        verifyJobScheduled(job, 2000, "REGULAR");
    }

    @Test public void shouldScheduleContentUriTrigger() throws Exception {
        Job job = subject.scheduleTriggerJob();
        assertThat(job.getTrigger()).isInstanceOf(JobTrigger.ContentUriTrigger.class);
    }

    @Test public void shouldScheduleBootForOldScheduler() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.isUseOldScheduler()).thenReturn(true);
        Job job = subject.scheduleBootup();
        verifyJobScheduled(job, 60, "REGULAR");
    }

    @Test public void shouldNotScheduleBootForNewScheduler() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.isUseOldScheduler()).thenReturn(false);
        Job job = subject.scheduleBootup();
        assertThat(job).isNull();
    }

    @Test public void shouldScheduleIncoming() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(true);
        when(preferences.getIncomingTimeoutSecs()).thenReturn(2000);
        Job job = subject.scheduleIncoming();
        verifyJobScheduled(job, 2000, "INCOMING");
    }

    @Test public void shouldNotScheduleRegularBackupIfAutoBackupIsDisabled() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(false);
        assertThat(subject.scheduleRegular()).isEqualTo(null);
    }

    @Test public void shouldNotScheduleIncomingBackupIfAutoBackupIsDisabled() throws Exception {
        when(preferences.isEnableAutoSync()).thenReturn(false);
        assertThat(subject.scheduleIncoming()).isEqualTo(null);
    }

    private void verifyJobScheduled(Job job, int scheduled, String expectedType) {
        assertThat(job).isNotNull();
        if (scheduled <= 0) {
            assertThat(job.getTrigger()).isInstanceOf(JobTrigger.ImmediateTrigger.class);
        } else {
            assertThat(job.getTrigger()).isInstanceOf(JobTrigger.ExecutionWindowTrigger.class);
            JobTrigger.ExecutionWindowTrigger trigger = (JobTrigger.ExecutionWindowTrigger) job.getTrigger();
            JobTrigger.ExecutionWindowTrigger testTrigger = Trigger.executionWindow(scheduled, scheduled);
            assertThat(trigger.getWindowEnd()).isEqualTo(testTrigger.getWindowEnd());
            assertThat(trigger.getWindowStart()).isEqualTo(testTrigger.getWindowStart());
        }
        assertThat(job.getTag()).isEqualTo(expectedType);
    }
}
