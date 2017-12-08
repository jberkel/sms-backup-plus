package com.zegoggles.smssync.preferences;

import android.provider.CallLog;

public enum CallLogTypes {
    EVERYTHING,
    MISSED,
    INCOMING,
    OUTGOING,
    INCOMING_OUTGOING;

    public boolean isTypeEnabled(int type) {
        switch (this) {
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
