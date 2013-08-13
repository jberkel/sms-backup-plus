package com.zegoggles.smssync.tasks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.auth.XOAuthConsumer;
import com.zegoggles.smssync.preferences.AuthPreferences;
import oauth.signpost.OAuth;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthException;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public class OAuthCallbackTask extends AsyncTask<Intent, Void, XOAuthConsumer> {
    private Context context;

    public OAuthCallbackTask(Context smsSync) {
        this.context = smsSync;
    }

    @Override
    protected XOAuthConsumer doInBackground(Intent... callbackIntent) {
        Uri uri = callbackIntent[0].getData();
        if (LOCAL_LOGV) Log.v(TAG, "oauth callback: " + uri);

        XOAuthConsumer consumer = new AuthPreferences(context).getOAuthConsumer();
        CommonsHttpOAuthProvider provider = consumer.getProvider(context);
        String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
        try {
            provider.retrieveAccessToken(consumer, verifier);
            String username = consumer.loadUsernameFromContacts();

            if (username != null) {
                Log.i(TAG, "Valid access token for " + username);
                // intent has been handled
                callbackIntent[0].setData(null);

                return consumer;
            } else {
                Log.e(TAG, "No valid user name");
                return null;
            }
        } catch (OAuthException e) {
            Log.e(TAG, "error", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(XOAuthConsumer consumer) {
        if (LOCAL_LOGV)
            Log.v(TAG, String.format("%s#onPostExecute(%s)", getClass().getName(), consumer));

        if (consumer != null) {
            App.bus.post(new OAuthCallbackEvent(consumer.getUsername(),
                    consumer.getToken(),
                    consumer.getTokenSecret()));
        } else {
            App.bus.post(new OAuthCallbackEvent(null, null, null));
        }
    }

    public static class OAuthCallbackEvent {
        public final String username, token, tokenSecret;

        public OAuthCallbackEvent(String username, String token, String tokenSecret) {
            this.username = username;
            this.token = token;
            this.tokenSecret = tokenSecret;
        }

        public boolean valid() {
            return !TextUtils.isEmpty(username) &&
                   !TextUtils.isEmpty(token) &&
                   !TextUtils.isEmpty(tokenSecret);
        }
    }
}
