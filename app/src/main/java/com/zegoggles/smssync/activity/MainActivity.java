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
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony.Sms;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.Dialogs.WebConnect;
import com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity;
import com.zegoggles.smssync.activity.auth.OAuth2WebAuthActivity;
import com.zegoggles.smssync.activity.events.AccountAddedEvent;
import com.zegoggles.smssync.activity.events.AccountConnectionChangedEvent;
import com.zegoggles.smssync.activity.events.FallbackAuthEvent;
import com.zegoggles.smssync.activity.events.MissingPermissionsEvent;
import com.zegoggles.smssync.activity.events.PerformAction;
import com.zegoggles.smssync.activity.events.PerformAction.Actions;
import com.zegoggles.smssync.activity.events.ThemeChangedEvent;
import com.zegoggles.smssync.activity.fragments.MainSettings;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.compat.SmsReceiver;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.service.BackupType;
import com.zegoggles.smssync.service.SmsBackupService;
import com.zegoggles.smssync.service.SmsRestoreService;
import com.zegoggles.smssync.service.state.BackupState;
import com.zegoggles.smssync.service.state.RestoreState;
import com.zegoggles.smssync.tasks.OAuth2CallbackTask;
import com.zegoggles.smssync.utils.BundleBuilder;

import java.util.Arrays;
import java.util.List;

import static android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT;
import static android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME;
import static android.widget.Toast.LENGTH_LONG;
import static androidx.core.role.RoleManagerCompat.ROLE_SMS;
import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;
import static com.zegoggles.smssync.App.LOCAL_LOGV;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.App.post;
import static com.zegoggles.smssync.activity.AppPermission.allGranted;
import static com.zegoggles.smssync.activity.Dialogs.ConfirmAction.ACTION;
import static com.zegoggles.smssync.activity.Dialogs.FirstSync.MAX_ITEMS_PER_SYNC;
import static com.zegoggles.smssync.activity.Dialogs.Type.ABOUT;
import static com.zegoggles.smssync.activity.Dialogs.Type.ACCOUNT_MANAGER_TOKEN_ERROR;
import static com.zegoggles.smssync.activity.Dialogs.Type.CONFIRM_ACTION;
import static com.zegoggles.smssync.activity.Dialogs.Type.DISCONNECT;
import static com.zegoggles.smssync.activity.Dialogs.Type.FIRST_SYNC;
import static com.zegoggles.smssync.activity.Dialogs.Type.MISSING_CREDENTIALS;
import static com.zegoggles.smssync.activity.Dialogs.Type.OAUTH2_ACCESS_TOKEN_ERROR;
import static com.zegoggles.smssync.activity.Dialogs.Type.OAUTH2_ACCESS_TOKEN_PROGRESS;
import static com.zegoggles.smssync.activity.Dialogs.Type.RESET;
import static com.zegoggles.smssync.activity.Dialogs.Type.SMS_DEFAULT_PACKAGE_CHANGE;
import static com.zegoggles.smssync.activity.Dialogs.Type.VIEW_LOG;
import static com.zegoggles.smssync.activity.Dialogs.Type.WEB_CONNECT;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.ACTION_ADD_ACCOUNT;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.ACTION_FALLBACK_AUTH;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.EXTRA_ACCOUNT;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.EXTRA_TOKEN;
import static com.zegoggles.smssync.activity.events.PerformAction.Actions.Backup;
import static com.zegoggles.smssync.compat.SmsReceiver.isSmsBackupDefaultSmsApp;
import static com.zegoggles.smssync.service.BackupType.MANUAL;
import static com.zegoggles.smssync.service.BackupType.SKIP;

/**
 * This is the main activity showing the status of the SMS Sync service and
 * providing controls to configure it.
 */
