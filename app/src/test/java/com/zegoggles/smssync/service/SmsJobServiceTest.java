package com.zegoggles.smssync.service;

import com.firebase.jobdispatcher.JobParameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Robolectric.setupService;

@RunWith(RobolectricTestRunner.class)
public class SmsJobServiceTest {

    private SmsJobService smsJobService;

    @Before
    public void setUp() throws Exception {
        smsJobService = setupService(SmsJobService.class);
    }

    @Test public void testOnStartJob() {
        final JobParameters jobParameters = mock(JobParameters.class);
        when(jobParameters.getTag()).thenReturn(BackupJobs.CONTENT_TRIGGER_TAG);

        boolean moreWork = smsJobService.onStartJob(jobParameters);
        assertThat(moreWork).isFalse();
    }

    @Test public void testOnStopJob() {
        final JobParameters jobParameters = mock(JobParameters.class);
        boolean shouldRetry = smsJobService.onStopJob(jobParameters);
        assertThat(shouldRetry).isFalse();
    }
}
