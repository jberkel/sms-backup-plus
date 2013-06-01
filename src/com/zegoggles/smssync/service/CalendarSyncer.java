package com.zegoggles.smssync.service;

import android.content.Context;
import android.provider.CallLog;
import android.util.Log;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.mail.CursorToMessage;

import java.util.Date;
import java.util.Map;

import static com.zegoggles.smssync.App.TAG;

class CalendarSyncer {
    private final CalendarAccessor calendars;
    private final Context context;
    private final int calendarId;

    CalendarSyncer(Context context, CalendarAccessor calendars, int calendarId) {
        this.calendars = calendars;
        this.context = context;
        this.calendarId = calendarId;
    }

    public void syncCalendar(CursorToMessage converter, CursorToMessage.ConversionResult result) {
        if (result.type != CursorToMessage.DataType.CALLLOG) return;
        for (Map<String, String> m : result.mapList) {
            try {
                final int duration = Integer.parseInt(m.get(CallLog.Calls.DURATION));
                final int callType = Integer.parseInt(m.get(CallLog.Calls.TYPE));
                final String number = m.get(CallLog.Calls.NUMBER);
                final Date then = new Date(Long.valueOf(m.get(CallLog.Calls.DATE)));
                final CursorToMessage.PersonRecord record = converter.lookupPerson(number);

                StringBuilder description = new StringBuilder();
                description.append(context.getString(R.string.call_number_field, record.getNumber()))
                        .append(" (")
                        .append(converter.callTypeString(callType, null))
                        .append(" )")
                        .append("\n");

                if (callType != CallLog.Calls.MISSED_TYPE) {
                    description.append(context.getString(R.string.call_duration_field,
                            CursorToMessage.formattedDuration(duration)));
                }

                // insert into calendar
                calendars.addEntry(context,
                        calendarId, /* PrefStore.getCallLogCalendarId(context) */
                        then, duration,
                        converter.callTypeString(callType, record.getName()),
                        description.toString());
            } catch (NumberFormatException e) {
                Log.w(TAG, "error", e);
            }
        }
    }
}
