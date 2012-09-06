package com.zegoggles.smssync;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class AuthActivity extends Activity {
    private WebView mWebview;
    private ProgressDialog mProgress;

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.auth_activity);

        final Uri urlToLoad = getIntent().getData();

        mWebview = (WebView) findViewById(R.id.webview);
        mWebview.getSettings().setJavaScriptEnabled(true);
        mWebview.getSettings().setLoadsImagesAutomatically(true);

        mProgress = new ProgressDialog(this);
        mProgress.setIndeterminate(true);
        mProgress.setMessage(getString(R.string.loading));
        mProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mWebview.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY); // fix white bar
        mWebview.clearSslPreferences();

        mWebview.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showConnectionError(description);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.w(App.TAG, "onReceiveSslError(" + error + ")");
                // pre-froyo devices don't trust the cert used by google
                // see https://knowledge.verisign.com/support/mpki-for-ssl-support/index?page=content&id=SO17511&actp=AGENT_REFERAL
                if (error.getPrimaryError() == SslError.SSL_IDMISMATCH
                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                    handler.proceed();
                } else {
                    handler.cancel();
                    showConnectionError(getString(R.string.ssl_error));
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (App.LOCAL_LOGV) Log.d(App.TAG, "onPageStarted("+url+")");
                if (!isFinishing()) mProgress.show();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!isFinishing()) mProgress.dismiss();
            }

            @Override
            public boolean shouldOverrideUrlLoading(final WebView view, String url) {
                if (url.startsWith(Consts.CALLBACK_URL)) {
                    Intent intent = new Intent().setData(Uri.parse(url));
                    startActivity(intent);
                    finish();
                    return true;
                } else {
                    return false;
                }
            }
        });
        removeAllCookies();

        // finally load url
        mWebview.loadUrl(urlToLoad.toString());
    }

    private void showConnectionError(final String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this).
                setMessage(message).
                setTitle(getString(R.string.status_unknown_error)).
                setIcon(android.R.drawable.ic_dialog_alert).
                setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).
                create().
                show();
    }

    private void removeAllCookies() {
        CookieSyncManager.createInstance(this);
        CookieManager.getInstance().removeAllCookie();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWebview.stopLoading();
        mProgress.dismiss();
    }
}