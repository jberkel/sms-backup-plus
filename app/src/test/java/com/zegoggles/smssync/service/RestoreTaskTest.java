package com.zegoggles.smssync.service;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.Telephony;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.store.imap.ImapMessage;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.auth.TokenRefresher;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.mail.MessageConverter;
import com.zegoggles.smssync.preferences.DataTypePreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.state.RestoreState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class RestoreTaskTest {
    RestoreTask task;
    RestoreConfig config;
    @Mock BackupImapStore store;
    @Mock BackupImapStore.BackupFolder folder;
    @Mock SmsRestoreService service;
    @Mock RestoreState state;
    @Mock MessageConverter converter;
    @Mock ContentResolver resolver;
    @Mock TokenRefresher tokenRefresher;

    @Before
    public void before() throws MessagingException {
        initMocks(this);
        config = new RestoreConfig(store, 0, true, false, false, -1, 0);
        when(service.getApplicationContext()).thenReturn(RuntimeEnvironment.application);
        when(service.getState()).thenReturn(state);
        when(service.getPreferences()).thenReturn(new Preferences(RuntimeEnvironment.application));

        when(store.getFolder(any(DataType.class), any(DataTypePreferences.class))).thenReturn(folder);

        task = new RestoreTask(service, converter, resolver, tokenRefresher);
    }

    @Test public void shouldAcquireAndReleaseLocksDuringRestore() throws Exception {
        task.doInBackground(config);
        verify(service).acquireLocks();
        verify(service).releaseLocks();
    }

    @Test public void shouldVerifyStoreSettings() throws Exception {
        task.doInBackground(config);
        verify(store).checkSettings();
    }

    @Test public void shouldCloseFolders() throws Exception {
        task.doInBackground(config);
        verify(store).closeFolders();
    }

    @Test
    public void shouldRestoreItems() throws Exception {
        Date now = new Date();
        List<ImapMessage> messages = new ArrayList<ImapMessage>();
        ContentValues values = new ContentValues();
        values.put(Telephony.TextBasedSmsColumns.TYPE, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX);
        values.put(Telephony.TextBasedSmsColumns.DATE, now.getTime());

        ImapMessage mockMessage = mock(ImapMessage.class);
        when(mockMessage.getFolder()).thenReturn(folder);
        when(converter.getDataType(mockMessage)).thenReturn(DataType.SMS);
        when(converter.messageToContentValues(mockMessage)).thenReturn(values);

        messages.add(mockMessage);

        when(folder.getMessages(anyInt(), anyBoolean(), any(Date.class))).thenReturn(messages);
        when(resolver.insert(Consts.SMS_PROVIDER, values)).thenReturn(Uri.parse("content://sms/123"));
        task.doInBackground(config);

        verify(resolver).insert(Consts.SMS_PROVIDER, values);
        verify(resolver).delete(Uri.parse("content://sms/conversations/-1"), null, null);

        assertThat(service.getPreferences().getDataTypePreferences().getMaxSyncedDate(DataType.SMS)).isEqualTo(now.getTime());
        assertThat(task.getSmsIds()).containsExactly("123");

        verify(store).closeFolders();
    }
}
