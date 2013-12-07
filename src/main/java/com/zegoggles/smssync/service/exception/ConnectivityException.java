package com.zegoggles.smssync.service.exception;

/**
 * Exception connecting.
 */
public abstract class ConnectivityException extends Exception implements LocalizableException {
    public ConnectivityException(String msg) {
        super(msg);
    }
}
