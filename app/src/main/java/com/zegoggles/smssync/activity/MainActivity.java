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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.BuildConfig;
import com.zegoggles.smssync.Consts;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity;
import com.zegoggles.smssync.activity.auth.OAuth2WebAuthActivity;
import com.zegoggles.smssync.activity.donation.DonationActivity;
import com.zegoggles.smssync.auth.OAuth2Client;
import com.zegoggles.smssync.calendar.CalendarAccessor;
import com.zegoggles.smssync.contacts.ContactAccessor;
import com.zegoggles.smssync.mail.BackupImapStore;
import com.zegoggles.smssync.mail.DataType;
import com.zegoggles.smssync.preferences.AuthMode;
import com.zegoggles.smssync.preferences.AuthPreferences;
import com.zegoggles.smssync.preferences.BackupManagerWrapper;
import com.zegoggles.smssync.preferences.Preferences;
import com.zegoggles.smssync.receiver.SmsBroadcastReceiver;
import com.zegoggles.smssync.service.Alarms;
import com.zegoggles.smssync.service.BackupType;
import com.zegoggles.smssync.service.SmsBackupService;
import com.zegoggles.smssync.service.SmsRestoreService;
import com.zegoggles.smssync.service.state.RestoreState;
import com.zegoggles.smssync.tasks.OAuth2CallbackTask;
import com.zegoggles.smssync.utils.AppLog;
import com.zegoggles.smssync.utils.ListPreferenceHelper;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.mail.DataType.CALLLOG;
import static com.zegoggles.smssync.mail.DataType.MMS;
import static com.zegoggles.smssync.mail.DataType.SMS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.BACKUP_CONTACT_GROUP;
import static com.zegoggles.smssync.preferences.Preferences.Keys.BACKUP_SETTINGS_SCREEN;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CALLLOG_SYNC_CALENDAR_ENABLED;
import static com.zegoggles.smssync.preferences.Preferences.Keys.CONNECTED;
import static com.zegoggles.smssync.preferences.Preferences.Keys.DONATE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.ENABLE_AUTO_BACKUP;
import static com.zegoggles.smssync.preferences.Preferences.Keys.IMAP_SETTINGS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.INCOMING_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_RESTORE;
import static com.zegoggles.smssync.preferences.Preferences.Keys.MAX_ITEMS_PER_SYNC;
import static com.zegoggles.smssync.preferences.Preferences.Keys.REGULAR_TIMEOUT_SECONDS;
import static com.zegoggles.smssync.preferences.Preferences.Keys.WIFI_ONLY;

/**
 * This is the main activity showing the status of the SMS Sync service and
 * providing controls to configure it.
 */
public class MainActivity extends PreferenceActivity {
    public static final int MIN_VERSION_MMS = Build.VERSION_CODES.ECLAIR;
    public static final int MIN_VERSION_BACKUP = Build.VERSION_CODES.FROYO;

    private static final int REQUEST_CHANGE_DEFAULT_SMS_PACKAGE = 1;
    private static final int REQUEST_PICK_ACCOUNT = 2;
    private static final int REQUEST_WEB_AUTH = 3;

    enum Actions {
        Backup,
        Restore
    }
    private Actions mActions;

    private AuthPreferences authPreferences;
    private Preferences preferences;
    private StatusPreference statusPref;
    private OAuth2Client oauth2Client;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        authPreferences = new AuthPreferences(this);
        oauth2Client = new OAuth2Client(authPreferences.getOAuth2ClientId());
        preferences = new Preferences(this);
        addPreferencesFromResource(R.xml.preferences);

        statusPref = new StatusPreference(this);
        statusPref.setOrder(-1);
        getPreferenceScreen().addPreference(statusPref);

        int version = Build.VERSION.SDK_INT;
        if (version < MIN_VERSION_MMS) {
            CheckBoxPreference backupMms = (CheckBoxPreference) findPreference(MMS.backupEnabledPreference);
            backupMms.setEnabled(false);
            backupMms.setChecked(false);
            backupMms.setSummary(R.string.ui_backup_mms_not_supported);
        }
        if (preferences.shouldShowUpgradeMessage()) show(Dialogs.UPGRADE_FROM_SMSBACKUP);
        setPreferenceListeners(version >= MIN_VERSION_BACKUP);

