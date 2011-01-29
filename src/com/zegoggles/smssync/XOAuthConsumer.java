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
package com.zegoggles.smssync;

import oauth.signpost.OAuth;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.http.HttpParameters;
import oauth.signpost.signature.SignatureBaseString;
import java.net.URLEncoder;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpResponse;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.XMLReader;
import org.xml.sax.InputSource;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;


import java.util.Map;
import java.util.Iterator;
import java.util.SortedSet;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import android.util.Log;
import org.apache.commons.codec.binary.Base64;

import static com.zegoggles.smssync.App.*;

public class XOAuthConsumer extends CommonsHttpOAuthConsumer {
  private String mUsername;
  private static final String MAC_NAME = "HmacSHA1";
  private static final String ANONYMOUS = "anonymous";

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
        final URI uri = new URI(String.format("https://mail.google.com/mail/b/%s/imap/",
                                              urlEncode(username)));
        final HttpRequest request = wrap(new HttpGet(uri));
        final HttpParameters requestParameters = new HttpParameters();

         completeOAuthParameters(requestParameters);

         StringBuilder sasl = new StringBuilder()
              .append("GET ")
              .append(uri.toString())
              .append(" ");

         requestParameters.put("oauth_signature", generateSig(request, requestParameters) , true);

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

         if (LOCAL_LOGV) Log.v(TAG, "sasl: " + sasl.toString());
         return base64(sasl.toString().getBytes(OAuth.ENCODING));
      } catch (URISyntaxException e) {
          throw new IllegalArgumentException(e);
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
  }

  public CommonsHttpOAuthProvider getProvider(android.content.Context context ) {
      //System.setProperty("debug", "true");
      final String scope = Consts.GMAIL_SCOPE + " " + Consts.CONTACTS_SCOPE;
      return new CommonsHttpOAuthProvider(
          String.format("https://www.google.com/accounts/OAuthGetRequestToken" +
            "?scope=%s&xoauth_displayname=%s",
            urlEncode(scope),
            urlEncode(context.getString(R.string.app_name))),
          "https://www.google.com/accounts/OAuthGetAccessToken",
          "https://www.google.com/accounts/OAuthAuthorizeToken?btmpl=mobile") {
              { setOAuth10a(true); }
          };
  }

  public String getUsername() {
     return mUsername;
  }

  public String loadUsernameFromContacts() {
    this.mUsername = getUsernameFromContacts();
    return this.mUsername;
  }

  /** Retrieves the google email account address using the contacts API */
  protected String getUsernameFromContacts() {

    final HttpClient httpClient = new DefaultHttpClient();
    final String url = "https://www.google.com/m8/feeds/contacts/default/thin?max-results=1";
    final StringBuilder email = new StringBuilder();

    try {
      HttpGet get = new HttpGet(sign(url));
      HttpResponse resp = httpClient.execute(get);
      SAXParserFactory spf = SAXParserFactory.newInstance();
      SAXParser sp = spf.newSAXParser();
      XMLReader xr = sp.getXMLReader();
      xr.setContentHandler(new DefaultHandler() {
        boolean inEmail;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
           inEmail = "email".equals(localName);
        }
        @Override
        public void characters(char[] c, int start, int length) {
          if (inEmail) {
            email.append(c, start, length);
          }
        }
      });
      xr.parse(new InputSource(resp.getEntity().getContent()));
      return email.toString();

    } catch (oauth.signpost.exception.OAuthException e) {
       Log.e(TAG, "error", e);
       return null;
    } catch (org.xml.sax.SAXException e) {
       Log.e(TAG, "error", e);
       return null;
    } catch (java.io.IOException e) {
       Log.e(TAG, "error", e);
       return null;
    } catch (javax.xml.parsers.ParserConfigurationException e) {
       Log.e(TAG, "error", e);
       return null;
    }
  }

  private String urlEncode(String s) {
      try {
          return URLEncoder.encode(s, "utf-8");
     } catch (Exception e) {
         throw new RuntimeException(e);
     }
  }

  private String generateSig(HttpRequest request, HttpParameters requestParameters) throws Exception {
      String keyString = OAuth.percentEncode(getConsumerSecret()) + '&' + OAuth.percentEncode(getTokenSecret());
      byte[] keyBytes = keyString.getBytes(OAuth.ENCODING);

      SecretKey key = new SecretKeySpec(keyBytes, MAC_NAME);
      Mac mac = Mac.getInstance(MAC_NAME);
      mac.init(key);

      String sbs = new SignatureBaseString(request, requestParameters).generate();
      return base64(mac.doFinal(sbs.getBytes(OAuth.ENCODING)));
  }

  private String base64(byte[] data) {
    try {
      return new String(Base64.encodeBase64(data), "UTF-8");
    } catch (java.io.UnsupportedEncodingException e) {
       throw new RuntimeException(e);
    }
  }
}
