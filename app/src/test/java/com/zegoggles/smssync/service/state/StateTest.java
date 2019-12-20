package com.zegoggles.smssync.service.state;

import android.content.res.Resources;
import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.service.BackupType;
import com.zegoggles.smssync.service.exception.RequiresWifiException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class StateTest {
    Resources resources;

    @Before public void before() {
        resources = RuntimeEnvironment.application.getResources();
    }

    @Test public void shouldCheckError() throws Exception {
        assertThat(new BackupState(SmsSyncState.ERROR, 0, 0,
                BackupType.REGULAR, DataType.SMS, new IOException("foo")
        ).isError()).isTrue();

        assertThat(new BackupState(SmsSyncState.FINISHED_BACKUP, 0, 0,
                BackupType.REGULAR, DataType.SMS, null
        ).isError()).isFalse();
    }

    @Test public void shouldGetErrorMessage() throws Exception {
        BackupState state = new BackupState(SmsSyncState.ERROR, 0, 0,
                BackupType.REGULAR, DataType.SMS, new IOException("foo"));

        assertThat(state.getErrorMessage(resources)).isEqualTo("foo");
        assertThat(state.getDetailedErrorMessage(resources)).isEqualTo("foo (exception: java.io.IOException: foo)");
    }

    @Test public void shouldGetErrorMessageRootCause() throws Exception {
        RuntimeException exception = new RuntimeException("foo", new IOException("bar"));

        BackupState state = new BackupState(SmsSyncState.ERROR, 0, 0,
                BackupType.REGULAR, DataType.SMS, exception);

        assertThat(state.getErrorMessage(resources)).isEqualTo("foo");
        assertThat(state.getDetailedErrorMessage(resources)).isEqualTo("foo (exception: java.lang.RuntimeException: foo, underlying=java.io.IOException: bar)");
    }

    @Test public void shouldGetErrorMessageRequiresWifi() throws Exception {
        BackupState state = new BackupState(SmsSyncState.ERROR, 0, 0,
                BackupType.REGULAR, DataType.SMS, new RequiresWifiException());

        assertThat(state.getErrorMessage(resources)).isEqualTo("No Wifi connection");
    }

    @Test public void shouldGetErrorMessagePrefix() throws Exception {
        BackupState state = new BackupState(SmsSyncState.ERROR, 0, 0,
                BackupType.REGULAR, DataType.SMS, new MessagingException("Unable to get IMAP prefix"));

        assertThat(state.getErrorMessage(resources)).isEqualTo("Temporary IMAP error, try again later.");
    }

    @Test public void shouldGetNotificationLabelLogin() throws Exception {
        assertThat(new BackupState(SmsSyncState.LOGIN, 0, 0,
                BackupType.REGULAR, DataType.SMS, null).getNotificationLabel(resources)).isEqualTo(
                    resources.getString(R.string.status_login_details)
                );

    }

    @Test public void shouldGetNotificationLabelCalculating() throws Exception {
        assertThat(new BackupState(SmsSyncState.CALC, 0, 0,
                BackupType.REGULAR, DataType.SMS, null).getNotificationLabel(resources)).isEqualTo(
                resources.getString(R.string.status_calc_details)
        );
    }

    @Test public void shouldGetNotificationLabelError() throws Exception {
        assertThat(new BackupState(SmsSyncState.ERROR, 0, 0,
                BackupType.REGULAR, DataType.SMS, new IOException("foo")).getNotificationLabel(resources))
                .isEqualTo("foo");
    }
}
