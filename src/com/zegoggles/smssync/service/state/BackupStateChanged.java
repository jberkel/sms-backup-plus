package com.zegoggles.smssync.service.state;

import com.zegoggles.smssync.service.BackupType;

public class BackupStateChanged extends StateChanged {
    public final int currentSyncedItems, itemsToSync;
    public final BackupType backupType;

    public BackupStateChanged(SmsSyncState state,
                              int currentSyncedItems,
                              int itemsToSync,
                              BackupType backupType,
                              Exception exception) {
        super(state, exception);
        this.currentSyncedItems = currentSyncedItems;
        this.itemsToSync = itemsToSync;
        this.backupType = backupType;
    }

    @Override
    public String toString() {
        return "BackupStateChanged{" +
                "state=" + state +
                ", currentSyncedItems=" + currentSyncedItems +
                ", itemsToSync=" + itemsToSync +
                ", backupType=" + backupType +
                '}';
    }

    @Override
    public BackupStateChanged transition(SmsSyncState newState, Exception exception) {
        return new BackupStateChanged(newState, currentSyncedItems, itemsToSync, backupType, exception);
    }
}
