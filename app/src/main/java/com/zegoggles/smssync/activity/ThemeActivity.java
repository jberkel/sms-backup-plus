package com.zegoggles.smssync.activity;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import com.zegoggles.smssync.preferences.Preferences;

public abstract class ThemeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(new Preferences(this).getAppTheme());
        super.onCreate(savedInstanceState);
    }
}
