package com.zegoggles.smssync.activity.events;

public class PerformAction {
    public enum Actions {
        Backup,
        BackupSkip,
        Restore
    }

    public final Actions action;
    public final boolean confirm;

    public PerformAction(Actions action, boolean confirm) {
        this.action = action;
        this.confirm = confirm;
    }
}
