package com.zegoggles.smssync.service;

import android.app.Notification;
import android.content.Intent;
import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.service.exception.RequiresBackgroundDataException;
import com.zegoggles.smssync.service.state.SmsSyncState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNotification;
import org.robolectric.shadows.ShadowNotificationManager;

import java.util.List;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class SmsBackupServiceTest {
    SmsBackupService service;
    ShadowConnectivityManager shadowConnectivityManager;
    ShadowNotificationManager shadowNotificationManager;

    @Mock AuthPreferences authPreferences;
    @Mock BackupTask backupTask;
    @Mock Alarms alarms;

    @Before
    public void before() {
        initMocks(this);

        service = new SmsBackupService() {
            @Override protected BackupTask getBackupTask() {
                return backupTask;
            }
            @Override protected Alarms getAlarms() {
                return alarms;
            }

            @Override protected AuthPreferences getAuthPreferences() {
                return authPreferences;
            }
        };
        shadowNotificationManager = shadowOf(service.getNotifier());
        shadowConnectivityManager = shadowOf(service.getConnectivityManager());

        service.onCreate();

        when(authPreferences.getStoreUri()).thenReturn("imap+ssl+://xoauth:foooo@imap.gmail.com:993");
    }

    @After public void after() {
        service.onDestroy();
    }

    @Test
    public void shouldTriggerBackupWithManualIntent() throws Exception {
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());
        service.handleIntent(intent);
        verify(backupTask).execute(any(BackupConfig.class));
    }

    @Test
    public void shouldRespectBackgroundDataSetting() throws Exception {
        Intent intent = new Intent();
        service.handleIntent(intent);

        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(RequiresBackgroundDataException.class);

        shadowConnectivityManager.setBackgroundDataSetting(true);

        service.handleIntent(intent);
        verify(backupTask).execute(any(BackupConfig.class));
    }

    @Test
    public void shouldPassInCorrectBackupConfig() throws Exception {
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());
        ArgumentCaptor<BackupConfig> config = ArgumentCaptor.forClass(BackupConfig.class);

        service.handleIntent(intent);
        verify(backupTask).execute(config.capture());

        BackupConfig backupConfig = config.getValue();
        assertThat(backupConfig.backupType).isEqualTo(BackupType.MANUAL);
        assertThat(backupConfig.maxItemsPerSync).isEqualTo(-1);
        assertThat(backupConfig.maxMessagePerRequest).isEqualTo(1);
        assertThat(backupConfig.currentTry).isEqualTo(0);
        assertThat(backupConfig.skip).isFalse();
    }

    @Test
    public void shouldScheduleNextBackupAfterFinished() throws Exception {
        shadowConnectivityManager.setBackgroundDataSetting(true);
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.REGULAR.name());
        service.handleIntent(intent);

        verify(backupTask).execute(any(BackupConfig.class));

        service.backupStateChanged(service.transition(SmsSyncState.FINISHED_BACKUP, null));

        verify(alarms).scheduleRegularBackup();

        assertThat(shadowOf(service).isStoppedBySelf());
        assertThat(shadowOf(service).isForegroundStopped());
    }

    @Test
    public void shouldCheckForValidStore() throws Exception {
        when(authPreferences.getStoreUri()).thenReturn("invalid");
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());

        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(MessagingException.class);
    }

    @Test
    public void shouldNotifyUserAboutErrorInManualMode() throws Exception {
        when(authPreferences.getStoreUri()).thenReturn("invalid");
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());

        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);

        assertNotificationShown("SMSBackup+ error", "No valid IMAP URI: invalid");

        assertThat(shadowOf(service).isStoppedBySelf());
        assertThat(shadowOf(service).isForegroundStopped());
    }

    private void assertNotificationShown(String title, String message) {
        List<Notification> notifications = shadowNotificationManager.getAllNotifications();
        assertThat(notifications).hasSize(1);
        ShadowNotification n = shadowOf(notifications.get(0));
        assertThat(n.getLatestEventInfo().getContentTitle()).isEqualTo(title);
        assertThat(n.getLatestEventInfo().getContentText()).isEqualTo(message);
    }
}
