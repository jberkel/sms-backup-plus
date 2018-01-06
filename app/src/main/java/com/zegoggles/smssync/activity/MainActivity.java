/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 * Copyright (c) 2010 Jan Berkel <jan.berkel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zegoggles.smssync.activity;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentManager.BackStackEntry;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import android.support.v7.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.BuildConfig;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity;
import com.zegoggles.smssync.activity.auth.OAuth2WebAuthActivity;
import com.zegoggles.smssync.activity.events.ConnectEvent;
import com.zegoggles.smssync.activity.fragments.MainSettings;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.BackupManagerWrapper;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.BackupType;
import com.zegoggles.smssync.service.SmsBackupService;
import com.zegoggles.smssync.service.SmsRestoreService;
import com.zegoggles.smssync.service.state.RestoreState;
import com.zegoggles.smssync.tasks.OAuth2CallbackTask;

import static android.support.v4.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
import static android.support.v7.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;
import static android.widget.Toast.LENGTH_LONG;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;

/**
 * This is the main activity showing the status of the SMS Sync service and
 * providing controls to configure it.
 */
public class MainActivity extends AppCompatActivity implements
        OnPreferenceStartFragmentCallback,
        OnPreferenceStartScreenCallback,
        FragmentManager.OnBackStackChangedListener {
    private static final int REQUEST_CHANGE_DEFAULT_SMS_PACKAGE = 1;
    private static final int REQUEST_PICK_ACCOUNT = 2;
    private static final int REQUEST_WEB_AUTH = 3;
    private static final String SCREEN_TITLE = "title";

    enum Actions {
        Backup,
        BackupSkip,
        Restore
    }
    private Actions mActions;

    private Preferences preferences;
    private AuthPreferences authPreferences;
    private OAuth2Client oauth2Client;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        authPreferences = new AuthPreferences(this);
        oauth2Client = new OAuth2Client(authPreferences.getOAuth2ClientId());
        preferences = new Preferences(this);
        preferences.getSharedPreferences().registerOnSharedPreferenceChangeListener(
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    BackupManagerWrapper.dataChanged(MainActivity.this);
                }
            }
        );
        showFragment(new MainSettings(), null);
        if (preferences.shouldShowUpgradeMessage()) {
            show(Dialogs.Type.UPGRADE_FROM_SMSBACKUP);
        }
        preferences.migrateMarkAsRead();

        if (preferences.shouldShowAboutDialog()) {
            show(Dialogs.Type.ABOUT);
        }
        checkDefaultSmsApp();
        setupStrictMode();
        App.register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            App.unregister(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about:
                show(Dialogs.Type.ABOUT);
                return true;
            case R.id.menu_reset:
                show(Dialogs.Type.RESET);
                return true;
            case R.id.menu_view_log:
                show(Dialogs.Type.VIEW_LOG);

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data + ")");

        switch (requestCode) {
            case REQUEST_CHANGE_DEFAULT_SMS_PACKAGE: {
                preferences.setSeenSmsDefaultPackageChangeDialog();

                if (preferences.getSmsDefaultPackage() != null) {
                    startRestore();
                }
                break;
            }
            case REQUEST_WEB_AUTH: {
                if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, R.string.ui_dialog_access_token_error_msg, LENGTH_LONG).show();
                    return;
                }

                final String code = data == null ? null : data.getStringExtra(OAuth2WebAuthActivity.EXTRA_CODE);
                if (!TextUtils.isEmpty(code)) {
                    show(Dialogs.Type.ACCESS_TOKEN);
                    new OAuth2CallbackTask(oauth2Client).execute(code);
                } else {
                    show(Dialogs.Type.ACCESS_TOKEN_ERROR);
                }
                break;
            }
            case REQUEST_PICK_ACCOUNT: {
                if (resultCode == RESULT_OK && data != null) {
                    if (AccountManagerAuthActivity.ACTION_ADD_ACCOUNT.equals(data.getAction())) {
                        handleAccountManagerAuth(data);
                    } else if (AccountManagerAuthActivity.ACTION_FALLBACKAUTH.equals(data.getAction())) {
                        handleFallbackAuth();
                    }
                } else if (LOCAL_LOGV) {
                    Log.v(TAG, "request canceled, result="+resultCode);
                }
                break;
            }
        }
    }

    @Subscribe public void restoreStateChanged(final RestoreState newState) {
        if (isSmsBackupDefaultSmsApp() && newState.isFinished()) {
            restoreDefaultSmsProvider(preferences.getSmsDefaultPackage());
        }
    }

    @Subscribe public void onOAuth2Callback(OAuth2CallbackTask.OAuth2CallbackEvent event) {
        if (event.valid()) {
            authPreferences.setOauth2Token(event.token.userName, event.token.accessToken, event.token.refreshToken);
            onAuthenticated();
        } else {
            show(Dialogs.Type.ACCESS_TOKEN_ERROR);
        }
    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setSubtitle(getCurrentTitle());
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() > 0);
    }

    private CharSequence getCurrentTitle() {
        final int entryCount = getSupportFragmentManager().getBackStackEntryCount();
        if (entryCount == 0) {
            return null;
        } else {
            final BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(entryCount - 1);
            return entry.getBreadCrumbTitle();
        }
    }

    private void onAuthenticated() {
        // Invite use to perform a backup, but only once
        if (preferences.isFirstUse()) {
            show(Dialogs.Type.FIRST_SYNC);
        }
    }

    private void initiateRestore() {
        if (checkLoginInformation()) {
            startRestore();
        }
    }

    private void initiateBackup() {
        if (checkLoginInformation()) {
            if (preferences.isFirstBackup()) {
                show(Dialogs.Type.FIRST_SYNC);
            } else {
                startBackup(false);
            }
        }
    }

    private boolean checkLoginInformation() {
        if (!authPreferences.isLoginInformationSet()) {
            show(Dialogs.Type.MISSING_CREDENTIALS);
            return false;
        } else {
            return true;
        }
    }

    @Subscribe
    public void performAction(Actions act) {
        performAction(act, preferences.confirmAction());
    }

    private void performAction(Actions act, boolean needConfirm) {
        if (needConfirm) {
            this.mActions = act;
            show(Dialogs.Type.CONFIRM_ACTION);
        } else {
            if (Actions.Backup.equals(act)) {
                initiateBackup();
            } else if (Actions.Restore.equals(act)) {
                initiateRestore();
            }
        }
    }

    private void startBackup(boolean skip) {
        final Intent intent = new Intent(this, SmsBackupService.class);
        if (preferences.isFirstBackup()) {
            intent.putExtra(Consts.KEY_SKIP_MESSAGES, skip);
        }
        intent.putExtra(BackupType.EXTRA, BackupType.MANUAL.name());
        startService(intent);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void startRestore() {
        final Intent intent = new Intent(this, SmsRestoreService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (isSmsBackupDefaultSmsApp()) {
                startService(intent);
            } else {
                String defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this);
                Log.d(TAG, "default SMS package: " + defaultSmsPackage);
                preferences.setSmsDefaultPackage(defaultSmsPackage);

                if (preferences.hasSeenSmsDefaultPackageChangeDialog()) {
                    requestDefaultSmsPackageChange();
                } else {
                    show(Dialogs.Type.SMS_DEFAULT_PACKAGE_CHANGE);
                }
            }
        } else {
            startService(intent);
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean isSmsBackupDefaultSmsApp() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));
    }



    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference preference) {
        final Fragment fragment = Fragment.instantiate(this, preference.getFragment());
        Bundle args = new Bundle();
        args.putString(SCREEN_TITLE, String.valueOf(preference.getTitle()));
        fragment.setArguments(args);
        showFragment(fragment, preference.getKey());
        return true;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen preference) {
        // API level 9 compatibility
        if (preference.getFragment() == null) {
            preference.setFragment(preference.getKey());
            return onPreferenceStartFragment(caller, preference);
        }
        return false;
    }

    private void showFragment(Fragment fragment, String rootKey) {
        Bundle args = fragment.getArguments() == null ? new Bundle() : fragment.getArguments();
        args.putString(ARG_PREFERENCE_ROOT, rootKey);
        fragment.setArguments(args);
        FragmentTransaction tx = getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.preferences_container, fragment, rootKey);
        if (rootKey != null) {
            tx.addToBackStack(null);
            tx.setTransition(TRANSIT_FRAGMENT_OPEN);
            tx.setBreadCrumbTitle(args.getString(SCREEN_TITLE));
        }
        tx.commit();
    }

    private void show(Dialogs.Type d) {
        d.instantiate(this).show(getSupportFragmentManager(), null);
    }

    @Subscribe public void onConnect(ConnectEvent event) {
        if (event.connect) {
            startActivityForResult(new Intent(MainActivity.this,
                    AccountManagerAuthActivity.class), REQUEST_PICK_ACCOUNT);
        } else {
            show(Dialogs.Type.DISCONNECT);
        }
    }

    @TargetApi(11) @SuppressWarnings({"ConstantConditions", "PointlessBooleanExpression"})
    private void setupStrictMode() {
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 11) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
//                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyFlashScreen()
                    .build());
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void requestDefaultSmsPackageChange() {
        final Intent changeIntent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());

        startActivityForResult(changeIntent, REQUEST_CHANGE_DEFAULT_SMS_PACKAGE);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void restoreDefaultSmsProvider(String smsPackage) {
        Log.d(TAG, "restoring SMS provider "+smsPackage);
        if (!TextUtils.isEmpty(smsPackage)) {
            final Intent changeDefaultIntent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, smsPackage);

            startActivity(changeDefaultIntent);
        }
    }

    private void handleAccountManagerAuth(Intent data) {
        String token = data.getStringExtra(AccountManagerAuthActivity.EXTRA_TOKEN);
        String account = data.getStringExtra(AccountManagerAuthActivity.EXTRA_ACCOUNT);
        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(account)) {
            authPreferences.setOauth2Token(account, token, null);
            onAuthenticated();
        } else {
            String error = data.getStringExtra(AccountManagerAuthActivity.EXTRA_ERROR);
            if (!TextUtils.isEmpty(error)) {
                show(Dialogs.Type.ACCOUNT_MANAGER_TOKEN_ERROR);
            }
        }
    }

    private void handleFallbackAuth() {
        show(Dialogs.Type.CONNECT);
    }

    private void checkDefaultSmsApp() {
        if (isSmsBackupDefaultSmsApp() && !SmsRestoreService.isServiceWorking()) {
            restoreDefaultSmsProvider(preferences.getSmsDefaultPackage());
        }
    }
}
