package com.zegoggles.smssync.service;

import android.database.Cursor;
import com.github.jberkel.whassup.Whassup;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class WhatsAppItemsFetcherTest {

    @Mock Whassup whassup;
    WhatsAppItemsFetcher fetcher;

    @Before public void before() {
        initMocks(this);
        fetcher = new WhatsAppItemsFetcher(whassup);
    }

    @Test public void shouldFetchItems() throws Exception {
        Cursor cursor = mock(Cursor.class);
        when(whassup.hasBackupDB()).thenReturn(true);
        when(whassup.queryMessages(-1, -1)).thenReturn(cursor);

        Cursor result = fetcher.getItems(-1, -1);

        assertThat(result).isSameAs(cursor);
    }

    @Test public void shouldReturnEmptyCursorWhenWhatsAppDbIsNotAvailable() throws Exception {
        when(whassup.hasBackupDB()).thenReturn(false);
        Cursor result = fetcher.getItems(-1, -1);
        assertThat(result.getCount()).isEqualTo(0);
    }

    @Test public void shouldReturnEmptyCursorWhenIOExceptionIsThrown() throws Exception {
        when(whassup.hasBackupDB()).thenReturn(true);
        when(whassup.queryMessages(-1, -1)).thenThrow(new IOException());
        Cursor result = fetcher.getItems(-1, -1);
        assertThat(result.getCount()).isEqualTo(0);
    }

    @Test public void shouldGetMostRecentTimestamp() throws Exception {
        when(whassup.hasBackupDB()).thenReturn(true);
        when(whassup.getMostRecentTimestamp(true)).thenReturn(1234L);
        assertThat(fetcher.getMostRecentTimestamp()).isEqualTo(1234);
    }

    @Test public void shouldGetMostRecentTimestampWithoutDB() throws Exception {
        when(whassup.hasBackupDB()).thenReturn(false);
        assertThat(fetcher.getMostRecentTimestamp()).isEqualTo(-1);
    }
}
