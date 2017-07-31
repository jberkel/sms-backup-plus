package com.zegoggles.smssync.mail;

import com.fsck.k9.mail.store.StoreConfig;
import com.fsck.k9.mail.NetworkType;

class BackupStoreConfig implements StoreConfig {
    private static final String INBOX = "INBOX";
    private final String storeUri;

    BackupStoreConfig(String storeUri) {
        this.storeUri = storeUri;
    }

    @Override public String getStoreUri() {
        return storeUri;
    }
    @Override public String getTransportUri() { return null; }
    @Override public boolean subscribedFoldersOnly() {
        return false;
    }
    @Override public boolean useCompression(NetworkType type) {
        return false;
    }
    @Override public String getInboxFolderName() {
        return INBOX;
    }
    @Override public String getOutboxFolderName() {
        return null;
    }
    @Override public String getDraftsFolderName() {
        return null;
    }
    @Override public void setArchiveFolderName(String s) { }
    @Override public void setDraftsFolderName(String s) { }
    @Override public void setTrashFolderName(String s) { }
    @Override public void setSpamFolderName(String s) { }
    @Override public void setSentFolderName(String s) { }
    @Override public void setAutoExpandFolderName(String s) { }
    @Override public void setInboxFolderName(String s) { }
    @Override public int getMaximumAutoDownloadMessageSize() { return 0; }
    @Override public boolean allowRemoteSearch() { return false; }
    @Override public boolean isRemoteSearchFullText() {
        return false;
    }
    @Override public boolean isPushPollOnConnect() {
        return false;
    }
    @Override public int getDisplayCount() {
        return 0;
    }
    @Override public int getIdleRefreshMinutes() {
        return 0;
    }
}
