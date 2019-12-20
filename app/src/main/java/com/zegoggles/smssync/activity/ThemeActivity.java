package com.zegoggles.smssync.activity;

import android.annotation.TargetApi;
import android.os.Build.VERSION;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AppCompatActivity;

import com.zegoggles.smssync.R;
import com.zegoggles.smssync.preferences.Preferences;

public abstract class ThemeActivity extends AppCompatActivity {
    private static final int SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 16;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        final int themeResId = new Preferences(this).getAppTheme();
        setTheme(themeResId);
        if (VERSION.SDK_INT >= 26) {
            setNavBarColor(themeResId);
        }
        super.onCreate(savedInstanceState);
    }

    @TargetApi(26)
    private void setNavBarColor(@StyleRes final int themeId) {
        final int navBarColor = getResources().getColor(
            themeId == R.style.SMSBackupPlusTheme_Light ?
            R.color.navigation_bar_light : R.color.navigation_bar_dark, null);

        getWindow().setNavigationBarColor(navBarColor);

        int visibility = getWindow().getDecorView().getSystemUiVisibility();
        if (themeId == R.style.SMSBackupPlusTheme_Light) {
            visibility |= SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            visibility &= ~(SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        getWindow().getDecorView().setSystemUiVisibility(visibility);
    }
}
