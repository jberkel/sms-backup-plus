package com.zegoggles.smssync.mail;

import android.content.res.Resources;
import android.provider.CallLog;
import com.zegoggles.smssync.R;

import java.util.Locale;

public class CallFormatter {
    private final Resources resources;

    public CallFormatter(Resources resources) {
        this.resources = resources;
    }

    public String format(int callType, String number, int duration) {
        final StringBuilder text = new StringBuilder();

        if (callType != CallLog.Calls.MISSED_TYPE && callType != CallLog.Calls.REJECTED_TYPE) {
            text.append(duration)
                    .append("s")
                    .append(" (").append(formattedCallDuration(duration)).append(")")
                    .append("\n");
        }
        text.append(number)
            .append(" (").append(callTypeString(callType, null)).append(")");

        return text.toString();
    }


    public String formatForCalendar(int callType, String number, int duration) {
        StringBuilder description = new StringBuilder();
        description.append(resources.getString(R.string.call_number_field, number))
                .append(" (")
                .append(callTypeString(callType, null))
                .append(")")
                .append("\n");

        if (callType != CallLog.Calls.MISSED_TYPE) {
            description.append(resources.getString(R.string.call_duration_field,
                    formattedCallDuration(duration)));
        }
        return description.toString();
    }

    public String formattedCallDuration(int duration) {
        return String.format(Locale.ENGLISH, "%02d:%02d:%02d",
                duration / 3600,
                duration % 3600 / 60,
                duration % 3600 % 60);
    }

    public String callTypeString(int callType, String name) {
        if (name == null) {
            return resources.getString(mapCallType(callType));
        } else {
            return resources.getString(mapTextCallType(callType), name);
        }
    }

    private int mapCallType(int callType) {
        switch (callType) {
            case CallLog.Calls.OUTGOING_TYPE: return R.string.call_outgoing;
            case CallLog.Calls.INCOMING_TYPE: return R.string.call_incoming;
            case CallLog.Calls.MISSED_TYPE: return R.string.call_missed;
            case CallLog.Calls.REJECTED_TYPE: return R.string.call_rejected;
            case CallLog.Calls.VOICEMAIL_TYPE: return R.string.call_voicemail;
            default:
                return R.string.call_incoming;
        }
    }

    private int mapTextCallType(int callType) {
        switch (callType) {
            case CallLog.Calls.OUTGOING_TYPE: return R.string.call_outgoing_text;
            case CallLog.Calls.INCOMING_TYPE: return R.string.call_incoming_text;
            case CallLog.Calls.MISSED_TYPE: return R.string.call_missed_text;
            case CallLog.Calls.REJECTED_TYPE: return R.string.call_rejected_text;
            case CallLog.Calls.VOICEMAIL_TYPE: return R.string.call_voicemail_text;
            default:
                return R.string.call_incoming_text;
        }
    }
}
