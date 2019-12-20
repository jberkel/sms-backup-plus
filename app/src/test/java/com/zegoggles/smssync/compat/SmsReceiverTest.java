package com.zegoggles.smssync.compat;

import android.content.Intent;
import android.os.Build;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class SmsReceiverTest {
    private SmsReceiver subject;

    @Before
    public void setUp() throws Exception {
        subject = new SmsReceiver();
    }

    @Test @Config(sdk = Build.VERSION_CODES.JELLY_BEAN)
    public void testOnReceivePreKitKat() {
        subject.onReceive(RuntimeEnvironment.application, new Intent());
    }

    @Test @Config(sdk = Build.VERSION_CODES.KITKAT)
    public void testOnReceiveKitKat() {
        subject.onReceive(RuntimeEnvironment.application, new Intent());
    }

    @Test @Config(sdk = Build.VERSION_CODES.JELLY_BEAN)
    public void testIsSmsBackupDefaultSmsAppPreKitKat() {
        assertThat(SmsReceiver.isSmsBackupDefaultSmsApp(RuntimeEnvironment.application)).isFalse();
    }

    @Test @Config(sdk = Build.VERSION_CODES.KITKAT)
    public void testIsSmsBackupDefaultSmsAppKitKat() {
        assertThat(SmsReceiver.isSmsBackupDefaultSmsApp(RuntimeEnvironment.application)).isFalse();
    }
}
