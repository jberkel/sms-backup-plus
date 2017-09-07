package com.zegoggles.smssync.mail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.zegoggles.smssync.mail.Attachment.encodeRFC2231;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class AttachmentTest {

    @Test
    public void shouldEncodeRFC2231() throws Exception {
        assertThat(encodeRFC2231("key", "value")).isEqualTo("; key=value");
        assertThat(encodeRFC2231("key", "\"*über*")).isEqualTo("; key*=UTF-8''%22%2A%C3%BCber%2A");
    }
}
