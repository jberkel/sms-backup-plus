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

import android.content.Context;
import android.database.Cursor;
import android.content.ContentValues;
import android.util.Log;
import android.os.Build;
import android.net.Uri;

import java.util.Date;
import java.util.Map;
import java.util.LinkedHashMap;

import static com.zegoggles.smssync.App.*;

public class CalendarApi {

  private static final Uri CALENDAR_URI     = Uri.parse("content://calendar");
  private static final Uri CALENDAR_URI_2_2 = Uri.parse("content://com.android.calendar");
  private static final Uri CALENDAR;

  static {
    if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.FROYO) {
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

  public static void addEntry(Context context, int calId, Date when, int duration,
                              String title, String description) {
    if (LOCAL_LOGV) {
      Log.v(TAG, String.format("addEntry(%d, %s, %d, %s, %s)",
            calId, when.toString(), duration, title, description));
    }

    final ContentValues v = new ContentValues();
    v.put(Consts.TITLE, title);
    v.put(Consts.DESCRIPTION, description);
    v.put(Consts.DTSTART, when.getTime());
    v.put(Consts.DTEND, when.getTime() + duration * 1000);
    v.put(Consts.VISIBILITY, Consts.VISIBILITY_DEFAULT);
    v.put(Consts.STATUS, Consts.STATUS_CONFIRMED);
    v.put(Consts.CALENDAR_ID, calId);

    try {
      context.getContentResolver().insert(Uri.withAppendedPath(CALENDAR, "events"), v);
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "could not add calendar entry", e);
    }
  }

  public static Map<String, String> getCalendars(Context context) {
    final Map<String, String> map = new LinkedHashMap<String, String>();

    if ( Build.VERSION.SDK_INT >= 14) {
        Log.d(TAG, "calendar sync disabled in ICS for now");
        return map;
    }

    Cursor c = null;
    try {
      c = context.getContentResolver().query(Uri.withAppendedPath(CALENDAR, "calendars"),
                       new String[]{ "_id", "displayname" },
                       null, null, "displayname ASC");

      while (c != null && c.moveToNext()) {
        map.put(c.getString(0), c.getString(1));
      }
    } catch (IllegalArgumentException e) {
       Log.e(TAG, "calendars not available", e);
    } finally {
       if (c != null) c.close();
    }
    return map;
  }
}
