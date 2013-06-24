package com.zegoggles.smssync.mail;

import com.zegoggles.smssync.preferences.AddressStyle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class PersonRecordTest {


    @Test
    public void shouldSanitizeInputData() throws Exception {
        PersonRecord r = new PersonRecord(1, "foo\n\r\n", "foo\n@gmail.com", "\r\r1234");

        assertThat(r.getEmail()).isEqualTo("foo@gmail.com");
        assertThat(r.getName()).isEqualTo("foo");
        assertThat(r.getNumber()).isEqualTo("1234");
    }
    @Test
    public void shouldBeUnknownForInvalidIds() throws Exception {
        PersonRecord record = new PersonRecord(0, null, null, "-1");
        assertThat(record.isUnknown()).isTrue();
    }

    @Test
    public void shouldNotBeUnknownForValidIds() throws Exception {
        PersonRecord record = new PersonRecord(12, null, null, "-1");
        assertThat(record.isUnknown()).isFalse();
    }

    @Test
    public void shouldReturnNumberForUnknown() throws Exception {
        PersonRecord record = new PersonRecord(1, null, null, "-1");
        assertThat(record.getNumber()).isEqualTo("Unknown");
    }

    @Test
    public void shouldReturnUnknownEmail() throws Exception {
        PersonRecord record = new PersonRecord(0, null, null, "-1");
        assertThat(record.getEmail()).isEqualTo("unknown.number@unknown.email");
    }

    @Test
    public void shouldGetAddress() throws Exception {
        PersonRecord record = new PersonRecord(1, "John Appleseed", "john@appleseed.com", "+141543432");
        assertThat(record.getAddress(AddressStyle.NAME_AND_NUMBER).toString()).isEqualTo(
             "\"John Appleseed (+141543432)\" <john@appleseed.com>");

        assertThat(record.getAddress(AddressStyle.NAME).toString()).isEqualTo(
                "\"John Appleseed\" <john@appleseed.com>");

        assertThat(record.getAddress(AddressStyle.NUMBER).toString()).isEqualTo(
                "\"+141543432\" <john@appleseed.com>");

    }
}
