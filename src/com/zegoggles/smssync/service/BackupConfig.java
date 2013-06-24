package com.zegoggles.smssync.service;

import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.BackupImapStore;
import org.jetbrains.annotations.NotNull;

public class BackupConfig {
    public final BackupImapStore imap;
    public final boolean skip;
    public final int tries;

    public final int maxItemsPerSync;
    public final ContactGroup groupToBackup;
    public final int maxMessagePerRequest;
    public final BackupType backupType;

    public BackupConfig(@NotNull BackupImapStore imap,
                        int tries,
                        boolean skip,
                        int maxItemsPerSync,
                        @NotNull ContactGroup groupToBackup,
                        int maxMessagePerRequest,
                        BackupType backupType) {
        this.imap = imap;
        this.skip = skip;
        this.tries = tries;
        this.maxItemsPerSync = maxItemsPerSync;
        this.groupToBackup = groupToBackup;
        this.maxMessagePerRequest = maxMessagePerRequest;
        this.backupType = backupType;
    }

    public BackupConfig retryWithStore(BackupImapStore store) {
        return new BackupConfig(store, tries + 1,
                skip,
                maxItemsPerSync,
                groupToBackup,
                maxMessagePerRequest,
                backupType);
    }
}
