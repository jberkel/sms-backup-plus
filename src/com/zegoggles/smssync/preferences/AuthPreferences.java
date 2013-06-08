package com.zegoggles.smssync.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity;
import com.zegoggles.smssync.auth.XOAuthConsumer;
import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.preferences.Preferences.prefs;

public class AuthPreferences {
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

    public static XOAuthConsumer getOAuthConsumer(Context ctx) {
        return new XOAuthConsumer(
                getOauthUsername(ctx),
                getOauthToken(ctx),
                getOauthTokenSecret(ctx));
    }

    public static String getOauth2Token(Context ctx) {
        return getCredentials(ctx).getString(OAUTH2_TOKEN, null);
    }

    public static boolean hasOauthTokens(Context ctx) {
        return getOauthUsername(ctx) != null &&
                getOauthToken(ctx) != null &&
                getOauthTokenSecret(ctx) != null;
    }

    public static boolean hasOAuth2Tokens(Context ctx) {
        return getOauth2Username(ctx) != null &&
                getOauth2Token(ctx) != null;
    }

    public static String getUsername(Context ctx) {
        return prefs(ctx).getString(OAUTH_USER, getOauth2Username(ctx));
    }

    public static void setOauthUsername(Context ctx, String s) {
        prefs(ctx).edit().putString(OAUTH_USER, s).commit();
    }

    public static void setOauthTokens(Context ctx, String token, String secret) {
        getCredentials(ctx).edit()
                .putString(OAUTH_TOKEN, token)
                .putString(OAUTH_TOKEN_SECRET, secret)
                .commit();
    }

    public static void setOauth2Token(Context ctx, String username, String token) {
        prefs(ctx).edit()
                .putString(OAUTH2_USER, username)
                .commit();

        getCredentials(ctx).edit()
                .putString(OAUTH2_TOKEN, token)
                .commit();
    }

   public static void clearOauthData(Context ctx) {
        final String oauth2token = getOauth2Token(ctx);

        prefs(ctx).edit()
                .remove(OAUTH_USER)
                .remove(OAUTH2_USER)
                .commit();

        getCredentials(ctx).edit()
                .remove(OAUTH_TOKEN)
                .remove(OAUTH_TOKEN_SECRET)
                .remove(OAUTH2_TOKEN)
                .commit();

        if (!TextUtils.isEmpty(oauth2token) && Integer.parseInt(Build.VERSION.SDK) >= 5) {
            AccountManagerAuthActivity.invalidateToken(ctx, oauth2token);
        }
    }


    public static void setImapPassword(Context ctx, String s) {
        getCredentials(ctx).edit().putString(LOGIN_PASSWORD, s).commit();
    }

    public static boolean useXOAuth(Context ctx) {
        return getAuthMode(ctx) == AuthMode.XOAUTH && ServerPreferences.isGmail(ctx);
    }

    public static String getUserEmail(Context ctx) {
        switch (getAuthMode(ctx)) {
            case XOAUTH:
                return getUsername(ctx);
            default:
                return getImapUsername(ctx);
        }
    }

    public static boolean isLoginInformationSet(Context ctx) {
        switch (getAuthMode(ctx)) {
            case PLAIN:
                return !TextUtils.isEmpty(getImapPassword(ctx)) &&
                        !TextUtils.isEmpty(getImapUsername(ctx));
            case XOAUTH:
                return hasOauthTokens(ctx) || hasOAuth2Tokens(ctx);
            default:
                return false;
        }
    }

    public static String getStoreUri(Context ctx) {
        if (useXOAuth(ctx)) {
            if (hasOauthTokens(ctx)) {
                XOAuthConsumer consumer = getOAuthConsumer(ctx);
                return String.format(IMAP_URI,
                        ServerPreferences.Defaults.SERVER_PROTOCOL,
                        "xoauth:" + encode(consumer.getUsername()),
                        encode(consumer.generateXOAuthString()),
                        ServerPreferences.getServerAddress(ctx));
            } else if (hasOAuth2Tokens(ctx)) {
                return String.format(IMAP_URI,
                        ServerPreferences.Defaults.SERVER_PROTOCOL,
                        "xoauth2:" + encode(getOauth2Username(ctx)),
                        encode(generateXOAuth2Token(ctx)),
                        ServerPreferences.getServerAddress(ctx));
            } else {
                Log.w(TAG, "No valid xoauth1/2 tokens");
                return null;
            }

        } else {
            return String.format(IMAP_URI,
                    ServerPreferences.getServerProtocol(ctx),
                    encode(getImapUsername(ctx)),
                    encode(getImapPassword(ctx)).replace("+", "%20"),
                    ServerPreferences.getServerAddress(ctx));
        }
    }

    private static String getOauthTokenSecret(Context ctx) {
        return getCredentials(ctx).getString(OAUTH_TOKEN_SECRET, null);
    }

    private static String getOauthToken(Context ctx) {
        return getCredentials(ctx).getString(OAUTH_TOKEN, null);
    }

    private static String getOauthUsername(Context ctx) {
        return prefs(ctx).getString(OAUTH_USER, null);
    }

    private static String getOauth2Username(Context ctx) {
        return prefs(ctx).getString(OAUTH2_USER, null);
    }

    private static AuthMode getAuthMode(Context ctx) {
        return Preferences.getDefaultType(ctx, SERVER_AUTHENTICATION, AuthMode.class, AuthMode.XOAUTH);
    }

    // All sensitive information is stored in a separate prefs file so we can
    // backup the rest without exposing sensitive data
    private static SharedPreferences getCredentials(Context ctx) {
        return ctx.getSharedPreferences("credentials", Context.MODE_PRIVATE);
    }

    private static String getImapUsername(Context ctx) {
        return prefs(ctx).getString(LOGIN_USER, null);
    }

    private static String getImapPassword(Context ctx) {
        return getCredentials(ctx).getString(LOGIN_PASSWORD, null);
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
    private static String generateXOAuth2Token(Context context) {
        final String username = getOauth2Username(context);
        final String token = getOauth2Token(context);
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
