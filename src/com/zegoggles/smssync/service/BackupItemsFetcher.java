package com.zegoggles.smssync.service;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import com.github.jberkel.whassup.Whassup;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.PrefStore;

import java.io.IOException;

import static com.zegoggles.smssync.App.*;
import static com.zegoggles.smssync.mail.DataType.WHATSAPP;

public class BackupItemsFetcher {
    private final Context context;
    private final BackupQueryBuilder queryBuilder;

    public BackupItemsFetcher(Context context, BackupQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
        this.context = context;
    }

    public Cursor getItemsForDataType(DataType dataType, ContactGroup group, int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getItemsForDataType(type=" + dataType + ", max=" + max + ")");
        if (!PrefStore.isDataTypeBackupEnabled(context, dataType)) {
            if (LOCAL_LOGV) Log.v(TAG, "backup disabled for " + dataType + ", returning empty cursor");
            return new MatrixCursor(new String[0], 0);
        }
        if (dataType == WHATSAPP) {
            return getWhatsAppItemsToSync(max);
        } else {
            BackupQueryBuilder.Query query =
                    queryBuilder.buildQueryForDataType(dataType, max, group);

            return query == null ? null : performQuery(query);
        }
    }

    public long getMaxData(DataType dataType) {
        Cursor cursor = performQuery(queryBuilder.buildMaxQueryForDataType(dataType));
        try {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            } else {
                return PrefStore.DEFAULT_MAX_SYNCED_DATE;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private Cursor performQuery(BackupQueryBuilder.Query query) {
        try {
            return context.getContentResolver().query(
                    query.uri,
                    query.projection,
                    query.selection,
                    query.selectionArgs,
                    query.sortOrder
            );
        } catch (SQLiteException e) {
            Log.w(TAG, "error querying DB", e);
            return null;
        }
    }

    private Cursor getWhatsAppItemsToSync(int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getWhatsAppItemsToSync(max=" + max + ")");

        if (!PrefStore.isDataTypeBackupEnabled(context, WHATSAPP)) {
            if (LOCAL_LOGV) Log.v(TAG, "WhatsApp backup disabled, returning empty");
            return null;
        }
        Whassup whassup = new Whassup();
        if (!whassup.hasBackupDB()) {
            if (LOCAL_LOGV) Log.v(TAG, "No whatsapp backup DB found, returning empty");
            return null;
        }

        try {
            return whassup.queryMessages(PrefStore.getMaxSyncedDate(context, WHATSAPP), max);
        } catch (IOException e) {
            Log.w(LOG, "error fetching whatsapp messages", e);
            return null;
        }
    }
}