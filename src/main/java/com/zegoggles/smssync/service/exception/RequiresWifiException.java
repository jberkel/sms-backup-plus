package com.zegoggles.smssync.service.exception;

import com.zegoggles.smssync.R;

public class RequiresWifiException extends ConnectivityException {
    public RequiresWifiException() {
        super(null);
    }

    @Override
    public int errorResourceId() {
        return R.string.error_wifi_only_no_connection;
    }
}
