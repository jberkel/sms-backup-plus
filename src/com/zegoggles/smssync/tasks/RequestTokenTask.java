package com.zegoggles.smssync.tasks;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.auth.XOAuthConsumer;
import com.zegoggles.smssync.preferences.AuthPreferences;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthException;

import static com.zegoggles.smssync.App.TAG;

public class RequestTokenTask extends AsyncTask<String, Void, String> {
    private Context context;

    public RequestTokenTask(Context smsSync) {
        this.context = smsSync;
    }

    public String doInBackground(String... callback) {
        synchronized (XOAuthConsumer.class) {
            XOAuthConsumer consumer = AuthPreferences.getOAuthConsumer(context);
            CommonsHttpOAuthProvider provider = consumer.getProvider(context);
            try {
                String url = provider.retrieveRequestToken(consumer, callback[0]);
                AuthPreferences.setOauthTokens(context, consumer.getToken(), consumer.getTokenSecret());
                return url;
            } catch (OAuthCommunicationException e) {
                Log.e(TAG, "error requesting token: " + e.getResponseBody(), e);
                return null;
            } catch (OAuthException e) {
                Log.e(TAG, "error requesting token", e);
                return null;
            }
        }
    }

    @Override
    protected void onPostExecute(String authorizeUrl) {
        if (authorizeUrl != null) {
            App.bus.post(new AuthorizedURLReceived(Uri.parse(authorizeUrl)));
        } else {
            App.bus.post(new AuthorizedURLReceived(null));
        }
    }

    public static class AuthorizedURLReceived {
        public final Uri uri;

        public AuthorizedURLReceived(Uri uri) {
            this.uri = uri;
        }
    }
}
