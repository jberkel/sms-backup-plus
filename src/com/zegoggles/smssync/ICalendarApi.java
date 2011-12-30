package com.zegoggles.smssync;

import java.util.Date;
import java.util.Map;

import android.content.Context;

public interface ICalendarApi {
	Map<String, String> getCalendars(Context context);
	void addEntry(Context context, int calId, Date when, int duration,
      String title, String description);
}
