package com.zegoggles.smssync.service;

import com.fsck.k9.mail.MessagingException;
import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import org.jetbrains.annotations.NotNull;

public class BackupConfig {
    private final BackupImapStore imap;
    public final boolean skip;
    public final int tries;
    public final int maxItemsPerSync;
    public final ContactGroup groupToBackup;
    public final int maxMessagePerRequest;
    public final BackupType backupType;
    public final boolean debug;

    public BackupConfig(@NotNull BackupImapStore imap,
                        int tries,
                        boolean skip,
                        int maxItemsPerSync,
                        @NotNull ContactGroup groupToBackup,
                        int maxMessagePerRequest,
                        BackupType backupType,
                        boolean debug) {
        this.imap = imap;
        this.skip = skip;
        this.tries = tries;
        this.maxItemsPerSync = maxItemsPerSync;
        this.groupToBackup = groupToBackup;
        this.maxMessagePerRequest = maxMessagePerRequest;
        this.backupType = backupType;
        this.debug = debug;
    }

    public BackupConfig retryWithStore(BackupImapStore store) {
        return new BackupConfig(store, tries + 1,
                skip,
                maxItemsPerSync,
                groupToBackup,
                maxMessagePerRequest,
                backupType,
                debug);
    }

    public BackupImapStore.BackupFolder getFolder(DataType type) throws MessagingException {
        return imap.getFolder(type);
    }

    @Override public String toString() {
        return "BackupConfig{" +
                "imap=" + imap +
                ", skip=" + skip +
                ", tries=" + tries +
                ", maxItemsPerSync=" + maxItemsPerSync +
                ", groupToBackup=" + groupToBackup +
                ", maxMessagePerRequest=" + maxMessagePerRequest +
                ", backupType=" + backupType +
                ", debug=" + debug +
                '}';
    }
}
