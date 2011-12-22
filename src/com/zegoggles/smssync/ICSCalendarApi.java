package com.zegoggles.smssync;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.provider.CalendarContract;
import android.util.Log;

public class ICSCalendarApi implements ICalendarApi {

	@Override
	public void addEntry(Context context, int calId, Date when, int duration,
			String title, String description) {
		if (LOCAL_LOGV) {
			Log.v(TAG, String.format("addEntry(%d, %s, %d, %s, %s)",
					calId, when.toString(), duration, title, description));
		}

		final ContentValues values = new ContentValues();
		values.put(CalendarContract.Events.DTSTART, when.getTime());
		values.put(CalendarContract.Events.DTEND, when.getTime() + duration * 1000);
		values.put(CalendarContract.Events.TITLE, title);
		values.put(CalendarContract.Events.DESCRIPTION, description);
		values.put(CalendarContract.Events.CALENDAR_ID, calId);

		try {
			context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "could not add calendar entry", e);
		}
	}

	@Override
	public Map<String, String> getCalendars(Context context) {
		final Map<String, String> map = new LinkedHashMap<String, String>();

		Cursor c = null;
		try {
			c = context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
					new String[]{ CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME },
					null, null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC");

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
