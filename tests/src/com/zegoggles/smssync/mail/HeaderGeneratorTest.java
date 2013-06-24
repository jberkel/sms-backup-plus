package com.zegoggles.smssync.mail;

import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.MimeMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.zegoggles.smssync.mail.Headers.get;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class HeaderGeneratorTest {

    private HeaderGenerator generator;

    @Before
    public void before() {
        generator = new HeaderGenerator("ref", "1.0");
    }

    @Test public void testShouldGenerateHeader() throws Exception {
        Message message = new MimeMessage();
        Map<String, String> map = new HashMap<String, String>();
        Date sent = new Date();

        PersonRecord person = new PersonRecord(0, null, null, null);

        generator.setHeaders(message, map, DataType.SMS, "1234", person, sent, 0);

        assertThat(get(message, Headers.ADDRESS)).isEqualTo("1234");
        assertThat(get(message, Headers.DATATYPE)).isEqualTo("SMS");
        assertThat(message.getSentDate()).isEqualTo(sent);
        assertThat(message.getMessageId()).isNotEmpty();
        assertThat(message.getReferences()).isNotEmpty();
    }
}
