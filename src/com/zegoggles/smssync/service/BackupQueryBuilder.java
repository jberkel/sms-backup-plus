package com.zegoggles.smssync.service;

import android.content.Context;
import android.net.Uri;
import android.provider.CallLog;
import android.text.TextUtils;
import android.util.Log;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.MmsConsts;
import com.zegoggles.smssync.SmsConsts;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.DataType;

import java.util.Locale;
import java.util.Set;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.DataType.*;

class BackupQueryBuilder {
    private final Context context;
    private final ContactAccessor contacts;

    // only query for needed fields
    // http://stackoverflow.com/questions/12033234/get-calls-provider-internal-structure
    private static final String[] CALLLOG_PROJECTION = {
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.DURATION,
        CallLog.Calls.DATE,
        CallLog.Calls.TYPE
    };

    public BackupQueryBuilder(Context context, ContactAccessor contacts) {
        this.context = context;
        this.contacts = contacts;
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
                    max > 0 ? SmsConsts.DATE + " LIMIT "+max : SmsConsts.DATE);
        }
    }

    public Query buildQueryForDataType(DataType type, int max, ContactGroup group) {
        switch (type) {
            case MMS:     return getQueryForMMS(max, group);
            case SMS:     return getQueryForSMS(max, group);
            case CALLLOG: return getQueryForCallLog(max);
            default:      return null;
        }
    }

    public Query buildMaxQueryForDataType(DataType type) {
        switch (type) {
            case MMS:
                return new Query(
                    Consts.MMS_PROVIDER,
                    new String[] {MmsConsts.DATE },
                    null,
                    null,
                    MmsConsts.DATE + " DESC LIMIT 1");
            case SMS:
                return new Query(
                    Consts.SMS_PROVIDER,
                    new String[]{SmsConsts.DATE},
                    SmsConsts.TYPE + " <> ?",
                    new String[]{String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT)},
                    SmsConsts.DATE + " DESC LIMIT 1");
            case CALLLOG:
                return new Query(
                    Consts.CALLLOG_PROVIDER,
                    new String[]{CallLog.Calls.DATE},
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC LIMIT 1");
            default:
                return null;
        }
    }

    private Query getQueryForSMS(int max, ContactGroup group) {
        return new Query(Consts.SMS_PROVIDER,
            null,
            String.format(Locale.ENGLISH,
                "%s > ? AND %s <> ? %s",
                    SmsConsts.DATE,
                    SmsConsts.TYPE,
                    groupSelection(SMS, group)),
            new String[] {
                String.valueOf(SMS.getMaxSyncedDate(context)),
                String.valueOf(SmsConsts.MESSAGE_TYPE_DRAFT)
            },
            max);
    }

    private Query getQueryForMMS(int max, ContactGroup group) {
        return new Query(
            Consts.MMS_PROVIDER,
            null,
            String.format(Locale.ENGLISH, "%s > ? AND %s <> ? %s",
                    SmsConsts.DATE,
                    MmsConsts.TYPE,
                    groupSelection(DataType.MMS, group)),
            new String[] {
                String.valueOf(MMS.getMaxSyncedDate(context)),
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
                String.valueOf(CALLLOG.getMaxSyncedDate(context))
            },
            max);
    }

    private String groupSelection(DataType type, ContactGroup group) {
        /* MMS group selection not supported at the moment */
        if (type != SMS || group.type == ContactGroup.Type.EVERYBODY) return "";

        final Set<Long> ids = contacts.getGroupContactIds(context, group).rawIds;
        if (LOCAL_LOGV) Log.v(TAG, "only selecting contacts matching " + ids);
        return String.format(Locale.ENGLISH, " AND (%s = %d OR %s IN (%s))",
            SmsConsts.TYPE,
            SmsConsts.MESSAGE_TYPE_SENT,
            SmsConsts.PERSON,
            TextUtils.join(",", ids.toArray(new Long[ids.size()])));
    }
}
