package com.zegoggles.smssync.mail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.preferences.AddressStyle.NAME;
import static com.zegoggles.smssync.preferences.AddressStyle.NAME_AND_NUMBER;
import static com.zegoggles.smssync.preferences.AddressStyle.NUMBER;

@RunWith(RobolectricTestRunner.class)
public class PersonRecordTest {

    @Test public void shouldSanitizeInputDataEmail() throws Exception {
        PersonRecord r = new PersonRecord(1, "foo\n\r\n", "foo\n@gmail.com", "\r\r1234");
        assertThat(r.getEmail()).isEqualTo("foo@gmail.com");
    }

    @Test public void shouldSanitizeInputDataName() throws Exception {
        PersonRecord r = new PersonRecord(1, "foo\n\r\n", "foo\n@gmail.com", "\r\r1234");
        assertThat(r.getName()).isEqualTo("foo");
    }

    @Test public void shouldSanitizeInputDataNumber() throws Exception {
        PersonRecord r = new PersonRecord(1, "foo\n\r\n", "foo\n@gmail.com", "\r\r1234");
        assertThat(r.getNumber()).isEqualTo("1234");
    }

    @Test public void shouldBeUnknownForInvalidIds() throws Exception {
        PersonRecord record = new PersonRecord(0, null, null, "-1");
        assertThat(record.isUnknown()).isTrue();
    }

    @Test public void shouldNotBeUnknownForValidIds() throws Exception {
        PersonRecord record = new PersonRecord(12, null, null, "-1");
        assertThat(record.isUnknown()).isFalse();
    }

    @Test public void shouldReturnNumberForUnknown() throws Exception {
        PersonRecord record = new PersonRecord(1, null, null, "-1");
        assertThat(record.getNumber()).isEqualTo("Unknown");
    }

    @Test public void shouldReturnUnknownEmail() throws Exception {
        PersonRecord record = new PersonRecord(0, null, null, "-1");
        assertThat(record.getEmail()).isEqualTo("unknown.number@unknown.email");
    }

    // all fields present

    @Test public void shouldGetAddressNameAndNumber() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", "john@appleseed.com", "+141543432");
        assertThat(record.getAddress(NAME_AND_NUMBER).toString()).isEqualTo(
                "\"John Appleseed (+141543432)\" <john@appleseed.com>");
    }

    @Test public void shouldGetAddressName() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", "john@appleseed.com", "+141543432");

        assertThat(record.getAddress(NAME).toString()).isEqualTo(
                "John Appleseed <john@appleseed.com>");
    }

    @Test public void shouldGetAddressNumber() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", "john@appleseed.com", "+141543432");
        assertThat(record.getAddress(NUMBER).toString()).isEqualTo(
                "+141543432 <john@appleseed.com>");
    }

    // email missing

    @Test public void shouldGetAddressMissingEmail_NameAndNumber() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", null, "+141543432");
        assertThat(record.getAddress(NAME_AND_NUMBER).toString()).isEqualTo(
                "\"John Appleseed (+141543432)\" <+141543432@unknown.email>");
    }

    @Test public void shouldGetAddressMissingEmail_Name() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", null, "+141543432");
        assertThat(record.getAddress(NAME).toString()).isEqualTo(
                "John Appleseed <+141543432@unknown.email>");
    }

    @Test public void shouldGetAddressMissingEmail_Number() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", null, "+141543432");
        assertThat(record.getAddress(NUMBER).toString()).isEqualTo(
                "+141543432 <+141543432@unknown.email>");
    }

    // name is missing

    @Test public void shouldGetAddressMissingName_Name() throws Exception {
        PersonRecord record = new PersonRecord(1, null, "john@appleseed.com", "+141543432");
        assertThat(record.getAddress(NAME).toString()).isEqualTo(
                "+141543432 <john@appleseed.com>");
    }

    @Test public void shouldGetAddressMissingName_NameAndNumber() throws Exception {
        PersonRecord record = new PersonRecord(1, null, "john@appleseed.com", "+141543432");
        assertThat(record.getAddress(NAME_AND_NUMBER).toString()).isEqualTo(
                "+141543432 <john@appleseed.com>");
    }

    @Test public void shouldGetAddressMissingName_Number() throws Exception {
        PersonRecord record = new PersonRecord(1, null, "john@appleseed.com", "+141543432");
        assertThat(record.getAddress(NUMBER).toString()).isEqualTo(
                "+141543432 <john@appleseed.com>");
    }

    // number is missing

    @Test public void shouldGetAddressMissingNumber_Number() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", "john@appleseed.com", null);
        assertThat(record.getAddress(NUMBER).toString()).isEqualTo(
                "Unknown <john@appleseed.com>");
    }

    @Test public void shouldGetAddressMissingNumber_Name() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", "john@appleseed.com", null);
        assertThat(record.getAddress(NAME).toString()).isEqualTo(
                "John Appleseed <john@appleseed.com>");
    }

    @Test public void shouldGetAddressMissingNumber_NameAndNumber() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", "john@appleseed.com", null);

        assertThat(record.getAddress(NAME_AND_NUMBER).toString()).isEqualTo(
                "\"John Appleseed (Unknown)\" <john@appleseed.com>");
    }

    // local part quote (#595)

    @Test public void shouldQuoteLocalPart_NameAndNumber() throws Exception {
        PersonRecord record = new PersonRecord(1, null, null, "name with space");
        assertThat(record.getAddress(NAME_AND_NUMBER).toString()).isEqualTo(
                "name with space <\"name with space\"@unknown.email>");
    }

    @Test public void shouldQuoteLocalPart_Name() throws Exception {
        PersonRecord record = new PersonRecord(1, null, null, "name with space");
        assertThat(record.getAddress(NAME).toString()).isEqualTo(
                "name with space <\"name with space\"@unknown.email>");
    }

    @Test public void shouldQuoteLocalPart_Number() throws Exception {
        PersonRecord record = new PersonRecord(1, null, null, "name with space");
        assertThat(record.getAddress(NUMBER).toString()).isEqualTo(
                "name with space <\"name with space\"@unknown.email>");
    }
}
