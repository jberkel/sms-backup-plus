/*
 * Copyright (c) 2011 Michael Scharfstein <smike@alum.mit.edu>
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
