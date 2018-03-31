package com.zegoggles.smssync.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.firebase.jobdispatcher.Driver;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobTrigger;
import com.firebase.jobdispatcher.JobValidator;
import com.firebase.jobdispatcher.ObservedUri;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.Collections;
import java.util.List;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static com.firebase.jobdispatcher.FirebaseJobDispatcher.SCHEDULE_RESULT_SUCCESS;
import static com.firebase.jobdispatcher.FirebaseJobDispatcher.SCHEDULE_RESULT_UNSUPPORTED_TRIGGER;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class AlarmManagerDriverTest {
    private AlarmManagerDriver subject;

    @Before
    public void setUp() throws Exception {
        subject = new AlarmManagerDriver(RuntimeEnvironment.application);
    }

    @Test
    public void testScheduleJobWithExecutionWindowTrigger() throws Exception {
        final Job job = jobBuilder()
            .setTrigger(Trigger.executionWindow(30, 30))
            .build();
        final int result = subject.schedule(job);
        assertThat(result).isEqualTo(SCHEDULE_RESULT_SUCCESS);
        assertAlarmScheduled("UNKNOWN");
    }

    @Test
    public void testScheduleJobWithExecutionWindowTriggerAndTag() throws Exception {
        final Job job = jobBuilder()
            .setTag("REGULAR")
            .setTrigger(Trigger.executionWindow(30, 30))
            .build();
        final int result = subject.schedule(job);
        assertThat(result).isEqualTo(SCHEDULE_RESULT_SUCCESS);
        assertAlarmScheduled("REGULAR");
    }

    @Test
    public void testScheduleJobWithoutTrigger() throws Exception {
        final Job job = jobBuilder().build();
        final int result = subject.schedule(job);
        assertThat(result).isEqualTo(SCHEDULE_RESULT_SUCCESS);
        assertAlarmScheduled("UNKNOWN");
    }

    @Test
    public void testScheduleJobWithUnknownTrigger() throws Exception {
        final Job job = jobBuilder()
            .setTrigger(Trigger.contentUriTrigger(Collections.singletonList(new ObservedUri(Uri.parse("foo://bar"), 0))))
            .build();
        final int result = subject.schedule(job);
        assertThat(result).isEqualTo(SCHEDULE_RESULT_UNSUPPORTED_TRIGGER);
    }

    private Intent assertAlarmScheduled(String ofExpectedType) {
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = shadowOf(alarmManager);

        ShadowAlarmManager.ScheduledAlarm nextScheduledAlarm = shadow.getNextScheduledAlarm();

        assertThat(nextScheduledAlarm.type).isEqualTo(AlarmManager.RTC_WAKEUP);
        assertThat(nextScheduledAlarm.triggerAtTime).isGreaterThan(0L);

        PendingIntent pendingIntent = nextScheduledAlarm.operation;
        ShadowPendingIntent shadowPendingIntent = shadowOf(pendingIntent);

        ComponentName component = shadowPendingIntent.getSavedIntent().getComponent();
        assertThat(component.getPackageName()).isEqualTo("com.zegoggles.smssync");
        assertThat(component.getClassName()).isEqualTo("com.zegoggles.smssync.service.SmsBackupService");
        assertThat(shadowPendingIntent.getFlags()).isEqualTo(FLAG_UPDATE_CURRENT);
        assertThat(shadowPendingIntent.getSavedIntent().getAction()).isNotEmpty();

        assertThat(shadowPendingIntent.getSavedIntent().getAction())
                .isEqualTo(ofExpectedType);

        return shadowPendingIntent.getSavedIntent();
    }

    private Job.Builder jobBuilder() {
        return new FirebaseJobDispatcher(new Driver() {
            @Override
            public int schedule(Job job) {
                return 0;
            }

            @Override
            public int cancel(String tag) {
                return 0;
            }

            @Override
            public int cancelAll() {
                return 0;
            }

            @Override
            public JobValidator getValidator() {
                return new JobValidator() {
                    @Override
                    public List<String> validate(JobParameters jobParameters) {
                        return null;
                    }

                    @Override
                    public List<String> validate(JobTrigger jobTrigger) {
                        return null;
                    }

                    @Override
                    public List<String> validate(RetryStrategy retryStrategy) {
                        return null;
                    }
                };
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        }).newJobBuilder();
    }
}
