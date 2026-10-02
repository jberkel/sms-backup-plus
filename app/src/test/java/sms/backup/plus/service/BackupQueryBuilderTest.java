package sms.backup.plus.service;

import android.net.Uri;
import sms.backup.plus.contacts.ContactGroupIds;
import sms.backup.plus.mail.DataType;
import sms.backup.plus.preferences.DataTypePreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;
import static sms.backup.plus.mail.DataType.CALLLOG;
import static sms.backup.plus.mail.DataType.MMS;
import static sms.backup.plus.mail.DataType.SMS;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class BackupQueryBuilderTest {

    BackupQueryBuilder builder;
    @Mock DataTypePreferences dataTypePreferences;

    @Before public void before() {
        initMocks(this);
        when(dataTypePreferences.getMaxSyncedDate(any(DataType.class))).thenReturn(-1L);
        builder = new BackupQueryBuilder(dataTypePreferences);
    }

    @Test public void shouldBuildQueryForSMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(SMS, null, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND type <> ?");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "3");
        assertThat(query.sortOrder).isEqualTo("date");
        assertThat(query.limit).isEqualTo(200);
    }

    @Test public void shouldBuildQueryForSMSIncludingContactGroup() throws Exception {
        ContactGroupIds ids = new ContactGroupIds();
        ids.add(1L, 20L);

        BackupQueryBuilder.Query query = builder.buildQueryForDataType(SMS, ids, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND type <> ?  AND (type = 2 OR person IN (20))");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "3");
        assertThat(query.sortOrder).isEqualTo("date");
        assertThat(query.limit).isEqualTo(200);
    }

    @Test public void shouldBuildQueryForMMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(MMS, null, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND m_type <> ?");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "134");
        assertThat(query.sortOrder).isEqualTo("date");
        assertThat(query.limit).isEqualTo(200);
    }

    @Test public void shouldBuildQueryForMMSWithSyncedDate() throws Exception {
        long nowInSecs = System.currentTimeMillis();

        when(dataTypePreferences.getMaxSyncedDate(MMS)).thenReturn(nowInSecs);
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(MMS, null, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND m_type <> ?");
        assertThat(query.selectionArgs).asList().containsExactly(String.valueOf(nowInSecs / 1000L), "134");
        assertThat(query.sortOrder).isEqualTo("date");
        assertThat(query.limit).isEqualTo(200);
    }

    @Test public void shouldBuildQueryForCallLog() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(CALLLOG, null, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://call_log/calls"));
        assertThat(query.projection).asList().containsExactly("_id", "number", "duration", "date", "type");
        assertThat(query.selection).isEqualTo("date > ?");
        assertThat(query.selectionArgs).asList().containsExactly("-1");
        assertThat(query.sortOrder).isEqualTo("date");
        assertThat(query.limit).isEqualTo(200);
    }

    @Test public void shouldBuildMostRecentQueryForSMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(SMS);
        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).asList().containsExactly("date");
        assertThat(query.selection).isEqualTo("type <> ?");
        assertThat(query.selectionArgs).asList().containsExactly("3");
        assertThat(query.sortOrder).isEqualTo("date DESC");
        assertThat(query.limit).isEqualTo(1);
    }

    @Test public void shouldBuildMostRecentQueryForMMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(MMS);
        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).asList().containsExactly("date");
        assertThat(query.selection).isNull();
        assertThat(query.selectionArgs).isNull();
        assertThat(query.sortOrder).isEqualTo("date DESC");
        assertThat(query.limit).isEqualTo(1);
    }

    @Test public void shouldBuildMostRecentQueryForCallLog() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(CALLLOG);
        assertThat(query.uri).isEqualTo(Uri.parse("content://call_log/calls"));
        assertThat(query.projection).asList().containsExactly("date");
        assertThat(query.selection).isNull();
        assertThat(query.selectionArgs).isNull();
        assertThat(query.sortOrder).isEqualTo("date DESC");
        assertThat(query.limit).isEqualTo(1);
    }
}
