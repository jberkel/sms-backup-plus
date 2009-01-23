/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.studer.smssync;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class PrefStore {
    /** Name of shared preference bundle containing settings for this service. */
    static final String SHARED_PREFS_NAME = "syncprefs";
    
    /**
     * Preference key containing the maximum date of messages that were
     * successfully synced.
     */
    static final String PREF_MAX_SYNCED_DATE = "max_synced_date";

    /** Preference key containing the Google account username. */
    static final String PREF_LOGIN_USER = "login_user";

    /** Preference key containing the Google account password. */
    static final String PREF_LOGIN_PASSWORD = "login_password";

    /** Preference key containing a UID used for the threading reference header. */
    static final String PREF_REFERENECE_UID = "reference_uid";
    
    /** Preference key containing the IMAP folder name where SMS should be backed up to. */
    static final String PREF_IMAP_FOLDER = "imap_folder";
    
    /** Preference key for storing whether to enable auto sync or not. */
    static final String PREF_ENABLE_AUTO_SYNC = "enable_auto_sync";
    
    /** Preference key for the timeout between an SMS is received and the scheduled sync. */
    static final String PREF_INCOMING_TIMEOUT_SECONDS = "incoming_timeout_seconds";
    
    /** Preference key for the interval between backup of outgoing SMS. */
    static final String PREF_REGULAR_TIMEOUT_SECONDS = "regular_timeout_seconds";
    
    /** Preference for storing the time of the last sync. */
    static final String PREF_LAST_SYNC = "last_sync";
    
    /** Default value for {@link PrefStore#PREF_MAX_SYNCED_DATE}. */
    static final long DEFAULT_MAX_SYNCED_DATE = -1;
    
    /** Default value for {@link PrefStore#PREF_IMAP_FOLDER}. */
    static final String DEFAULT_IMAP_FOLDER = "SMS";
    
    /** Default value for {@link PrefStore#PREF_ENABLE_AUTO_SYNC}. */
    static final boolean DEFAULT_ENABLE_AUTO_SYNC = true;
    
    /** Default value for {@link PrefStore#PREF_INCOMING_TIMEOUT_SECONDS}. */
    static final int DEFAULT_INCOMING_TIMEOUT_SECONDS = 20;
    
    /** Default value for {@link PrefStore#PREF_REGULAR_TIMEOUT_SECONDS}. */
    static final int DEFAULT_REGULAR_TIMEOUT_SECONDS = 30 * 60; // 30 minutes
    
    /** Default value for {@link #PREF_LAST_SYNC}. */
    static final long DEFAULT_LAST_SYNC = -1;
    
    static SharedPreferences getSharedPreferences(Context ctx) {
        return ctx.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    static long getMaxSyncedDate(Context ctx) {
        return getSharedPreferences(ctx).getLong(PREF_MAX_SYNCED_DATE,
                DEFAULT_MAX_SYNCED_DATE);
    }
    
    static boolean isMaxSyncedDateSet(Context ctx) {
        return getSharedPreferences(ctx).contains(PREF_MAX_SYNCED_DATE);
    }
    
    static void setMaxSyncedDate(Context ctx, long maxSyncedDate) {
        Editor editor = getSharedPreferences(ctx).edit();
        editor.putLong(PREF_MAX_SYNCED_DATE, maxSyncedDate);
        editor.commit();
    }
    
    static String getLoginUsername(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_LOGIN_USER, null);
    }
    
    static String getLoginPassword(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_LOGIN_PASSWORD, null);
    }
    
    public static boolean isLoginUsernameSet(Context ctx) {
        return getLoginUsername(ctx) != null;
    }
    
    static boolean isLoginInformationSet(Context ctx) {
        return isLoginUsernameSet(ctx) && getLoginPassword(ctx) != null;
    }
    
    static String getReferenceUid(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_REFERENECE_UID, null);
    }
    
    static void setReferenceUid(Context ctx, String referenceUid) {
        Editor editor = getSharedPreferences(ctx).edit();
        editor.putString(PREF_REFERENECE_UID, referenceUid);
        editor.commit();
    }
    
    static String getImapFolder(Context ctx) {
        return getSharedPreferences(ctx).getString(PREF_IMAP_FOLDER, DEFAULT_IMAP_FOLDER);
    }
    
    static boolean isImapFolderSet(Context ctx) {
        return getSharedPreferences(ctx).contains(PREF_IMAP_FOLDER);
    }
    
    /**
     * Returns whether an IMAP folder is valid. This is the case if the name
     * only contains unaccented latin letters <code>[a-zA-Z]</code>.
     */
    static boolean isValidImapFolder(String imapFolder) {
        for (int i = 0; i < imapFolder.length(); i++) {
            char currChar = imapFolder.charAt(i);
            if (!((currChar >= 'a' && currChar <= 'z')
                    || (currChar >= 'A' && currChar <= 'Z'))) {
                return false;
            }
        }
        return true;
    }
    
    static void setImapFolder(Context ctx, String imapFolder) {
        Editor editor = getSharedPreferences(ctx).edit();
        editor.putString(PREF_IMAP_FOLDER, imapFolder);
        editor.commit();
    }
    
    static boolean isEnableAutoSync(Context ctx) {
        return getSharedPreferences(ctx).getBoolean(PREF_ENABLE_AUTO_SYNC,
                DEFAULT_ENABLE_AUTO_SYNC);
    }
    
    static boolean isEnableAutoSyncSet(Context ctx) {
        return getSharedPreferences(ctx).contains(PREF_ENABLE_AUTO_SYNC);
    }
    
    static void setEnableAutoSync(Context ctx, boolean enableAutoSync) {
        Editor editor = getSharedPreferences(ctx).edit();
        editor.putBoolean(PREF_ENABLE_AUTO_SYNC, enableAutoSync);
        editor.commit();
    }
    
    static int getIncomingTimeoutSecs(Context ctx) {
       return getSharedPreferences(ctx).getInt(PREF_INCOMING_TIMEOUT_SECONDS,
               DEFAULT_INCOMING_TIMEOUT_SECONDS);
    }
    
    static int getRegularTimeoutSecs(Context ctx) {
        return getSharedPreferences(ctx).getInt(PREF_REGULAR_TIMEOUT_SECONDS,
                DEFAULT_REGULAR_TIMEOUT_SECONDS); 
    }
    
    static long getLastSync(Context ctx) {
        return getSharedPreferences(ctx).getLong(PREF_LAST_SYNC, DEFAULT_LAST_SYNC);
    }
    
    static void setLastSync(Context ctx) {
        Editor editor = getSharedPreferences(ctx).edit();
        editor.putLong(PREF_LAST_SYNC, System.currentTimeMillis());
        editor.commit();
    }
    
    static boolean isFirstSync(Context ctx) {
        return getLastSync(ctx) == DEFAULT_LAST_SYNC;
    }
    
    static void clearSyncData(Context ctx) {
        Editor editor = getSharedPreferences(ctx).edit();
        editor.remove(PREF_LOGIN_PASSWORD);
        editor.remove(PREF_MAX_SYNCED_DATE);
        editor.remove(PREF_LAST_SYNC);
        editor.commit();
    }
}
