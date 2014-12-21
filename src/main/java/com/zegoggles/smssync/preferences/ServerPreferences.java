package com.zegoggles.smssync.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class ServerPreferences {
    private final SharedPreferences preferences;

    public ServerPreferences(Context context) {
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Preference key containing the server address
     */
    private static final String SERVER_ADDRESS = "server_address";
    /**
     * Preference key containing the server protocol
     */
    private static final String SERVER_PROTOCOL = "server_protocol";


    String getServerAddress() {
        return preferences.getString(SERVER_ADDRESS, Defaults.SERVER_ADDRESS);
    }

    String getServerProtocol() {
        return preferences.getString(SERVER_PROTOCOL, Defaults.SERVER_PROTOCOL);
    }

    boolean isGmail() {
        return Defaults.SERVER_ADDRESS.equalsIgnoreCase(getServerAddress());
    }

    static class Defaults {
        /**
         * Default value for {@link ServerPreferences#SERVER_ADDRESS}.
         */
        public static final String SERVER_ADDRESS = "imap.gmail.com:993";
        /**
         * Default value for {@link ServerPreferences#SERVER_PROTOCOL}.
         */
        public static final String SERVER_PROTOCOL = "+ssl+";
    }
}
