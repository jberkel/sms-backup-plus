package com.zegoggles.smssync.utils;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import com.zegoggles.smssync.activity.auth.AuthActivity;

import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

public interface UrlOpener {
    boolean open(Uri uri);

    public static class Default {
        public static boolean openUriForAuthorization(Context context, final Uri uri) {
            if (LOCAL_LOGV) {
                Log.d(TAG, "openUrlForAutorization(" + uri + ")");

            }
            for (UrlOpener opener : new UrlOpener[]{
                    new UrlOpener.WebViewOpenener(context),
                    new UrlOpener.StockBrowser(context),
                    new UrlOpener.StandardViewOpener(context)}) {
                if (opener.open(uri)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class WebViewOpenener implements UrlOpener {
        private final Context context;

        public WebViewOpenener(Context context) {
            this.context = context;
        }

        @Override
        public boolean open(Uri uri) {
            context.startActivity(new Intent(context, AuthActivity.class)
                    .setData(uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY));
            return true;
        }
    }

    public static class StandardViewOpener implements UrlOpener {
        private final Context context;
        public StandardViewOpener(Context context) {
            this.context = context;
        }

        @Override
        public boolean open(Uri uri) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY));
            return true;

        }
    }

    public static class StockBrowser implements UrlOpener {
        private final Context context;
        public StockBrowser(Context context) {
            this.context = context;
        }

        @Override
        public boolean open(Uri uri) {
            final Intent stockBrowser = new Intent()
                    .setComponent(new ComponentName("com.android.browser",
                            "com.android.browser.BrowserActivity"))
                    .setData(uri)
                    .setAction(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            try {
                context.startActivity(stockBrowser);
                return true;
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "default browser not found, falling back");
                return false;
            }
        }
    }
}
