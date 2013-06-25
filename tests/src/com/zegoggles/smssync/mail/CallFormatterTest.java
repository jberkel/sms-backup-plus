package com.zegoggles.smssync.mail;

import android.provider.CallLog;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class CallFormatterTest {
    private CallFormatter formatter;

    @Before
    public void before() {
        formatter = new CallFormatter(Robolectric.application.getResources());
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
}
