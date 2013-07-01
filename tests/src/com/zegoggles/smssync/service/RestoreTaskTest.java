package com.zegoggles.smssync.service;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.SmsConsts;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.mail.MessageConverter;
import com.zegoggles.smssync.service.state.RestoreState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class RestoreTaskTest {
    RestoreTask task;
    RestoreConfig config;
    Context context;
    @Mock BackupImapStore store;
    @Mock BackupImapStore.BackupFolder folder;
    @Mock SmsRestoreService service;
    @Mock RestoreState state;
    @Mock MessageConverter converter;
    @Mock ContentResolver resolver;

    @Before
    public void before() throws MessagingException {
        initMocks(this);
        config = new RestoreConfig(store, 0, true, false, false, -1, 0);
        when(service.getApplicationContext()).thenReturn(Robolectric.application);
        when(service.getState()).thenReturn(state);

        when(store.getFolder(any(DataType.class))).thenReturn(folder);

        task = new RestoreTask(service, converter, resolver);
        context = Robolectric.application;
    }

    @Test public void shouldAcquireAndReleaseLocksDuringRestore() throws Exception {
        task.doInBackground(config);
        verify(service).acquireLocks();
        verify(service).releaseLocks();
    }

    @Test
    public void shouldRestoreItems() throws Exception {
        Date now = new Date();
        List<Message> messages = new ArrayList<Message>();
        ContentValues values = new ContentValues();
        values.put(SmsConsts.TYPE, SmsConsts.MESSAGE_TYPE_INBOX);
        values.put(SmsConsts.DATE, now.getTime());

        Message mockMessage = mock(Message.class);
        when(mockMessage.getFolder()).thenReturn(folder);
        when(converter.getDataType(mockMessage)).thenReturn(DataType.SMS);
        when(converter.messageToContentValues(mockMessage)).thenReturn(values);

        messages.add(mockMessage);

        when(folder.getMessages(anyInt(), anyBoolean(), any(Date.class))).thenReturn(messages);
        when(resolver.insert(Consts.SMS_PROVIDER, values)).thenReturn(Uri.parse("content://sms/123"));
        task.doInBackground(config);

        verify(resolver).insert(Consts.SMS_PROVIDER, values);
        verify(resolver).delete(Uri.parse("content://sms/conversations/-1"), null, null);

        assertThat(DataType.SMS.getMaxSyncedDate(context)).isEqualTo(now.getTime());
        assertThat(task.getSmsIds()).containsExactly("123");
    }
}
