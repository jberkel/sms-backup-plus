package com.zegoggles.smssync.service;

import android.content.ContentResolver;
import android.content.Context;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.Preferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.MMS;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class BackupItemsFetcherTest {
    BackupItemsFetcher fetcher;
    @Mock BackupQueryBuilder queryBuilder;
    @Mock ContentResolver resolver;
    Context context;
    Preferences preferences;

    @Before public void before() {
        initMocks(this);
        context = RuntimeEnvironment.application;
        preferences = new Preferences(context);
        fetcher = new BackupItemsFetcher(
                resolver,
                queryBuilder);
    }

    @Test public void shouldGetItemsForDataType() throws Exception {
        preferences.getDataTypePreferences().setBackupEnabled(true, SMS);
        assertThat(fetcher.getItemsForDataType(SMS, null, 0, -1).getCount()).isEqualTo(0);
        verifyZeroInteractions(resolver);
    }

    @Test public void shouldCatchSQLiteExceptions() throws Exception {
        preferences.getDataTypePreferences().setBackupEnabled(true, SMS);
        when(resolver.query(any(Uri.class), any(String[].class), anyString(), any(String[].class), anyString()))
                .thenThrow(new SQLiteException());

        mockEmptyQuery();

        assertThat(fetcher.getItemsForDataType(SMS, null, 0, -1).getCount()).isEqualTo(0);
    }

    @Test public void shouldCatchNullPointerExceptions() throws Exception {
        preferences.getDataTypePreferences().setBackupEnabled(true, SMS);
        when(resolver.query(any(Uri.class), any(String[].class), anyString(), any(String[].class), anyString()))
                .thenThrow(new NullPointerException());

        mockEmptyQuery();

        assertThat(fetcher.getItemsForDataType(SMS, null, 0, -1).getCount()).isEqualTo(0);
    }

    @Test public void shouldReturnDefaultIfDataTypeCannotBeRead() throws Exception {
        for (DataType type : DataType.values()) {
            assertThat(fetcher.getMostRecentTimestamp(type)).isEqualTo(-1);
        }
    }

    @Test public void shouldGetMostRecentTimestampForItemTypeSMS() throws Exception {
        mockMostRecentTimestampForType(SMS, 23L);
        assertThat(fetcher.getMostRecentTimestamp(SMS)).isEqualTo(23L);
    }

    @Test public void shouldMostRecentTimestampForItemTypeMMS() throws Exception {
        mockMostRecentTimestampForType(MMS, 23L);
        assertThat(fetcher.getMostRecentTimestamp(MMS)).isEqualTo(23L);
    }

    @Test public void shouldGetMostRecentTimestampForItemTypeCallLog() throws Exception {
        mockMostRecentTimestampForType(CALLLOG, 23L);
        assertThat(fetcher.getMostRecentTimestamp(CALLLOG)).isEqualTo(23L);
    }

    private void mockMostRecentTimestampForType(DataType type, long max) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"date"});
        cursor.addRow(new Object[] { max });

        BackupQueryBuilder.Query query = mock(BackupQueryBuilder.Query.class);
        when(queryBuilder.buildMostRecentQueryForDataType(type)).thenReturn(query);

        when(resolver.query(any(Uri.class),
                any(String[].class),
                any(String.class),
                any(String[].class),
                any(String.class))).thenReturn(cursor);
    }

    private void mockEmptyQuery() {
        BackupQueryBuilder.Query query = mock(BackupQueryBuilder.Query.class);
        when(queryBuilder.buildQueryForDataType(SMS, null, 0, -1)).thenReturn(query);
    }
}
