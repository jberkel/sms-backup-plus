package com.zegoggles.smssync.service;

import android.net.Uri;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.DataType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class BackupQueryBuilderTest {

    BackupQueryBuilder builder;
    @Mock private ContactAccessor accessor;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        builder = new BackupQueryBuilder(Robolectric.application);
    }

    @Test
    public void shouldBuildQueryForSMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(DataType.SMS, null, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND type <> ?");
        assertThat(query.selectionArgs).isEqualTo(new String[] { "-1", "3"} );
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test
    public void shouldBuildQueryForSMSIncludingContactGroup() throws Exception {
        ContactGroupIds ids = new ContactGroupIds();
        ids.add(1L, 20L);

        BackupQueryBuilder.Query query = builder.buildQueryForDataType(DataType.SMS, ids, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND type <> ?  AND (type = 2 OR person IN (20))");
        assertThat(query.selectionArgs).isEqualTo(new String[] { "-1", "3"} );
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test
    public void shouldBuildQueryForMMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(DataType.MMS, null, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND m_type <> ?");
        assertThat(query.selectionArgs).isEqualTo(new String[] { "-1", "134"} );
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test
    public void shouldBuildQueryForCallLog() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(DataType.CALLLOG, null, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://call_log/calls"));
        assertThat(query.projection).isEqualTo(new String[] { "_id", "number", "duration", "date", "type" });
        assertThat(query.selection).isEqualTo("date > ?");
        assertThat(query.selectionArgs).isEqualTo(new String[] { "-1" } );
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test
    public void shouldBuildMostRecentQueryForSMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(DataType.SMS);
        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).isEqualTo(new String[] { "date" } );
        assertThat(query.selection).isEqualTo("type <> ?");
        assertThat(query.selectionArgs).isEqualTo(new String[] { "3"} );
        assertThat(query.sortOrder).isEqualTo("date DESC LIMIT 1");
    }

    @Test
    public void shouldBuildMostRecentQueryForMMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(DataType.MMS);
        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).isEqualTo(new String[] { "date" } );
        assertThat(query.selection).isNull();
        assertThat(query.selectionArgs).isNull();
        assertThat(query.sortOrder).isEqualTo("date DESC LIMIT 1");
    }

    @Test
    public void shouldBuildMostRecentQueryForCallLog() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(DataType.CALLLOG);
        assertThat(query.uri).isEqualTo(Uri.parse("content://call_log/calls"));
        assertThat(query.projection).isEqualTo(new String[] { "date" } );
        assertThat(query.selection).isNull();
        assertThat(query.selectionArgs).isNull();
        assertThat(query.sortOrder).isEqualTo("date DESC LIMIT 1");
    }
}
