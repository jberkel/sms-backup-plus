package com.zegoggles.smssync.calendar;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import androidx.annotation.NonNull;
import android.util.Log;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CalendarAccessorPost40 implements CalendarAccessor {

    private ContentResolver resolver;

    CalendarAccessorPost40(ContentResolver resolver) {
        this.resolver = resolver;
    }

    @Override public boolean enableSync(long calendarId) {
        final ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);

        final Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId);
        return resolver.update(uri, values, null, null) == 1;
    }

    @Override
    public boolean addEntry(long calendarId, @NonNull Date when, int duration, @NonNull String title,
                            @NonNull String description) {
        if (LOCAL_LOGV) {
            Log.v(TAG, String.format("addEntry(%d, %s, %d, %s, %s)",
                    calendarId, when.toString(), duration, title, description));
        }

        final ContentValues contentValues = new ContentValues();
        contentValues.put(CalendarContract.Events.TITLE, title);
        contentValues.put(CalendarContract.Events.DESCRIPTION, description);
        contentValues.put(CalendarContract.Events.DTSTART, when.getTime());
        contentValues.put(CalendarContract.Events.DTEND, when.getTime() + duration * 1000);
        contentValues.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT);
        contentValues.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED);
        contentValues.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        contentValues.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

        try {
            resolver.insert(CalendarContract.Events.CONTENT_URI, contentValues);
            return true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "could not add calendar entry", e);
            return false;
        } catch (SQLiteException e) {
            Log.w(TAG, "could not add calendar entry", e);
            return false;
        } catch (SecurityException e) {
            Log.w(TAG, "could not add calendar entry (permission)", e);
            return false;
        }
    }

    @Override @NonNull
    public Map<String, String> getCalendars() {
        final Map<String, String> map = new LinkedHashMap<String, String>();

        Cursor cursor = null;
        try {
            cursor = resolver.query(CalendarContract.Calendars.CONTENT_URI,
                new String[] {
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.NAME,
                    CalendarContract.Calendars.SYNC_EVENTS
                },
                null,
                null,
                CalendarContract.Calendars.NAME + " ASC");

            while (cursor != null && cursor.moveToNext()) {

                String id        = cursor.getString(0);
                String name      = cursor.getString(1);
                boolean isSynced = cursor.getInt(2) == 1;
                if (LOCAL_LOGV) Log.d(TAG, "id:"+id+", name:"+name+", synced:"+isSynced);
                map.put(id, name);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "calendars not available", e);
        } catch (SecurityException e) {
            Log.e(TAG, "calendar permission missing", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return map;
    }
}
