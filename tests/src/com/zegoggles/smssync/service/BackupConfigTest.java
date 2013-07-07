package com.zegoggles.smssync.service;

import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.EnumSet;

import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
public class BackupConfigTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldCheckForDataTypesEmpty() throws Exception {
        new BackupConfig(mock(BackupImapStore.class),
                0,
                false,
                -1,
                ContactGroup.EVERYBODY, -1,
                BackupType.MANUAL,
                EnumSet.noneOf(DataType.class),
                false);

    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldCheckForDataTypesNull() throws Exception {
        new BackupConfig(mock(BackupImapStore.class),
                0,
                false,
                -1,
                ContactGroup.EVERYBODY, -1,
                BackupType.MANUAL,
                null,
                false);
    }


    @Test(expected = IllegalArgumentException.class)
    public void shouldCheckForPositiveTry() throws Exception {
        new BackupConfig(mock(BackupImapStore.class),
                -1,
                false,
                -1,
                ContactGroup.EVERYBODY, -1,
                BackupType.MANUAL,
                EnumSet.of(DataType.MMS),
                false);
    }
}
