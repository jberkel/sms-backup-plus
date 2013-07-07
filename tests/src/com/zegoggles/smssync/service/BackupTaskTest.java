package com.zegoggles.smssync.service;


import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.MimeMessage;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.HashMap;

import static com.zegoggles.smssync.mail.DataType.*;
import static com.zegoggles.smssync.service.BackupItemsFetcher.emptyCursor;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
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

    @Before
    public void before() {
        initMocks(this);
        config = new BackupConfig(store, 0, false, 100, new ContactGroup(-1), -1, BackupType.MANUAL, false,
                Arrays.asList(SMS));
        when(service.getApplicationContext()).thenReturn(Robolectric.application);
        when(service.getState()).thenReturn(state);

        task = new BackupTask(service, fetcher, converter, syncer, authPreferences, preferences, accessor);
        context = Robolectric.application;
    }

    @Test
    public void shouldAcquireAndReleaseLocksDuringBackup() throws Exception {
        mockAllFetchEmpty();
        task.doInBackground(config);
        verify(service).acquireLocks();
        verify(service).releaseLocks();
    }

    @Test
    public void shouldBackupItems() throws Exception {
        when(authPreferences.isLoginInformationSet()).thenReturn(true);

        mockFetch(SMS, testMessages());
        mockFetch(MMS, emptyCursor());
        mockFetch(CALLLOG, emptyCursor());
        mockFetch(WHATSAPP, emptyCursor());

        when(converter.convertMessages(any(Cursor.class), anyInt(), eq(SMS))).thenReturn(result(SMS, 1));
        when(store.getFolder(SMS)).thenReturn(folder);

        task.doInBackground(config);

        verify(folder).appendMessages(any(Message[].class));
    }

    @Test
    public void shouldSkipItems() throws Exception {
        when(authPreferences.isLoginInformationSet()).thenReturn(true);
        when(fetcher.getMostRecentTimestamp(any(DataType.class))).thenReturn(-23L);

        task.doInBackground(new BackupConfig(
            store, 0, true, 100, new ContactGroup(-1), -1, BackupType.MANUAL, false,
                Arrays.asList(SMS))
        );

        for (DataType type : DataType.values()) {
            assertThat(type.getMaxSyncedDate(context)).isEqualTo(-23);
        }
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
            result.add(new MimeMessage(), new HashMap<String, String>());
        }
        return result;
    }

    private void mockFetch(DataType type, Cursor cursor) {
        when(fetcher.getItemsForDataType(eq(type), any(ContactGroupIds.class), anyInt())).thenReturn(cursor);
    }

    private void mockAllFetchEmpty() {
        when(fetcher.getItemsForDataType(any(DataType.class), any(ContactGroupIds.class), anyInt())).thenReturn(emptyCursor());
    }
}
