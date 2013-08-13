package com.zegoggles.smssync.service;

import android.provider.CallLog;
import android.util.Log;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.mail.CallFormatter;
import com.zegoggles.smssync.mail.ConversionResult;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.mail.PersonLookup;
import com.zegoggles.smssync.mail.PersonRecord;

import java.util.Date;
import java.util.Map;

import static com.zegoggles.smssync.App.TAG;

class CalendarSyncer {
    private final CalendarAccessor calendarAccessor;
    private final long calendarId;
    private final PersonLookup personLookup;
    private final CallFormatter callFormatter;
    private boolean syncEnabled;

    CalendarSyncer(CalendarAccessor calendarAccessor,
                   long calendarId,
                   PersonLookup personLookup,
                   CallFormatter callFormatter) {
        this.calendarAccessor = calendarAccessor;
        this.calendarId = calendarId;
        this.personLookup = personLookup;
        this.callFormatter = callFormatter;
    }

    public void syncCalendar(ConversionResult result) {
        enableSync();

        if (result.type != DataType.CALLLOG) return;
        for (Map<String, String> m : result.getMapList()) {
            try {
                final int duration = Integer.parseInt(m.get(CallLog.Calls.DURATION));
                final int callType = Integer.parseInt(m.get(CallLog.Calls.TYPE));
                final String number = m.get(CallLog.Calls.NUMBER);
                final Date then = new Date(Long.valueOf(m.get(CallLog.Calls.DATE)));
                final PersonRecord record = personLookup.lookupPerson(number);

                // insert into calendar
                calendarAccessor.addEntry(
                        calendarId,
                        then,
                        duration,
                        callFormatter.callTypeString(callType, record.getName()),
                        callFormatter.formatForCalendar(callType, record.getNumber(), duration));
            } catch (NumberFormatException e) {
                Log.w(TAG, "error", e);
            }
        }
    }

    private void enableSync() {
        if (!syncEnabled) {
            calendarAccessor.enableSync(calendarId);
            syncEnabled = true;
        }
    }
}
