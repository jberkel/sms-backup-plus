package com.zegoggles.smssync.service;

import android.net.Uri;
import android.provider.CallLog;
import android.provider.Telephony;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.DataTypePreferences;

import java.util.Locale;
import java.util.Set;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.MMS;
import static com.zegoggles.smssync.mail.DataType.SMS;

class BackupQueryBuilder {
    private static final String DESC_LIMIT_1 = " DESC LIMIT 1";

    // only query for needed fields
    // http://stackoverflow.com/questions/12033234/get-calls-provider-internal-structure
    private static final String[] CALLLOG_PROJECTION = {
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.DURATION,
        CallLog.Calls.DATE,
        CallLog.Calls.TYPE
    };
    private final DataTypePreferences preferences;

    BackupQueryBuilder(DataTypePreferences preferences) {
        this.preferences = preferences;
    }

    static class Query {
        final Uri      uri;
        final String[] projection;
        final String   selection;
        final String[] selectionArgs;
        final String   sortOrder;

        Query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            this.uri = uri;
            this.projection = projection;
            this.selection = selection;
            this.selectionArgs = selectionArgs;
            this.sortOrder = sortOrder;
        }

        Query(Uri uri, String[] projection, String selection, String[] selectionArgs, int max) {
            this(uri, projection, selection, selectionArgs,
                    max > 0 ? Telephony.TextBasedSmsColumns.DATE + " LIMIT "+max : Telephony.TextBasedSmsColumns.DATE);
        }
    }

    public @Nullable Query buildQueryForDataType(DataType type, @Nullable ContactGroupIds groupIds, int max) {
        switch (type) {
            case SMS:     return getQueryForSMS(groupIds, max);
            case MMS:     return getQueryForMMS(groupIds, max);
            case CALLLOG: return getQueryForCallLog(max);
            default:      return null;
        }
    }

    public @Nullable Query buildMostRecentQueryForDataType(DataType type) {
        switch (type) {
            case MMS:
                return new Query(
                    Consts.MMS_PROVIDER,
                    new String[] {Telephony.BaseMmsColumns.DATE },
                    null,
                    null,
                    Telephony.BaseMmsColumns.DATE + DESC_LIMIT_1);
            case SMS:
                return new Query(
                    Consts.SMS_PROVIDER,
                    new String[]{Telephony.TextBasedSmsColumns.DATE},
                    Telephony.TextBasedSmsColumns.TYPE + " <> ?",
                    new String[]{String.valueOf(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT)},
                    Telephony.TextBasedSmsColumns.DATE + DESC_LIMIT_1);
            case CALLLOG:
                return new Query(
                    Consts.CALLLOG_PROVIDER,
                    new String[]{CallLog.Calls.DATE},
                    null,
                    null,
                    CallLog.Calls.DATE + DESC_LIMIT_1);
            default:
                return null;
        }
    }

    private Query getQueryForSMS(@Nullable ContactGroupIds groupIds, int max) {
        return new Query(Consts.SMS_PROVIDER,
            null,
            String.format(Locale.ENGLISH,
                "%s > ? AND %s <> ? %s",
                    Telephony.TextBasedSmsColumns.DATE,
                    Telephony.TextBasedSmsColumns.TYPE,
                    groupSelection(SMS, groupIds)).trim(),
            new String[] {
                String.valueOf(preferences.getMaxSyncedDate(SMS)),
                String.valueOf(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT)
            },
            max);
    }

    private Query getQueryForMMS(@Nullable ContactGroupIds group, int max) {
        long maxSynced = preferences.getMaxSyncedDate(MMS);
        if (maxSynced > 0) {
            // NB: max synced date is stored in seconds since epoch in database
            maxSynced = (long) (maxSynced / 1000d);
        }
        return new Query(
            Consts.MMS_PROVIDER,
            null,
            String.format(Locale.ENGLISH, "%s > ? AND %s <> ? %s",
                    Telephony.BaseMmsColumns.DATE,
                    Telephony.BaseMmsColumns.MESSAGE_TYPE,
                    groupSelection(DataType.MMS, group)).trim(),
            new String[] {
                String.valueOf(maxSynced),
                MmsConsts.DELIVERY_REPORT
            },
            max);
    }

    private Query getQueryForCallLog(int max) {
        return new Query(
            Consts.CALLLOG_PROVIDER,
            CALLLOG_PROJECTION,
            String.format(Locale.ENGLISH, "%s > ?", CallLog.Calls.DATE),
            new String[] {
                String.valueOf(preferences.getMaxSyncedDate(CALLLOG))
            },
            max);
    }

    private String groupSelection(DataType type, @Nullable ContactGroupIds group) {
        /* Only MMS selection is supported at the moment */
        if (type != SMS || group == null) {
            return "";
        }

        final Set<Long> ids = group.getRawIds();

        if (LOCAL_LOGV) Log.v(TAG, "only selecting contacts matching " + ids);
        return String.format(Locale.ENGLISH, " AND (%s = %d OR %s IN (%s))",
            Telephony.TextBasedSmsColumns.TYPE,
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT,
            Telephony.TextBasedSmsColumns.PERSON,
            TextUtils.join(",", ids.toArray(new Long[ids.size()])));
    }
}
