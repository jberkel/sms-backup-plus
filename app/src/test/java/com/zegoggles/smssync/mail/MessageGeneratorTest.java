package com.zegoggles.smssync.mail;

import android.net.Uri;
import android.provider.CallLog;
import android.provider.Telephony;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.internet.MimeHeader;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.preferences.AddressStyle;
import com.zegoggles.smssync.preferences.CallLogTypes;
import com.zegoggles.smssync.preferences.DataTypePreferences;
import com.zegoggles.smssync.preferences.Preferences;
import org.apache.james.mime4j.util.MimeUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static android.provider.CallLog.Calls.INCOMING_TYPE;
import static android.provider.CallLog.Calls.MISSED_TYPE;
import static android.provider.CallLog.Calls.OUTGOING_TYPE;
import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
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
    @Mock private ContactGroupIds groupIds;
    @Mock private DataTypePreferences dataTypePreferences;

    @Before public void before() {
        initMocks(this);
        me = new Address("mine@mine.com", "me");
        generator = new MessageGenerator(RuntimeEnvironment.application,
                me,
                AddressStyle.NAME,
                headerGenerator,
                personLookup,
                false,
                null,
                mmsSupport,
                CallLogTypes.EVERYTHING,
                dataTypePreferences
        );
    }

    @Test public void testShouldReturnNullIfMessageHasNoAddress() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        Message msg = generator.messageForDataType(map, DataType.SMS);
        assertThat(msg).isNull();
    }

    @Test public void testShouldGenerateSubjectWithNameForSMS() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with Test Testor");
    }

    @Test public void testShouldGenerateSMSMessageWithCorrectEncoding() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg.getHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING)).isEqualTo(new String[] {
                MimeUtil.ENC_QUOTED_PRINTABLE
        });
    }

    @Test public void testShouldGenerateSubjectWithNameForMMS() throws Exception {
        PersonRecord personRecord = new PersonRecord(1, "Foo Bar", "foo@bar.com", "1234");

        MmsSupport.MmsDetails details = new MmsSupport.MmsDetails(true, "foo",
                personRecord,
                new Address("foo@bar.com"));

        when(mmsSupport.getDetails(any(Uri.class), any(AddressStyle.class))).thenReturn(details);
        Message msg = generator.messageForDataType(mockMessage("1234", personRecord), DataType.MMS);

        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with Foo Bar");
    }

    @Test public void testShouldGenerateMMSMessageWithCorrectEncoding() throws Exception {
        PersonRecord personRecord = new PersonRecord(1, "Foo Bar", "foo@bar.com", "1234");
        MmsSupport.MmsDetails details = new MmsSupport.MmsDetails(true, "foo",
                personRecord,
                new Address("foo@bar.com"));

        when(mmsSupport.getDetails(any(Uri.class), any(AddressStyle.class))).thenReturn(details);
        Message msg = generator.messageForDataType(mockMessage("1234", personRecord), DataType.MMS);
        assertThat(msg.getHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING)).isEqualTo(new String[] {
                MimeUtil.ENC_7BIT
        });
    }

    @Test public void testShouldGenerateMessageForCallLogOutgoing() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockCalllogMessage("1234", OUTGOING_TYPE, record), CALLLOG);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("Call with Test Testor");
        assertThat(msg.getFrom()[0]).isEqualTo(me);
        assertThat(msg.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("Test Testor <unknown.number@unknown.email>");
    }

    @Test public void testShouldGenerateMessageForCallLogIncoming() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message message = generator.messageForDataType(mockCalllogMessage("1234", INCOMING_TYPE, record), CALLLOG);
        assertMessage(message);
    }

    @Test public void testShouldGenerateMessageForCallLogMissed() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message message = generator.messageForDataType(mockCalllogMessage("1234", MISSED_TYPE, record), CALLLOG);
        assertMessage(message);
    }

    private void assertMessage(Message message) {
        assertThat(message).isNotNull();
        assertThat(message.getSubject()).isEqualTo("Call with Test Testor");
        assertThat(message.getFrom()[0].toString()).isEqualTo("Test Testor <unknown.number@unknown.email>");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0]).isEqualTo(me);
    }

    @Test public void testShouldGenerateMessageForCallLogIncomingUnknown() throws Exception {
        PersonRecord record = new PersonRecord(0, null, null, "-1");
        Message msg = generator.messageForDataType(mockCalllogMessage("", INCOMING_TYPE, record), CALLLOG);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("Call with Unknown");
        assertThat(msg.getFrom()[0].toString()).isEqualTo("Unknown <unknown.number@unknown.email>");
        assertThat(msg.getRecipients(Message.RecipientType.TO)[0]).isEqualTo(me);
    }

    @Test public void testShouldGenerateCallLogMessageWithCorrectEncoding() throws Exception {
        PersonRecord record = new PersonRecord(-1, "Test Testor", null, null);
        Message msg = generator.messageForDataType(mockCalllogMessage("1234", OUTGOING_TYPE, record), CALLLOG);
        assertThat(msg.getHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING)).isEqualTo(new String[] {
                MimeUtil.ENC_QUOTED_PRINTABLE
        });
    }

    @Test public void testShouldGenerateSubjectWithNameAndNumberForSMS() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with Test Testor");
    }

    @Test public void shouldGenerateCorrectFromHeaderWithUsersEmailAddress() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getFrom()[0]).isEqualTo(me);
    }

    @Test public void shouldGenerateCorrectToHeader() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();

        assertThat(msg.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("Test Testor <test@test.com>");
    }

    @Test public void shouldGenerateCorrectHeaders() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Map<String, String> map = mockMessage("1234", record);

        Date date = new Date();
        map.put(Telephony.TextBasedSmsColumns.DATE, String.valueOf(date.getTime()));
        map.put(Telephony.TextBasedSmsColumns.TYPE, "0");

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

    @Test public void shouldGenerateCorrectToHeaderWhenUserisRecipient() throws Exception {
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Map<String, String> map = mockMessage("1234", record);
        map.put(Telephony.TextBasedSmsColumns.TYPE, "1");

        Message msg = generator.messageForDataType(map, DataType.SMS);
        assertThat(msg).isNotNull();

        assertThat(msg.getFrom()[0].toString())
                .isEqualTo("Test Testor <test@test.com>");

        assertThat(msg.getRecipients(Message.RecipientType.TO)[0]).isEqualTo(me);
    }

    @Test public void testShouldUseNumberIfNameIsUnknown() throws Exception {
        PersonRecord record = new PersonRecord(-1, null, null, "1234");
        Message msg = generator.messageForDataType(mockMessage("1234", record), DataType.SMS);
        assertThat(msg).isNotNull();
        assertThat(msg.getSubject()).isEqualTo("SMS with 1234");
    }

    @Test public void shouldOnlyIncludePeopleFromContactIdsIfSpecified() throws Exception {
        MessageGenerator generator = new MessageGenerator(RuntimeEnvironment.application,
                me,
                AddressStyle.NAME,
                headerGenerator,
                personLookup,
                false,
                groupIds,
                mmsSupport,
                CallLogTypes.EVERYTHING,
                dataTypePreferences
        );
        PersonRecord record = new PersonRecord(1, "Test Testor", "test@test.com", "1234");
        Map<String, String> map = mockMessage("1234", record);
        map.put(Telephony.TextBasedSmsColumns.TYPE, "1");

        when(groupIds.contains(record)).thenReturn(false);
        assertThat(generator.messageForDataType(map, DataType.SMS)).isNull();
        when(groupIds.contains(record)).thenReturn(true);
        assertThat(generator.messageForDataType(map, DataType.SMS)).isNotNull();
    }

    private Map<String, String> mockMessage(String address, PersonRecord record) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(Telephony.TextBasedSmsColumns.ADDRESS, address);
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
