/*
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
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
package com.zegoggles.smssync.auth;

import android.content.Context;
import android.util.Log;
import com.zegoggles.smssync.R;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthException;
import oauth.signpost.http.HttpParameters;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.signature.SignatureBaseString;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import static com.zegoggles.smssync.App.TAG;
import static java.net.HttpURLConnection.HTTP_OK;
import static oauth.signpost.OAuth.ENCODING;
import static oauth.signpost.OAuth.OAUTH_SIGNATURE;
import static oauth.signpost.OAuth.percentEncode;

@Deprecated
public class XOAuthConsumer extends CommonsHttpOAuthConsumer {
    private static final String MAC_NAME = "HmacSHA1";
    private static final String ANONYMOUS = "anonymous";

    // Scopes as defined in http://code.google.com/apis/accounts/docs/OAuth.html#prepScope
    private static final String GMAIL_SCOPE = "https://mail.google.com/";
    private static final String CONTACTS_SCOPE = "https://www.google.com/m8/feeds/";
    private static final String DEFAULT_SCOPE  = GMAIL_SCOPE + " " + CONTACTS_SCOPE;

    // endpoints
    private static final String CONTACTS_URL = "https://www.google.com/m8/feeds/contacts/default/thin?max-results=1";
    private static final String REQUEST_TOKEN_URL = "https://www.google.com/accounts/OAuthGetRequestToken" +
            "?scope=%s&xoauth_displayname=%s";
    private static final String ACCESS_TOKEN_ENDPOINT_URL = "https://www.google.com/accounts/OAuthGetAccessToken";
    private static final String AUTHORIZE_TOKEN_URL       = "https://www.google.com/accounts/OAuthAuthorizeToken?btmpl=mobile";

    private String mUsername;

    public XOAuthConsumer(String username) {
        super(ANONYMOUS, ANONYMOUS);
        this.mUsername = username;
    }

    public XOAuthConsumer(String username, String token, String secret) {
        this(username);
        setTokenWithSecret(token, secret);
    }

    public String generateXOAuthString() {
        return generateXOAuthString(mUsername);
    }

    public String generateXOAuthString(final String username) {
        if (username == null) throw new IllegalArgumentException("username is null");

        try {
            final URI uri = new URI(String.format("https://mail.google.com/mail/b/%s/imap/", urlEncode(username)));
            final HttpRequest request = wrap(new HttpGet(uri));
            final HttpParameters requestParameters = new HttpParameters();

            completeOAuthParameters(requestParameters);

            StringBuilder sasl = new StringBuilder()
                    .append("GET ")
                    .append(uri.toString())
                    .append(" ");

            requestParameters.put(OAUTH_SIGNATURE, generateSig(request, requestParameters), true);

            Iterator<Map.Entry<String, SortedSet<String>>> it = requestParameters.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, SortedSet<String>> e = it.next();

                sasl.append(e.getKey()).append("=\"").append(
                        e.getValue().iterator().next()
                ).append("\"");

                if (it.hasNext()) {
                    sasl.append(",");
                }
            }

            return base64(sasl.toString().getBytes(ENCODING));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CommonsHttpOAuthProvider getProvider(Context context) {
        //System.setProperty("debug", "true");
        return new CommonsHttpOAuthProvider(
                requestTokenEndpointUrl(context),
                ACCESS_TOKEN_ENDPOINT_URL,
                AUTHORIZE_TOKEN_URL) {
            {
                setOAuth10a(true);
            }
        };
    }

    /**
     * @param clientId the oauth2 client id
     * @return an oauth2 refresh token
     * @throws IOException
     */
    public String migrateToken(String clientId) throws IOException {
        HttpPost post = new HttpPost(OAuth2Client.TOKEN_URL);
        List<NameValuePair> postParams = new ArrayList<NameValuePair>();
        postParams.add(new BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:migration:oauth1"));
        postParams.add(new BasicNameValuePair("client_id", clientId));

        try {
            post.setEntity(new UrlEncodedFormEntity(postParams));
        } catch (UnsupportedEncodingException e) {
            return null;
        }

        try {
            HttpUriRequest request = (HttpUriRequest) sign(post).unwrap();
            final HttpClient httpClient = new DefaultHttpClient();
            final HttpResponse response = httpClient.execute(request);

            // After a migration request is validated, your application is issued a refresh token
            // with down-scoped scopes. The response body is in JSON format, and it contains only an OAuth 2.0 refresh token
            // (no access token).
            if (response.getStatusLine().getStatusCode() == HTTP_OK) {
                final HttpEntity responseString = response.getEntity();
                JSONTokener tokener = new JSONTokener(EntityUtils.toString(responseString));
                Object value = tokener.nextValue();

                if (value instanceof JSONObject) {
                    return ((JSONObject)value).optString("refresh_token", null);
                } else {
                    Log.w(TAG, "invalid response from server: " + responseString);
                }
            } else {
                Log.w(TAG, "invalid response from server: " + response.getStatusLine());
            }
        } catch (OAuthException ignored) {
            Log.w(TAG, ignored);
        } catch (JSONException e) {
            Log.w(TAG, e);
        }
        return null;
    }

    private String requestTokenEndpointUrl(Context context) {
        return String.format(REQUEST_TOKEN_URL,
            urlEncode(DEFAULT_SCOPE),
            urlEncode(context.getString(R.string.app_name)));
    }

    public String getUsername() {
        return mUsername;
    }

    public String loadUsernameFromContacts() {
        this.mUsername = getUsernameFromContacts();
        return this.mUsername;
    }

    // Retrieves the google email account address using the contacts API
    protected String getUsernameFromContacts() {
        final HttpClient httpClient = new DefaultHttpClient();

        try {
            HttpGet get = new HttpGet(sign(CONTACTS_URL));
            return extractEmail(httpClient.execute(get));
        } catch (OAuthException e) {
            Log.e(TAG, "error", e);
            return null;
        } catch (SAXException e) {
            Log.e(TAG, "error", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "error", e);
            return null;
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "error", e);
            return null;
        }
    }

    private String extractEmail(HttpResponse response) throws ParserConfigurationException, SAXException, IOException {
        final XMLReader xmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        final FeedHandler feedHandler = new FeedHandler();
        xmlReader.setContentHandler(feedHandler);
        xmlReader.parse(new InputSource(response.getEntity().getContent()));
        return feedHandler.getEmail();
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "utf-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String generateSig(HttpRequest request, HttpParameters requestParameters) throws Exception {
        String keyString = percentEncode(getConsumerSecret()) + '&' + percentEncode(getTokenSecret());

        SecretKey key = new SecretKeySpec(keyString.getBytes(ENCODING), MAC_NAME);
        Mac mac = Mac.getInstance(MAC_NAME);
        mac.init(key);

        String sbs = new SignatureBaseString(request, requestParameters).generate();
        return base64(mac.doFinal(sbs.getBytes(ENCODING)));
    }

    private String base64(byte[] data) {
        try {
            return new String(Base64.encodeBase64(data), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
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
