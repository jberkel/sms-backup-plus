package com.zegoggles.smssync.service;

import android.net.Uri;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.DataTypePreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.utils.SimCard;

import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.MMS;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class BackupQueryBuilderTest {

    BackupQueryBuilder builder;
    @Mock DataTypePreferences dataTypePreferences;

    @Before public void before() {
        initMocks(this);
        when(dataTypePreferences.getMaxSyncedDate(any(DataType.class), anyInt())).thenReturn(-1L);
        builder = new BackupQueryBuilder(dataTypePreferences);
    }

    @Test public void shouldBuildQueryForSMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(SMS, null, 0, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND type <> ?  AND (1 OR sub_id = ?)");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "3", "1");
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test public void shouldBuildQueryForSMSIncludingContactGroup() throws Exception {
        ContactGroupIds ids = new ContactGroupIds();
        ids.add(1L, 20L);

        BackupQueryBuilder.Query query = builder.buildQueryForDataType(SMS, ids, 0, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND type <> ?  AND (type = 2 OR person IN (20)) AND (1 OR sub_id = ?)");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "3", "1");
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test public void shouldBuildQueryForMMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(MMS, null, 0, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND m_type <> ?  AND (1 OR sub_id = ?)");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "134", "1");
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test public void shouldBuildQueryForMMSWithSyncedDate() throws Exception {
        long nowInSecs = System.currentTimeMillis();

        when(dataTypePreferences.getMaxSyncedDate(MMS, 0)).thenReturn(nowInSecs);
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(MMS, null, 0, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND m_type <> ?  AND (1 OR sub_id = ?)");
        assertThat(query.selectionArgs).asList().containsExactly(String.valueOf(nowInSecs / 1000L), "134", "1");
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test public void shouldBuildQueryForCallLog() throws Exception {
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(CALLLOG, null, 0, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://call_log/calls"));
        assertThat(query.projection).asList().containsExactly("_id", "number", "duration", "date", "type");
        assertThat(query.selection).isEqualTo("date > ? AND (1 OR subscription_id = ? OR subscription_id = ?)");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "1", "1");
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test public void shouldBuildMostRecentQueryForSMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(SMS);
        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).asList().containsExactly("date");
        assertThat(query.selection).isEqualTo("type <> ?");
        assertThat(query.selectionArgs).asList().containsExactly("3");
        assertThat(query.sortOrder).isEqualTo("date DESC LIMIT 1");
    }

    @Test public void shouldBuildMostRecentQueryForMMS() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(MMS);
        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).asList().containsExactly("date");
        assertThat(query.selection).isNull();
        assertThat(query.selectionArgs).isNull();
        assertThat(query.sortOrder).isEqualTo("date DESC LIMIT 1");
    }

    @Test public void shouldBuildMostRecentQueryForCallLog() throws Exception {
        BackupQueryBuilder.Query query = builder.buildMostRecentQueryForDataType(CALLLOG);
        assertThat(query.uri).isEqualTo(Uri.parse("content://call_log/calls"));
        assertThat(query.projection).asList().containsExactly("date");
        assertThat(query.selection).isNull();
        assertThat(query.selectionArgs).isNull();
        assertThat(query.sortOrder).isEqualTo("date DESC LIMIT 1");
    }

    @Test public void shouldBuildQueryForMultiSimSMS() throws Exception {
        setMultipleSimCards();
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(SMS, null, 0, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://sms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND type <> ?  AND ( sub_id = ?)");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "3", "1");
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test public void shouldBuildQueryForMultiSimMMS() throws Exception {
        setMultipleSimCards();
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(MMS, null, 0, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://mms"));
        assertThat(query.projection).isNull();
        assertThat(query.selection).isEqualTo("date > ? AND m_type <> ?  AND ( sub_id = ?)");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "134", "1");
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    @Test public void shouldBuildQueryForMultiSimCallLog() throws Exception {
        setMultipleSimCards();
        BackupQueryBuilder.Query query = builder.buildQueryForDataType(CALLLOG, null, 0, 200);

        assertThat(query.uri).isEqualTo(Uri.parse("content://call_log/calls"));
        assertThat(query.projection).asList().containsExactly("_id", "number", "duration", "date", "type");
        assertThat(query.selection).isEqualTo("date > ? AND ( subscription_id = ? OR subscription_id = ?)");
        assertThat(query.selectionArgs).asList().containsExactly("-1", "1", "1");
        assertThat(query.sortOrder).isEqualTo("date LIMIT 200");
    }

    
    private void setMultipleSimCards() {
        SimCard[] simCards = new SimCard[2];
        simCards[0] = new SimCard("0", "0");
        simCards[1] = new SimCard("1", "1");
        simCards[0].IccId = "1";
        App.SimCards = simCards;
    }
}
