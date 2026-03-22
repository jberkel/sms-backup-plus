package com.zegoggles.smssync.tasks;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.auth.OAuth2Token;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.zegoggles.smssync.App.TAG;

public class OAuth2CallbackTask {

    private final OAuth2Client oauth2Client;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public OAuth2CallbackTask(OAuth2Client oauth2Client) {
        this.oauth2Client = oauth2Client;
    }

    public void execute(final String code) {
        if (TextUtils.isEmpty(code)) {
            Log.w(TAG, "invalid input: " + code);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    App.post(new OAuth2CallbackEvent(null));
                }
            });
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                OAuth2Token token = null;
                try {
                    token = oauth2Client.getToken(code);
                } catch (IOException e) {
                    Log.w(TAG, e);
                }
                final OAuth2Token result = token;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        App.post(new OAuth2CallbackEvent(result));
                    }
                });
            }
        });
    }

    public static class OAuth2CallbackEvent {
        public final OAuth2Token token;

        OAuth2CallbackEvent(OAuth2Token token) {
            this.token = token;
        }

        public boolean valid() {
            return token != null;
        }
    }
}
