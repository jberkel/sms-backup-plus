package com.zegoggles.smssync.mail;

import android.provider.CallLog;
import android.provider.Telephony;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.MimeMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.mail.Headers.get;

@RunWith(RobolectricTestRunner.class)
public class HeaderGeneratorTest {

    private HeaderGenerator generator;

    @Before
    public void before() {
        generator = new HeaderGenerator("ref", 1);
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

        map.put(android.provider.BaseColumns._ID, "someId");
        map.put(Telephony.TextBasedSmsColumns.TYPE, "type");
        map.put(Telephony.TextBasedSmsColumns.DATE, "date");
        map.put(Telephony.TextBasedSmsColumns.THREAD_ID, "tid");
        map.put(Telephony.TextBasedSmsColumns.READ, "read");
        map.put(Telephony.TextBasedSmsColumns.STATUS, "status");
        map.put(Telephony.TextBasedSmsColumns.PROTOCOL, "protocol");
        map.put(Telephony.TextBasedSmsColumns.SERVICE_CENTER, "svc");

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

        map.put(Telephony.BaseMmsColumns._ID, "id");
        map.put(Telephony.BaseMmsColumns.MESSAGE_TYPE, "type");
        map.put(Telephony.BaseMmsColumns.THREAD_ID, "tid");
        map.put(Telephony.BaseMmsColumns.DATE, "date");
        map.put(Telephony.BaseMmsColumns.READ, "read");

        generator.setHeaders(message, map, DataType.MMS, "1234", person, sent, 0);

        assertThat(get(message, Headers.ID)).isEqualTo("id");
        assertThat(get(message, Headers.TYPE)).isEqualTo("type");
        assertThat(get(message, Headers.THREAD_ID)).isEqualTo("tid");
        assertThat(get(message, Headers.READ)).isEqualTo("read");
        assertThat(get(message, Headers.DATE)).isEqualTo("date");
    }

    @Test public void testShouldSetHeadersWithNullAddress() throws Exception {
        Message message = new MimeMessage();
        Map<String, String> map = new HashMap<String, String>();
        Date sent = new Date();
        PersonRecord person = new PersonRecord(0, null, null, null);

        generator.setHeaders(message, map, DataType.SMS, null, person, sent, 0);
    }
}
