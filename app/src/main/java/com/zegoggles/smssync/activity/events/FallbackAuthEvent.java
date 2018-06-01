package com.zegoggles.smssync.activity.events;

public class FallbackAuthEvent {
    public final boolean showDialog;

    public FallbackAuthEvent(boolean showDialog) {
        this.showDialog = showDialog;
    }
}
