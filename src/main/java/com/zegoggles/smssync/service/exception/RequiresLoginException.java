package com.zegoggles.smssync.service.exception;

import com.zegoggles.smssync.R;

public class RequiresLoginException extends Exception implements LocalizableException {
    @Override
    public int errorResourceId() {
        return R.string.err_sync_requires_login_info;
    }
}
