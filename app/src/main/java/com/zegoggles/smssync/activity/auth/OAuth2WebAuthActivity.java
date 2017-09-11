package com.zegoggles.smssync.activity.auth;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;

import static com.zegoggles.smssync.App.TAG;

public class OAuth2WebAuthActivity extends Activity {
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_ERROR = "error";

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        final Uri urlToLoad = getIntent().getData();
        App.bus.register(this);

        startActivity(new Intent(Intent.ACTION_VIEW, urlToLoad));
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // user navigated back, cancel auth flow
        setResult(RESULT_CANCELED);
        finish();
    }

    @Subscribe
    public void onBrowserAuthResult(RedirectReceiverActivity.BrowserAuthResult event) {
        if (!TextUtils.isEmpty(event.code)) {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_CODE, event.code));
        } else {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_ERROR, event.error));
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            App.bus.unregister(this);
        } catch (Exception ignored) {
        }
    }
}
