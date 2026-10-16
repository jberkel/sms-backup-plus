package sms.backup.plus.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import android.text.TextUtils;
import com.fsck.k9.mail.AuthType;
import sms.backup.plus.R;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import static sms.backup.plus.App.TAG;
import static sms.backup.plus.preferences.Preferences.getDefaultType;

public class AuthPreferences {
    private static final String UTF_8 = "UTF-8";
    private final Context context;
    private final SharedPreferences preferences;
    private SharedPreferences credentials;

    public static final String SERVER_AUTHENTICATION = "server_authentication";


    public static final String IMAP_USER = "login_user";
    public static final String IMAP_PASSWORD = "login_password";

    /**
     * Preference key containing the server address
     */
    public static final String SERVER_ADDRESS = "server_address";
    /**
     * Preference key containing the server protocol
     */
    private static final String SERVER_PROTOCOL = "server_protocol";

    private static final String SERVER_TRUST_ALL_CERTIFICATES = "server_trust_all_certificates";

    /**
     * IMAP URI.
     *
     * This should be in the form of:
     * <ol>
     *     *     * <li><code>imap+ssl+://PLAIN:ENCODED_USERNAME:ENCODED_PASSWOR@imap.gmail.com:993</code></li>
     * <li><code>imap://PLAIN:ENCODED_USERNAME:ENCODED_PASSWOR@imap.gmail.com:993</code></li>
     * <li><code>imap://PLAIN:ENCODED_USERNAME:ENCODED_PASSWOR@imap.gmail.com</code></li>
     * </ol>
     */
    private static final String IMAP_URI = "imap%s://%s:%s:%s@%s";

    private static final String DEFAULT_SERVER_ADDRESS = "imap.gmail.com:993";
    private static final String DEFAULT_SERVER_PROTOCOL = "+ssl+";

    public AuthPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setImapPassword(String s) {
        getCredentials().edit().putString(IMAP_PASSWORD, s).commit();
    }

    public void setImapUser(String s) {
        preferences.edit().putString(IMAP_USER, s).commit();
    }

    public boolean usePlain() { return true; }

    public String getUserEmail() { return getImapUsername(); }

    public String toString() {
        if (DEFAULT_SERVER_ADDRESS.equals(getServername())) {
            return getImapUsername() + " (Gmail)";
        } else {
            return getImapUsername() + "@" + getServername();
        }
    }

    public boolean isLoginInformationSet() {
        return !TextUtils.isEmpty(getImapPassword()) &&
               !TextUtils.isEmpty(getImapUsername()) &&
               !TextUtils.isEmpty(getServerAddress());
    }

    public String getStoreUri() {
        return formatUri(AuthType.PLAIN, getServerProtocol(), getImapUsername(), getImapPassword(), getServerAddress());
    }

    private String getServerAddress() {
        return preferences.getString(SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS);
    }

    private String getServerProtocol() {
        return preferences.getString(SERVER_PROTOCOL, DEFAULT_SERVER_PROTOCOL);
    }

    public boolean isTrustAllCertificates() {
        return preferences.getBoolean(SERVER_TRUST_ALL_CERTIFICATES, false);
    }

    private String formatUri(AuthType authType, String serverProtocol, String username, String password, String serverAddress) {
        return String.format(IMAP_URI,
            serverProtocol,
            authType.name().toUpperCase(Locale.US),
            // NB: there's a bug in K9mail-library which requires double-encoding of uris
            // https://github.com/k9mail/k-9/commit/b0d401c3b73c6b57402dc81d3cfd6488a71a1b98
            encode(encode(username)),
            encode(encode(password)),
            serverAddress);
    }

    private AuthMode getAuthMode() { return AuthMode.PLAIN; }

    // All sensitive information is stored in a separate prefs file so we can
    // backup the rest without exposing sensitive data
    private SharedPreferences getCredentials() {
        if (credentials == null) {
            credentials = context.getSharedPreferences("credentials", Context.MODE_PRIVATE);
        }
        return credentials;
    }

    public String getServername() {
        return preferences.getString(SERVER_ADDRESS, null);
    }

    public String getImapUsername() {
        return preferences.getString(IMAP_USER, null);
    }

    private String getImapPassword() {
        return getCredentials().getString(IMAP_PASSWORD, null);
    }

    private static String encode(String s) {
        try {
            return s == null ? "" : URLEncoder.encode(s, UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    void migrate() {
        // convert deprecated authentication methods
        if ("+ssl".equals(getServerProtocol()) ||
            "+tls".equals(getServerProtocol())) {
            preferences.edit()
                .putBoolean(SERVER_TRUST_ALL_CERTIFICATES, true)
                .putString(SERVER_PROTOCOL, getServerProtocol()+"+")
                .commit();
        }
    }
}
