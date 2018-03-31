package com.zegoggles.smssync.service;

import android.database.Cursor;
import android.database.MatrixCursor;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.DataType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import java.util.EnumSet;

import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.mail.DataType.MMS;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class BulkFetcherTest {

    @Mock BackupItemsFetcher fetcher;
    BulkFetcher bulkFetcher;

    @Before public void before() {
        initMocks(this);
        bulkFetcher = new BulkFetcher(fetcher);
    }


    @Test public void shouldFetchAllItems() throws Exception {
        when(fetcher.getItemsForDataType(SMS, null, 50)).thenReturn(cursor(3));
        when(fetcher.getItemsForDataType(MMS, null, 47)).thenReturn(cursor(5));

        BackupCursors cursors = bulkFetcher.fetch(EnumSet.of(SMS, MMS), null, 50);

        assertThat(cursors.count()).isEqualTo(8);
        assertThat(cursors.count(SMS)).isEqualTo(3);
        assertThat(cursors.count(MMS)).isEqualTo(5);
    }

    @Test public void shouldFetchAllItemsRespectingMaxItems() throws Exception {
        when(fetcher.getItemsForDataType(SMS, null, 5)).thenReturn(cursor(5));

        BackupCursors cursors = bulkFetcher.fetch(EnumSet.of(SMS, MMS), null, 5);

        assertThat(cursors.count()).isEqualTo(5);
        assertThat(cursors.count(SMS)).isEqualTo(5);

        verify(fetcher, never()).getItemsForDataType(eq(DataType.MMS), any(ContactGroupIds.class), anyInt());
    }


    @Test public void shouldFetchAllItemsEmptyList() throws Exception {
        BackupCursors cursors = bulkFetcher.fetch(EnumSet.noneOf(DataType.class), null, 50);
        assertThat(cursors.count()).isEqualTo(0);
    }

    private Cursor cursor(int rows) {
        MatrixCursor c = new MatrixCursor(new String[] {});
        for (int i=0; i<rows; i++) {
            c.addRow(new Object[] {});
        }
        return c;
    }
}
