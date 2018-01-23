package com.zegoggles.smssync.contacts;

import android.content.ContentResolver;
import android.database.MatrixCursor;
import android.provider.ContactsContract;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.contacts.ContactAccessor.EVERYBODY_ID;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class ContactAccessorTest {
    ContactAccessor accessor;
    @Mock ContentResolver resolver;

    @Before public void before() {
        initMocks(this);
        accessor = new ContactAccessor();
    }

    @Test public void shouldAccessContactsWithEverybody() throws Exception {
        Map<Integer,Group> groups = accessor.getGroups(resolver, RuntimeEnvironment.application.getResources());
        assertThat(groups).hasSize(1);

        Group everybody = groups.get(EVERYBODY_ID);
        assertThat(everybody.title).isEqualTo("Everybody");
        assertThat(everybody._id).isEqualTo(EVERYBODY_ID);
        assertThat(everybody.count).isEqualTo(0);

        verify(resolver).query(eq(ContactsContract.Groups.CONTENT_SUMMARY_URI),
                any(String[].class),
                any(String.class),
                any(String[].class),
                eq(ContactsContract.Groups.TITLE + " ASC"));
    }

    @Test
    public void shouldGetGroupsFromResolver() throws Exception {

        MatrixCursor cursor = new MatrixCursor(
            new String[]{ContactsContract.Groups._ID, ContactsContract.Groups.TITLE, ContactsContract.Groups.SUMMARY_COUNT}
        );

        cursor.addRow(new Object[] { 23, "Testing", 42 });

        when(resolver.query(eq(ContactsContract.Groups.CONTENT_SUMMARY_URI),
                any(String[].class),
                any(String.class),
                any(String[].class),
                eq(ContactsContract.Groups.TITLE + " ASC"))).thenReturn(
                cursor
        );
        Map<Integer,Group> groups = accessor.getGroups(resolver, RuntimeEnvironment.application.getResources());

        assertThat(groups).hasSize(2);

        Group everybody = groups.get(23);
        assertThat(everybody.title).isEqualTo("Testing");
        assertThat(everybody._id).isEqualTo(23);
        assertThat(everybody.count).isEqualTo(42);
    }

    @Test
    public void shouldGetGroupContactIdsEmpty() throws Exception {
        ContactGroupIds ids = accessor.getGroupContactIds(resolver, new ContactGroup(1));
        assertThat(ids.isEmpty()).isTrue();

        verify(resolver).query(
                eq(ContactsContract.Data.CONTENT_URI),
                eq(new String[]{ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID, ContactsContract.CommonDataKinds.GroupMembership.RAW_CONTACT_ID,
                        ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID}),
                eq(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + " = ? AND " + ContactsContract.CommonDataKinds.GroupMembership.MIMETYPE + " = ?"),
                eq(new String[]{String.valueOf(1), ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE}),
                any(String.class)
        );
    }

    @Test public void shouldGetGroupContactIdsFromResolver() throws Exception {
        MatrixCursor cursor = new MatrixCursor(
            new String[] {
                ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID,
                ContactsContract.CommonDataKinds.GroupMembership.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
            });
        cursor.addRow(new Object[] { 123L, 256L, 789L });

        when(resolver.query(
                eq(ContactsContract.Data.CONTENT_URI),
                any(String[].class),
                any(String.class),
                any(String[].class),
                any(String.class))
        ).thenReturn(cursor);

        ContactGroupIds ids = accessor.getGroupContactIds(resolver, new ContactGroup(1));

        assertThat(ids.getIds().contains(123L));
        assertThat(ids.getRawIds().contains(256L));
    }
}
