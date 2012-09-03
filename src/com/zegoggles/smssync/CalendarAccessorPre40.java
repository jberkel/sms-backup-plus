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
package com.zegoggles.smssync;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public class CalendarAccessorPre40 implements CalendarAccessor {
  private static final Uri CALENDAR_URI     = Uri.parse("content://calendar");
  private static final Uri CALENDAR_URI_2_2 = Uri.parse("content://com.android.calendar");
  private static final Uri CALENDAR;

  static {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
      CALENDAR = CALENDAR_URI_2_2;
    } else {
      CALENDAR = CALENDAR_URI;
    }
  }

  /** @noinspection UnusedDeclaration*/
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

  public void addEntry(Context context, int calendarId, Date when, int duration,
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
      context.getContentResolver().insert(Uri.withAppendedPath(CALENDAR, "events"), contentValues);
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "could not add calendar entry", e);
    }
  }

  public Map<String, String> getCalendars(Context context) {
    final Map<String, String> map = new LinkedHashMap<String, String>();

    Cursor cursor = null;
    try {
      cursor = context.getContentResolver().query(Uri.withAppendedPath(CALENDAR, "calendars"),
                                                  new String[]{ "_id", "displayname" },
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
