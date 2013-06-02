package com.zegoggles.smssync.mail;

import com.zegoggles.smssync.R;

public enum DataType {
    MMS(R.string.mms),
    SMS(R.string.sms),
    CALLLOG(R.string.calllog),
    WHATSAPP(R.string.whatsapp);

    public final int resId;

    private DataType(int resId) {
        this.resId = resId;
    }
}
