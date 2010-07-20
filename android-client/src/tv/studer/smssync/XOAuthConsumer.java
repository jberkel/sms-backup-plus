package tv.studer.smssync;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.http.HttpParameters;
import oauth.signpost.signature.SignatureBaseString;
import java.net.URLEncoder;

import org.apache.http.client.methods.HttpGet;
import java.util.Map;
import java.util.Iterator;
import java.util.SortedSet;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

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
         return Base64.encodeToString(sasl.toString().getBytes(OAuth.ENCODING), Base64.NO_WRAP);
      } catch (Exception e) {
          throw new RuntimeException(e);
      }
  }

  public CommonsHttpOAuthProvider getProvider() {
      System.setProperty("debug", "true");

      return new CommonsHttpOAuthProvider(
          String.format("https://www.google.com/accounts/OAuthGetRequestToken?scope=%s&xoauth_displayname=%s",
            urlEncode(Consts.GMAIL_SCOPE),
            urlEncode(Consts.XOAUTH_DISPLAYNAME)),
          "https://www.google.com/accounts/OAuthGetAccessToken",
          "https://www.google.com/accounts/OAuthAuthorizeToken?btmpl=mobile") {
              { setOAuth10a(true); }
          };
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
      return Base64.encodeToString(mac.doFinal(sbs.getBytes(OAuth.ENCODING)), Base64.NO_WRAP);
  }
}
