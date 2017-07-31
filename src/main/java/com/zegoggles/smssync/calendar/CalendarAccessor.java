package com.zegoggles.smssync.calendar;

import android.content.ContentResolver;
import android.os.Build;

import java.util.Date;
import java.util.Map;

public interface CalendarAccessor {

    /**
     * Enables syncing for this calendar id.
     * @param calendarId the calendar id to enable syncing for.
     * @return if sync was enabled
     */
    public boolean enableSync(long calendarId);

    /**
     * Adds an event to a calendar.
     *
     * @param calendarId  the ID of the calendar to add to
     * @param when        when the call was made
     * @param duration    the duration of the event, in seconds
     * @param title       a title for the calendar event
     * @param description a description for the calendar event
     * @return if the event was added
     */
    public boolean addEntry(long calendarId, Date when, int duration, String title, String description);

    /**
     * Finds a list of calendars available on the phone.
     *
     *
     * @return a Map relating the id of the calendars found to their names.
     */
    public Map<String, String> getCalendars();


    public static class Get {
        private static CalendarAccessor sCalendarAccessor;

        private Get() {}

        public static CalendarAccessor instance(ContentResolver resolver) {
            final  int sdkVersion = Build.VERSION.SDK_INT;
            if (sCalendarAccessor == null) {
                try {
                    if (sdkVersion < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        sCalendarAccessor = new CalendarAccessorPre40(resolver);
                    } else {
                        sCalendarAccessor = new CalendarAccessorPost40(resolver);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return sCalendarAccessor;
        }
    }
}
