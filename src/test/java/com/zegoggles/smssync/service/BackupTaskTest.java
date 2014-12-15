package com.zegoggles.smssync.service;


import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.store.XOAuth2AuthenticationFailedException;
import com.zegoggles.smssync.auth.TokenRefreshException;
import com.zegoggles.smssync.auth.TokenRefresher;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.ConversionResult;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.mail.MessageConverter;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.SmsSyncState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.EnumSet;
import java.util.HashMap;

import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.MMS;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static com.zegoggles.smssync.mail.DataType.WHATSAPP;
import static com.zegoggles.smssync.service.BackupItemsFetcher.emptyCursor;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.anyListOf;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class BackupTaskTest {
    BackupTask task;
    BackupConfig config;
    Context context;
    @Mock BackupImapStore store;
    @Mock BackupImapStore.BackupFolder folder;
    @Mock SmsBackupService service;
    @Mock BackupState state;
    @Mock BackupItemsFetcher fetcher;
    @Mock MessageConverter converter;
    @Mock CalendarSyncer syncer;
    @Mock AuthPreferences authPreferences;
    @Mock Preferences preferences;
    @Mock ContactAccessor accessor;
    @Mock TokenRefresher tokenRefresher;

    @Before public void before() {
        initMocks(this);
        config = getBackupConfig(EnumSet.of(SMS));
        when(service.getApplicationContext()).thenReturn(Robolectric.application);
        when(service.getState()).thenReturn(state);

        task = new BackupTask(service, fetcher, converter, syncer, authPreferences, preferences, accessor, tokenRefresher);
        context = Robolectric.application;
    }

    private BackupConfig getBackupConfig(EnumSet<DataType> types) {
        return new BackupConfig(store, 0, false, 100, new ContactGroup(-1), BackupType.MANUAL, types,
                false
        );
    }

    @Test public void shouldAcquireAndReleaseLocksDuringBackup() throws Exception {
        mockAllFetchEmpty();

        task.doInBackground(config);

        verify(service).acquireLocks();
        verify(service).releaseLocks();
        verify(service).transition(SmsSyncState.FINISHED_BACKUP, null);
    }

    @Test public void shouldVerifyStoreSettings() throws Exception {
        mockFetch(SMS, 1);
        when(converter.convertMessages(any(Cursor.class), eq(SMS))).thenReturn(result(SMS, 1));
        when(store.getFolder(SMS)).thenReturn(folder);
        task.doInBackground(config);
        verify(store).checkSettings();
    }

    @Test public void shouldBackupItems() throws Exception {
        mockFetch(SMS, 1);

        when(converter.convertMessages(any(Cursor.class), eq(SMS))).thenReturn(result(SMS, 1));
        when(store.getFolder(notNull(DataType.class))).thenReturn(folder);

        BackupState finalState = task.doInBackground(config);

        verify(folder).appendMessages(anyListOf(Message.class));

        verify(service).transition(SmsSyncState.LOGIN, null);
        verify(service).transition(SmsSyncState.CALC, null);

        assertThat(finalState).isNotNull();
        assertThat(finalState.isFinished()).isTrue();
        assertThat(finalState.currentSyncedItems).isEqualTo(1);
        assertThat(finalState.itemsToSync).isEqualTo(1);
        assertThat(finalState.backupType).isEqualTo(config.backupType);
    }

    @Test
    public void shouldBackupMultipleTypes() throws Exception {
        mockFetch(SMS, 1);
        mockFetch(MMS, 2);
        when(store.getFolder(notNull(DataType.class))).thenReturn(folder);
        when(converter.convertMessages(any(Cursor.class), any(DataType.class))).thenReturn(result(SMS, 1));

        BackupState finalState = task.doInBackground(getBackupConfig(EnumSet.of(SMS, MMS)));

        assertThat(finalState.currentSyncedItems).isEqualTo(3);

        verify(folder, times(3)).appendMessages(anyListOf(Message.class));
    }

    @Test public void shouldCreateFoldersLazilyOnlyForNeededTypes() throws Exception {
        mockFetch(SMS, 1);

        when(converter.convertMessages(any(Cursor.class), eq(SMS))).thenReturn(result(SMS, 1));
        when(store.getFolder(notNull(DataType.class))).thenReturn(folder);

        task.doInBackground(config);

        verify(store).getFolder(SMS);
        verify(store, never()).getFolder(MMS);
        verify(store, never()).getFolder(CALLLOG);
        verify(store, never()).getFolder(WHATSAPP);
    }

    @Test public void shouldCloseImapFolderAfterBackup() throws Exception {
        mockFetch(SMS, 1);
        when(converter.convertMessages(any(Cursor.class), eq(SMS))).thenReturn(result(SMS, 1));
        when(store.getFolder(notNull(DataType.class))).thenReturn(folder);

        task.doInBackground(config);

        verify(store).closeFolders();
    }

    @Test public void shouldCreateNoFoldersIfNoItemsToBackup() throws Exception {
        mockFetch(SMS, 0);
        task.doInBackground(config);
        verifyZeroInteractions(store);
    }

    @Test public void shouldSkipItems() throws Exception {
        when(fetcher.getMostRecentTimestamp(any(DataType.class))).thenReturn(-23L);

        BackupState finalState = task.doInBackground(new BackupConfig(
            store, 0, true, 100, new ContactGroup(-1), BackupType.MANUAL, EnumSet.of(SMS), false
            )
        );
        assertThat(DataType.SMS.getMaxSyncedDate(context)).isEqualTo(-23);
        assertThat(DataType.MMS.getMaxSyncedDate(context)).isEqualTo(-1);
        assertThat(DataType.CALLLOG.getMaxSyncedDate(context)).isEqualTo(-1);

        assertThat(finalState).isNotNull();
        assertThat(finalState.isFinished()).isTrue();
    }

    @Test public void shouldHandleAuthErrorAndTokenCannotBeRefreshed() throws Exception {
        mockFetch(SMS, 1);
        when(converter.convertMessages(any(Cursor.class), notNull(DataType.class))).thenReturn(result(SMS, 1));

        XOAuth2AuthenticationFailedException exception = mock(XOAuth2AuthenticationFailedException.class);
        when(exception.getStatus()).thenReturn(400);

        when(store.getFolder(notNull(DataType.class))).thenThrow(exception);

        doThrow(new TokenRefreshException("failed")).when(tokenRefresher).refreshOAuth2Token();

        task.doInBackground(config);

        verify(tokenRefresher, times(1)).refreshOAuth2Token();
        verify(service).transition(SmsSyncState.ERROR, exception);

        // make sure locks only get acquired+released once
        verify(service).acquireLocks();
        verify(service).releaseLocks();
    }

    @Test public void shouldHandleAuthErrorAndTokenCouldBeRefreshed() throws Exception {
        mockFetch(SMS, 1);
        when(converter.convertMessages(any(Cursor.class), notNull(DataType.class))).thenReturn(result(SMS, 1));

        XOAuth2AuthenticationFailedException exception = mock(XOAuth2AuthenticationFailedException.class);
        when(exception.getStatus()).thenReturn(400);

        when(store.getFolder(notNull(DataType.class))).thenThrow(exception);
        when(service.getBackupImapStore()).thenReturn(store);

        task.doInBackground(config);

        verify(tokenRefresher).refreshOAuth2Token();

        verify(service, times(2)).transition(SmsSyncState.LOGIN, null);
        verify(service, times(2)).transition(SmsSyncState.CALC, null);
        verify(service).transition(SmsSyncState.ERROR, exception);

        // make sure locks only get acquired+released once
        verify(service).acquireLocks();
        verify(service).releaseLocks();
    }


    private ConversionResult result(DataType type, int n) {
        ConversionResult result = new ConversionResult(type);
        for (int i = 0; i<n; i++) {
            result.add(new MimeMessage(), new HashMap<String, String>());
        }
        return result;
    }

    private void mockFetch(DataType type, final int n) {
        when(fetcher.getItemsForDataType(eq(type), any(ContactGroupIds.class), anyInt())).then(new Answer<Object>() {
            @Override public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                return testMessages(n);
            }
        });
    }

    private Cursor testMessages(int n) {
        MatrixCursor cursor = new MatrixCursor(new String[] {"_id"} );
        for (int i = 0; i < n; i++) {
            cursor.addRow(new Object[]{
                    "12345"
            });
        }
        return cursor;
    }


    private void mockAllFetchEmpty() {
        when(fetcher.getItemsForDataType(any(DataType.class), any(ContactGroupIds.class), anyInt())).thenReturn(emptyCursor());
    }
}
