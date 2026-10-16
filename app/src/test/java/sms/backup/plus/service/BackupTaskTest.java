package sms.backup.plus.service;


import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.MimeMessage;
import sms.backup.plus.contacts.ContactAccessor;
import sms.backup.plus.contacts.ContactGroup;
import sms.backup.plus.contacts.ContactGroupIds;
import sms.backup.plus.mail.BackupImapStore;
import sms.backup.plus.mail.ConversionResult;
import sms.backup.plus.mail.DataType;
import sms.backup.plus.mail.MessageConverter;
import sms.backup.plus.preferences.AuthPreferences;
import sms.backup.plus.preferences.DataTypePreferences;
import sms.backup.plus.preferences.Preferences;
import sms.backup.plus.service.state.BackupState;
import sms.backup.plus.service.state.SmsSyncState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.EnumSet;
import java.util.HashMap;

import static com.google.common.truth.Truth.assertThat;
import static sms.backup.plus.mail.DataType.CALLLOG;
import static sms.backup.plus.mail.DataType.MMS;
import static sms.backup.plus.mail.DataType.SMS;
import static sms.backup.plus.service.BackupItemsFetcher.emptyCursor;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.notNull;
import static org.mockito.Matchers.same;
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
    @Mock DataTypePreferences dataTypePreferences;
    @Mock Preferences preferences;
    @Mock ContactAccessor accessor;
    @Before public void before() {
        initMocks(this);
        config = getBackupConfig(EnumSet.of(SMS));
        when(service.getApplicationContext()).thenReturn(RuntimeEnvironment.application);
        when(service.getState()).thenReturn(state);
        when(preferences.getDataTypePreferences()).thenReturn(dataTypePreferences);

        task = new BackupTask(service, fetcher, converter, syncer, authPreferences, preferences, accessor);
        context = RuntimeEnvironment.application;
    }

    private BackupConfig getBackupConfig(EnumSet<DataType> types) {
        return new BackupConfig(store, 0, 100, new ContactGroup(-1), BackupType.MANUAL, types,
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
        when(store.getFolder(SMS, dataTypePreferences)).thenReturn(folder);
        task.doInBackground(config);
        verify(store).checkSettings();
    }

    @Test public void shouldBackupItems() throws Exception {
        mockFetch(SMS, 1);

        when(converter.convertMessages(any(Cursor.class), eq(SMS))).thenReturn(result(SMS, 1));
        when(store.getFolder(notNull(DataType.class), same(dataTypePreferences))).thenReturn(folder);

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
        when(store.getFolder(notNull(DataType.class), same(dataTypePreferences))).thenReturn(folder);
        when(converter.convertMessages(any(Cursor.class), any(DataType.class))).thenReturn(result(SMS, 1));

        BackupState finalState = task.doInBackground(getBackupConfig(EnumSet.of(SMS, MMS)));

        assertThat(finalState.currentSyncedItems).isEqualTo(3);

        verify(folder, times(3)).appendMessages(anyListOf(Message.class));
    }

    @Test public void shouldCreateFoldersLazilyOnlyForNeededTypes() throws Exception {
        mockFetch(SMS, 1);

        when(converter.convertMessages(any(Cursor.class), eq(SMS))).thenReturn(result(SMS, 1));
        when(store.getFolder(notNull(DataType.class), same(dataTypePreferences))).thenReturn(folder);

        task.doInBackground(config);

        verify(store).getFolder(SMS, dataTypePreferences);
        verify(store, never()).getFolder(MMS, dataTypePreferences);
        verify(store, never()).getFolder(CALLLOG, dataTypePreferences);
    }

    @Test public void shouldCloseImapFolderAfterBackup() throws Exception {
        mockFetch(SMS, 1);
        when(converter.convertMessages(any(Cursor.class), eq(SMS))).thenReturn(result(SMS, 1));
        when(store.getFolder(notNull(DataType.class), same(dataTypePreferences))).thenReturn(folder);

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
            store, 0, 100, new ContactGroup(-1), BackupType.SKIP, EnumSet.of(SMS), false
            )
        );
        verify(dataTypePreferences).setMaxSyncedDate(DataType.SMS, -23);
        verifyZeroInteractions(dataTypePreferences);

        assertThat(finalState).isNotNull();
        assertThat(finalState.isFinished()).isTrue();
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
