package com.zegoggles.smssync.service;

import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.util.ArrayList;
import java.util.List;

import java.util.EnumSet;

import static org.mockito.Mockito.mock;

@RunWith(RobolectricTestRunner.class)
public class BackupConfigTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldCheckForDataTypesEmpty() throws Exception {
        List<BackupImapStore> imapStores = new ArrayList<BackupImapStore>();
        imapStores.add(mock(BackupImapStore.class));
        new BackupConfig(imapStores,
                0,
                -1,
                ContactGroup.EVERYBODY,
                BackupType.MANUAL,
                EnumSet.noneOf(DataType.class),
                false);

    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = IllegalArgumentException.class)
    public void shouldCheckForDataTypesNull() throws Exception {
        List<BackupImapStore> imapStores = new ArrayList<BackupImapStore>();
        imapStores.add(mock(BackupImapStore.class));
        new BackupConfig(imapStores,
                0,
                -1,
                ContactGroup.EVERYBODY,
                BackupType.MANUAL,
                null,
                false);
    }


    @Test(expected = IllegalArgumentException.class)
    public void shouldCheckForPositiveTry() throws Exception {
        List<BackupImapStore> imapStores = new ArrayList<BackupImapStore>();
        imapStores.add(mock(BackupImapStore.class));
        new BackupConfig(imapStores,
                -1,
                -1,
                ContactGroup.EVERYBODY,
                BackupType.MANUAL,
                EnumSet.of(DataType.MMS),
                false);
    }
}
