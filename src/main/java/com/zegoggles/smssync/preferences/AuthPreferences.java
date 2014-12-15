package com.zegoggles.smssync.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import com.fsck.k9.mail.AuthType;
import com.zegoggles.smssync.auth.TokenRefresher;
import com.zegoggles.smssync.auth.XOAuthConsumer;
import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import static com.zegoggles.smssync.App.TAG;

public class AuthPreferences {
    private final Context context;
    private final SharedPreferences preferences;
    private final ServerPreferences serverPreferences;

    public AuthPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.preferences =  PreferenceManager.getDefaultSharedPreferences(this.context);
        this.serverPreferences = new ServerPreferences(this.context);
    }

    /**
     * Preference key containing the Google account username.
     */
    public static final String LOGIN_USER = "login_user";
    /**
     * Preference key containing the Google account password.
     */
    public static final String LOGIN_PASSWORD = "login_password";
    public static final String SERVER_AUTHENTICATION = "server_authentication";
    private static final String OAUTH_TOKEN = "oauth_token";
    private static final String OAUTH_TOKEN_SECRET = "oauth_token_secret";
    private static final String OAUTH_USER = "oauth_user";
    private static final String OAUTH2_USER = "oauth2_user";
    private static final String OAUTH2_TOKEN = "oauth2_token";

    /**
     * IMAP URI.
     *
     * This should be in the form of:
     * <ol>
     * <li><code>imap+ssl+://xoauth2:ENCODED_USERNAME:ENCODED_TOKEN@imap.gmail.com:993</code></li>
     * <li><code>imap+ssl+://xoauth:ENCODED_USERNAME:ENCODED_TOKEN@imap.gmail.com:993</code></li>
     * <li><code>imap+ssl+://ENCODED_USERNAME:ENCODED_PASSWOR@imap.gmail.com:993</code></li>
     * <li><code>imap://ENCODED_USERNAME:ENCODED_PASSWOR@imap.gmail.com:993</code></li>
     * <li><code>imap://ENCODED_USERNAME:ENCODED_PASSWOR@imap.gmail.com</code></li>
     * </ol>
     */
    private static final String IMAP_URI = "imap%s://%s:%s@%s";

    public XOAuthConsumer getOAuthConsumer() {
        return new XOAuthConsumer(
                getOauthUsername(),
                getOauthToken(),
                getOauthTokenSecret());
    }

    public String getOauth2Token() {
        return getCredentials().getString(OAUTH2_TOKEN, null);
    }

    public boolean hasOauthTokens() {
        return getOauthUsername() != null &&
                getOauthToken() != null &&
                getOauthTokenSecret() != null;
    }

    public boolean hasOAuth2Tokens() {
        return getOauth2Username() != null &&
                getOauth2Token() != null;
    }

    public String getUsername() {
        return preferences.getString(OAUTH_USER, getOauth2Username());
    }

    public void setOauthUsername(String s) {
        preferences.edit().putString(OAUTH_USER, s).commit();
    }

    public void setOauthTokens(String token, String secret) {
        getCredentials().edit()
                .putString(OAUTH_TOKEN, token)
                .putString(OAUTH_TOKEN_SECRET, secret)
                .commit();
    }

    public void setOauth2Token(String username, String token) {
        preferences.edit()
                .putString(OAUTH2_USER, username)
                .commit();

        getCredentials().edit()
                .putString(OAUTH2_TOKEN, token)
                .commit();
    }

   public void clearOauthData() {
        final String oauth2token = getOauth2Token();

        preferences.edit()
                .remove(OAUTH_USER)
                .remove(OAUTH2_USER)
                .commit();

        getCredentials().edit()
                .remove(OAUTH_TOKEN)
                .remove(OAUTH_TOKEN_SECRET)
                .remove(OAUTH2_TOKEN)
                .commit();

        if (!TextUtils.isEmpty(oauth2token)) {
            new TokenRefresher(context, this).invalidateToken(oauth2token);
        }
    }


    public void setImapPassword(String s) {
        getCredentials().edit().putString(LOGIN_PASSWORD, s).commit();
    }

    public boolean useXOAuth() {
        return getAuthMode() == AuthMode.XOAUTH && serverPreferences.isGmail();
    }

    public String getUserEmail() {
        switch (getAuthMode()) {
            case XOAUTH:
                return getUsername();
            default:
                return getImapUsername();
        }
    }

    public boolean isLoginInformationSet() {
        switch (getAuthMode()) {
            case PLAIN:
                return !TextUtils.isEmpty(getImapPassword()) &&
                        !TextUtils.isEmpty(getImapUsername());
            case XOAUTH:
                return hasOauthTokens() || hasOAuth2Tokens();
            default:
                return false;
        }
    }

    public String getStoreUri() {
        if (useXOAuth()) {
            if (hasOauthTokens()) {
                XOAuthConsumer consumer = getOAuthConsumer();
                return formatUri(AuthType.XOAUTH, consumer.getUsername(), consumer.generateXOAuthString());
            } else if (hasOAuth2Tokens()) {
                return formatUri(AuthType.XOAUTH2, getOauth2Username(), generateXOAuth2Token());
            } else {
                Log.w(TAG, "No valid xoauth1/2 tokens");
                return null;
            }
        } else {
            return String.format(IMAP_URI,
                    serverPreferences.getServerProtocol(),
                    encode(getImapUsername()),
                    encode(getImapPassword()).replace("+", "%20"),
                    serverPreferences.getServerAddress());
        }
    }

    private String formatUri(AuthType authType, String username, String password) {
        return String.format(IMAP_URI,
                ServerPreferences.Defaults.SERVER_PROTOCOL,
                authType.name().toUpperCase(Locale.US) + ":" + encode(username),
                encode(password),
                serverPreferences.getServerAddress());
    }

    private String getOauthTokenSecret() {
        return getCredentials().getString(OAUTH_TOKEN_SECRET, null);
    }

    private String getOauthToken() {
        return getCredentials().getString(OAUTH_TOKEN, null);
    }

    private String getOauthUsername() {
        return preferences.getString(OAUTH_USER, null);
    }

    private String getOauth2Username() {
        return preferences.getString(OAUTH2_USER, null);
    }

    private AuthMode getAuthMode() {
        return new Preferences(context).getDefaultType(SERVER_AUTHENTICATION, AuthMode.class, AuthMode.XOAUTH);
    }

    // All sensitive information is stored in a separate prefs file so we can
    // backup the rest without exposing sensitive data
    private SharedPreferences getCredentials() {
        return context.getSharedPreferences("credentials", Context.MODE_PRIVATE);
    }

    private String getImapUsername() {
        return preferences.getString(LOGIN_USER, null);
    }

    private String getImapPassword() {
        return getCredentials().getString(LOGIN_PASSWORD, null);
    }

    /**
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
    private String generateXOAuth2Token() {
        final String username = getOauth2Username();
        final String token = getOauth2Token();
        final String formatted = "user=" + username + "\001auth=Bearer " + token + "\001\001";
        try {
            return new String(Base64.encodeBase64(formatted.getBytes("UTF-8")), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String encode(String s) {
        try {
            return s == null ? "" : URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
