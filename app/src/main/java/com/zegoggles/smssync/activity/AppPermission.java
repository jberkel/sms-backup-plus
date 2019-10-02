package com.zegoggles.smssync.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.zegoggles.smssync.R;

import java.util.ArrayList;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_DENIED;

public enum AppPermission {
    READ_SMS(Manifest.permission.READ_SMS, R.string.permission_read_sms),
    READ_CALL_LOG(Manifest.permission.READ_CALL_LOG, R.string.permission_read_call_log),
    READ_CONTACTS(Manifest.permission.READ_CONTACTS, R.string.permission_read_contacts),
    UNKNOWN(null, R.string.permission_unknown);

    final int descriptionResource;
    final @Nullable String androidPermission;

    AppPermission(String androidPermission, int descriptionResource) {
        this.androidPermission = androidPermission;
        this.descriptionResource = descriptionResource;
    }

    @Override
    public String toString() {
        return "AppPermission{" + androidPermission + '}';
    }

    public static AppPermission from(@NonNull String androidPermission) {
        for (AppPermission appPermission : AppPermission.values()) {
            if (androidPermission.equals(appPermission.androidPermission)) {
                return appPermission;
            }
        }
        return UNKNOWN;
    }

    public static List<AppPermission> from(@NonNull String[] androidPermissions) {
        List<AppPermission> appPermissions = new ArrayList<AppPermission>();
        for (String permission : androidPermissions) {
            appPermissions.add(from(permission));
        }
        return appPermissions;
    }

    public static List<AppPermission> from(@NonNull String[] androidPermissions, @NonNull int[] grantResults) {
        List<AppPermission> appPermissions = new ArrayList<AppPermission>();
        for (int i=0; i<androidPermissions.length; i++) {
            if (grantResults[i] == PERMISSION_DENIED) {
                appPermissions.add(from(androidPermissions[i]));
            }
        }
        return appPermissions;
    }

    /**
     * “It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.“
     *
     * {@link androidx.core.app.FragmentActivity#onRequestPermissionsResult(int, String[], int[])}
     */
    public static boolean allGranted(@NonNull int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    public static String formatMissingPermissionDetails(Resources resources, String[] androidPermissions) {
        return formatMissingPermissionDetails(resources, from(androidPermissions));
    }

    public static String formatMissingPermissionDetails(Resources resources, List<AppPermission> appPermissions) {
        List<String> permissions = new ArrayList<String>();
        for (AppPermission permission : appPermissions) {
            permissions.add(resources.getString(permission.descriptionResource));
        }
        return resources.getQuantityString(R.plurals.status_permission_problem_details,
                permissions.size(),
                TextUtils.join(", ", permissions));
    }
}
