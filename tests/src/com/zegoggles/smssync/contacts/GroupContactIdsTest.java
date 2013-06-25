package com.zegoggles.smssync.contacts;

import com.zegoggles.smssync.mail.PersonRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class GroupContactIdsTest {

    @Test
    public void shouldAddIds() throws Exception {
        GroupContactIds ids = new GroupContactIds();
        assertThat(ids.getIds()).isEmpty();
        assertThat(ids.getRawIds()).isEmpty();

        ids.add(1, 4);
        ids.add(3, 4);

        assertThat(ids.getIds()).containsExactly(1L, 3L);
        assertThat(ids.getRawIds()).containsExactly(4L);
    }

    @Test
    public void shouldCheckForPerson() throws Exception {
        GroupContactIds ids = new GroupContactIds();

        PersonRecord record = new PersonRecord(22, "Test", "test@test.com", "123");
        assertThat(ids.contains(record)).isFalse();

        ids.add(22L, 44L);

        assertThat(ids.contains(record)).isTrue();
    }
}
