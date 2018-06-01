package com.zegoggles.smssync.calendar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.text.format.Time;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;
import java.util.Map;

import static android.provider.CalendarContract.Events;
import static android.provider.CalendarContract.Events.CONTENT_URI;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class CalendarAccessorPost40Test {

    CalendarAccessor accessor;
    @Mock ContentResolver resolver;

    @Before public void before() {
        initMocks(this);
        accessor = new CalendarAccessorPost40(resolver);
    }


    @Test
    public void shouldEnableSync() throws Exception {
        when(resolver.update(eq(Uri.parse("content://com.android.calendar/calendars/123")),
                any(ContentValues.class),
                anyString(),
                any(String[].class)))
            .thenReturn(1);

        assertThat(accessor.enableSync(123)).isTrue();
    }

    @Test
    @Config
    public void shouldAddEntry() throws Exception {
        ArgumentCaptor<Uri> uri = ArgumentCaptor.forClass(Uri.class);
        ArgumentCaptor<ContentValues> values = ArgumentCaptor.forClass(ContentValues.class);

        Date when = new Date();
        accessor.addEntry(
                12,
                when, 100, "Title", "Desc");

        verify(resolver).insert(uri.capture(), values.capture());
        assertThat(uri.getValue().toString()).isEqualTo("content://com.android.calendar/events");

        ContentValues cv = values.getValue();
        assertThat(cv.getAsString(Events.TITLE)).isEqualTo("Title");
        assertThat(cv.getAsString(Events.DESCRIPTION)).isEqualTo("Desc");
        assertThat(cv.getAsLong(Events.DTSTART)).isEqualTo(when.getTime());
        assertThat(cv.getAsLong(Events.DTEND)).isGreaterThan(when.getTime());
        assertThat(cv.getAsInteger(Events.ACCESS_LEVEL)).isEqualTo(CalendarContract.Events.ACCESS_DEFAULT);
        assertThat(cv.getAsInteger(Events.STATUS)).isEqualTo(CalendarContract.Events.STATUS_CONFIRMED);
        assertThat(cv.getAsLong(Events.CALENDAR_ID)).isEqualTo(12L);
        assertThat(cv.getAsString(Events.EVENT_TIMEZONE)).isEqualTo(Time.getCurrentTimezone());
    }

    @Test
    public void shouldGetCalendars() throws Exception {
        MatrixCursor cursor = new MatrixCursor(new String[] { "_id", "name", "sync_events" } );
        cursor.addRow(new Object[] { "12", "Testing", 1 });

        when(resolver.query(eq(CalendarContract.Calendars.CONTENT_URI), any(String[].class),
                any(String.class),
                any(String[].class),
                eq(CalendarContract.Calendars.NAME + " ASC"))).thenReturn(
            cursor
        );

        Map<String, String> calendars = accessor.getCalendars();
        assertThat(calendars).hasSize(1);
        assertThat(calendars).containsEntry("12", "Testing");
    }

    @Test
    public void shouldIgnoreSQLiteException() {
        when(resolver.insert(eq(CONTENT_URI), any(ContentValues.class))).thenThrow(SQLiteException.class);
        boolean result = accessor.addEntry(
                12,
                new Date(), 100, "Title", "Desc");

        assertThat(result).isFalse();
    }
}
