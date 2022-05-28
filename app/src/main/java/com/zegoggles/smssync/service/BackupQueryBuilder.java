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
import com.zegoggles.smssync.App;

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

    public @Nullable Query buildQueryForDataType(DataType type, @Nullable ContactGroupIds groupIds, Integer settingsId, int max) {
        switch (type) {
            case SMS:     return getQueryForSMS(groupIds, settingsId, max);
            case MMS:     return getQueryForMMS(groupIds, settingsId, max);
            case CALLLOG: return getQueryForCallLog(settingsId, max);
            default:      return null;
        }
    }

    private String getSimCardNumber(Integer settingsId) {
        //simCardNumber starts with 1, whereas settingsId is 0-based
        Integer simCardNumber = settingsId + 1;
        return simCardNumber.toString();
    }

    private String getIccId(Integer settingsId) {
        return App.SimCards[settingsId].IccId;
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

    private Query getQueryForSMS(@Nullable ContactGroupIds groupIds, Integer settingsId, int max) {
        return new Query(Consts.SMS_PROVIDER,
            null,
            String.format(Locale.ENGLISH,
                "%s > ? AND %s <> ? %s AND %s = ?",
                    Telephony.TextBasedSmsColumns.DATE,
                    Telephony.TextBasedSmsColumns.TYPE,
                    groupSelection(SMS, groupIds),
                    Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID
                    ).trim(),
            new String[] {
                String.valueOf(preferences.getMaxSyncedDate(SMS, settingsId)),
                String.valueOf(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT),
                getSimCardNumber(settingsId)
            },
            max);
    }

    private Query getQueryForMMS(@Nullable ContactGroupIds group, Integer settingsId, int max) {
        long maxSynced = preferences.getMaxSyncedDate(MMS, settingsId);
        if (maxSynced > 0) {
            // NB: max synced date is stored in seconds since epoch in database
            maxSynced = (long) (maxSynced / 1000d);
        }
        return new Query(
            Consts.MMS_PROVIDER,
            null,
            String.format(Locale.ENGLISH, "%s > ? AND %s <> ? %s AND %s = ?",
                    Telephony.BaseMmsColumns.DATE,
                    Telephony.BaseMmsColumns.MESSAGE_TYPE,
                    groupSelection(DataType.MMS, group),
                    Telephony.BaseMmsColumns.SUBSCRIPTION_ID
                    ).trim(),
            new String[] {
                String.valueOf(maxSynced),
                MmsConsts.DELIVERY_REPORT,
                getSimCardNumber(settingsId)
            },
            max);
    }

    private Query getQueryForCallLog(Integer settingsId, int max) {
        return new Query(
            Consts.CALLLOG_PROVIDER,
            CALLLOG_PROJECTION,
            String.format(Locale.ENGLISH, "%s > ? AND (%s = ? OR %s = ?)", CallLog.Calls.DATE, CallLog.Calls.PHONE_ACCOUNT_ID, CallLog.Calls.PHONE_ACCOUNT_ID),
            new String[] {
                String.valueOf(preferences.getMaxSyncedDate(CALLLOG, settingsId)),
                getSimCardNumber(settingsId),
                getIccId(settingsId)
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
