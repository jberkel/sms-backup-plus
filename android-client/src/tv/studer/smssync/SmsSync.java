/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
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

package tv.studer.smssync;

import tv.studer.smssync.SmsSyncService.SmsSyncState;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * This is the main activity showing the status of the SMS Sync service and
 * providing controls to configure it.
 */
public class SmsSync extends PreferenceActivity implements OnPreferenceChangeListener {
    private static final int DIALOG_MISSING_CREDENTIALS = 1;

    private static final int DIALOG_ERROR_DESCRIPTION = 2;

    private static final int DIALOG_FIRST_SYNC = 3;
    
    private static final int DIALOG_SYNC_DATA_RESET = 4;
    
    private static final int DIALOG_INVALID_IMAP_FOLDER = 5;

    private static final int DIALOG_NEED_FIRST_MANUAL_SYNC = 6;

    private StatusPreference mStatusPref;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Need to set a value here so the dialog for the label is pre-filled with the default.
        if (!PrefStore.isImapFolderSet(this)) {
            PrefStore.setImapFolder(this, PrefStore.DEFAULT_IMAP_FOLDER);
        }
        if (!PrefStore.isEnableAutoSyncSet(this)) {
            PrefStore.setEnableAutoSync(this, PrefStore.DEFAULT_ENABLE_AUTO_SYNC);
        }
        
        super.onCreate(savedInstanceState);
        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(PrefStore.SHARED_PREFS_NAME);
        prefMgr.setSharedPreferencesMode(MODE_PRIVATE);

        addPreferencesFromResource(R.xml.main_screen);

        PreferenceCategory cat = new PreferenceCategory(this);
        cat.setOrder(0);
        getPreferenceScreen().addPreference(cat);
        mStatusPref = new StatusPreference(this);
        mStatusPref.setSelectable(false);
        cat.setTitle(R.string.ui_status_label);
        cat.addPreference(mStatusPref);

        Preference pref = prefMgr.findPreference(PrefStore.PREF_LOGIN_USER);
        pref.setOnPreferenceChangeListener(this);
        
        pref = prefMgr.findPreference(PrefStore.PREF_IMAP_FOLDER);
        pref.setOnPreferenceChangeListener(this);
        
        pref = prefMgr.findPreference(PrefStore.PREF_ENABLE_AUTO_SYNC);
        pref.setOnPreferenceChangeListener(this);
        
