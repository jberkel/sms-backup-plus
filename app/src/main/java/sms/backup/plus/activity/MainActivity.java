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

package sms.backup.plus.activity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony.Sms;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.squareup.otto.Subscribe;
import sms.backup.plus.App;
import sms.backup.plus.R;
import sms.backup.plus.activity.Dialogs.WebConnect;
import sms.backup.plus.activity.events.AccountAddedEvent;
import sms.backup.plus.activity.events.FallbackAuthEvent;
import sms.backup.plus.activity.events.MissingPermissionsEvent;
import sms.backup.plus.activity.events.PerformAction;
import sms.backup.plus.activity.events.PerformAction.Actions;
import sms.backup.plus.activity.events.ThemeChangedEvent;
import sms.backup.plus.activity.fragments.MainSettings;
import sms.backup.plus.compat.SmsReceiver;
import sms.backup.plus.mail.DataType;
import sms.backup.plus.preferences.AuthPreferences;
import sms.backup.plus.preferences.Preferences;
import sms.backup.plus.service.BackupType;
import sms.backup.plus.service.SmsBackupService;
import sms.backup.plus.service.SmsRestoreService;
import sms.backup.plus.service.state.BackupState;
import sms.backup.plus.service.state.RestoreState;
import sms.backup.plus.utils.BundleBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT;
import static android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME;
import static android.widget.Toast.LENGTH_LONG;
import static androidx.core.role.RoleManagerCompat.ROLE_SMS;
import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;
import static sms.backup.plus.App.LOCAL_LOGV;
import static sms.backup.plus.App.TAG;
import static sms.backup.plus.App.post;
import static sms.backup.plus.activity.AppPermission.allGranted;
import static sms.backup.plus.activity.Dialogs.ConfirmAction.ACTION;
import static sms.backup.plus.activity.Dialogs.FirstSync.MAX_ITEMS_PER_SYNC;
import static sms.backup.plus.activity.Dialogs.Type.ABOUT;
import static sms.backup.plus.activity.Dialogs.Type.ACCOUNT_MANAGER_TOKEN_ERROR;
import static sms.backup.plus.activity.Dialogs.Type.CONFIRM_ACTION;
import static sms.backup.plus.activity.Dialogs.Type.DISCONNECT;
import static sms.backup.plus.activity.Dialogs.Type.FIRST_SYNC;
import static sms.backup.plus.activity.Dialogs.Type.MISSING_CREDENTIALS;
import static sms.backup.plus.activity.Dialogs.Type.OAUTH2_ACCESS_TOKEN_ERROR;
import static sms.backup.plus.activity.Dialogs.Type.OAUTH2_ACCESS_TOKEN_PROGRESS;
import static sms.backup.plus.activity.Dialogs.Type.RESET;
import static sms.backup.plus.activity.Dialogs.Type.SMS_DEFAULT_PACKAGE_CHANGE;
import static sms.backup.plus.activity.Dialogs.Type.VIEW_LOG;
import static sms.backup.plus.activity.Dialogs.Type.WEB_CONNECT;
import static sms.backup.plus.activity.events.PerformAction.Actions.Backup;
import static sms.backup.plus.compat.SmsReceiver.isSmsBackupDefaultSmsApp;
import static sms.backup.plus.service.BackupType.MANUAL;
import static sms.backup.plus.service.BackupType.SKIP;

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
    private static final int REQUEST_PERMISSIONS_STARTUP = 7;

    public static final String EXTRA_PERMISSIONS = "permissions";
    private static final String SCREEN_TITLE_RES = "titleRes";

    private Preferences preferences;
    private AuthPreferences authPreferences;
    private PreferenceTitles preferenceTitles;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        applyWindowInsets(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);

        authPreferences = new AuthPreferences(this);
        preferenceTitles = new PreferenceTitles(getResources(), R.xml.preferences);
        preferences = new Preferences(this);
        if (bundle == null) {
            showFragment(new MainSettings(), null);
        }
        if (preferences.shouldShowAboutDialog()) {
            showDialog(ABOUT);
        }
        checkDefaultSmsApp();
        requestPermissionsIfNeeded(bundle == null);
    }

    /**
     * Since {@code targetSdk} 35 Android forces edge-to-edge layout, so the toolbar would otherwise
     * draw behind the (now transparent) status bar, overlapping the clock and status icons. Pad the
     * toolbar down by the top system-bar inset (its background then colours the status bar area) and
     * keep the preference list clear of the bottom navigation bar. On older platforms the insets are
     * zero, so this is a no-op.
     */
    private void applyWindowInsets(Toolbar toolbar) {
        final View content = findViewById(R.id.preferences_container);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, v.getPaddingBottom());
            content.setPadding(bars.left, content.getPaddingTop(), bars.right, bars.bottom);
            return insets;
        });
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
        // AGP 8 makes R.id values non-final, so they can no longer be switch-case labels.
        final int id = item.getItemId();
        if (id == R.id.menu_about) {
            showDialog(ABOUT);
            return true;
        } else if (id == R.id.menu_reset) {
            showDialog(RESET);
            return true;
        } else if (id == R.id.menu_view_log) {
            showDialog(VIEW_LOG);
        }
        return super.onOptionsItemSelected(item);
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

                break;
            }
            case REQUEST_PICK_ACCOUNT: {
                if (resultCode == RESULT_OK && data != null) {

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

    private void checkDefaultSmsApp() {
        if (isSmsBackupDefaultSmsApp(this) && SmsRestoreService.isServiceIdle()) {
            restoreDefaultSmsProvider(preferences.getSmsDefaultPackage());
        }
    }

    private void requestPermissionsIfNeeded(boolean firstCreate) {
        final Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_PERMISSIONS)) {
            final String[] permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS);
            Log.v(TAG, "requesting permissions "+ Arrays.toString(permissions));
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_BACKUP_SERVICE);
        } else if (firstCreate) {
            requestStartupPermissions();
        }
    }

    /**
     * Proactively ask for every runtime permission the enabled backup types need, so the user
     * grants them once at launch instead of being interrupted when starting a backup. Permissions
     * that are already granted (or permanently denied) are skipped by the framework, so once they
     * have been handled this no longer prompts.
     */
    private void requestStartupPermissions() {
        final Set<String> missing = new LinkedHashSet<>();
        for (DataType dataType : preferences.getDataTypePreferences().enabled()) {
            missing.addAll(dataType.checkPermissions(this));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            Log.v(TAG, "requesting startup permissions " + missing);
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQUEST_PERMISSIONS_STARTUP);
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
            case REQUEST_PERMISSIONS_STARTUP:
                // grantResults is empty when the request was cancelled; only surface real denials
                if (grantResults.length > 0 && !allGranted(grantResults)) {
                    final List<AppPermission> denied = AppPermission.from(permissions, grantResults);
                    // POST_NOTIFICATIONS is optional (maps to UNKNOWN); it does not block a backup
                    denied.removeAll(Collections.singletonList(AppPermission.UNKNOWN));
                    if (!denied.isEmpty()) {
                        post(new MissingPermissionsEvent(denied));
                    }
                }
                break;
         }
    }
}
