package com.zegoggles.smssync.mail;

import android.net.Uri;
import android.provider.CallLog;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Message;
import com.zegoggles.smssync.SmsConsts;
import com.zegoggles.smssync.preferences.AddressStyle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class MessageGeneratorTest {

    private MessageGenerator generator;
    @Mock private PersonLookup personLookup;
    @Mock private HeaderGenerator headerGenerator;
    @Mock private MmsSupport mmsSupport;
    @Mock private Address me;

    @Before
    public void before() {
        initMocks(this);
        me = new Address("mine@mine.com", "me");
        generator = new MessageGenerator(Robolectric.application,
                me,
                AddressStyle.NAME,
                headerGenerator,
                personLookup,
                false,
                null,
                mmsSupport
        );
    }

    @Test
    public void testShouldReturnNullIfMessageHasNoAddress() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        Message msg = generator.messageForDataType(map, DataType.SMS);
        assertThat(msg).isNull();
    }

    @Test
    public void testShouldGenerateSubjectWithNameForSMS() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with Test Testor");
    }

    @Test
    public void testShouldGenerateSubjectWithNameForMMS() throws Exception {
        PersonRecord personRecord = new PersonRecord(1, "Foo Bar", "foo@bar.com", "1234");

        MmsSupport.MmsDetails details = new MmsSupport.MmsDetails(true, "foo",
                personRecord,
                new Address("foo@bar.com"));

        when(mmsSupport.getDetails(any(Uri.class), any(AddressStyle.class))).thenReturn(details);
        Message msg = generator.messageForDataType(mockMessage("1234", personRecord), DataType.MMS);

        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with Foo Bar");
    }

    @Test
    public void testShouldGenerateMessageForCallLogOutgoing() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockCalllogMessage("1234", CallLog.Calls.OUTGOING_TYPE, record), DataType.CALLLOG);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("Call with Test Testor");
        assertThat(msg.getFrom()[0]).isEqualTo(me);
        assertThat(msg.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("\"Test Testor\" <unknown.number@unknown.email>");

    }

    @Test
    public void testShouldGenerateMessageForCallLogIncoming() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockCalllogMessage("1234", CallLog.Calls.INCOMING_TYPE, record), DataType.CALLLOG);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("Call with Test Testor");
        assertThat(msg.getFrom()[0].toString()).isEqualTo("\"Test Testor\" <unknown.number@unknown.email>");
        assertThat(msg.getRecipients(Message.RecipientType.TO)[0]).isEqualTo(me);
    }

    @Test
    public void testShouldGenerateMessageForCallLogMissed() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockCalllogMessage("1234", CallLog.Calls.MISSED_TYPE, record), DataType.CALLLOG);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("Call with Test Testor");
        assertThat(msg.getFrom()[0].toString()).isEqualTo("\"Test Testor\" <unknown.number@unknown.email>");
        assertThat(msg.getRecipients(Message.RecipientType.TO)[0]).isEqualTo(me);
    }

    @Test
    public void testShouldGenerateSubjectWithNameAndNumberForSMS() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with Test Testor");
    }

    @Test
    public void shouldGenerateCorrectFromHeaderWithUsersEmailAddress() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getFrom()[0]).isEqualTo(me);
    }

    @Test
    public void shouldGenerateCorrectToHeader() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();

        assertThat(msg.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("\"Test Testor\" <test@test.com>");
    }

    @Test
    public void shouldGenerateCorrectHeaders() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Map<String, String> map = mockMessage("1234", record);

        Date date = new Date();
        map.put(SmsConsts.DATE, String.valueOf(date.getTime()));
        map.put(SmsConsts.TYPE, "0");

        Message msg = generator.messageForDataType(map, DataType.SMS);
        assertThat(msg).isNotNull();

        verify(headerGenerator).setHeaders(any(Message.class),
                any(Map.class),
                eq(DataType.SMS),
                anyString(),
                eq(record),
                eq(date),
                eq(0));
    }

    @Test
    public void shouldGenerateCorrectToHeaderWhenUserisRecipient() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Map<String, String> map = mockMessage("1234", record);
        map.put(SmsConsts.TYPE, "1");

        Message msg = generator.messageForDataType(map, DataType.SMS);
        assertThat(msg).isNotNull();

        assertThat(msg.getFrom()[0].toString())
                .isEqualTo("\"Test Testor\" <test@test.com>");

        assertThat(msg.getRecipients(Message.RecipientType.TO)[0]).isEqualTo(me);
    }

    @Test
    public void testShouldUseNumberIfNameIsUnknown() throws Exception {
        PersonRecord record = new PersonRecord(-1, null, null, "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with 1234");
    }

    private Map<String, String> mockMessage(String address, PersonRecord record) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(SmsConsts.ADDRESS, address);
        when(personLookup.lookupPerson(eq(address))).thenReturn(record);
        return map;
    }

    private Map<String, String> mockCalllogMessage(String address, int type, PersonRecord record) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(CallLog.Calls.NUMBER, address);
        map.put(CallLog.Calls.TYPE, String.valueOf(type));
        when(personLookup.lookupPerson(eq(address))).thenReturn(record);
        return map;
    }
}
