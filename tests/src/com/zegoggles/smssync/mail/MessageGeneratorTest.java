package com.zegoggles.smssync.mail;

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

    @Before
    public void before() {
        initMocks(this);
        generator = new MessageGenerator(Robolectric.application,
                new Address("mine@mine.com", "me"),
                AddressStyle.NAME,
                headerGenerator,
                personLookup,
                false,
                null);
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
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.MMS);
        assertThat(msg).isNull();
        // TODO
    }

    @Test
    public void testShouldGenerateSubjectWithNameForCallLog() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.CALLLOG);
        // TODO
        assertThat(msg).isNull();
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

        assertThat(msg.getFrom()[0].toString())
                .isEqualTo("\"me\" <mine@mine.com>");
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

        assertThat(msg.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("\"me\" <mine@mine.com>");
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
}
