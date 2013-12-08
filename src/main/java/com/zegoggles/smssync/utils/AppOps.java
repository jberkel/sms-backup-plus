package com.zegoggles.smssync.utils;

import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceActivity;
import android.util.Log;
import com.zegoggles.smssync.App;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.zegoggles.smssync.App.TAG;

/**
 * @see <a href="http://commonsware.com/blog/2013/07/26/app-ops-developer-faq.html">App Ops Developer FAQ</a>
 * @see <a href="http://www.androidpolice.com/2013/12/06/non-default-sms-apps-in-kitkat-can-still-write-to-the-sms-database-using-a-switch-in-app-ops-no-root-required/">Non-Default SMS Apps In KitKat Can Still Write To The SMS Database Using A Switch In App Ops (No Root Required)</a>
 */
public final class AppOps {
    private AppOps() {}

    /**
     * Android KitKat requires a permission to write to the SMS Provider.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static  boolean hasSMSWritePermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return true;

        AppOpsManager manager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (manager != null) {
            //AppOpsManager.checkOp(int, int, java.lang.String)()
            try {
                Method m = manager.getClass().getMethod("checkOp", int.class, int.class, String.class);
                Field f = manager.getClass().getField("OP_WRITE_SMS");
                final int OP_WRITE_SMS = f.getInt(m);
                Integer ret = (Integer) m.invoke(manager,
                        /* op */  OP_WRITE_SMS,
                        /* uid */ context.getApplicationInfo().uid,
                        /* package */ context.getPackageName());
                return ret == AppOpsManager.MODE_ALLOWED;
            } catch (NoSuchMethodException e) {
                Log.w(TAG, e);
            } catch (InvocationTargetException e) {
                Log.w(TAG, e);
            } catch (IllegalAccessException e) {
                Log.w(TAG, e);
            } catch (NoSuchFieldException e) {
                Log.w(TAG, e);
            }

            return false;
        } else {
            Log.w(TAG, "app ops manager not available");
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static boolean launchSettings(Context context) {
        final Intent intent = new Intent()
            .setClassName("com.android.settings", "com.android.settings.Settings")
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            .putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, "com.android.settings.applications.AppOpsSummary");

        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }
}
