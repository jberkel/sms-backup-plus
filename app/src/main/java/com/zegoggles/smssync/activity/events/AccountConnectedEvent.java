package com.zegoggles.smssync.activity.events;

public class AccountConnectedEvent {
    public final boolean connect;

    public AccountConnectedEvent(boolean connect) {
        this.connect = connect;
    }
}
