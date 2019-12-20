package com.zegoggles.smssync.service;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.DataType;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class BackupItemsFetcher {
    private final ContentResolver resolver;
    private final BackupQueryBuilder queryBuilder;

    BackupItemsFetcher(@NonNull ContentResolver resolver,
                       @NonNull BackupQueryBuilder queryBuilder) {
        if (resolver == null) throw new IllegalArgumentException("resolver cannot be null");
        if (queryBuilder == null) throw new IllegalArgumentException("queryBuilder cannot be null");

        this.queryBuilder = queryBuilder;
        this.resolver = resolver;
    }

    public @NonNull Cursor getItemsForDataType(DataType dataType, ContactGroupIds group, int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getItemsForDataType(type=" + dataType + ", max=" + max + ")");
        return performQuery(queryBuilder.buildQueryForDataType(dataType, group, max));
    }

    /**
     * Gets the most recent timestamp for given datatype.
     * @param dataType the data type
     * @return timestamp
     * @throws SecurityException if app does not hold necessary permissions
     */
    public long getMostRecentTimestamp(DataType dataType) {
        return getMostRecentTimestampForQuery(queryBuilder.buildMostRecentQueryForDataType(dataType));
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

    @SuppressLint("Recycle")
    private @NonNull Cursor performQuery(@Nullable BackupQueryBuilder.Query query) {
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
        return new MatrixCursor(new String[]{});
    }
}