        pref = prefMgr.findPreference(PrefStore.PREF_LOGIN_PASSWORD);
        pref.setOnPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SmsSyncService.unsetStateChangeListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SmsSyncService.setStateChangeListener(mStatusPref);
        updateUsernameLabelFromPref();
        updateImapFolderLabelFromPref();
    }
    
    private void updateUsernameLabelFromPref() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String username = prefs.getString(PrefStore.PREF_LOGIN_USER,
                getString(R.string.ui_login_label));
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_LOGIN_USER);
        pref.setTitle(username);
    }
    
    private void updateImapFolderLabelFromPref() {
        String imapFolder = PrefStore.getImapFolder(this);
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_IMAP_FOLDER);
        pref.setTitle(imapFolder);
    }

    private boolean initiateSync() {
        if (!PrefStore.isLoginInformationSet(this)) {
            showDialog(DIALOG_MISSING_CREDENTIALS);
            return false;
        } else if (PrefStore.isFirstSync(this)) {
            showDialog(DIALOG_FIRST_SYNC);
            return false;
        } else {
            startSync(false);
            return true;
        }
    }
    
    private void startSync(boolean skip) {
        Intent intent = new Intent(this, SmsSyncService.class);
        if (PrefStore.isFirstSync(this)) {
            intent.putExtra(Consts.KEY_SKIP_MESSAGES, skip);
        }
        startService(intent);
    }

    private class StatusPreference extends Preference implements
            SmsSyncService.StateChangeListener, OnClickListener {
        private View mView;

        private Button mSyncButton;

        private TextView mStatusLabel;

        private ProgressBar mProgressBar;

        public StatusPreference(Context context) {
            super(context);
        }

        public void update() {
            stateChanged(SmsSyncService.getState(), SmsSyncService.getState());
        }

        @Override
        public void stateChanged(final SmsSyncState oldState, final SmsSyncState newState) {
            if (mView != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        CharSequence statusLabel;
                        mProgressBar.setIndeterminate(false);
                        mProgressBar.setProgress(0);
                        switch (newState) {
                            case AUTH_FAILED:
                                statusLabel = getText(R.string.status_auth_failure);
                                break;
                            case CALC:
                                statusLabel = getText(R.string.status_calc);
                                mProgressBar.setIndeterminate(true);
                                break;
                            case IDLE:
                                if (oldState == SmsSyncState.SYNC || oldState == SmsSyncState.CALC) {
                                    statusLabel = getText(R.string.status_done);
                                    mProgressBar.setMax(100);
                                    mProgressBar.setProgress(100);
                                } else {
                                    statusLabel = getText(R.string.status_idle);
                                }
                                break;
                            case LOGIN:
                                statusLabel = getText(R.string.status_login);
                                mProgressBar.setIndeterminate(true);
                                break;
                            case SYNC:
                                statusLabel = getString(R.string.status_sync, SmsSyncService
                                        .getCurrentSyncedItems(), SmsSyncService
                                        .getItemsToSyncCount());
                                mProgressBar.setMax(SmsSyncService.getItemsToSyncCount());
                                mProgressBar.setProgress(SmsSyncService.getCurrentSyncedItems());
                                break;
                            case GENERAL_ERROR:
                                statusLabel = getString(R.string.status_unknown_error);
                                showDialog(DIALOG_ERROR_DESCRIPTION);
                            default:
                                statusLabel = "";
                        } // switch (newStatus) { ... }
                        mStatusLabel.setText(statusLabel);

                        if (getOnPreferenceChangeListener() != null) {
                            getOnPreferenceChangeListener().onPreferenceChange(
                                    StatusPreference.this, null);
                        }
                    } // run() { ... }
                }); // runOnUiThread(...)
            } // if (mView != null) { ... }
        }

        @Override
        public void onClick(View v) {
            if (v == mSyncButton) {
                initiateSync();
            }
        }

        @Override
        public View getView(View convertView, ViewGroup parent) {
            if (mView == null) {
                mView = getLayoutInflater().inflate(R.layout.status, parent, false);
                mSyncButton = (Button)mView.findViewById(R.id.sync_button);
                mSyncButton.setOnClickListener(this);
                mStatusLabel = (TextView)mView.findViewById(R.id.status);
                mProgressBar = (ProgressBar)mView.findViewById(R.id.status_progress);
                update();
            }
            return mView;
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        String title;
        String msg;
        Builder builder;
        switch (id) {
            case DIALOG_MISSING_CREDENTIALS:
                title = getString(R.string.ui_dialog_missing_credentials_title);
                msg = getString(R.string.ui_dialog_missing_credentials_msg);
                break;
            case DIALOG_ERROR_DESCRIPTION:
                title = getString(R.string.ui_dialog_general_error_title);
                msg = SmsSyncService.getErrorDescription();
                break;
            case DIALOG_SYNC_DATA_RESET:
                title = getString(R.string.ui_dialog_sync_data_reset_title);
                msg = getString(R.string.ui_dialog_sync_data_reset_msg);
                break;
            case DIALOG_INVALID_IMAP_FOLDER:
                title = getString(R.string.ui_dialog_invalid_imap_folder_title);
                msg = getString(R.string.ui_dialog_invalid_imap_folder_msg);
                break;
            case DIALOG_NEED_FIRST_MANUAL_SYNC:
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // BUTTON1 == BUTTON_POSITIVE == "Yes"
                        if (which == DialogInterface.BUTTON1) {
                            showDialog(DIALOG_FIRST_SYNC);
                        }
                    }
                };

                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.ui_dialog_need_first_manual_sync_title);
                builder.setMessage(R.string.ui_dialog_need_first_manual_sync_msg);
                builder.setPositiveButton(R.string.ui_yes, dialogClickListener);
                builder.setNegativeButton(R.string.ui_no, dialogClickListener);
                builder.setCancelable(false);
                return builder.create();
            case DIALOG_FIRST_SYNC:
                DialogInterface.OnClickListener firstSyncListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // BUTTON2 == BUTTON_NEGATIVE == "Skip"
                        startSync(which == DialogInterface.BUTTON2);
                    }
                };

                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.ui_dialog_first_sync_title);
                builder.setMessage(R.string.ui_dialog_first_sync_msg);
                builder.setPositiveButton(R.string.ui_sync, firstSyncListener);
                builder.setNegativeButton(R.string.ui_skip, firstSyncListener);
                return builder.create();
            default:
                return null;
        }

        return createMessageDialog(id, title, msg);
    }

    private Dialog createMessageDialog(final int id, String title, String msg) {
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton(R.string.ui_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissDialog(id);
            }
        });
        return builder.create();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (PrefStore.PREF_LOGIN_USER.equals(preference.getKey())) {
            preference.setTitle(newValue.toString());
            SharedPreferences prefs = preference.getSharedPreferences();
            final String oldValue = prefs.getString(PrefStore.PREF_LOGIN_USER, null);
            if (!newValue.equals(oldValue)) {
                // We need to post the reset of sync state such that we do not interfere
                // with the current transaction of the SharedPreference.
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        PrefStore.clearSyncData(SmsSync.this);
                        if (oldValue != null) {
                            showDialog(DIALOG_SYNC_DATA_RESET);
                        }
                    }
                });
            }
        } else if (PrefStore.PREF_IMAP_FOLDER.equals(preference.getKey())) {
            String imapFolder = newValue.toString();
            if (PrefStore.isValidImapFolder(imapFolder)) {
                preference.setTitle(imapFolder);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showDialog(DIALOG_INVALID_IMAP_FOLDER);
                    }
                });
                return false;
            }
        } else if (PrefStore.PREF_ENABLE_AUTO_SYNC.equals(preference.getKey())) {
            boolean isEnabled = (Boolean) newValue;
            ComponentName componentName = new ComponentName(this,
                    SmsBroadcastReceiver.class);
            PackageManager pkgMgr = getPackageManager();
            if (isEnabled) {
                pkgMgr.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
                initiateSync();
            } else {
                pkgMgr.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                Alarms.cancel(this);
            }
        } else if (PrefStore.PREF_LOGIN_PASSWORD.equals(preference.getKey())) {
            if (PrefStore.isFirstSync(this) && PrefStore.isLoginUsernameSet(this)) {
                showDialog(DIALOG_NEED_FIRST_MANUAL_SYNC);
            }
        }
        return true;
    }

}
