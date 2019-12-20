package com.zegoggles.smssync.service;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.core.app.NotificationCompat;
import android.telephony.TelephonyManager;
import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.DataTypePreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.exception.BackupDisabledException;
import com.zegoggles.smssync.service.exception.NoConnectionException;
import com.zegoggles.smssync.service.exception.RequiresLoginException;
import com.zegoggles.smssync.service.exception.RequiresWifiException;
import com.zegoggles.smssync.service.state.SmsSyncState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.shadows.ShadowWifiManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.service.BackupType.MANUAL;
import static com.zegoggles.smssync.service.BackupType.REGULAR;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class SmsBackupServiceTest {
    SmsBackupService service;
    ShadowConnectivityManager shadowConnectivityManager;
    ShadowWifiManager shadowWifiManager;
    List<NotificationCompat.Builder> sentNotifications;

    @Mock AuthPreferences authPreferences;
    @Mock Preferences preferences;
    @Mock DataTypePreferences dataTypePreferences;
    @Mock BackupTask backupTask;
    @Mock BackupJobs backupJobs;

    @Before public void before() {
        initMocks(this);
        sentNotifications = new ArrayList<NotificationCompat.Builder>();
        service = new SmsBackupService() {
            @Override public Context getApplicationContext() { return RuntimeEnvironment.application; }
            @Override public Resources getResources() { return getApplicationContext().getResources(); }
            @Override protected BackupTask getBackupTask() { return backupTask; }
            @Override protected BackupJobs getBackupJobs() { return backupJobs; }
            @Override protected Preferences getPreferences() { return preferences; }
            @Override public int checkPermission(String permission, int pid, int uid) { return PERMISSION_GRANTED; }
            @Override protected AuthPreferences getAuthPreferences() { return authPreferences; }
            @Override protected void notifyUser(int icon, NotificationCompat.Builder builder) {
                sentNotifications.add(builder);
            }
        };
        shadowConnectivityManager = shadowOf(service.getConnectivityManager());
        shadowWifiManager = shadowOf(service.getWifiManager());

        service.onCreate();

        when(authPreferences.getStoreUri()).thenReturn("imap+ssl+://xoauth:foooo@imap.gmail.com:993");
        when(authPreferences.isLoginInformationSet()).thenReturn(true);
        when(preferences.getBackupContactGroup()).thenReturn(ContactGroup.EVERYBODY);
        when(preferences.isUseOldScheduler()).thenReturn(true);
        when(preferences.getDataTypePreferences()).thenReturn(dataTypePreferences);
        when(dataTypePreferences.enabled()).thenReturn(EnumSet.of(DataType.SMS));
    }

    @After public void after() {
        service.onDestroy();
    }

    @Test public void shouldTriggerBackupWithManualIntent() throws Exception {
        Intent intent = new Intent(MANUAL.name());
        service.handleIntent(intent);
        verify(backupTask).execute(any(BackupConfig.class));
    }

    @Test public void shouldCheckForConnectivityBeforeBackingUp() throws Exception {
        Intent intent = new Intent(MANUAL.name());

        shadowConnectivityManager.setActiveNetworkInfo(null);
        service.handleIntent(intent);

        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isInstanceOf(NoConnectionException.class);
    }

    @Test public void shouldNotCheckForConnectivityBeforeBackingUpWithNewScheduler() throws Exception {
        when(preferences.isUseOldScheduler()).thenReturn(false);

        Intent intent = new Intent(REGULAR.name());
        shadowConnectivityManager.setActiveNetworkInfo(null);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        service.handleIntent(intent);
        verify(backupTask).execute(any(BackupConfig.class));
    }

    @Test public void shouldCheckForWifiConnectivity() throws Exception {
        Intent intent = new Intent();
        when(preferences.isWifiOnly()).thenReturn(true);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        service.handleIntent(intent);

        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isInstanceOf(RequiresWifiException.class);
    }

    @Test public void shouldCheckForWifiConnectivityAndNetworkType() throws Exception {
        Intent intent = new Intent();
            when(preferences.isWifiOnly()).thenReturn(true);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        shadowConnectivityManager.setActiveNetworkInfo(connectedViaEdge());
        service.handleIntent(intent);

        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isInstanceOf(RequiresWifiException.class);
    }

    @Test public void shouldCheckForLoginCredentials() throws Exception {
        Intent intent = new Intent();
        when(authPreferences.isLoginInformationSet()).thenReturn(false);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isInstanceOf(RequiresLoginException.class);
    }

    @Test public void shouldCheckForEnabledDataTypes() throws Exception {
        when(dataTypePreferences.enabled()).thenReturn(EnumSet.noneOf(DataType.class));

        Intent intent = new Intent();
        when(authPreferences.isLoginInformationSet()).thenReturn(true);
        shadowConnectivityManager.setBackgroundDataSetting(true);
        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isInstanceOf(BackupDisabledException.class);
        assertThat(service.getState().state).isEqualTo(SmsSyncState.FINISHED_BACKUP);
    }

    @Test public void shouldPassInCorrectBackupConfig() throws Exception {
        Intent intent = new Intent(MANUAL.name());
        ArgumentCaptor<BackupConfig> config = ArgumentCaptor.forClass(BackupConfig.class);

        service.handleIntent(intent);
        verify(backupTask).execute(config.capture());

        BackupConfig backupConfig = config.getValue();
        assertThat(backupConfig.backupType).isEqualTo(MANUAL);
        assertThat(backupConfig.currentTry).isEqualTo(0);
    }

    @Test public void shouldScheduleNextRegularBackupAfterFinished() throws Exception {
        shadowConnectivityManager.setBackgroundDataSetting(true);
        Intent intent = new Intent(REGULAR.name());
        service.handleIntent(intent);

        verify(backupTask).execute(any(BackupConfig.class));

        service.backupStateChanged(service.transition(SmsSyncState.FINISHED_BACKUP, null));

        verify(backupJobs).scheduleRegular();

        assertThat(shadowOf(service).isStoppedBySelf()).isTrue();
        assertThat(shadowOf(service).isForegroundStopped()).isTrue();
    }

    @Test public void shouldCheckForValidStore() throws Exception {
        when(authPreferences.getStoreUri()).thenReturn("invalid");
        Intent intent = new Intent(MANUAL.name());

        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);
        assertThat(service.getState().exception).isInstanceOf(MessagingException.class);
    }

    @Test public void shouldNotifyUserAboutErrorInManualMode() throws Exception {
        when(authPreferences.getStoreUri()).thenReturn("invalid");
        Intent intent = new Intent(MANUAL.name());

        service.handleIntent(intent);
        verifyZeroInteractions(backupTask);

        assertNotificationShown("SMSBackup+ error", "No valid IMAP URI: invalid");

        assertThat(shadowOf(service).isStoppedBySelf()).isTrue();
        assertThat(shadowOf(service).isForegroundStopped()).isTrue();
    }

    private void assertNotificationShown(CharSequence title, CharSequence message) {
        assertThat(sentNotifications).hasSize(1);
        // TODO
        /*
        NotificationCompat.Builder u = sentNotifications.get(0);
        assertThat(u.mContentTitle).isEqualTo(title);
        assertThat(u.mContentText).isEqualTo(message);
        */
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
