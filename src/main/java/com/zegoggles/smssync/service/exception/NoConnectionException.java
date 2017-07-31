package com.zegoggles.smssync.service.exception;

import com.zegoggles.smssync.R;

public class NoConnectionException extends ConnectivityException {
    public NoConnectionException() {
        super(null);
    }

    @Override public int errorResourceId() {
        return R.string.error_no_connection;
    }
}
