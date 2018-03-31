package com.zegoggles.smssync.mail;

import com.fsck.k9.mail.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class ConversionResultTest {

    @Test public void emptyResult() throws Exception {
        ConversionResult result = new ConversionResult(DataType.SMS);
        assertThat(result.isEmpty()).isTrue();
    }

    @Test public void shouldAddMessage() throws Exception {
        ConversionResult result = new ConversionResult(DataType.SMS);
        Message message = mock(Message.class);
        when(message.getHeader(anyString())).thenReturn(new String[] {});
        Map<String, String> map = new HashMap<String, String>();
        result.add(message, map);
        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getMaxDate()).isEqualTo(-1);
    }

    @Test public void shouldAddMessageWithValidDate() throws Exception {
        ConversionResult result = new ConversionResult(DataType.SMS);
        Message message = mock(Message.class);
        when(message.getHeader(Headers.DATE)).thenReturn(new String[] { "12345" });
        Map<String, String> map = new HashMap<String, String>();
        result.add(message, map);
        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getMaxDate()).isEqualTo(12345);
    }

    @Test public void shouldAddMessageWithInvalidDate() throws Exception {
        ConversionResult result = new ConversionResult(DataType.SMS);
        Message message = mock(Message.class);
        when(message.getHeader(Headers.DATE)).thenReturn(new String[] { "foo" });
        Map<String, String> map = new HashMap<String, String>();
        result.add(message, map);
        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getMaxDate()).isEqualTo(-1);
    }

    @Test public void shouldAddMessageAndRememberMaxDate() throws Exception {
        ConversionResult result = new ConversionResult(DataType.SMS);
        Message message = mock(Message.class);
        when(message.getHeader(Headers.DATE)).thenReturn(new String[] { "12345" });

        Map<String, String> map = new HashMap<String, String>();
        result.add(message, map);
        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getMaxDate()).isEqualTo(12345);

        Message newerMessage = mock(Message.class);
        when(newerMessage.getHeader(Headers.DATE)).thenReturn(new String[]{"123456789"});
        result.add(newerMessage, map);
        assertThat(result.getMaxDate()).isEqualTo(123456789);
    }
}
