/*
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zegoggles.smssync.calendar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class CalendarAccessorPre40 implements CalendarAccessor {
    private static final Uri CALENDAR = Uri.parse("content://com.android.calendar");

    private ContentResolver resolver;

    CalendarAccessorPre40(ContentResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * @noinspection UnusedDeclaration
     */
    interface Consts {
        String TITLE = "title";
        String DESCRIPTION = "description";
        String CALENDAR_ID = "calendar_id";

        String HAS_ALARM = "hasAlarm";

        String VISIBILITY = "visibility";
        int VISIBILITY_DEFAULT = 0;
        int VISIBILITY_CONFIDENTIAL = 1;
        int VISIBILITY_PRIVATE = 2;
        int VISIBILITY_PUBLIC = 3;

        String TRANSPARENCY = "transparency";
        int TRANSPARENCY_OPAQUE = 0;
        int TRANSPARENCY_TRANSPARENT = 1;

        String ALL_DAY = "allDay";
        /* Type: INTEGER (long; millis since epoch) */
        String DTSTART = "dtstart";
        /* Type: INTEGER (long; millis since epoch) */
        String DTEND = "dtend";
        /* Type: TEXT (duration in RFC2445 format) */
        String DURATION = "duration";

        String STATUS = "eventStatus";
        int STATUS_TENTATIVE = 0;
        int STATUS_CONFIRMED = 1;
        int STATUS_CANCELED = 2;
    }

    @Override public boolean enableSync(long calendarId) {
        return false;
    }

    public boolean addEntry(long calendarId, Date when, int duration,
                            String title, String description) {
        if (LOCAL_LOGV) {
            Log.v(TAG, String.format("addEntry(%d, %s, %d, %s, %s)",
                    calendarId, when.toString(), duration, title, description));
        }

        final ContentValues contentValues = new ContentValues();
        contentValues.put(Consts.TITLE, title);
        contentValues.put(Consts.DESCRIPTION, description);
        contentValues.put(Consts.DTSTART, when.getTime());
        contentValues.put(Consts.DTEND, when.getTime() + duration * 1000);
        contentValues.put(Consts.VISIBILITY, Consts.VISIBILITY_DEFAULT);
        contentValues.put(Consts.STATUS, Consts.STATUS_CONFIRMED);
        contentValues.put(Consts.CALENDAR_ID, calendarId);

        try {
            resolver.insert(Uri.withAppendedPath(CALENDAR, "events"), contentValues);
            return true;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "could not add calendar entry", e);
            return false;
        }
    }

    public Map<String, String> getCalendars() {
        final Map<String, String> map = new LinkedHashMap<String, String>();

        Cursor cursor = null;
        try {
            cursor = resolver.query(Uri.withAppendedPath(CALENDAR, "calendars"),
                    new String[]{"_id", "displayname"},
                    null, null, "displayname ASC");

            while (cursor != null && cursor.moveToNext()) {
                map.put(cursor.getString(0), cursor.getString(1));
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "calendars not available", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return map;
    }
}
