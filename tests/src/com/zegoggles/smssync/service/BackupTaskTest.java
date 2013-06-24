package com.zegoggles.smssync.service;


import android.database.Cursor;
import android.database.MatrixCursor;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.MimeMessage;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.ConversionResult;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.mail.MessageConverter;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.service.state.BackupState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class BackupTaskTest {
    BackupTask task;
    BackupConfig config;
    @Mock BackupImapStore store;
    @Mock BackupImapStore.BackupFolder folder;
    @Mock SmsBackupService service;
    @Mock BackupState state;
    @Mock BackupItemsFetcher fetcher;
    @Mock MessageConverter converter;
    @Mock CalendarSyncer syncer;
    @Mock AuthPreferences authPreferences;

    @Before
    public void before() {
        initMocks(this);
        config = new BackupConfig(store, 0, false, 100, new ContactGroup(-1), -1, BackupType.MANUAL);

        when(service.getApplicationContext()).thenReturn(Robolectric.application);
        when(service.getState()).thenReturn(state);

        task = new BackupTask(service, fetcher, converter, syncer, authPreferences);
    }

    @Test
    public void shouldAcquireAndReleaseLocksDuringBackup() throws Exception {
        task.doInBackground(config);
        verify(service).acquireLocks();
        verify(service).releaseLocks();
    }

    @Test
    public void shouldBackupItems() throws Exception {
        when(authPreferences.isLoginInformationSet()).thenReturn(true);
        when(fetcher.getItemsForDataType(eq(DataType.SMS), any(ContactGroup.class), anyInt())).thenReturn(testMessages());
        when(converter.cursorToMessages(any(Cursor.class), anyInt(), eq(DataType.SMS))).thenReturn(result(DataType.SMS, 1));
        when(store.getFolder(DataType.SMS)).thenReturn(folder);

        task.doInBackground(config);

        verify(folder).appendMessages(any(Message[].class));
    }

    private Cursor testMessages() {
        MatrixCursor cursor = new MatrixCursor(new String[] {"_id"} );
        cursor.addRow(new Object[] {
            "12345"
        });
        return cursor;
    }

    private ConversionResult result(DataType type, int n) {
        ConversionResult result = new ConversionResult(type);
        for (int i = 0; i<n; i++) {
            result.messageList.add(new MimeMessage());
        }
        return result;
    }
}
