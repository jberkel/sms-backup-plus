package com.zegoggles.smssync.service.state;

public class RestoreStateChanged extends StateChanged {
    /** items currently restored */
    public final int currentRestoredCount;

    /** total number of items to be restored */
    public final int itemsToRestore;

    /** how many items did get actually restored */
    public final int actualRestoredCount;

    /** how many duplicates where detected after restore */
    public final int duplicateCount;

    public RestoreStateChanged(SmsSyncState state,
                              int currentRestoredCount,
                              int itemsToRestore,
                              int actualRestoredCount,
                              int duplicateCount,
                              Exception exception) {
        super(state, exception);
        this.currentRestoredCount = currentRestoredCount;
        this.actualRestoredCount = actualRestoredCount;
        this.itemsToRestore = itemsToRestore;
        this.duplicateCount = duplicateCount;
    }

    @Override
    public String toString() {
        return "RestoreStateChanged{" +
                "state=" + state +
                ", currentRestoredCount=" + currentRestoredCount +
                ", itemsToRestore=" + itemsToRestore +
                ", actualRestoredCount=" + actualRestoredCount +
                ", duplicateCount=" + duplicateCount +
                '}';
    }

    @Override
    public RestoreStateChanged transition(SmsSyncState newState, Exception exception) {
        return new RestoreStateChanged(newState, currentRestoredCount, itemsToRestore, actualRestoredCount, duplicateCount, exception);
    }
}
