package com.zegoggles.smssync.preferences;

import android.content.Context;
import android.provider.CallLog;

public enum CallLogTypes {
    EVERYTHING,
    MISSED,
    INCOMING,
    OUTGOING,
    INCOMING_OUTGOING;

    private static final String CALLLOG_TYPES = "backup_calllog_types";

    static CallLogTypes getCallLogType(Context ctx) {
        return Preferences.getDefaultType(ctx, CALLLOG_TYPES, CallLogTypes.class, CallLogTypes.EVERYTHING);
    }

    public static boolean isTypeEnabled(Context ctx, int type) {
        switch (getCallLogType(ctx)) {
            case OUTGOING:
                return type == CallLog.Calls.OUTGOING_TYPE;
            case INCOMING:
                return type == CallLog.Calls.INCOMING_TYPE;
            case MISSED:
                return type == CallLog.Calls.MISSED_TYPE;
            case INCOMING_OUTGOING:
                return type != CallLog.Calls.MISSED_TYPE;
            default:
                return true;
        }
    }
}
