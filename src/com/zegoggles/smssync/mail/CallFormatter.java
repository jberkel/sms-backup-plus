package com.zegoggles.smssync.mail;

import android.content.res.Resources;
import android.provider.CallLog;
import com.zegoggles.smssync.R;

import java.util.Locale;

public class CallFormatter {
    private final Resources mResources;

    public CallFormatter(Resources resources) {
        mResources = resources;
    }

    public String formattedCallDuration(int duration) {
        return String.format(Locale.ENGLISH, "%02d:%02d:%02d",
                duration / 3600,
                duration % 3600 / 60,
                duration % 3600 % 60);
    }

    public String callTypeString(int callType, String name) {
        if (name == null) {
            return mResources.getString(
                    callType == CallLog.Calls.OUTGOING_TYPE ? R.string.call_outgoing :
                            callType == CallLog.Calls.INCOMING_TYPE ? R.string.call_incoming :
                                    R.string.call_missed);
        } else {
            return mResources.getString(
                    callType == CallLog.Calls.OUTGOING_TYPE ? R.string.call_outgoing_text :
                            callType == CallLog.Calls.INCOMING_TYPE ? R.string.call_incoming_text :
                                    R.string.call_missed_text,
                    name);
        }
    }
}
