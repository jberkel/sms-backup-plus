package com.zegoggles.smssync.mail;

import android.provider.CallLog;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class CallFormatterTest {
    private CallFormatter formatter;

    @Before
    public void before() {
        formatter = new CallFormatter(RuntimeEnvironment.application.getResources());
    }

    @Test public void shouldFormatIncoming() throws Exception {
        assertThat(formatter.format(CallLog.Calls.INCOMING_TYPE, "Foo", 100))
                .isEqualTo("100s (00:01:40)\n" +
                          "Foo (incoming call)");
    }

    @Test public void shouldFormatOutgoing() throws Exception {
        assertThat(formatter.format(CallLog.Calls.OUTGOING_TYPE, "Foo", 100))
                .isEqualTo("100s (00:01:40)\n" +
                           "Foo (outgoing call)");
    }

    @Test public void shouldFormatMissing() throws Exception {
        assertThat(formatter.format(CallLog.Calls.MISSED_TYPE, "Foo", 100))
                .isEqualTo("Foo (missed call)");
    }

    @Test public void shouldFormatRejected() throws Exception {
        assertThat(formatter.format(CallLog.Calls.REJECTED_TYPE, "Foo", 0))
                .isEqualTo("Foo (incoming call, rejected)");
    }

    @Test public void shouldFormatVoicemail() throws Exception {
        assertThat(formatter.format(CallLog.Calls.VOICEMAIL_TYPE, "Foo", 100))
                .isEqualTo("100s (00:01:40)\n" +
                           "Foo (incoming call, voicemail)");
    }

    @Test public void shouldFormatCallIncoming() throws Exception {
        assertThat(formatter.callTypeString(CallLog.Calls.INCOMING_TYPE, "Foo")).isEqualTo("Call from Foo");
    }

    @Test public void shouldFormatCallOutgoing() throws Exception {
        assertThat(formatter.callTypeString(CallLog.Calls.OUTGOING_TYPE, "Foo")).isEqualTo("Called Foo");
    }

    @Test public void shouldFormatCallMissed() throws Exception {
        assertThat(formatter.callTypeString(CallLog.Calls.MISSED_TYPE, "Foo")).isEqualTo("Missed call from Foo");
    }

    @Test public void shouldFormatCallDuration() throws Exception {
        assertThat(formatter.formattedCallDuration(1242)).isEqualTo("00:20:42");
    }

    @Test public void shouldFormatForCalendarOutgoing() throws Exception {
        assertThat(formatter.formatForCalendar(CallLog.Calls.OUTGOING_TYPE, "Foo", 100))
                .isEqualTo("Number: Foo (outgoing call)\n" +
                        "Duration: 00:01:40");
    }

    @Test public void shouldFormatForCalendarIncoming() throws Exception {
        assertThat(formatter.formatForCalendar(CallLog.Calls.INCOMING_TYPE, "Foo", 100))
                .isEqualTo("Number: Foo (incoming call)\n" +
                        "Duration: 00:01:40");
    }

    @Test public void shouldFormatForCalendarMissed() throws Exception {
        assertThat(formatter.formatForCalendar(CallLog.Calls.MISSED_TYPE, "Foo", 100))
                .isEqualTo("Number: Foo (missed call)\n");
    }
}