        checkAndDisplayDroidWarning();

        preferences.migrateMarkAsRead();

        if (preferences.shouldShowAboutDialog()) {
            show(Dialogs.ABOUT);
        }

        checkDefaultSmsApp();

        setupStrictMode();
        App.bus.register(this);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");

        super.onResume();
        initCalendars();
        initGroups();

        updateLastBackupTimes();
        updateAutoBackupSummary();
        updateAutoBackupEnabledSummary();
        updateBackupContactGroupLabelFromPref();
        updateCallLogCalendarLabelFromPref();
        updateImapFolderLabelFromPref();
        updateImapCallogFolderLabelFromPref();
        updateUsernameLabel(null);
        updateMaxItemsPerSync(null);
        updateMaxItemsPerRestore(null);

        updateImapSettings(!authPreferences.useXOAuth());
        checkUserDonationStatus();
        App.bus.register(statusPref);
    }

    @Override protected void onPause() {
        super.onPause();
        App.bus.unregister(statusPref);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            App.bus.unregister(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        android.view.MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about:
                show(Dialogs.ABOUT);
                return true;
            case R.id.menu_reset:
                show(Dialogs.RESET);
                return true;
            case R.id.menu_view_log:
                show(Dialogs.VIEW_LOG);

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data + ")");
        if (resultCode == RESULT_CANCELED) return;

        switch (requestCode) {
            case REQUEST_CHANGE_DEFAULT_SMS_PACKAGE: {
                preferences.setSeenSmsDefaultPackageChangeDialog();

                if (preferences.getSmsDefaultPackage() != null) {
                    startRestore();
                }
                break;
            }
            case REQUEST_WEB_AUTH: {
                final String code = data.getStringExtra(OAuth2WebAuthActivity.EXTRA_CODE);
                if (!TextUtils.isEmpty(code)) {
                    show(Dialogs.ACCESS_TOKEN);
                    new OAuth2CallbackTask(oauth2Client).execute(code);
                } else {
                    show(Dialogs.ACCESS_TOKEN_ERROR);
                }
                break;
            }
            case REQUEST_PICK_ACCOUNT: {
                if (AccountManagerAuthActivity.ACTION_ADD_ACCOUNT.equals(data.getAction())) {
                    handleAccountManagerAuth(data);
                } else if (AccountManagerAuthActivity.ACTION_FALLBACKAUTH.equals(data.getAction())) {
                    handleFallbackAuth();
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
        dismiss(Dialogs.ACCESS_TOKEN);
        if (event.valid()) {
            authPreferences.setOauth2Token(event.token.userName, event.token.accessToken, event.token.refreshToken);
            onAuthenticated();
        } else {
            show(Dialogs.ACCESS_TOKEN_ERROR);
        }
    }

    private void onAuthenticated() {
        updateConnected();
        // Invite use to perform a backup, but only once
        if (preferences.isFirstUse()) {
            show(Dialogs.FIRST_SYNC);
        }
    }

    String getLastSyncText(final long lastSync) {
        return getString(R.string.status_idle_details,
                lastSync < 0 ? getString(R.string.status_idle_details_never) :
                DateFormat.getDateTimeInstance().format(new Date(lastSync)));

    }

    private void updateLastBackupTimes() {
        for (DataType type : DataType.values()) {
            findPreference(type.backupEnabledPreference).setSummary(
                getLastSyncText(type.getMaxSyncedDate(this))
            );
        }
    }

    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private void updateAutoBackupEnabledSummary() {
        findPreference(ENABLE_AUTO_BACKUP.key).setSummary(getEnabledBackupSummary());
        for (DataType t : DataType.values()) {
            addSummaryListener(new Runnable() {
                public void run() {
                    updateAutoBackupEnabledSummary();
                }
            }, t.backupEnabledPreference);
        }
    }

    protected String getEnabledBackupSummary() {
        final List<String> enabled = new ArrayList<String>();
        for (DataType dataType : DataType.enabled(this)) {
            enabled.add(getString(dataType.resId));
        }
        StringBuilder summary = new StringBuilder();
        if (!enabled.isEmpty()) {
            summary.append(getString(R.string.ui_enable_auto_sync_summary, TextUtils.join(", ", enabled)));
            if (!getConnectivityManager().getBackgroundDataSetting()) {
                summary.append(' ').append(getString(R.string.ui_enable_auto_sync_bg_data));
            }
            if (preferences.isInstalledOnSDCard()) {
                summary.append(' ').append(getString(R.string.sd_card_disclaimer));
            }
        } else {
            summary.append(getString(R.string.ui_enable_auto_sync_no_enabled_summary));
        }
        return summary.toString();
    }

    private void updateAutoBackupSummary() {
        final Preference autoBackup = findPreference(BACKUP_SETTINGS_SCREEN.key);
        final StringBuilder summary = new StringBuilder();

        final ListPreference regSchedule = (ListPreference)
                findPreference(REGULAR_TIMEOUT_SECONDS.key);

        final ListPreference incomingSchedule = (ListPreference)
                findPreference(INCOMING_TIMEOUT_SECONDS.key);

        summary.append(regSchedule.getTitle())
                .append(": ")
                .append(regSchedule.getEntry())
                .append(", ")
                .append(incomingSchedule.getTitle())
                .append(": ")
                .append(incomingSchedule.getEntry());

        if (preferences.isWifiOnly()) {
            summary.append(" (")
                    .append(findPreference(WIFI_ONLY.key).getTitle())
                    .append(")");
        }

        autoBackup.setSummary(summary.toString());

        addSummaryListener(new Runnable() {
            public void run() {
                updateAutoBackupSummary();
            }
        }, INCOMING_TIMEOUT_SECONDS.key,
                REGULAR_TIMEOUT_SECONDS.key,
                WIFI_ONLY.key);
    }

    private void addSummaryListener(final Runnable r, String... prefs) {
        for (String p : prefs) {
            findPreference(p).setOnPreferenceChangeListener(
                    new OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, final Object newValue) {
                            new Handler().post(new Runnable() {
                                @Override
                                public void run() {
                                    r.run();
                                    onContentChanged();
                                }
                            });
                            return true;
                        }
                    });
        }
    }

    private void updateUsernameLabel(String username) {
        if (username == null) {
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            username = prefs.getString(AuthPreferences.LOGIN_USER, getString(R.string.ui_login_label));
        }
        Preference pref = findPreference(AuthPreferences.LOGIN_USER);
        pref.setTitle(username);
    }

    private void updateBackupContactGroupLabelFromPref() {
        final ListPreference groupPref = (ListPreference)
                findPreference(BACKUP_CONTACT_GROUP.key);

        groupPref.setTitle(groupPref.getEntry() != null ? groupPref.getEntry() :
                getString(R.string.ui_backup_contact_group_label));
    }

    private void updateCallLogCalendarLabelFromPref() {
        final ListPreference calendarPref = (ListPreference)
                findPreference(CALLLOG_SYNC_CALENDAR.key);

        calendarPref.setTitle(calendarPref.getEntry() != null ? calendarPref.getEntry() :
                getString(R.string.ui_backup_calllog_sync_calendar_label));
    }

    private void updateImapFolderLabelFromPref() {
        String imapFolder = SMS.getFolder(this);
        Preference pref = findPreference(SMS.folderPreference);
        pref.setTitle(imapFolder);
    }

    private void updateImapCallogFolderLabelFromPref() {
        String imapFolder = CALLLOG.getFolder(this);
        Preference pref = findPreference(CALLLOG.folderPreference);
        pref.setTitle(imapFolder);
    }

    private void initGroups() {
        ContactAccessor contacts = ContactAccessor.Get.instance();
        ListPreferenceHelper.initListPreference((ListPreference) findPreference(BACKUP_CONTACT_GROUP.key),
                contacts.getGroups(getContentResolver(), getResources()), false);
    }

    private void initCalendars() {
        final ListPreference calendarPref = (ListPreference)
                findPreference(CALLLOG_SYNC_CALENDAR.key);
        CalendarAccessor calendars = CalendarAccessor.Get.instance(getContentResolver());
        boolean enabled = ListPreferenceHelper.initListPreference(calendarPref, calendars.getCalendars(), false);

        findPreference(CALLLOG_SYNC_CALENDAR_ENABLED.key).setEnabled(enabled);
    }

    private void initiateRestore() {
        if (checkLoginInformation()) {
            startRestore();
        }
    }

    private void initiateBackup() {
        if (checkLoginInformation()) {
            if (preferences.isFirstBackup()) {
                show(Dialogs.FIRST_SYNC);
            } else {
                startBackup(false);
            }
        }
    }

    private boolean checkLoginInformation() {
        if (!authPreferences.isLoginInformationSet()) {
            show(Dialogs.MISSING_CREDENTIALS);
            return false;
        } else {
            return true;
        }
    }

    void performAction(Actions act) {
        performAction(act, preferences.confirmAction());
    }

    private void performAction(Actions act, boolean needConfirm) {
        if (needConfirm) {
            this.mActions = act;
            show(Dialogs.CONFIRM_ACTION);
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
                    show(Dialogs.SMS_DEFAULT_PACKAGE_CHANGE);
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
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        switch (Dialogs.values()[id]) {
            case VIEW_LOG:
                View view = dialog.findViewById(AppLog.ID);
                if (view instanceof TextView) {
                    AppLog.readLog(App.LOG, (TextView) view);
                }
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        String title, msg;
        switch (Dialogs.values()[id]) {
            case MISSING_CREDENTIALS:
                title = getString(R.string.ui_dialog_missing_credentials_title);
                msg = authPreferences.useXOAuth() ?
                        getString(R.string.ui_dialog_missing_credentials_msg_xoauth) :
                        getString(R.string.ui_dialog_missing_credentials_msg_plain);
                break;
            case INVALID_IMAP_FOLDER:
                title = getString(R.string.ui_dialog_invalid_imap_folder_title);
                msg = getString(R.string.ui_dialog_invalid_imap_folder_msg);
                break;
            case FIRST_SYNC:
                DialogInterface.OnClickListener firstSyncListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startBackup(which == DialogInterface.BUTTON2);
                            }
                        };
                final int maxItems = preferences.getMaxItemsPerSync();
                final String syncMsg = maxItems < 0 ?
                        getString(R.string.ui_dialog_first_sync_msg) :
                        getString(R.string.ui_dialog_first_sync_msg_batched, maxItems);

                return new AlertDialog.Builder(this)
                        .setTitle(R.string.ui_dialog_first_sync_title)
                        .setMessage(syncMsg)
                        .setPositiveButton(R.string.ui_sync, firstSyncListener)
                        .setNegativeButton(R.string.ui_skip, firstSyncListener)
                        .create();
            case ABOUT:
                View contentView = getLayoutInflater().inflate(R.layout.about_dialog, null, false);
                WebView webView = (WebView) contentView.findViewById(R.id.about_content);
                webView.setWebViewClient(new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)));
                        return true;

                    }
                });
                webView.loadUrl("file:///android_asset/about.html");

                return new AlertDialog.Builder(this)
                        .setCustomTitle(null)
                        .setPositiveButton(android.R.string.ok, null)
                        .setView(contentView)
                        .create();

            case VIEW_LOG:
                return AppLog.displayAsDialog(App.LOG, this);

            case RESET:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.ui_dialog_reset_title)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                reset();
                                dismissDialog(id);
                            }
                        })
                        .setMessage(R.string.ui_dialog_reset_message)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();

            case REQUEST_TOKEN:
                ProgressDialog req = new ProgressDialog(this);
                req.setTitle(null);
                req.setMessage(getString(R.string.ui_dialog_request_token_msg));
                req.setIndeterminate(true);
                req.setCancelable(false);
                return req;
            case ACCESS_TOKEN:
                ProgressDialog acc = new ProgressDialog(this);
                acc.setTitle(null);
                acc.setMessage(getString(R.string.ui_dialog_access_token_msg));
                acc.setIndeterminate(true);
                acc.setCancelable(false);
                return acc;
            case ACCESS_TOKEN_ERROR:
                title = getString(R.string.ui_dialog_access_token_error_title);
                msg = getString(R.string.ui_dialog_access_token_error_msg);
                break;
            case CONNECT:
                return new AlertDialog.Builder(this)
                        .setCustomTitle(null)
                        .setMessage(getString(R.string.ui_dialog_connect_msg, getString(R.string.app_name)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            startActivityForResult(new Intent(MainActivity.this, OAuth2WebAuthActivity.class)
                                .setData(oauth2Client.requestUrl()), REQUEST_WEB_AUTH);

                                dismissDialog(id);
                            }
                        }).create();
            case CONNECT_TOKEN_ERROR:
                return new AlertDialog.Builder(this)
                        .setCustomTitle(null)
                        .setMessage(R.string.ui_dialog_connect_token_error)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        }).create();

            case ACCOUNTMANAGER_TOKEN_ERROR:
                return new AlertDialog.Builder(this)
                        .setCustomTitle(null)
                        .setMessage(R.string.ui_dialog_account_manager_token_error)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                handleFallbackAuth();
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .create();
            case DISCONNECT:
                return new AlertDialog.Builder(this)
                        .setCustomTitle(null)
                        .setMessage(R.string.ui_dialog_disconnect_msg)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                authPreferences.clearOAuth1Data();
                                authPreferences.clearOauth2Data();
                                DataType.clearLastSyncData(MainActivity.this);
                                updateConnected();
                            }
                        }).create();
            case UPGRADE_FROM_SMSBACKUP:
                title = getString(R.string.ui_dialog_upgrade_title);
                msg = getString(R.string.ui_dialog_upgrade_msg);
                break;
            case BROKEN_DROIDX:
                title = getString(R.string.ui_dialog_brokendroidx_title);
                msg = getString(R.string.ui_dialog_brokendroidx_msg);
                break;
            case CONFIRM_ACTION:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.ui_dialog_confirm_action_title)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (MainActivity.this.mActions != null) {
                                    performAction(MainActivity.this.mActions, false);
                                }
                            }
                        })
                        .setMessage(R.string.ui_dialog_confirm_action_msg)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
            case SMS_DEFAULT_PACKAGE_CHANGE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.ui_dialog_sms_default_package_change_title)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                requestDefaultSmsPackageChange();
                            }
                        })
                        .setMessage(R.string.ui_dialog_sms_default_package_change_msg)
                        .create();

            default:
                return null;
        }
        return createMessageDialog(id, title, msg);
    }

    private void reset() {
        DataType.clearLastSyncData(MainActivity.this);
        preferences.reset();
    }

    private Dialog createMessageDialog(final int id, String title, String msg) {
        return new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .create();
    }

    private void updateMaxItemsPerSync(String newValue) {
        updateMaxItems(MAX_ITEMS_PER_SYNC.key, preferences.getMaxItemsPerSync(), newValue);
    }

    private void updateMaxItemsPerRestore(String newValue) {
        updateMaxItems(MAX_ITEMS_PER_RESTORE.key, preferences.getMaxItemsPerRestore(), newValue);
    }

    private void updateMaxItems(String prefKey, int currentValue, String newValue) {
        Preference pref = findPreference(prefKey);
        if (newValue == null) {
            newValue = String.valueOf(currentValue);
        }
        // XXX
        pref.setTitle("-1".equals(newValue) ? getString(R.string.all_messages) : newValue);
    }

    private CheckBoxPreference updateConnected() {
        CheckBoxPreference connected = (CheckBoxPreference) findPreference(CONNECTED.key);

        connected.setEnabled(authPreferences.useXOAuth());
        connected.setChecked(authPreferences.hasOauthTokens() || authPreferences.hasOAuth2Tokens());

        final String username = authPreferences.getUsername();
        String summary = connected.isChecked() && !TextUtils.isEmpty(username) ?
                getString(R.string.gmail_already_connected, username) :
                getString(R.string.gmail_needs_connecting);
        connected.setSummary(summary);

        return connected;
    }

    private void show(Dialogs d) {
        showDialog(d.ordinal());
    }

    private void dismiss(Dialogs d) {
        try {
            dismissDialog(d.ordinal());
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

    private void updateImapSettings(boolean enabled) {
        findPreference(IMAP_SETTINGS.key).setEnabled(enabled);
    }

    private void setPreferenceListeners(boolean backup) {
        if (backup) {
            PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(
                    new SharedPreferences.OnSharedPreferenceChangeListener() {
                        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                            BackupManagerWrapper.dataChanged(MainActivity.this);
                        }
                    }
            );
        }

        findPreference(ENABLE_AUTO_BACKUP.key)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        boolean isEnabled = (Boolean) newValue;
                        final ComponentName componentName = new ComponentName(MainActivity.this,
                                SmsBroadcastReceiver.class);
                        getPackageManager().setComponentEnabledSetting(componentName,
                                isEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP);

                        if (!isEnabled) new Alarms(MainActivity.this).cancel();
                        return true;
                    }
                });

        findPreference(AuthPreferences.SERVER_AUTHENTICATION)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        final boolean plain = (AuthMode.PLAIN) ==
                                AuthMode.valueOf(newValue.toString().toUpperCase(Locale.ENGLISH));

                        updateConnected().setEnabled(!plain);
                        updateImapSettings(plain);
                        return true;
                    }
                });

        findPreference(MAX_ITEMS_PER_SYNC.key)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        updateMaxItemsPerSync(newValue.toString());
                        return true;
                    }
                });

        findPreference(MAX_ITEMS_PER_RESTORE.key)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        updateMaxItemsPerRestore(newValue.toString());
                        return true;
                    }
                });

        findPreference(AuthPreferences.LOGIN_USER)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        updateUsernameLabel(newValue.toString());
                        return true;
                    }
                });

        findPreference(AuthPreferences.LOGIN_PASSWORD)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        authPreferences.setImapPassword(newValue.toString());
                        return true;
                    }
                });

        updateConnected().setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object change) {
                boolean newValue = (Boolean) change;
                if (newValue) {
                    if (Build.VERSION.SDK_INT >= 5) {
                        // use account manager on newer phones
                        startActivityForResult(new Intent(MainActivity.this, AccountManagerAuthActivity.class), REQUEST_PICK_ACCOUNT);
                    } else {
                        // fall back to webview on older ones
                        handleFallbackAuth();
                    }
                } else {
                    show(Dialogs.DISCONNECT);
                }
                return false;
            }
        });

        findPreference(SMS.folderPreference)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, final Object newValue) {
                        String imapFolder = newValue.toString();

                        if (BackupImapStore.isValidImapFolder(imapFolder)) {
                            preference.setTitle(imapFolder);
                            return true;
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    show(Dialogs.INVALID_IMAP_FOLDER);
                                }
                            });
                            return false;
                        }
                    }
                });

        findPreference(CALLLOG.folderPreference)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, final Object newValue) {
                        String imapFolder = newValue.toString();

                        if (BackupImapStore.isValidImapFolder(imapFolder)) {
                            preference.setTitle(imapFolder);
                            return true;
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    show(Dialogs.INVALID_IMAP_FOLDER);
                                }
                            });
                            return false;
                        }
                    }
                });
    }

    private void checkUserDonationStatus() {
        try {
            DonationActivity.checkUserHasDonated(this, new DonationActivity.DonationStatusListener() {
                @Override
                public void userDonationState(State s) {
                    switch (s) {
                        case NOT_AVAILABLE:
                        case DONATED:
                            Preference donate = getPreferenceScreen().findPreference(DONATE.key);
                            if (donate != null) {
                                getPreferenceScreen().removePreference(donate);
                            }
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, e);
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

    private void checkAndDisplayDroidWarning() {
        if ("DROIDX".equals(Build.MODEL) ||
            "DROID2".equals(Build.MODEL) &&
            Build.VERSION.SDK_INT == Build.VERSION_CODES.FROYO &&
            !getPreferences(MODE_PRIVATE).getBoolean("droidx_warning_displayed", false)) {

            getPreferences(MODE_PRIVATE).edit().putBoolean("droidx_warning_displayed", true).commit();
            show(Dialogs.BROKEN_DROIDX);
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
                show(Dialogs.ACCOUNTMANAGER_TOKEN_ERROR);
            }
        }
    }

    private void handleFallbackAuth() {
        show(Dialogs.CONNECT);
    }

    private void checkDefaultSmsApp() {
        if (isSmsBackupDefaultSmsApp() && !SmsRestoreService.isServiceWorking()) {
            restoreDefaultSmsProvider(preferences.getSmsDefaultPackage());
        }
    }
}
