package com.zegoggles.smssync.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import com.fsck.k9.mail.AuthType;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.auth.TokenRefresher;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import static android.util.Base64.NO_WRAP;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.preferences.Preferences.getDefaultType;

public class AuthPreferences {
    private static final String UTF_8 = "UTF-8";
    private final Context context;
    private final SharedPreferences preferences;
    private SharedPreferences credentials;

    public static final String SERVER_AUTHENTICATION = "server_authentication";

    private static final String OAUTH2_USER = "oauth2_user";
    private static final String OAUTH2_TOKEN = "oauth2_token";
    private static final String OAUTH2_REFRESH_TOKEN = "oauth2_refresh_token";

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
     * <li><code>imap+ssl+://XOAUTH2:ENCODED_USERNAME:ENCODED_TOKEN@imap.gmail.com:993</code></li>
     * <li><code>imap+ssl+://XOAUTH:ENCODED_USERNAME:ENCODED_TOKEN@imap.gmail.com:993</code></li>
     * <li><code>imap+ssl+://PLAIN:ENCODED_USERNAME:ENCODED_PASSWOR@imap.gmail.com:993</code></li>
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

    public String getOauth2Token() {
        return getCredentials().getString(OAUTH2_TOKEN, null);
    }

    public String getOauth2RefreshToken() {
        return getCredentials().getString(OAUTH2_REFRESH_TOKEN, null);
    }

    public boolean hasOAuth2Tokens() {
        return getOauth2Username() != null &&
                getOauth2Token() != null;
    }

    public void setOauth2Token(String username, String accessToken, String refreshToken) {
        preferences.edit()
                .putString(OAUTH2_USER, username)
                .commit();

        getCredentials().edit()
                .putString(OAUTH2_TOKEN, accessToken)
                .commit();
        getCredentials().edit()
                .putString(OAUTH2_REFRESH_TOKEN, refreshToken)
                .commit();
    }

   public void clearOauth2Data() {
        final String oauth2token = getOauth2Token();

        preferences.edit()
                .remove(OAUTH2_USER)
                .commit();

        getCredentials().edit()
                .remove(OAUTH2_TOKEN)
                .remove(OAUTH2_REFRESH_TOKEN)
                .commit();

        if (!TextUtils.isEmpty(oauth2token)) {
            new TokenRefresher(context, new OAuth2Client(getOAuth2ClientId()), this).invalidateToken(oauth2token);
        }
    }

    public String getOAuth2ClientId() {
        return context.getString(R.string.oauth2_client_id);
    }

    public void setImapPassword(String s) {
        getCredentials().edit().putString(IMAP_PASSWORD, s).commit();
    }

    public void setImapUser(String s) {
        preferences.edit().putString(IMAP_USER, s).commit();
    }

    @SuppressWarnings("deprecation")
    public boolean useXOAuth() {
        return getAuthMode() == AuthMode.XOAUTH;
    }

    public boolean usePlain() {
        return getAuthMode() == AuthMode.PLAIN;
    }

    public String getUserEmail() {
        if (getAuthMode() == AuthMode.PLAIN) {
            return getImapUsername();
        } else {
            return getOauth2Username();
        }
    }

    public String toString() {
        if (DEFAULT_SERVER_ADDRESS.equals(getServername())) {
            return getImapUsername() + " (Gmail)";
        } else {
            return getImapUsername() + "@" + getServername();
        }
    }

    @SuppressWarnings("deprecation")
    public boolean isLoginInformationSet() {
        switch (getAuthMode()) {
            case PLAIN:
                return !TextUtils.isEmpty(getImapPassword()) &&
                       !TextUtils.isEmpty(getImapUsername()) &&
                       !TextUtils.isEmpty(getServerAddress());
            case XOAUTH:
                return hasOAuth2Tokens();
            default:
                return false;
        }
    }

    public String getStoreUri() {
        if (useXOAuth()) {
            if (hasOAuth2Tokens()) {
                return formatUri(
                    AuthType.XOAUTH2,
                        DEFAULT_SERVER_PROTOCOL,
                        getOauth2Username(),
                        generateXOAuth2Token(),
                        DEFAULT_SERVER_ADDRESS);
            } else {
                Log.w(TAG, "No valid xoauth2 tokens");
                return null;
            }
        } else {
            return formatUri(AuthType.PLAIN,
                getServerProtocol(),
                getImapUsername(),
                getImapPassword(),
                getServerAddress());
        }
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

    public String getOauth2Username() {
        return preferences.getString(OAUTH2_USER, null);
    }

    private AuthMode getAuthMode() {
        return getDefaultType(preferences, SERVER_AUTHENTICATION, AuthMode.class, AuthMode.PLAIN);
    }

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

    /**
     * TODO: this should probably be handled in K9
     *
     * <p>
     * The SASL XOAUTH2 initial client response has the following format:
     * </p>
     * <code>base64("user="{User}"^Aauth=Bearer "{Access Token}"^A^A")</code>
     * <p>
     * For example, before base64-encoding, the initial client response might look like this:
     * </p>
     * <code>user=someuser@example.com^Aauth=Bearer vF9dft4qmTc2Nvb3RlckBhdHRhdmlzdGEuY29tCg==^A^A</code>
     * <p/>
     * <em>Note:</em> ^A represents a Control+A (\001).
     *
     * @see <a href="https://developers.google.com/google-apps/gmail/xoauth2_protocol#the_sasl_xoauth2_mechanism">
     *      The SASL XOAUTH2 Mechanism</a>
     */
    private @NonNull String generateXOAuth2Token() {
        final String username = getOauth2Username();
        final String token = getOauth2Token();
        final String formatted = "user=" + username + "\001auth=Bearer " + token + "\001\001";
        try {
            return Base64.encodeToString(formatted.getBytes(UTF_8), NO_WRAP);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String encode(String s) {
        try {
            return s == null ? "" : URLEncoder.encode(s, UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    void migrate() {
        if (useXOAuth()) {
            return;
        }
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
