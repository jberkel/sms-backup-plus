package com.zegoggles.smssync.service;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import com.github.jberkel.whassup.Whassup;
import com.zegoggles.smssync.mail.DataType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.zegoggles.smssync.App.*;
import static com.zegoggles.smssync.mail.DataType.WHATSAPP;
import static com.zegoggles.smssync.service.BackupItemsFetcher.emptyCursor;

public class WhatsAppItemsFetcher {
    public @NotNull Cursor getItems(Context context, int max) {
        if (LOCAL_LOGV) Log.v(TAG, "getItems(max=" + max + ")");

        if (!WHATSAPP.isBackupEnabled(context)) {
            if (LOCAL_LOGV) Log.v(TAG, "WhatsApp backup disabled, returning empty");
            return emptyCursor();
        }
        Whassup whassup = new Whassup();
        if (!whassup.hasBackupDB()) {
            if (LOCAL_LOGV) Log.v(TAG, "No whatsapp backup DB found, returning empty");
            return emptyCursor();
        }

        try {
            return whassup.queryMessages(WHATSAPP.getMaxSyncedDate(context), max);
        } catch (IOException e) {
            Log.w(LOG, "error fetching whatsapp messages", e);
            return emptyCursor();
        }
    }

    public long getMostRecentTimestamp() {
        // TODO
        return DataType.Defaults.MAX_SYNCED_DATE;
    }
}
