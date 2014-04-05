package com.zegoggles.smssync.service;

import android.provider.CallLog;
import com.fsck.k9.mail.internet.MimeMessage;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.mail.CallFormatter;
import com.zegoggles.smssync.mail.ConversionResult;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.mail.PersonLookup;
import com.zegoggles.smssync.mail.PersonRecord;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class CalendarSyncerTest {
    CalendarSyncer syncer;

    @Mock CalendarAccessor accessor;
    @Mock PersonLookup personLookup;
    @Mock CallFormatter callFormatter;

    final static long CALENDAR_ID = 123;

    @Before public void before() {
        initMocks(this);

        syncer = new CalendarSyncer(
            accessor,
            CALENDAR_ID,
            personLookup,
            callFormatter
        );
    }

    @Test public void shouldSyncCalendar() throws Exception {
        ConversionResult result = new ConversionResult(DataType.CALLLOG);

        final String NUMBER = "12345";
        final String NAME   = "Foo";
        final int DURATION = 10;
        final int TYPE     = 1;

        Date callTime = new Date();
        result.add(new MimeMessage(), message(DURATION, TYPE, NUMBER, callTime));
        result.add(new MimeMessage(), message(DURATION, TYPE, NUMBER, callTime));

        when(callFormatter.callTypeString(TYPE, NAME)).thenReturn("title1");
        when(callFormatter.formatForCalendar(TYPE, NUMBER, DURATION)).thenReturn("title2");
        when(personLookup.lookupPerson(NUMBER)).thenReturn(new PersonRecord(1, NAME, "foo@bar", NUMBER));

        syncer.syncCalendar(result);
        verify(accessor, times(2)).addEntry(eq(CALENDAR_ID), eq(callTime), eq(DURATION), eq("title1"), eq("title2"));
    }

    @Test
    public void shouldEnableSync() throws Exception {
        shouldSyncCalendar();
        verify(accessor).enableSync(CALENDAR_ID);
    }

    @Test
    public void shouldOnlyEnableSyncOnce() throws Exception {
        shouldSyncCalendar();
        reset(accessor);
        shouldSyncCalendar();
        verify(accessor, never()).enableSync(CALENDAR_ID);
    }

    private Map<String,String> message(int DURATION, int TYPE, String NUMBER, Date callTime) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(CallLog.Calls.DURATION, String.valueOf(DURATION));
        map.put(CallLog.Calls.TYPE, String.valueOf(TYPE));
        map.put(CallLog.Calls.NUMBER,   NUMBER);
        map.put(CallLog.Calls.DATE, String.valueOf(callTime.getTime()));
        return map;
    }
}
