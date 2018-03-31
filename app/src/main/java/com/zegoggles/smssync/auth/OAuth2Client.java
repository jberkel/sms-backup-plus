package com.zegoggles.smssync.auth;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.zegoggles.smssync.App.TAG;

/**
 * https://developers.google.com/identity/protocols/OAuth2UserAgent
 */
public class OAuth2Client {
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/auth";
    private static final String TOKEN_URL = "https://www.googleapis.com/oauth2/v3/token";

    /**
     * When choosing a URI scheme to associate with the app, apps MUST use a
     * URI scheme based on a domain name under their control, expressed in
     * reverse order, as recommended by Section 3.8 of [RFC7595] for
     * private-use URI schemes.
     *
     * For more details, see
     * <a href="https://tools.ietf.org/html/draft-ietf-oauth-native-apps-12#page-8">
     *     OAuth 2.0 for Native Apps
     * </a>
     */
    public static final Uri REDIRECT_URL = Uri.parse("com.zegoggles.smssync:/oauth2redirect");

    /**
     * For installed applications, use a value of code, indicating that the Google OAuth 2.0 endpoint should return an authorization code.
     */
    private static final String RESPONSE_TYPE = "response_type";

    /**
     * Identifies the client that is making the request.
     * The value passed in this parameter must exactly match the value shown in the Google Developers Console.
     */
    private static final String CLIENT_ID = "client_id";

    /**
     * Determines where the response is sent.
     * The value of this parameter must exactly match one of the values that appear in the
     * Credentials page in the Google Developers Console (including the http or https scheme, case, and trailing slash).
     * You may choose between <code>urn:ietf:wg:oauth:2.0:oob</code>,
     * <code>urn:ietf:wg:oauth:2.0:oob:auto</code>, or an <code>http://localhost</code> port.
     * For more details, see <a href="https://developers.google.com/identity/protocols/OAuth2InstalledApp#choosingredirecturi">Choosing a redirect URI</a>.
     */
    private static final String REDIRECT_URI = "redirect_uri";

    /**
     * Space-delimited set of scope strings.
     *
     * Identifies the Google API access that your application is requesting.
     * The values passed in this parameter inform the consent screen that is shown to the user. There may be an inverse
     * relationship between the number of permissions requested and the likelihood
     * of obtaining user consent.
     */
    private static final String SCOPE = "scope";

    /**
     * Provides any state information that might be useful to your application upon receipt
     * of the response. The Google Authorization Server roundtrips this parameter, so your application receives
     * the same value it sent. Possible uses include redirecting the user to the
     * correct resource in your site, nonces, and cross-site-request-forgery mitigations.
     */
    private static final String STATE = "state";

    /**
     * When your application knows which user it is trying to authenticate, it can
     * provide this parameter as a hint to the Authentication Server.
     * Passing this hint will either pre-fill the email box on the sign-in form or select the proper
     * multi-login session, thereby simplifying the login flow.
     */
    private static final String LOGIN_HINT = "login_hint";


    /**
     * If this is provided with the value true, and the authorization request is granted, the authorization will include
     * any previous authorizations granted to this user/application combination
     * for other scopes; see Incremental Authorization.
     */
    private static final String INCLUDE_GRANTED_SCOPES = "include_granted_scopes";

    // Scopes as defined in http://code.google.com/apis/accounts/docs/OAuth.html#prepScope
    private static final String GMAIL_SCOPE = "https://mail.google.com/";
    private static final String CONTACTS_SCOPE = "https://www.google.com/m8/feeds/";
    private static final String DEFAULT_SCOPE  = GMAIL_SCOPE + " " + CONTACTS_SCOPE;

    private static final String CONTACTS_URL = "https://www.google.com/m8/feeds/contacts/default/thin?max-results=1";

    /**
     * As defined in the OAuth 2.0 specification, this field must contain a value of authorization_code.
     */
    private static final String GRANT_TYPE = "grant_type";
    private static final String AUTHORIZATION_CODE = "authorization_code";
    /**
     * The authorization code returned from the initial request.
     */
    private static final String CODE = "code";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String ERROR = "error";

    private final String clientId;

    public OAuth2Client(String clientId) {
        if (TextUtils.isEmpty(clientId)) {
            throw new IllegalArgumentException("empty client id");
        }
        this.clientId = clientId;
    }

    public Uri requestUrl() {
        return Uri.parse(AUTH_URL)
            .buildUpon()
            .appendQueryParameter(SCOPE, DEFAULT_SCOPE)
            .appendQueryParameter(CLIENT_ID, clientId)
            .appendQueryParameter(RESPONSE_TYPE, "code")
            .appendQueryParameter(REDIRECT_URI, REDIRECT_URL.toString()).build();
    }

