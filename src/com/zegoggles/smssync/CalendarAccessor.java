package com.zegoggles.smssync;

import java.util.Date;
import java.util.Map;

import android.content.Context;

public interface CalendarAccessor {
  /**
   * Adds an event to a calendar.
   *
   * @param context the context
   * @param calendarId the ID of the calendar to add to
   * @param when when the call was made
   * @param duration the duration of the event, in seconds
   * @param title a title for the calendar event
   * @param description a description for the calendar event
   */
  public void addEntry(
      Context context, int calendarId, Date when, int duration, String title,
      String description);

  /**
   * Finds a list of calendars available on the phone.
   *
   * @param context the context
   * @return a Map relating the id of the calendars found to their names.
   */
  public Map<String, String> getCalendars(Context context);
}
