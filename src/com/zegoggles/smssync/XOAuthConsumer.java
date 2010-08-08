package com.zegoggles.smssync;

import oauth.signpost.OAuth;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.http.HttpParameters;
import oauth.signpost.signature.SignatureBaseString;
import java.net.URLEncoder;

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

public class XOAuthConsumer extends CommonsHttpOAuthConsumer {
  private String username;
  private static final String MAC_NAME = "HmacSHA1";
  private static final String ANONYMOUS = "anonymous";

  public XOAuthConsumer(String username) {
      super(ANONYMOUS, ANONYMOUS);
      this.username = username;
  }

  public XOAuthConsumer(String username, String token, String secret) {
      this(username);
      setTokenWithSecret(token, secret);
  }

  public String generateXOAuthString() {
      return generateXOAuthString(username);
  }

  public String generateXOAuthString(final String username) {
      final String url = String.format("https://mail.google.com/mail/b/%s/imap/", username);
      final HttpRequest request = wrap(new HttpGet(url));
      final HttpParameters requestParameters = new HttpParameters();

      try {
         completeOAuthParameters(requestParameters);

         StringBuilder sasl = new StringBuilder()
              .append("GET ")
              .append(url)
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

         Log.d(Consts.TAG, "sasl: " + sasl.toString());
         return base64(sasl.toString().getBytes(OAuth.ENCODING));
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
            urlEncode(context.getString(com.zegoggles.smssync.R.string.app_name))),
          "https://www.google.com/accounts/OAuthGetAccessToken",
          "https://www.google.com/accounts/OAuthAuthorizeToken?btmpl=mobile") {
              { setOAuth10a(true); }
          };
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getOwnerEmail() {
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
    } catch (Exception e) {
       Log.e(Consts.TAG, "error", e);
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
