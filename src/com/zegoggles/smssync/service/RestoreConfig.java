package com.zegoggles.smssync.service;

import com.zegoggles.smssync.mail.BackupImapStore;

public class RestoreConfig {
    final int tries;
    final boolean restoreSms;
    final boolean restoreCallLog;
    final boolean restoreOnlyStarred;
    final int maxRestore;
    final int currentRestoredItem;
    final BackupImapStore imapStore;

    public RestoreConfig(BackupImapStore imapStore,
                         int tries,
                         boolean restoreSms,
                         boolean restoreCallLog,
                         boolean restoreOnlyStarred,
                         int maxRestore,
                         int currentRestoredItem) {

        this.tries = tries;
        this.imapStore = imapStore;
        this.restoreSms = restoreSms;
        this.restoreCallLog = restoreCallLog;
        this.restoreOnlyStarred = restoreOnlyStarred;
        this.maxRestore = maxRestore;
        this.currentRestoredItem = currentRestoredItem;
    }

    public RestoreConfig retryWithStore(int currentItem, BackupImapStore backupImapStore) {
        return new RestoreConfig(
                backupImapStore,
                tries + 1,
                restoreSms,
                restoreCallLog,
                restoreOnlyStarred,
                maxRestore,
                currentItem
        );
    }

    @Override public String toString() {
        return "RestoreConfig{" +
                "currentTry=" + tries +
                ", restoreSms=" + restoreSms +
                ", restoreCallLog=" + restoreCallLog +
                ", restoreOnlyStarred=" + restoreOnlyStarred +
                ", maxRestore=" + maxRestore +
                ", currentRestoredItem=" + currentRestoredItem +
                ", imapStore=" + imapStore +
                '}';
    }
}
