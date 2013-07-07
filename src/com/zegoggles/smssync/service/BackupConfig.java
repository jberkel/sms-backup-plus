package com.zegoggles.smssync.service;

import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class BackupConfig {
    private final BackupImapStore imap;
    public final boolean skip;
    public final int currentTry;
    public final int maxItemsPerSync;
    public final ContactGroup groupToBackup;
    public final int maxMessagePerRequest;
    public final BackupType backupType;
    public final boolean debug;
    public final EnumSet<DataType> typesToBackup;

    public BackupConfig(@NotNull BackupImapStore imap,
                        int currentTry,
                        boolean skip,
                        int maxItemsPerSync,
                        @NotNull ContactGroup groupToBackup,
                        int maxMessagePerRequest,
                        @NotNull BackupType backupType,
                        @NotNull EnumSet<DataType> typesToBackup,
                        boolean debug) {
        if (typesToBackup == null || typesToBackup.isEmpty()) throw new IllegalArgumentException("need to specify types to backup");
        if (currentTry < 0) throw new IllegalArgumentException("currentTry < 0");

        this.imap = imap;
        this.skip = skip;
        this.currentTry = currentTry;
        this.maxItemsPerSync = maxItemsPerSync;
        this.groupToBackup = groupToBackup;
        this.maxMessagePerRequest = maxMessagePerRequest;
        this.backupType = backupType;
        this.debug = debug;
        this.typesToBackup = typesToBackup;
    }

    public BackupConfig retryWithStore(BackupImapStore store) {
        return new BackupConfig(store, currentTry + 1,
                skip,
                maxItemsPerSync,
                groupToBackup,
                maxMessagePerRequest,
                backupType,
                typesToBackup, debug);
    }

    public BackupImapStore.BackupFolder getFolder(DataType type) throws MessagingException {
        return imap.getFolder(type);
    }

    @Override public String toString() {
        return "BackupConfig{" +
                "imap=" + imap +
                ", skip=" + skip +
                ", currentTry=" + currentTry +
                ", maxItemsPerSync=" + maxItemsPerSync +
                ", groupToBackup=" + groupToBackup +
                ", maxMessagePerRequest=" + maxMessagePerRequest +
                ", backupType=" + backupType +
                ", debug=" + debug +
                '}';
    }
}