    public OAuth2Token getToken(String code) throws IOException {
        HttpsURLConnection connection = postTokenEndpoint(getAccessTokenPostData(code));
        final int responseCode = connection.getResponseCode();
        if (responseCode == HttpsURLConnection.HTTP_OK) {
            OAuth2Token token = parseResponse(connection.getInputStream());
            String username = getUsernameFromContacts(token);
            Log.d(TAG, "got token " + token.getTokenForLogging()+ ", username="+username);

            return new OAuth2Token(token.accessToken, token.tokenType, token.refreshToken, token.expiresIn, username);
        } else {
            Log.e(TAG, "error: " + responseCode);
            throw new IOException("Invalid response from server:" + responseCode);
        }
    }

    public OAuth2Token refreshToken(String refreshToken) throws IOException {
        HttpsURLConnection connection = postTokenEndpoint(getRefreshTokenPostData(refreshToken));
        final int responseCode = connection.getResponseCode();
        if (responseCode == HttpsURLConnection.HTTP_OK) {
            return parseResponse(connection.getInputStream());
        } else {
            Log.e(TAG, "error: " + responseCode);
            throw new IOException("Invalid response from server:" + responseCode);
        }
    }

    private OAuth2Token parseResponse(InputStream inputStream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, n);
        }
        inputStream.close();
        return OAuth2Token.fromJSON(bos.toString("UTF-8"));
    }

    private HttpsURLConnection postTokenEndpoint(String payload) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(TOKEN_URL).openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        final OutputStream os = connection.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        writer.write(payload);
        writer.flush();
        writer.close();
        os.close();
        return connection;
    }

    private String getAccessTokenPostData(String code) {
        final Uri uri = Uri.parse(TOKEN_URL)
            .buildUpon()
            .appendQueryParameter(GRANT_TYPE, AUTHORIZATION_CODE)
            .appendQueryParameter(REDIRECT_URI, REDIRECT_URL.toString())
            .appendQueryParameter(CLIENT_ID, clientId)
            .appendQueryParameter(CODE, code)
            .build();
        return uri.getEncodedQuery();
    }

    private String getRefreshTokenPostData(String refreshToken) {
        final Uri uri = Uri.parse(TOKEN_URL)
            .buildUpon()
            .appendQueryParameter(GRANT_TYPE, REFRESH_TOKEN)
            .appendQueryParameter(REFRESH_TOKEN, refreshToken)
            .appendQueryParameter(CLIENT_ID, clientId)
            .build();
        return uri.getEncodedQuery();
    }

    // Retrieves the google email account address using the contacts API
    private String getUsernameFromContacts(OAuth2Token token) {
        try {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(CONTACTS_URL).openConnection();
            connection.addRequestProperty("Authorization", "Bearer "+token.accessToken);
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                final InputStream inputStream = connection.getInputStream();
                String email = extractEmail(inputStream);
                inputStream.close();
                return email;
            } else {
                Log.w(TAG, String.format("unexpected server response: %d (%s)",
                        connection.getResponseCode(), connection.getResponseMessage()));
                return null;
            }

        } catch (SAXException e) {
            Log.e(TAG, ERROR, e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, ERROR, e);
            return null;
        } catch (ParserConfigurationException e) {
            Log.e(TAG, ERROR, e);
            return null;
        }
    }

    private String extractEmail(InputStream inputStream) throws ParserConfigurationException, SAXException, IOException {
        final XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        final FeedHandler feedHandler = new FeedHandler();
        xmlReader.setContentHandler(feedHandler);
        xmlReader.parse(new InputSource(inputStream));
        return feedHandler.getEmail();
    }

    private static class FeedHandler extends DefaultHandler {
        private static final String EMAIL = "email";
        private static final String AUTHOR = "author";
        private final StringBuilder email = new StringBuilder();
        private boolean inEmail;
        private boolean inAuthor;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            inEmail = EMAIL.equals(qName);
            if (AUTHOR.equals(qName)) {
                inAuthor = true;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (inAuthor && AUTHOR.equals(qName)) {
                inAuthor = false;
            }
        }

        @Override
        public void characters(char[] c, int start, int length) {
            if (inAuthor && inEmail) {
                email.append(c, start, length);
            }
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            Log.e(TAG, "error during parsing", e);
        }

        @Override public void warning(SAXParseException e) throws SAXException {
            Log.w(TAG, "error during parsing", e);
        }

        public String getEmail() {
            return email.toString().trim();
        }
    }
}
