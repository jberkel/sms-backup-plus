package com.zegoggles.smssync.service;

/**
 * Exception connecting.
 */
@SuppressWarnings("serial")
public class ConnectivityErrorException extends Exception {
    public ConnectivityErrorException(String msg) {
        super(msg);
    }
}
