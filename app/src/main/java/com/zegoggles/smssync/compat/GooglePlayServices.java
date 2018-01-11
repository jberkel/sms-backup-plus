package com.zegoggles.smssync.compat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import static android.content.pm.PackageManager.GET_SIGNATURES;
import static android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES;
import static com.zegoggles.smssync.App.TAG;

public class GooglePlayServices {
    private static final String COM_GOOGLE_ANDROID_GMS = "com.google.android.gms";
    private static final String COM_ANDROID_VENDING = "com.android.vending";
    private static final int MIN_GMS_VERSION = 9256030; // 9256030  9.2.56 (030-124593566)

    @SuppressWarnings("deprecation")
    @SuppressLint("PackageManagerGetSignatures")
    public static boolean isAvailable(Context context) {
        final PackageManager packageManager = context.getPackageManager();

        try {
            packageManager.getPackageInfo(COM_ANDROID_VENDING, GET_UNINSTALLED_PACKAGES|GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        try {
            final PackageInfo info = packageManager.getPackageInfo(COM_GOOGLE_ANDROID_GMS, GET_SIGNATURES);
            // TODO: check signatures

            if (info.versionCode < MIN_GMS_VERSION) {
                Log.w(TAG, "Google Play Services out of date: "+ info.versionCode);
                return false;
            } else {
                ApplicationInfo applicationInfo = info.applicationInfo;
                if (applicationInfo == null) {
                    try {
                        applicationInfo = packageManager.getApplicationInfo(COM_GOOGLE_ANDROID_GMS, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.w("GooglePlayServicesUtil", "Google Play services missing when getting application info.");
                        return false;
                    }
                }
                return applicationInfo.enabled;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Google Play Services is missing");
            return false;
        }
    }
}
