package com.zegoggles.smssync.receiver;

import android.content.Context;
import android.content.Intent;
import com.zegoggles.smssync.service.BackupJobs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class BootReceiverTest {
    @Mock BackupJobs backupJobs;
    BootReceiver receiver;

    @Before public void before() {
        initMocks(this);
        receiver = new BootReceiver() {
            @Override protected BackupJobs getBackupJobs(Context context) {
                return backupJobs;
            }
        };
    }

    @Test
    public void shouldScheduleBootupBackupAfterBootup() throws Exception {
        receiver.onReceive(RuntimeEnvironment.application, new Intent().setAction(Intent.ACTION_BOOT_COMPLETED));
        verify(backupJobs, times(1)).scheduleBootup();
    }
}
