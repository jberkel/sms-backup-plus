package com.zegoggles.smssync.service;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import com.github.jberkel.whassup.Whassup;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.DataType;
import org.jetbrains.annotations.Nullable;

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

    public @Nullable Cursor getItemsForDataType(DataType dataType, ContactGroup group, int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getItemsForDataType(type=" + dataType + ", max=" + max + ")");
        if (!dataType.isBackupEnabled(context)) {
            if (LOCAL_LOGV) Log.v(TAG, "backup disabled for " + dataType + ", returning empty cursor");
            return null;
        }
        if (dataType == WHATSAPP) {
            return getWhatsAppItemsToSync(max);
        } else {
            return performQuery(queryBuilder.buildQueryForDataType(dataType, max, group));
        }
    }

    public long getMaxData(DataType dataType) {
        Cursor cursor = performQuery(queryBuilder.buildMaxQueryForDataType(dataType));
        try {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            } else {
                return DataType.Defaults.MAX_SYNCED_DATE;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private @Nullable Cursor performQuery(BackupQueryBuilder.Query query) {
        if (query == null) return null;
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

    private @Nullable Cursor getWhatsAppItemsToSync(int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getWhatsAppItemsToSync(max=" + max + ")");

        if (!WHATSAPP.isBackupEnabled(context)) {
            if (LOCAL_LOGV) Log.v(TAG, "WhatsApp backup disabled, returning empty");
            return null;
        }
        Whassup whassup = new Whassup();
        if (!whassup.hasBackupDB()) {
            if (LOCAL_LOGV) Log.v(TAG, "No whatsapp backup DB found, returning empty");
            return null;
        }

        try {
            return whassup.queryMessages(WHATSAPP.getMaxSyncedDate(context), max);
        } catch (IOException e) {
            Log.w(LOG, "error fetching whatsapp messages", e);
            return null;
        }
    }
}