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

package com.zegoggles.smssync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import oauth.signpost.OAuth;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;

import com.zegoggles.smssync.R;
import com.zegoggles.smssync.ServiceBase.SmsSyncState;


/**
 * This is the main activity showing the status of the SMS Sync service and
 * providing controls to configure it.
 */
public class SmsSync extends PreferenceActivity {
    private static final String TAG = SmsSync.class.getSimpleName();

    enum Dialogs {
      MISSING_CREDENTIALS,
      FIRST_SYNC,
      INVALID_IMAP_FOLDER,
      ABOUT,
      RESET,
      DISCONNECT,
      REQUEST_TOKEN,
      ACCESS_TOKEN,
      ACCESS_TOKEN_ERROR,
      REQUEST_TOKEN_ERROR,
      CONNECT,
      CONNECT_TOKEN_ERROR,
      UPGRADE
    }

    StatusPreference statusPref;
    private Uri mAuthorizeUri = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ServiceBase.smsSync = this;

        addPreferencesFromResource(R.xml.main_screen);

        statusPref = new StatusPreference(this);
        statusPref.setSelectable(false);
        statusPref.setOrder(0);

        getPreferenceScreen().addPreference(statusPref);
        setPreferenceListeners(getPreferenceManager());

        if (PrefStore.showUpgradeMessage(this)) {
          show(Dialogs.UPGRADE);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null && uri.toString().startsWith(Consts.CALLBACK_URL) &&
           (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {

            new OAuthCallbackTask().execute(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ServiceBase.smsSync = this;

        updateImapFolderLabelFromPref();
        updateUsernameLabel(null);
        updateMaxItemsPerSync(null);
        updateMaxItemsPerRestore(null);

        statusPref.update();

        // XXX
        getPreferenceManager()
          .findPreference(PrefStore.PREF_LOGIN_USER)
          .setEnabled(!PrefStore.useXOAuth(this));
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
             default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateUsernameLabel(String username) {
        if (username == null) {
          SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
          username = prefs.getString(PrefStore.PREF_LOGIN_USER, getString(R.string.ui_login_label));
        }
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_LOGIN_USER);
        pref.setTitle(username);
    }

    private void updateImapFolderLabelFromPref() {
        String imapFolder = PrefStore.getImapFolder(this);
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_IMAP_FOLDER);
        pref.setTitle(imapFolder);
    }

    private void initiateRestore() {
        if (checkLoginInformation()) {
            startRestore();
        }
    }

    private void initiateSync() {
        if (checkLoginInformation()) {
            if (PrefStore.isFirstSync(this)) {
                show(Dialogs.FIRST_SYNC);
            } else {
                startSync(false);
            }
        }
    }

    private boolean checkLoginInformation() {
        if (!PrefStore.isLoginInformationSet(this)) {
            show(Dialogs.MISSING_CREDENTIALS);
            return false;
        } else {
            return true;
        }
    }

    private void startSync(boolean skip) {
        Intent intent = new Intent(this, SmsBackupService.class);
        if (PrefStore.isFirstSync(this)) {
            intent.putExtra(Consts.KEY_SKIP_MESSAGES, skip);
        }
        startService(intent);
    }

    private void startRestore() {
        Intent intent = new Intent(this, SmsRestoreService.class);
        startService(intent);
    }

    class StatusPreference extends Preference implements OnClickListener {
        private View mView;

        private Button mSyncButton;
        private Button mRestoreButton;

        private ImageView mStatusIcon;

        private TextView mStatusLabel;
        private TextView mSyncDetailsLabel;

        private View mSyncDetails;

        private ProgressBar mProgressBar;

        public StatusPreference(Context context) {
            super(context);
        }

        public void update() {
            stateChanged(ServiceBase.getState());
        }

        private void authFailed() {
            mStatusLabel.setText(R.string.status_auth_failure);

            if (PrefStore.useXOAuth(getContext())) {
              mSyncDetailsLabel.setText(R.string.status_auth_failure_details_xoauth);
            } else {
              mSyncDetailsLabel.setText(R.string.status_auth_failure_details_plain);
            }
        }

        private void calc() {
            mStatusLabel.setText(R.string.status_working);
            mSyncDetailsLabel.setText(R.string.status_calc_details);
            mProgressBar.setIndeterminate(true);
        }

        private void finishedBackup() {
            int backedUpCount = SmsBackupService.getCurrentSyncedItems();
            String text = null;
            if (backedUpCount == PrefStore.getMaxItemsPerSync(getContext())) {
                text = getString(R.string.status_backup_done_details_max_per_sync, backedUpCount);
            } else if (backedUpCount > 0) {
                text = getResources().getQuantityString(R.plurals.status_backup_done_details, backedUpCount,
                                                        backedUpCount);
            } else if (backedUpCount == 0) {
                text = getString(R.string.status_backup_done_details_noitems);
            }
            mSyncDetailsLabel.setText(text);
            mStatusLabel.setText(R.string.status_done);
            mStatusLabel.setTextColor(getResources().getColor(R.color.status_done));
        }

        private void finishedRestore() {
            mStatusLabel.setTextColor(getResources().getColor(R.color.status_done));
            mStatusLabel.setText(R.string.status_done);
            mSyncDetailsLabel.setText(getResources().getQuantityString(
                  R.plurals.status_restore_done_details,
                  SmsRestoreService.sRestoredCount,
                  SmsRestoreService.sRestoredCount,
                  SmsRestoreService.sDuplicateCount));
        }

        private void idle() {
            long lastSync = PrefStore.getLastSync(getContext());
            String text;
            if (lastSync == PrefStore.DEFAULT_LAST_SYNC) {
                text = getString(R.string.status_idle_details_never);
            } else {
                text = PrefStore.getMaxSyncedDate(SmsSync.this) != -1 ?
                              new Date(PrefStore.getMaxSyncedDate(SmsSync.this)).toLocaleString() :
                              new Date(lastSync).toLocaleString();
            }
            mSyncDetailsLabel.setText(getString(R.string.status_idle_details, text));
            mStatusLabel.setText(R.string.status_idle);
        }

        public void stateChanged(final SmsSyncState newState) {
            if (mView == null) return;
            setAttributes(newState);

            switch (newState) {
               case FINISHED_RESTORE: finishedRestore(); break;
               case FINISHED_BACKUP: finishedBackup(); break;
               case IDLE: idle(); break;
               case LOGIN:
                    mStatusLabel.setText(R.string.status_working);
                    mSyncDetailsLabel.setText(R.string.status_login_details);
                    mProgressBar.setIndeterminate(true);
                    break;
                case CALC: calc(); break;
                case BACKUP:
                    mRestoreButton.setEnabled(false);
                    mSyncButton.setText(R.string.ui_sync_button_label_syncing);
                    mStatusLabel.setText(R.string.status_backup);

                    mSyncDetailsLabel.setText(getString(R.string.status_backup_details,
                            SmsBackupService.getCurrentSyncedItems(),
                            SmsBackupService.getItemsToSyncCount()));

                    mProgressBar.setIndeterminate(false);
                    mProgressBar.setProgress(SmsBackupService.getCurrentSyncedItems());
                    mProgressBar.setMax(SmsBackupService.getItemsToSyncCount());
                    break;
                case RESTORE:
                    mSyncButton.setEnabled(false);
                    mRestoreButton.setText(R.string.ui_restore_button_label_restoring);

                    mStatusLabel.setText(R.string.status_restore);

                    mSyncDetailsLabel.setText(getString(R.string.status_restore_details,
                              SmsRestoreService.getCurrentRestoredItems(),
                              SmsRestoreService.getItemsToRestoreCount()));

                    mProgressBar.setIndeterminate(false);
                    mProgressBar.setProgress(SmsRestoreService.getCurrentRestoredItems());
                    mProgressBar.setMax(SmsRestoreService.getItemsToRestoreCount());
                    break;
                case AUTH_FAILED: authFailed(); break;
                case FOLDER_ERROR:
                  mStatusLabel.setText(R.string.status_folder_error);
                  mSyncDetailsLabel.setText(R.string.status_folder_error_details);
                   break;
                case GENERAL_ERROR:
                case CONNECTIVITY_ERROR:
                    mStatusLabel.setText(R.string.status_unknown_error);
                    mSyncDetailsLabel.setText(getString(R.string.status_unknown_error_details,
                          ServiceBase.lastError == null ? "N/A" : ServiceBase.lastError));
                    break;
               case CANCELED_BACKUP:
                    mStatusLabel.setText(R.string.status_canceled);

                    mSyncDetailsLabel.setText(getString(R.string.status_canceled_details,
                        SmsBackupService.getCurrentSyncedItems(),
                        SmsBackupService.getItemsToSyncCount()));
                    break;
                case CANCELED_RESTORE:
                    mStatusLabel.setText(R.string.status_canceled);

                    mSyncDetailsLabel.setText(getString(R.string.status_restore_canceled_details,
                        SmsRestoreService.getCurrentRestoredItems(),
                        SmsRestoreService.getItemsToRestoreCount()));
                    break;
              }
          }

        public void setAttributes(final SmsSyncState state) {
          switch (state) {
            case GENERAL_ERROR:
            case CONNECTIVITY_ERROR:
            case FOLDER_ERROR:
            case AUTH_FAILED:
              mProgressBar.setProgress(0);
              mProgressBar.setIndeterminate(false);
              mStatusLabel.setTextColor(getResources().getColor(R.color.status_error));
              mStatusIcon.setImageResource(R.drawable.ic_error);
              break;
            case LOGIN:
            case CALC:
            case BACKUP:
            case RESTORE:
              mStatusLabel.setTextColor(getResources().getColor(R.color.status_sync));
              mStatusIcon.setImageResource(R.drawable.ic_syncing);
              break;
            default:
              mProgressBar.setProgress(0);
              mProgressBar.setIndeterminate(false);
              mRestoreButton.setEnabled(true);
              mSyncButton.setEnabled(true);

              mSyncButton.setText(R.string.ui_sync_button_label_idle);
              mRestoreButton.setText(R.string.ui_restore_button_label_idle);

              mStatusLabel.setTextColor(getResources().getColor(R.color.status_idle));
              mStatusIcon.setImageResource(R.drawable.ic_idle);
          }
        }

        @Override
        public void onClick(View v) {
            if (v == mSyncButton) {
                if (!SmsBackupService.isWorking()) {
                    Log.d(TAG, "user requested sync");
                    initiateSync();
                } else {
                    Log.d(TAG, "user requested cancel");
                    // Sync button will be restored on next status update.
                    mSyncButton.setText(R.string.ui_sync_button_label_canceling);
                    mSyncButton.setEnabled(false);
                    SmsBackupService.cancel();
                }
            } else if (v == mRestoreButton) {
                Log.d(TAG, "restore");
                if (!SmsRestoreService.isWorking()) {
                    initiateRestore();
                } else {
                    mRestoreButton.setText(R.string.ui_sync_button_label_canceling);
                    mRestoreButton.setEnabled(false);
                    SmsRestoreService.cancel();
                }
            }
        }

        @Override
        public View getView(View convertView, ViewGroup parent) {
            if (mView == null) {
                mView = getLayoutInflater().inflate(R.layout.status, parent, false);
                mSyncButton = (Button) mView.findViewById(R.id.sync_button);
                mSyncButton.setOnClickListener(this);

                mRestoreButton = (Button) mView.findViewById(R.id.restore_button);
                mRestoreButton.setOnClickListener(this);

                mStatusIcon = (ImageView) mView.findViewById(R.id.status_icon);
                mStatusLabel = (TextView) mView.findViewById(R.id.status_label);
                mSyncDetails = mView.findViewById(R.id.details_sync);
                mSyncDetailsLabel = (TextView) mSyncDetails.findViewById(R.id.details_sync_label);
                mProgressBar = (ProgressBar) mSyncDetails.findViewById(R.id.details_sync_progress);
                update();
            }
            return mView;
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        String title, msg;
        Builder builder;
        switch (Dialogs.values()[id]) {
            case MISSING_CREDENTIALS:
                title = getString(R.string.ui_dialog_missing_credentials_title);
                msg = PrefStore.useXOAuth(this) ? getString(R.string.ui_dialog_missing_credentials_msg_xoauth) :
                                                  getString(R.string.ui_dialog_missing_credentials_msg_plain);
                break;
            case INVALID_IMAP_FOLDER:
                title = getString(R.string.ui_dialog_invalid_imap_folder_title);
                msg = getString(R.string.ui_dialog_invalid_imap_folder_msg);
                break;
           case FIRST_SYNC:
                DialogInterface.OnClickListener firstSyncListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startSync(which == DialogInterface.BUTTON2);
                    }
                };
                final int maxItems = PrefStore.getMaxItemsPerSync(this);
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
                webView.getSettings().setJavaScriptEnabled(true);
                webView.loadUrl("file:///android_asset/about.html");

                return new AlertDialog.Builder(this)
                    .setCustomTitle(null)
                    .setPositiveButton(android.R.string.ok, null)
                    .setView(contentView)
                    .create();
           case RESET:
                return new AlertDialog.Builder(this)
                    .setTitle(R.string.ui_dialog_reset_title)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int which) {
                          PrefStore.clearLastSyncData(SmsSync.this);
                          dismissDialog(id);
                       }})
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
                          if (mAuthorizeUri != null) {
                            startActivity(new Intent(Intent.ACTION_VIEW, mAuthorizeUri)
                                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY));
                          }
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
            case DISCONNECT:
                return new AlertDialog.Builder(this)
                    .setCustomTitle(null)
                    .setMessage(R.string.ui_dialog_disconnect_msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        PrefStore.clearOauthData(SmsSync.this);
                        PrefStore.clearLastSyncData(SmsSync.this);
                        updateConnected();
                    }
                }).create();
            case UPGRADE:
                title = getString(R.string.ui_dialog_upgrade_title);
                msg = getString(R.string.ui_dialog_upgrade_msg);
                break;
            default:
                return null;
        }
        return createMessageDialog(id, title, msg);
    }

    private Dialog createMessageDialog(final int id, String title, String msg) {
        return new AlertDialog.Builder(this)
          .setTitle(title)
          .setMessage(msg)
          .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                  dismissDialog(id);
              }
          })
          .create();
      }

    private void updateMaxItemsPerSync(String newValue) {
        updateMaxItems(PrefStore.PREF_MAX_ITEMS_PER_SYNC, PrefStore.getMaxItemsPerSync(this), newValue);
    }

    private void updateMaxItemsPerRestore(String newValue) {
        updateMaxItems(PrefStore.PREF_MAX_ITEMS_PER_RESTORE, PrefStore.getMaxItemsPerRestore(this), newValue);
    }

    private void updateMaxItems(String prefKey, int currentValue, String newValue) {
        Preference pref = getPreferenceManager().findPreference(prefKey);
        if (newValue == null) {
            newValue = String.valueOf(currentValue);
        }
        // XXX
        pref.setTitle("-1".equals(newValue) ? getString(R.string.all_messages) : newValue);
    }

    private CheckBoxPreference updateConnected() {
        CheckBoxPreference connected = (CheckBoxPreference) getPreferenceManager()
              .findPreference(PrefStore.PREF_CONNECTED);

        connected.setEnabled(PrefStore.useXOAuth(this));
        connected.setChecked(PrefStore.hasOauthTokens(this));

        String summary = connected.isChecked() ? getString(R.string.gmail_already_connected,
                                                 PrefStore.getOauthUsername(this)) :
                                                 getString(R.string.gmail_needs_connecting);
        connected.setSummary(summary);

        return connected;
    }

    class RequestTokenTask extends android.os.AsyncTask<String, Void, String> {
        public String doInBackground(String... callback) {
            synchronized(XOAuthConsumer.class) {
                XOAuthConsumer consumer = PrefStore.getOAuthConsumer(SmsSync.this);
                CommonsHttpOAuthProvider provider = consumer.getProvider(SmsSync.this);
                try {
                    String url = provider.retrieveRequestToken(consumer, callback[0]);
                    PrefStore.setOauthTokens(SmsSync.this, consumer.getToken(), consumer.getTokenSecret());
                    return url;
                } catch (oauth.signpost.exception.OAuthException e) {
                    Log.e(TAG, "error requesting token", e);
                    return null;
                }
            }
        }

        @Override
        protected void onPostExecute(String authorizeUrl) {
            dismiss(Dialogs.REQUEST_TOKEN);

            if (authorizeUrl != null) {
                SmsSync.this.mAuthorizeUri = Uri.parse(authorizeUrl);
                show(Dialogs.CONNECT);
            } else {
                SmsSync.this.mAuthorizeUri = null;
                show(Dialogs.CONNECT_TOKEN_ERROR);
            }
        }
    }

    class OAuthCallbackTask extends android.os.AsyncTask<Intent, Void, XOAuthConsumer> {

        @Override
        protected void onPreExecute() {
            show(Dialogs.ACCESS_TOKEN);
        }

        @Override
        protected XOAuthConsumer doInBackground(Intent... callbackIntent) {
            Uri uri = callbackIntent[0].getData();
            Log.d(TAG, "oauth callback: " + uri);

            XOAuthConsumer consumer = PrefStore.getOAuthConsumer(SmsSync.this);
            CommonsHttpOAuthProvider provider = consumer.getProvider(SmsSync.this);
            String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
            try {
                provider.retrieveAccessToken(consumer, verifier);
                String username = consumer.loadUsernameFromContacts();

                if (username != null) {
                  Log.i(TAG, "Valid access token for " + username);
                  // intent has been handled
                  callbackIntent[0].setData(null);

                  return consumer;
                } else {
                  Log.e(TAG, "No valid user name");
                  return null;
                }
           } catch (oauth.signpost.exception.OAuthException e) {
                Log.e(TAG, "error", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(XOAuthConsumer consumer) {
            dismiss(Dialogs.ACCESS_TOKEN);
            if (consumer != null) {
                PrefStore.setOauthUsername(SmsSync.this, consumer.getUsername());
                PrefStore.setOauthTokens(SmsSync.this, consumer.getToken(), consumer.getTokenSecret());

                updateConnected();
                updateUsernameLabel(null);

                // Invite use to perform a backup, but only once
                if (PrefStore.isFirstUse(SmsSync.this)) {
                    show(Dialogs.FIRST_SYNC);
                }
            } else {
              show(Dialogs.ACCESS_TOKEN_ERROR);
            }
        }
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

    private void setPreferenceListeners(final PreferenceManager prefMgr) {

        prefMgr.findPreference(PrefStore.PREF_ENABLE_AUTO_SYNC)
               .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean isEnabled = (Boolean) newValue;
                ComponentName componentName = new ComponentName(SmsSync.this, SmsBroadcastReceiver.class);
                PackageManager pkgMgr = getPackageManager();

                pkgMgr.setComponentEnabledSetting(componentName,
                            isEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP);

                if (!isEnabled) {
                    Alarms.cancel(SmsSync.this);
                }
                return true;
             }
        });

        prefMgr.findPreference(PrefStore.PREF_SERVER_AUTHENTICATION)
               .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final boolean plain = (PrefStore.AuthMode.PLAIN) ==
                  PrefStore.AuthMode.valueOf(newValue.toString().toUpperCase());

                updateConnected().setEnabled(!plain);
                prefMgr.findPreference(PrefStore.PREF_LOGIN_USER).setEnabled(plain);
                prefMgr.findPreference(PrefStore.PREF_LOGIN_PASSWORD).setEnabled(plain);
                return true;
            }
        });

        prefMgr.findPreference(PrefStore.PREF_MAX_ITEMS_PER_SYNC)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateMaxItemsPerSync(newValue.toString());
                return true;
            }
        });

        prefMgr.findPreference(PrefStore.PREF_MAX_ITEMS_PER_RESTORE)
               .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateMaxItemsPerRestore(newValue.toString());
                return true;
            }
        });

        prefMgr.findPreference(PrefStore.PREF_LOGIN_USER)
                .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateUsernameLabel(newValue.toString());
                return true;
            }
        });

        updateConnected().setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object change) {
                boolean newValue = (Boolean) change;
                if (newValue) {
                  show(Dialogs.REQUEST_TOKEN);
                  new RequestTokenTask().execute(Consts.CALLBACK_URL);
                } else {
                  show(Dialogs.DISCONNECT);
                }
                return false;
            }
        });

        prefMgr.findPreference(PrefStore.PREF_IMAP_FOLDER)
               .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, final Object newValue) {
              String imapFolder = newValue.toString();

              if (PrefStore.isValidImapFolder(imapFolder)) {
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
}
