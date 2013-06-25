package com.zegoggles.smssync.mail;

import android.provider.CallLog;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.MimeMessage;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.SmsConsts;
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

    @Test public void testShouldGenerateStandardHeaders() throws Exception {
        Message message = new MimeMessage();
        Map<String, String> map = new HashMap<String, String>();
        Date sent = new Date();

        PersonRecord person = new PersonRecord(0, null, null, null);

        generator.setHeaders(message, map, DataType.SMS, "1234", person, sent, 0);

        assertThat(get(message, Headers.ADDRESS)).isEqualTo("1234");
        assertThat(get(message, Headers.DATATYPE)).isEqualTo("SMS");
        assertThat(get(message, Headers.BACKUP_TIME)).isNotEmpty();
        assertThat(get(message, Headers.VERSION)).isNotEmpty();

        assertThat(message.getMessageId()).contains("sms-backup-plus.local");

        assertThat(message.getSentDate()).isEqualTo(sent);
        assertThat(message.getReferences()).isNotEmpty();
    }


    @Test public void testShouldGenerateSMSHeaders() throws Exception {
        Message message = new MimeMessage();
        Map<String, String> map = new HashMap<String, String>();
        Date sent = new Date();

        PersonRecord person = new PersonRecord(0, null, null, null);

        map.put(SmsConsts.ID, "someId");
        map.put(SmsConsts.TYPE, "type");
        map.put(SmsConsts.DATE, "date");
        map.put(SmsConsts.THREAD_ID, "tid");
        map.put(SmsConsts.READ, "read");
        map.put(SmsConsts.STATUS, "status");
        map.put(SmsConsts.PROTOCOL, "protocol");
        map.put(SmsConsts.SERVICE_CENTER, "svc");

        generator.setHeaders(message, map, DataType.SMS, "1234", person, sent, 0);

        assertThat(get(message, Headers.ID)).isEqualTo("someId");
        assertThat(get(message, Headers.TYPE)).isEqualTo("type");
        assertThat(get(message, Headers.DATE)).isEqualTo("date");
        assertThat(get(message, Headers.THREAD_ID)).isEqualTo("tid");
        assertThat(get(message, Headers.READ)).isEqualTo("read");
        assertThat(get(message, Headers.STATUS)).isEqualTo("status");
        assertThat(get(message, Headers.PROTOCOL)).isEqualTo("protocol");
        assertThat(get(message, Headers.SERVICE_CENTER)).isEqualTo("svc");
    }

    @Test public void testShouldGenerateCallLogHeaders() throws Exception {
        Message message = new MimeMessage();
        Map<String, String> map = new HashMap<String, String>();
        Date sent = new Date();

        PersonRecord person = new PersonRecord(0, null, null, null);

        map.put(CallLog.Calls._ID, "id");
        map.put(CallLog.Calls.TYPE, "type");
        map.put(CallLog.Calls.DURATION, "duration");
        map.put(CallLog.Calls.DATE, "date");

        generator.setHeaders(message, map, DataType.CALLLOG, "1234", person, sent, 0);

        assertThat(get(message, Headers.ID)).isEqualTo("id");
        assertThat(get(message, Headers.TYPE)).isEqualTo("type");
        assertThat(get(message, Headers.DURATION)).isEqualTo("duration");
        assertThat(get(message, Headers.DATE)).isEqualTo("date");
    }

    @Test public void testShouldGenerateMMSHeaders() throws Exception {
        Message message = new MimeMessage();
        Map<String, String> map = new HashMap<String, String>();
        Date sent = new Date();

        PersonRecord person = new PersonRecord(0, null, null, null);

        map.put(MmsConsts.ID, "id");
        map.put(MmsConsts.TYPE, "type");
        map.put(MmsConsts.THREAD_ID, "tid");
        map.put(MmsConsts.DATE, "date");
        map.put(MmsConsts.READ, "read");

        generator.setHeaders(message, map, DataType.MMS, "1234", person, sent, 0);

        assertThat(get(message, Headers.ID)).isEqualTo("id");
        assertThat(get(message, Headers.TYPE)).isEqualTo("type");
        assertThat(get(message, Headers.THREAD_ID)).isEqualTo("tid");
        assertThat(get(message, Headers.READ)).isEqualTo("read");
        assertThat(get(message, Headers.DATE)).isEqualTo("date");
    }
}
