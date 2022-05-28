package com.zegoggles.smssync.service;

import androidx.annotation.NonNull;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;

import java.util.EnumSet;
import java.util.List;

public class BackupConfig {
    public final List<BackupImapStore> imapStores;
    public final int currentTry;
    public final int maxItemsPerSync;
    public final ContactGroup groupToBackup;
    public final BackupType backupType;
    public final boolean debug;
    public final EnumSet<DataType> typesToBackup;

    BackupConfig(@NonNull List<BackupImapStore> imapStores,
                 int currentTry,
                 int maxItemsPerSync,
                 @NonNull ContactGroup groupToBackup,
                 @NonNull BackupType backupType,
                 @NonNull EnumSet<DataType> typesToBackup,
                 boolean debug) {
        if (imapStores == null) throw new IllegalArgumentException("need imapstores");
        if (typesToBackup == null || typesToBackup.isEmpty()) throw new IllegalArgumentException("need to specify types to backup");
        if (currentTry < 0) throw new IllegalArgumentException("currentTry < 0");

        this.imapStores = imapStores;
        this.currentTry = currentTry;
        this.maxItemsPerSync = maxItemsPerSync;
        this.groupToBackup = groupToBackup;
        this.backupType = backupType;
        this.debug = debug;
        this.typesToBackup = typesToBackup;
    }

    public BackupConfig retryWithStore(List<BackupImapStore> stores) {
        return new BackupConfig(stores, currentTry + 1,
                maxItemsPerSync,
                groupToBackup,
                backupType,
                typesToBackup, debug);
    }


    @Override public String toString() {
        return "BackupConfig{" +
                GetImapString() +
                "currentTry=" + currentTry +
                ", maxItemsPerSync=" + maxItemsPerSync +
                ", groupToBackup=" + groupToBackup +
                ", backupType=" + backupType +
                ", debug=" + debug +
                ", typesToBackup=" + typesToBackup +
                '}';
    }

    private String GetImapString() {
        String imapStoreString = "";
        Integer cnt = 0;
        for(BackupImapStore store: imapStores) {
            imapStoreString += "imap" + cnt.toString() + "=" + store.toString() + ", ";
            cnt++;
         }
         return imapStoreString;
    }
}
