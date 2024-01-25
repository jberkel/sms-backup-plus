package com.zegoggles.smssync.service;

import com.zegoggles.smssync.mail.BackupImapStore;
import java.util.List;

public class RestoreConfig {
    final int tries;
    final boolean restoreSms;
    final boolean restoreCallLog;
    final boolean restoreOnlyStarred;
    final int maxRestore;
    final int currentRestoredItem;
    final List<BackupImapStore> imapStores;

    public RestoreConfig(List<BackupImapStore> imapStores,
                         int tries,
                         boolean restoreSms,
                         boolean restoreCallLog,
                         boolean restoreOnlyStarred,
                         int maxRestore,
                         int currentRestoredItem) {

        this.tries = tries;
        this.imapStores = imapStores;
        this.restoreSms = restoreSms;
        this.restoreCallLog = restoreCallLog;
        this.restoreOnlyStarred = restoreOnlyStarred;
        this.maxRestore = maxRestore;
        this.currentRestoredItem = currentRestoredItem;
    }

    public RestoreConfig retryWithStore(int currentItem, List<BackupImapStore> backupImapStores) {
        return new RestoreConfig(
                backupImapStores,
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
                GetImapString() +
                '}';
    }

    private String GetImapString() {
        String imapStoreString = "";
        Integer cnt = 0;
        for(BackupImapStore store: imapStores) {
            imapStoreString += ", imapStore" + cnt.toString() + "=" + store.toString();
            cnt++;
         }
         return imapStoreString;
    }
}
