package com.zegoggles.smssync.service.exception;

import com.zegoggles.smssync.R;

public class SmsProviderNotWritableException extends Exception implements LocalizableException {
    @Override public int errorResourceId() {
        return R.string.error_sms_provider_not_writable;
    }
}
