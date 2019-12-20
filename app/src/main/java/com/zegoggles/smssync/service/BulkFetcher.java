package com.zegoggles.smssync.service;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zegoggles.smssync.contacts.ContactGroupIds;
import com.zegoggles.smssync.mail.DataType;

import java.util.EnumSet;

public class BulkFetcher {

    private BackupItemsFetcher itemsFetcher;

    public BulkFetcher(BackupItemsFetcher itemsFetcher) {
        this.itemsFetcher = itemsFetcher;
    }

    public @NonNull BackupCursors fetch(final @NonNull EnumSet<DataType> types,
                                        final @Nullable ContactGroupIds groups,
                                        final int maxItems) {

        int max = maxItems;
        BackupCursors cursors = new BackupCursors();
        for (DataType type : types) {
            Cursor cursor = itemsFetcher.getItemsForDataType(type, groups, max);
            cursors.add(type, cursor);

            if (max > 0) {
                max = Math.max(max - cursor.getCount(), 0);
            }

            if (max == 0) break;
        }
        return cursors;
    }

}
