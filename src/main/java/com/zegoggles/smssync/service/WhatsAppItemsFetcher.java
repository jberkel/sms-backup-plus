package com.zegoggles.smssync.service;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import com.github.jberkel.whassup.Whassup;
import com.zegoggles.smssync.mail.DataType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.zegoggles.smssync.App.*;
import static com.zegoggles.smssync.service.BackupItemsFetcher.emptyCursor;

public class WhatsAppItemsFetcher {
    private Whassup whassup;

    public WhatsAppItemsFetcher(Context context) {
        this(new Whassup(context));
    }

    protected WhatsAppItemsFetcher(Whassup whassup) {
        this.whassup = whassup;
    }

    public @NotNull Cursor getItems(long maxSyncedDate, int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getItems(max=" + max + ")");

        if (!whassup.hasBackupDB()) {
            if (LOCAL_LOGV) Log.v(TAG, "No whatsapp backup DB found, returning empty");
            return emptyCursor();
        }

        try {
            return whassup.queryMessages(maxSyncedDate, max);
        } catch (IOException e) {
            Log.w(LOG, "error fetching whatsapp messages", e);
            return emptyCursor();
        }
    }

    public long getMostRecentTimestamp() {
        if (!whassup.hasBackupDB()) {
            return DataType.Defaults.MAX_SYNCED_DATE;
        } else {
            try {
                return whassup.getMostRecentTimestamp(true);
            } catch (IOException e) {
                return DataType.Defaults.MAX_SYNCED_DATE;
            }
        }
    }
}
