package com.zegoggles.smssync.mail;

import com.zegoggles.smssync.preferences.AddressStyle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class PersonRecordTest {
    @Test
    public void shouldReturnNumber() throws Exception {
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
