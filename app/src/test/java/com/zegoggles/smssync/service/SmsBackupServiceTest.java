package com.zegoggles.smssync.service;

import android.app.Notification;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.exception.BackupDisabledException;
import com.zegoggles.smssync.service.exception.NoConnectionException;
import com.zegoggles.smssync.service.exception.RequiresBackgroundDataException;
import com.zegoggles.smssync.service.exception.RequiresLoginException;
import com.zegoggles.smssync.service.exception.RequiresWifiException;
import com.zegoggles.smssync.service.state.SmsSyncState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.shadows.ShadowNotification;
import org.robolectric.shadows.ShadowNotificationManager;
import org.robolectric.shadows.ShadowWifiManager;

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
    ShadowWifiManager shadowWifiManager;

    @Mock AuthPreferences authPreferences;
    @Mock Preferences preferences;
    @Mock BackupTask backupTask;
    @Mock Alarms alarms;

    @Before public void before() {
        initMocks(this);

        service = new SmsBackupService() {
            @Override protected BackupTask getBackupTask() {
                return backupTask;
            }
            @Override protected Alarms getAlarms() {
                return alarms;
            }

            @Override protected Preferences getPreferences() {
                return preferences;
            }

            @Override protected AuthPreferences getAuthPreferences() {
                return authPreferences;
            }
        };
        shadowNotificationManager = shadowOf(service.getNotifier());
        shadowConnectivityManager = shadowOf(service.getConnectivityManager());
        shadowWifiManager = shadowOf(service.getWifiManager());

        service.onCreate();

        when(authPreferences.getStoreUri()).thenReturn("imap+ssl+://xoauth:foooo@imap.gmail.com:993");
        when(authPreferences.isLoginInformationSet()).thenReturn(true);
        when(preferences.getBackupContactGroup()).thenReturn(ContactGroup.EVERYBODY);
    }

    @After public void after() {
        service.onDestroy();
    }

    @Test public void shouldTriggerBackupWithManualIntent() throws Exception {
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());
        service.handleIntent(intent);
        verify(backupTask).execute(any(BackupConfig.class));
    }

    @Test public void shouldRespectBackgroundDataSetting() throws Exception {
        Intent intent = new Intent();
        service.handleIntent(intent);

        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(RequiresBackgroundDataException.class);

        shadowConnectivityManager.setBackgroundDataSetting(true);

        service.handleIntent(intent);
        verify(backupTask).execute(any(BackupConfig.class));
    }

    @Test public void shouldCheckForConnectivityBeforeBackingUp() throws Exception {
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());
        shadowConnectivityManager.setActiveNetworkInfo(null);
        service.handleIntent(intent);

        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(NoConnectionException.class);
    }

    @Test public void shouldCheckForWifiConnectivity() throws Exception {
        Intent intent = new Intent();
        when(preferences.isWifiOnly()).thenReturn(true);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        shadowWifiManager.setWifiEnabled(false);
        service.handleIntent(intent);

        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(RequiresWifiException.class);
    }

    @Test public void shouldCheckForWifiConnectivityAndNetworkType() throws Exception {
        Intent intent = new Intent();
        when(preferences.isWifiOnly()).thenReturn(true);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        shadowConnectivityManager.setActiveNetworkInfo(connectedViaEdge());
        shadowWifiManager.setWifiEnabled(true);
        service.handleIntent(intent);

        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(RequiresWifiException.class);
    }

    @Test public void shouldCheckForLoginCredentials() throws Exception {
        Intent intent = new Intent();
        when(authPreferences.isLoginInformationSet()).thenReturn(false);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(RequiresLoginException.class);
    }

    @Test public void shouldCheckForEnabledDataTypes() throws Exception {
        for (DataType type : DataType.values()) {
            type.setBackupEnabled(Robolectric.application, false);
        }
        Intent intent = new Intent();
        when(authPreferences.isLoginInformationSet()).thenReturn(true);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(BackupDisabledException.class);
        assertThat(service.getState().state).isEqualTo(SmsSyncState.FINISHED_BACKUP);
    }

    @Test public void shouldPassInCorrectBackupConfig() throws Exception {
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());
        ArgumentCaptor<BackupConfig> config = ArgumentCaptor.forClass(BackupConfig.class);

        service.handleIntent(intent);
        verify(backupTask).execute(config.capture());

        BackupConfig backupConfig = config.getValue();
        assertThat(backupConfig.backupType).isEqualTo(BackupType.MANUAL);
        assertThat(backupConfig.currentTry).isEqualTo(0);
        assertThat(backupConfig.skip).isFalse();
    }

    @Test public void shouldScheduleNextBackupAfterFinished() throws Exception {
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

    @Test public void shouldCheckForValidStore() throws Exception {
        when(authPreferences.getStoreUri()).thenReturn("invalid");
        Intent intent = new Intent();
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());

        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isExactlyInstanceOf(MessagingException.class);
    }

    @Test public void shouldNotifyUserAboutErrorInManualMode() throws Exception {
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

    private NetworkInfo connectedViaEdge() {
        return ShadowNetworkInfo.newInstance(
            null,  /* detailed state */
            ConnectivityManager.TYPE_MOBILE_HIPRI,
            TelephonyManager.NETWORK_TYPE_EDGE,
            true,  /* available */
            true   /* connected */
        );
    }
}
