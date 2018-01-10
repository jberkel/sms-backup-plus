package com.zegoggles.smssync.preferences;

class Defaults {
    /**
     * Default value for {@link Preferences.Keys#MAIL_SUBJECT_PREFIX}.
     */
    public static final boolean MAIL_SUBJECT_PREFIX = false;
    /**
     * Default value for {@link Preferences.Keys#ENABLE_AUTO_BACKUP}.
     */
    public static final boolean ENABLE_AUTO_BACKUP = false;
    /**
     * Default value for {@link Preferences.Keys#INCOMING_TIMEOUT_SECONDS}.
     */
    public static final int INCOMING_TIMEOUT_SECONDS = 60;
    /**
     * Default value for {@link Preferences.Keys#REGULAR_TIMEOUT_SECONDS}.
     */
    public static final int REGULAR_TIMEOUT_SECONDS = 2 * 60 * 60; // 2h
    /**
     * Default value for {@link Preferences.Keys#MAX_ITEMS_PER_SYNC}.
     */
    public static final int MAX_ITEMS_PER_SYNC = -1;
    public static final int MAX_ITEMS_PER_RESTORE = -1;
    public static final boolean MARK_AS_READ_ON_RESTORE = true;

    private Defaults() {}
}
