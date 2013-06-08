package com.zegoggles.smssync.preferences;

import android.content.Context;

import static com.zegoggles.smssync.preferences.Preferences.prefs;

public class ServerPreferences {
    /**
     * Preference key containing the server address
     */
    private static final String SERVER_ADDRESS = "server_address";
    /**
     * Preference key containing the server protocol
     */
    private static final String SERVER_PROTOCOL = "server_protocol";


    static String getServerAddress(Context ctx) {
        return prefs(ctx).getString(SERVER_ADDRESS, Defaults.SERVER_ADDRESS);
    }

    static String getServerProtocol(Context ctx) {
        return prefs(ctx).getString(SERVER_PROTOCOL, Defaults.SERVER_PROTOCOL);
    }

    static boolean isGmail(Context ctx) {
        return "imap.gmail.com:993".equalsIgnoreCase(getServerAddress(ctx));
    }

    static class Defaults {
        /**
         * Default value for {@link ServerPreferences#SERVER_ADDRESS}.
         */
        private static final String SERVER_ADDRESS = "imap.gmail.com:993";
        /**
         * Default value for {@link ServerPreferences#SERVER_PROTOCOL}.
         */
        public static final String SERVER_PROTOCOL = "+ssl+";
    }
}
