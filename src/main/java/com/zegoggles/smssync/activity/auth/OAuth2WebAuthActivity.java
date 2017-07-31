package com.zegoggles.smssync.activity.auth;

import android.annotation.TargetApi;
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
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.zegoggles.smssync.App.TAG;

public class OAuth2WebAuthActivity extends Activity {
    private WebView mWebview;
    private ProgressDialog mProgress;

    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_ERROR = "error";

    // Success code=4/8imH8gQubRYrWu_Fpv6u4Yri5kTNEWmm_XyhytJqlJw
    // Denied error=access_denied
    private static final Pattern TITLE = Pattern.compile("(code|error)=(.+)\\Z");

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
            @TargetApi(Build.VERSION_CODES.FROYO)
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.w(TAG, "onReceiveSslError(" + error + ")");
                // pre-froyo devices don't trust the cert used by google
                // see https://knowledge.verisign.com/support/mpki-for-ssl-support/index?page=content&id=SO17511&actp=AGENT_REFERAL
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO &&
                        error.getPrimaryError() == SslError.SSL_IDMISMATCH) {
                    handler.proceed();
                } else {
                    handler.cancel();
                    showConnectionError(getString(R.string.ssl_error));
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (App.LOCAL_LOGV) Log.d(TAG, "onPageStarted(" + url + ")");
                if (!isFinishing()) mProgress.show();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                final String pageTitle = view.getTitle();
                final Matcher matcher = TITLE.matcher(pageTitle);
                if (matcher.find()) {
                    String status = matcher.group(1);
                    String value = matcher.group(2);

                    if ("code".equals(status)) {
                        onCodeReceived(value);
                    } else if ("error".equals(status)) {
                        onError(value);
                    }
                }
                if (!isFinishing()) mProgress.dismiss();
            }
        });
        removeAllCookies();
        // finally load url
        mWebview.loadUrl(urlToLoad.toString());
    }

    private void onCodeReceived(String code) {
        if (!TextUtils.isEmpty(code)) {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_CODE, code));
            finish();
        }
    }

    private void onError(String error) {
        Log.e(TAG, "onError("+error+")");
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_ERROR, error));
        finish();
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
