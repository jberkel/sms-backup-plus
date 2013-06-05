package com.zegoggles.smssync.service;

import com.zegoggles.smssync.contacts.ContactGroup;
import com.zegoggles.smssync.mail.BackupImapStore;

public class BackupConfig {
    public final BackupImapStore imap;
    public final boolean skip;
    public final int tries;

    public final int maxItemsPerSync;
    public final ContactGroup groupToBackup;
    public final int maxMessagePerRequest;

    public BackupConfig(BackupImapStore imap,
                        int tries,
                        boolean skip,
                        int maxItemsPerSync,
                        ContactGroup groupToBackup,
                        int maxMessagePerRequest) {
        this.imap = imap;
        this.skip = skip;
        this.tries = tries;
        this.maxItemsPerSync = maxItemsPerSync;
        this.groupToBackup = groupToBackup;
        this.maxMessagePerRequest = maxMessagePerRequest;
    }

    public BackupConfig retryWithStore(BackupImapStore store) {
        return new BackupConfig(store, tries + 1,
                skip,
                maxItemsPerSync,
                groupToBackup,
                maxMessagePerRequest);
    }
}