public class MainActivity extends ThemeActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
        FragmentManager.OnBackStackChangedListener {
    static final int REQUEST_CHANGE_DEFAULT_SMS_PACKAGE = 1;
    private static final int REQUEST_PICK_ACCOUNT = 2;
    static final int REQUEST_WEB_AUTH = 3;
    private static final int REQUEST_PERMISSIONS_BACKUP_MANUAL = 4;
    private static final int REQUEST_PERMISSIONS_BACKUP_MANUAL_SKIP = 5;
    private static final int REQUEST_PERMISSIONS_BACKUP_SERVICE = 6;

    public static final String EXTRA_PERMISSIONS = "permissions";
    private static final String SCREEN_TITLE_RES = "titleRes";

    private Preferences preferences;
    private AuthPreferences authPreferences;
    private OAuth2Client oauth2Client;
    private Intent fallbackAuthIntent;
    private PreferenceTitles preferenceTitles;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        authPreferences = new AuthPreferences(this);
        oauth2Client = new OAuth2Client(authPreferences.getOAuth2ClientId());
        fallbackAuthIntent = new Intent(this, OAuth2WebAuthActivity.class).setData(oauth2Client.requestUrl());
        preferenceTitles = new PreferenceTitles(getResources(), R.xml.preferences);
        preferences = new Preferences(this);
        if (bundle == null) {
            showFragment(new MainSettings(), null);
        }
        if (preferences.shouldShowAboutDialog()) {
            showDialog(ABOUT);
        }
        checkDefaultSmsApp();
        requestPermissionsIfNeeded();
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.register(this);
    }

    @Override
    protected void onStop() {
        App.unregister(this);
        super.onStop();
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
                showDialog(ABOUT);
                return true;
            case R.id.menu_reset:
                showDialog(RESET);
                return true;
            case R.id.menu_view_log:
                showDialog(VIEW_LOG);

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data + ")");

        switch (requestCode) {
            case REQUEST_CHANGE_DEFAULT_SMS_PACKAGE: {
                if (resultCode == RESULT_CANCELED) break;
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
                    showDialog(OAUTH2_ACCESS_TOKEN_PROGRESS);
                    new OAuth2CallbackTask(oauth2Client).execute(code);
                } else {
                    showDialog(OAUTH2_ACCESS_TOKEN_ERROR);
                }
                break;
            }
            case REQUEST_PICK_ACCOUNT: {
                if (resultCode == RESULT_OK && data != null) {
                    if (ACTION_ADD_ACCOUNT.equals(data.getAction())) {
                        handleAccountManagerAuth(data);
                    } else if (ACTION_FALLBACK_AUTH.equals(data.getAction())) {
                        handleFallbackAuth(new FallbackAuthEvent(true));
                    }
                } else if (LOCAL_LOGV) {
                    Log.v(TAG, "request canceled, result=" + resultCode);
                }
                break;
            }
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference preference) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "onPreferenceStartFragment(" + preference + ")");
        }

        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                preference.getFragment());
        fragment.setArguments(new BundleBuilder().putInt(SCREEN_TITLE_RES, preferenceTitles.getTitleRes(preference.getKey())).build());

        showFragment(fragment, preference.getKey());
        return true;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen preference) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "onPreferenceStartScreen(" + preference + ")");
        }
        // API level 9 compatibility
        if (preference.getFragment() == null) {
            preference.setFragment(preference.getKey());
            return onPreferenceStartFragment(caller, preference);
        } else {
            return false;
        }
    }

    @Subscribe public void restoreStateChanged(final RestoreState newState) {
        if (newState.isFinished() && isSmsBackupDefaultSmsApp(this)) {
             restoreDefaultSmsProvider(preferences.getSmsDefaultPackage());
        }
    }

    @Subscribe public void backupStateChanged(final BackupState newState) {
        if ((newState.backupType == MANUAL || newState.backupType == SKIP) && newState.isPermissionException()) {
            ActivityCompat.requestPermissions(this,
                newState.getMissingPermissions(),
                newState.backupType == SKIP ? REQUEST_PERMISSIONS_BACKUP_MANUAL_SKIP : REQUEST_PERMISSIONS_BACKUP_MANUAL
            );
        }
    }

    @Subscribe public void onOAuth2Callback(OAuth2CallbackTask.OAuth2CallbackEvent event) {
        if (event.valid()) {
            authPreferences.setOauth2Token(event.token.userName, event.token.accessToken, event.token.refreshToken);
            App.post(new AccountAddedEvent());
        } else {
            showDialog(OAUTH2_ACCESS_TOKEN_ERROR);
        }
    }

    @Subscribe public void onConnect(AccountConnectionChangedEvent event) {
        if (event.connected) {
            startActivityForResult(new Intent(this,
                    AccountManagerAuthActivity.class), REQUEST_PICK_ACCOUNT);
        } else {
            showDialog(DISCONNECT);
        }
    }

    @Subscribe public void handleFallbackAuth(FallbackAuthEvent event) {
        if (event.showDialog) {
            showDialog(WEB_CONNECT);
        } else {
            startActivityForResult(fallbackAuthIntent, REQUEST_WEB_AUTH);
        }
    }

    @Subscribe public void themeChangedEvent(ThemeChangedEvent event) {
        recreate();
    }

    @Override public void onBackStackChanged() {
        if (getSupportActionBar() == null) return;
        getSupportActionBar().setSubtitle(getCurrentTitle());
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() > 0);
    }

    @Override protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        onBackStackChanged();
    }

    private @StringRes int getCurrentTitle() {
        final int entryCount = getSupportFragmentManager().getBackStackEntryCount();
        if (entryCount == 0) {
            return 0;
        } else {
            final FragmentManager.BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(entryCount - 1);
            return entry.getBreadCrumbTitleRes();
        }
    }

    @Subscribe public void performAction(PerformAction action) {
        if (authPreferences.isLoginInformationSet()) {
            if (action.confirm) {
                showDialog(CONFIRM_ACTION, new BundleBuilder().putString(ACTION, action.action.name()).build());
            } else if (preferences.isFirstBackup() && action.action == Backup) {
                showDialog(FIRST_SYNC);
            } else {
                doPerform(action.action);
            }
        } else {
            showDialog(MISSING_CREDENTIALS);
        }
    }

    @Subscribe public void doPerform(Actions action) {
        switch (action) {
            case Backup:
            case BackupSkip:
                startBackup(action == Backup ? MANUAL : SKIP);
                break;
            case Restore:
                startRestore();
                break;
        }
    }

    private void startBackup(BackupType backupType) {
        startService(new Intent(this, SmsBackupService.class).setAction(backupType.name()));
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void startRestore() {
        final Intent intent = new Intent(this, SmsRestoreService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (isSmsBackupDefaultSmsApp(this)) {
                startService(intent);
            } else {
                final String defaultSmsPackage = Sms.getDefaultSmsPackage(this);
                Log.d(TAG, "default SMS package: " + defaultSmsPackage);
                if (!TextUtils.isEmpty(defaultSmsPackage)) {
                    preferences.setSmsDefaultPackage(defaultSmsPackage);
                    if (preferences.hasSeenSmsDefaultPackageChangeDialog()) {
                        requestDefaultSmsPackageChange();
                    } else {
                        showDialog(SMS_DEFAULT_PACKAGE_CHANGE);
                    }
                } else {
                    // no default package – running on tablet?
                    Toast.makeText(this, R.string.error_no_sms_default_package, LENGTH_LONG).show();
                }
            }
        } else {
            startService(intent);
        }
    }

    private void showFragment(@NonNull Fragment fragment, @Nullable String rootKey) {
        Bundle args = fragment.getArguments() == null ? new Bundle() : fragment.getArguments();
        args.putString(ARG_PREFERENCE_ROOT, rootKey);
        fragment.setArguments(args);
        FragmentTransaction tx = getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.preferences_container, fragment, rootKey);
        if (rootKey != null) {
            tx.addToBackStack(null);
            tx.setBreadCrumbTitle(args.getInt(SCREEN_TITLE_RES));
        }
        tx.commit();
    }

    private void showDialog(Dialogs.Type dialog) {
        final Bundle arguments = new Bundle();
        switch (dialog) {
            case FIRST_SYNC:
                arguments.putInt(MAX_ITEMS_PER_SYNC, preferences.getMaxItemsPerSync()); break;
            case WEB_CONNECT:
                arguments.putParcelable(WebConnect.INTENT, fallbackAuthIntent); break;
            case MISSING_CREDENTIALS:
            case SMS_DEFAULT_PACKAGE_CHANGE:
                break;
        }
        showDialog(dialog, arguments);
    }

    private void showDialog(@NonNull Dialogs.Type dialog, @Nullable Bundle args) {
        dialog.instantiate(getSupportFragmentManager(), args).show(getSupportFragmentManager(), dialog.name());
    }

    void requestDefaultSmsPackageChange() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (roleManager != null && !roleManager.isRoleHeld(ROLE_SMS)) {
                SmsReceiver.enable(this);
                Intent intent = roleManager.createRequestRoleIntent(ROLE_SMS);
                startActivityForResult(intent, REQUEST_CHANGE_DEFAULT_SMS_PACKAGE);
            }
        } else {
            Intent intent = new Intent(ACTION_CHANGE_DEFAULT).putExtra(EXTRA_PACKAGE_NAME, getPackageName());
            startActivityForResult(intent, REQUEST_CHANGE_DEFAULT_SMS_PACKAGE);
        }
    }

    private void restoreDefaultSmsProvider(String smsPackage) {
        Log.d(TAG, "restoring SMS provider "+smsPackage);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // release role by disabling receiver:
            // this will kill the app if the permission is revoked
            SmsReceiver.disable(this);
        } else if (!TextUtils.isEmpty(smsPackage)) {
            final Intent intent = new Intent(ACTION_CHANGE_DEFAULT).putExtra(EXTRA_PACKAGE_NAME, smsPackage);
            startActivity(intent);
        }
    }

    private void handleAccountManagerAuth(@NonNull Intent data) {
        final String token = data.getStringExtra(EXTRA_TOKEN);
        final String account = data.getStringExtra(EXTRA_ACCOUNT);
        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(account)) {
            authPreferences.setOauth2Token(account, token, null);
            App.post(new AccountAddedEvent());
        } else {
            String error = data.getStringExtra(AccountManagerAuthActivity.EXTRA_ERROR);
            if (!TextUtils.isEmpty(error)) {
                showDialog(ACCOUNT_MANAGER_TOKEN_ERROR);
            }
        }
    }

    private void checkDefaultSmsApp() {
        if (isSmsBackupDefaultSmsApp(this) && SmsRestoreService.isServiceIdle()) {
            restoreDefaultSmsProvider(preferences.getSmsDefaultPackage());
        }
    }

    private void requestPermissionsIfNeeded() {
        final Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_PERMISSIONS)) {
            final String[] permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS);
            Log.v(TAG, "requesting permissions "+ Arrays.toString(permissions));
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_BACKUP_SERVICE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.v(TAG, "onRequestPermissionsResult("+requestCode+ ","+ Arrays.toString(permissions) +","+ Arrays.toString(grantResults));
        switch (requestCode) {
            case REQUEST_PERMISSIONS_BACKUP_MANUAL:
            case REQUEST_PERMISSIONS_BACKUP_MANUAL_SKIP:
                if (allGranted(grantResults)) {
                    startBackup(requestCode == REQUEST_PERMISSIONS_BACKUP_MANUAL ? MANUAL : SKIP);
                } else {
                    final List<AppPermission> missing = AppPermission.from(permissions, grantResults);
                    Log.w(TAG, "not all permissions granted: "+missing);
                    post(new MissingPermissionsEvent(missing));
                }
                break;
            case REQUEST_PERMISSIONS_BACKUP_SERVICE:
                if (allGranted(grantResults)) {
                    startBackup(MANUAL);
                } else {
                    post(new MissingPermissionsEvent(AppPermission.from(permissions, grantResults)));
                }
                break;
         }
    }
}
