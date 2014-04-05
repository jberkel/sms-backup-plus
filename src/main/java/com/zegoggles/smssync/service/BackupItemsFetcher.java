package com.zegoggles.smssync.service;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.DataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class BackupItemsFetcher {
    private final Context context;
    private final ContentResolver resolver;
    private final BackupQueryBuilder queryBuilder;

    public BackupItemsFetcher(@NotNull Context context,
                              @NotNull ContentResolver resolver,
                              @NotNull BackupQueryBuilder queryBuilder) {
        if (resolver == null) throw new IllegalArgumentException("resolver cannot be null");
        if (queryBuilder == null) throw new IllegalArgumentException("queryBuilder cannot be null");

        this.queryBuilder = queryBuilder;
        this.context = context;
        this.resolver = resolver;
    }

    public @NotNull Cursor getItemsForDataType(DataType dataType, ContactGroupIds group, int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getItemsForDataType(type=" + dataType + ", max=" + max + ")");
        switch (dataType) {
            case WHATSAPP: return new WhatsAppItemsFetcher(context).getItems(DataType.WHATSAPP.getMaxSyncedDate(context), max);
            default: return performQuery(queryBuilder.buildQueryForDataType(dataType, group, max));
        }
    }

    public long getMostRecentTimestamp(DataType dataType) {
        switch (dataType) {
            case WHATSAPP: return new WhatsAppItemsFetcher(context).getMostRecentTimestamp();
            default: return getMostRecentTimestampForQuery(queryBuilder.buildMostRecentQueryForDataType(dataType));
        }
    }

    private long getMostRecentTimestampForQuery(BackupQueryBuilder.Query query) {
        Cursor cursor = performQuery(query);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            } else {
                return DataType.Defaults.MAX_SYNCED_DATE;
            }
        } finally {
            cursor.close();
        }
    }

    private @NotNull Cursor performQuery(@Nullable BackupQueryBuilder.Query query) {
        if (query == null) return emptyCursor();
        try {
            final Cursor cursor = resolver.query(
                    query.uri,
                    query.projection,
                    query.selection,
                    query.selectionArgs,
                    query.sortOrder
            );
            return cursor == null ? emptyCursor() : cursor;
        } catch (SQLiteException e) {
            Log.w(TAG, "error querying DB", e);
            return emptyCursor();
        } catch (NullPointerException e) {
            Log.w(TAG, "error querying DB", e);
            return emptyCursor();
        }
    }

    static Cursor emptyCursor() {
        return new MatrixCursor(new String[] {});
    }
}
