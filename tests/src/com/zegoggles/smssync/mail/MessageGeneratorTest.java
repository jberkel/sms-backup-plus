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

import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
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
                new Address("test@test.com"),
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
    public void testShouldGenerateSubjectWithNameForMessage() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with Test Testor");
    }

    @Test
    public void testShouldGenerateSubjectWithNameAndNumberForMessage() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with Test Testor");
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
