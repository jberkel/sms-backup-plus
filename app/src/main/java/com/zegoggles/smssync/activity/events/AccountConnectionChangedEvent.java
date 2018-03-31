package com.zegoggles.smssync.activity.events;

public class AccountConnectionChangedEvent {
    public final boolean connected;

    public AccountConnectionChangedEvent(boolean connect) {
        this.connected = connect;
    }
}
