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

import android.app.NotificationManager;
import android.app.Notification;
import android.widget.Toast;
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
import android.os.Build;
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

    private static final int MENU_INFO = 0;
    enum Dialogs {
      MISSING_CREDENTIALS,
      FIRST_SYNC,
      SYNC_DATA_RESET,
      INVALID_IMAP_FOLDER,
      NEED_FIRST_MANUAL_SYNC,
      ABOUT,
      DISCONNECT,
      REQUEST_TOKEN,
      CONNECT,
      CONNECT_TOKEN_ERROR,
      UPGRADE
    }

    private StatusPreference mStatusPref;

    private static final String TAG = "SmsSync";
    private static ContactAccessor sAccessor = null;

    enum Mode { BACKUP, RESTORE, NONE }

    private Mode mode = Mode.NONE;
    private Uri authorizeUri = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.main_screen);

        mStatusPref = new StatusPreference(this);
        mStatusPref.setSelectable(false);

        mStatusPref.setOrder(0);
        getPreferenceScreen().addPreference(mStatusPref);

        setPreferenceListeners(getPreferenceManager());
        ServiceBase.smsSync = this;

        if (PrefStore.showUpgradeMessage(this)) {
          show(Dialogs.UPGRADE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SmsSyncService.unsetStateChangeListener();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent:" + intent);

        Uri uri = intent.getData();
        if (uri != null && uri.toString().startsWith(Consts.CALLBACK_URL)) {
            new OAuthCallbackTask().execute(uri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        SmsSyncService.setStateChangeListener(mStatusPref);
        updateUsernameLabelFromPref();
        updateImapFolderLabelFromPref();
        updateMaxItemsPerSync(null);
        updateMaxItemsPerRestore(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_INFO, 0, R.string.menu_info).setIcon(
                android.R.drawable.ic_menu_info_details);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case MENU_INFO:
                show(Dialogs.ABOUT);
                return true;
        }
        return false;
    }

    private void openLink(String link) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        startActivity(intent);
    }

    private void prefillEmailAddress() {
      SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

      if (PrefStore.getLoginUsername(this) == null &&
          !prefs.getBoolean(PrefStore.PREF_PREFILLED, false)) {

          prefs.edit()
            .putString(PrefStore.PREF_LOGIN_USER, getContactAccessor().getOwnerEmail(this))
            .putBoolean(PrefStore.PREF_PREFILLED, true)
            .commit();
      }
    }

    private void updateUsernameLabelFromPref() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String username = prefs.getString(PrefStore.PREF_LOGIN_USER, getString(R.string.ui_login_label));
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
            mode = Mode.RESTORE;
            startRestore();
        }
    }

    private void initiateSync() {
        if (checkLoginInformation()) {
            if (PrefStore.isFirstSync(this)) {
                show(Dialogs.FIRST_SYNC);
            } else {
                mode = Mode.BACKUP;
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

    public static ContactAccessor getContactAccessor() {
       if (sAccessor == null) {
            String className;
            int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
            if (sdkVersion < Build.VERSION_CODES.ECLAIR) {
                className = "ContactAccessorPre20";
            } else {
                className = "ContactAccessorPost20";
            }
            try {
                Class<? extends ContactAccessor> clazz =
                   Class.forName(ContactAccessor.class.getPackage().getName() + "." + className)
                        .asSubclass(ContactAccessor.class);

                sAccessor = clazz.newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return sAccessor;
    }

    private void startSync(boolean skip) {
        Intent intent = new Intent(this, SmsSyncService.class);
        if (PrefStore.isFirstSync(this)) {
            intent.putExtra(Consts.KEY_SKIP_MESSAGES, skip);
        }
        startService(intent);
    }

    private void startRestore() {
        Intent intent = new Intent(this, SmsRestoreService.class);
        startService(intent);
    }

    class StatusPreference extends Preference implements
            SmsSyncService.StateChangeListener, OnClickListener {
        private View mView;

        private Button mSyncButton;
        private Button mRestoreButton;

        private ImageView mStatusIcon;

        private TextView mStatusLabel;

        private View mSyncDetails;

        private TextView mErrorDetails;

        private TextView mSyncDetailsLabel;

        private ProgressBar mProgressBar;

        private ProgressBar mProgressBarIndet;

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
                        // TODO jberkel this should be an enum

                        int STATUS_IDLE = 0;
                        int STATUS_WORKING = 1;
                        int STATUS_DONE = 2;
                        int STATUS_ERROR = 3;
                        int status = -1;

                        CharSequence statusLabel = null;
                        String statusDetails = null;
                        boolean progressIndeterminate = false;
                        int progressMax = 1;
                        int progressVal = 0;
                        //Log.d(TAG, "newState: " + newState);
                        switch (newState) {
                            case AUTH_FAILED:
                                // TODO jberkel possibility to do dynamic string lookups?

                                statusLabel = getText(R.string.status_auth_failure);
                                switch (PrefStore.getAuthMode(SmsSync.this)) {
                                  case PLAIN:
                                    statusDetails = getString(R.string.status_auth_failure_details_plain);
                                  case XOAUTH:
                                    statusDetails = getString(R.string.status_auth_failure_details_xoauth);
                                }

                                status = STATUS_ERROR;
                                break;
                            case FOLDER_ERROR:
                                statusLabel = getText(R.string.status_folder_error);
                                statusDetails = getString(R.string.status_folder_error_details);
                                status = STATUS_ERROR;
                                break;
                            case CALC:
                                statusLabel = getStatusLabelText();
                                statusDetails = getString(R.string.status_calc_details);
                                progressIndeterminate = true;
                                status = STATUS_WORKING;
                                break;
                            case IDLE:
                                if (oldState == SmsSyncState.SYNC
                                        || oldState == SmsSyncState.CALC) {
                                    statusLabel = getText(R.string.status_done);

                                    // TODO jberkel: pass context object through?

                                    int backedUpCount = SmsSyncService.getCurrentSyncedItems();
                                    progressMax = SmsSyncService.getItemsToSyncCount();
                                    progressVal = backedUpCount;
                                    if (backedUpCount ==
                                            PrefStore.getMaxItemsPerSync(SmsSync.this)) {
                                        // Maximum msg per sync reached.
                                        statusDetails = getString(
                                                R.string.status_backup_done_details_max_per_sync,
                                                backedUpCount);
                                    } else if (backedUpCount > 0) {
                                        statusDetails = getResources().getQuantityString(
                                                R.plurals.status_backup_done_details, backedUpCount,
                                                backedUpCount);
                                    } else {
                                        statusDetails = getString(
                                                R.string.status_backup_done_details_noitems);
                                        progressMax = 1;
                                        progressVal = 1;
                                    }

                                    progressIndeterminate = false;

                                    status = STATUS_DONE;
                                } else if (oldState == SmsSyncState.RESTORE) {
                                    statusDetails = getResources().getQuantityString(
                                                R.plurals.status_restore_done_details,
                                                SmsRestoreService.restoredCount,
                                                SmsRestoreService.restoredCount,
                                                SmsRestoreService.duplicateCount);

                                    progressIndeterminate = false;
                                    status = STATUS_DONE;
                                } else {
                                    statusLabel = getText(R.string.status_idle);
                                    long lastSync = PrefStore.getLastSync(SmsSync.this);
                                    String lastSyncStr;
                                    if (lastSync == PrefStore.DEFAULT_LAST_SYNC) {
                                        lastSyncStr =
                                            getString(R.string.status_idle_details_never);
                                    } else {
                                        lastSyncStr = PrefStore.getMaxSyncedDate(SmsSync.this) != -1 ?
                                                      new Date(PrefStore.getMaxSyncedDate(SmsSync.this)).toLocaleString() :
                                                      new Date(lastSync).toLocaleString();
                                    }
                                    statusDetails = getString(R.string.status_idle_details,
                                            lastSyncStr);
                                    status = STATUS_IDLE;
                                }
                                break;
                            case LOGIN:
                                statusLabel = getStatusLabelText();
                                statusDetails = getString(R.string.status_login_details);
                                progressIndeterminate = true;
                                status = STATUS_WORKING;
                                break;
                            case SYNC:
                                mRestoreButton.setEnabled(false);

                                statusLabel = getText(R.string.status_backup);
                                statusDetails = getString(R.string.status_backup_details,
                                        SmsSyncService.getCurrentSyncedItems(),
                                        SmsSyncService.getItemsToSyncCount());
                                progressMax = SmsSyncService.getItemsToSyncCount();
                                progressVal = SmsSyncService.getCurrentSyncedItems();
                                status = STATUS_WORKING;
                                break;
                            case RESTORE:
                                mSyncButton.setEnabled(false);

                                statusLabel = getText(R.string.status_restore);

                                statusDetails = getString(R.string.status_restore_details,
                                          SmsRestoreService.getCurrentRestoredItems(),
                                          SmsRestoreService.getItemsToRestoreCount());

                                progressMax = SmsRestoreService.getItemsToRestoreCount();
                                progressVal = SmsRestoreService.getCurrentRestoredItems();
                                status = STATUS_WORKING;

                                break;

                            case GENERAL_ERROR:
                                statusLabel = getString(R.string.status_unknown_error);
                                statusDetails = getString(R.string.status_unknown_error_details,
                                        SmsSyncService.getErrorDescription());
                                status = STATUS_ERROR;
                                break;
                            case CANCELED:
                                statusLabel = getString(R.string.status_canceled);

                                switch(mode) {
                                    case BACKUP:
                                        statusDetails = getString(R.string.status_canceled_details,
                                            SmsSyncService.getCurrentSyncedItems(),
                                            SmsSyncService.getItemsToSyncCount());
                                        break;
                                    case RESTORE:
                                         statusDetails = getString(R.string.status_restore_canceled_details,
                                            SmsRestoreService.getCurrentRestoredItems(),
                                            SmsRestoreService.getItemsToRestoreCount());
                                        break;
                                }
                                status = STATUS_IDLE;
                        } // switch (newStatus) { ... }

                        int color;
                        TextView detailTextView;
                        int buttonText;
                        int icon;

                        // map status => (icon, label)

                        if (status == STATUS_IDLE) {
                            color = R.color.status_idle;
                            detailTextView = mSyncDetailsLabel;
                            buttonText = (mode == Mode.RESTORE) ? R.string.ui_restore_button_label_idle : R.string.ui_sync_button_label_idle;
                            icon = R.drawable.ic_idle;
                        } else if (status == STATUS_WORKING) {
                            color = R.color.status_sync;
                            detailTextView = mSyncDetailsLabel;
                            buttonText = (mode == Mode.RESTORE) ? R.string.ui_restore_button_label_restoring : R.string.ui_sync_button_label_syncing;
                            icon = R.drawable.ic_syncing;
                        } else if (status == STATUS_DONE) {
                            color = R.color.status_done;
                            detailTextView = mSyncDetailsLabel;
                            buttonText = (mode == Mode.RESTORE) ?  R.string.ui_restore_button_label_done : R.string.ui_sync_button_label_done;
                            icon = R.drawable.ic_done;
                        } else if (status == STATUS_ERROR) {
                            color = R.color.status_error;
                            detailTextView = mErrorDetails;
                            buttonText = (mode == Mode.RESTORE) ?  R.string.ui_restore_button_label_error : R.string.ui_sync_button_label_error;
                            icon = R.drawable.ic_error;
                        } else {
                            Log.w(Consts.TAG, "Illegal state: Unknown status.");
                            return;
                        }

                        if (status != STATUS_ERROR) {
                            mSyncDetails.setVisibility(View.VISIBLE);
                            mErrorDetails.setVisibility(View.INVISIBLE);
                            if (progressIndeterminate) {
                                mProgressBarIndet.setVisibility(View.VISIBLE);
                                mProgressBar.setVisibility(View.GONE);
                            } else {
                                mProgressBar.setVisibility(View.VISIBLE);
                                mProgressBarIndet.setVisibility(View.GONE);
                                mProgressBar.setIndeterminate(progressIndeterminate);
                                mProgressBar.setMax(progressMax);
                                mProgressBar.setProgress(progressVal);
                            }

                        } else {
                            mErrorDetails.setVisibility(View.VISIBLE);
                            mSyncDetails.setVisibility(View.INVISIBLE);
                        }

                        mStatusLabel.setText(statusLabel);
                        mStatusLabel.setTextColor(getResources().getColor(color));

                        if (mode == Mode.RESTORE) {
                          mRestoreButton.setText(buttonText);
                        } else {
                          mSyncButton.setText(buttonText);
                        }

                        if (status != STATUS_WORKING) {
                            mode = Mode.NONE;
                            mSyncButton.setEnabled(true);
                            mRestoreButton.setEnabled(true);
                        }

                        detailTextView.setText(statusDetails);
                        mStatusIcon.setImageResource(icon);
                    } // run() { ... }
                }); // runOnUiThread(...)
            } // if (mView != null) { ... }
        }

        @Override
        public void onClick(View v) {
            if (v == mSyncButton) {
                Log.d(TAG, "sync");

                if (!SmsSyncService.isWorking() && !SmsSyncService.isCancelling()) {
                    initiateSync();
                } else {
                    // Sync button will be restored on next status update.
                    mSyncButton.setText(R.string.ui_sync_button_label_canceling);
                    mSyncButton.setEnabled(false);
                    SmsSyncService.cancel();

                }
            } else if (v == mRestoreButton) {
                Log.d(TAG, "restore");
                if (!SmsRestoreService.isWorking() && !SmsRestoreService.isCancelling()) {
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
                mProgressBarIndet =
                    (ProgressBar) mSyncDetails.findViewById(R.id.details_sync_progress_indet);
                mErrorDetails = (TextView) mView.findViewById(R.id.details_error);
                update();
            }
            return mView;
        }

        public CharSequence getStatusLabelText() {
             switch (mode) {
                 case BACKUP: return getText(R.string.status_backup);
                 case RESTORE:return getText(R.string.status_restore);
                 case NONE: return getText(R.string.status_idle);
                 default: throw new IllegalStateException();
             }
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        String title;
        String msg;
        Builder builder;
        switch (Dialogs.values()[id]) {
            case MISSING_CREDENTIALS:
                title = getString(R.string.ui_dialog_missing_credentials_title);

                if (PrefStore.getAuthMode(this) == PrefStore.AuthMode.XOAUTH) {
                  msg = getString(R.string.ui_dialog_missing_credentials_msg_xoauth);
                } else {
                  msg = getString(R.string.ui_dialog_missing_credentials_msg_plain);
                }
                break;
            case SYNC_DATA_RESET:
                title = getString(R.string.ui_dialog_sync_data_reset_title);
                msg = getString(R.string.ui_dialog_sync_data_reset_msg);
                break;
            case INVALID_IMAP_FOLDER:
                title = getString(R.string.ui_dialog_invalid_imap_folder_title);
                msg = getString(R.string.ui_dialog_invalid_imap_folder_msg);
                break;
            case NEED_FIRST_MANUAL_SYNC:
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON1) {
                            show(Dialogs.FIRST_SYNC);
                        }
                    }
                };

                return new AlertDialog.Builder(this)
                    .setTitle(R.string.ui_dialog_need_first_manual_sync_title)
                    .setMessage(R.string.ui_dialog_need_first_manual_sync_msg)
                    .setPositiveButton(android.R.string.yes, dialogClickListener)
                    .setNegativeButton(android.R.string.no, dialogClickListener)
                    .setCancelable(false)
                    .create();
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
                webView.loadData(getAboutText(), "text/html", "utf-8");

                return new AlertDialog.Builder(this)
                    .setCustomTitle(null)
                    .setPositiveButton(android.R.string.ok, null)
                    .setView(contentView)
                    .create();
            case DISCONNECT:
                return new AlertDialog.Builder(this)
                    .setCustomTitle(null)
                    .setMessage(R.string.ui_dialog_disconnect_msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        PrefStore.clearSyncData(SmsSync.this);
                        updateConnected();
                    }
                }).create();
            case REQUEST_TOKEN:
                ProgressDialog d = new ProgressDialog(this);
                d.setTitle(null);
                d.setMessage(getString(R.string.ui_dialog_request_token_msg));
                d.setIndeterminate(true);
                d.setCancelable(false);
                return d;

            case CONNECT:
                return new AlertDialog.Builder(this)
                    .setCustomTitle(null)
                    .setMessage(getString(R.string.ui_dialog_connect_msg, getString(R.string.app_name)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                          if (authorizeUri != null) {
                            startActivity(new Intent(Intent.ACTION_VIEW, authorizeUri));
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

    private String getAboutText() {
        try {
            InputStream input = getResources().getAssets().open("about.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            StringBuilder buf = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                buf.append(line);
            }
            String aboutText = buf.toString();
            aboutText = String.format(aboutText,
                    getString(R.string.app_name),
                    PrefStore.getVersion(this, false));
            aboutText.replaceAll("percent", "%");
            return aboutText;
        } catch (IOException e) {
            return "An error occured while reading about.html";
        }
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

        pref.setTitle("-1".equals(newValue) ? getString(R.string.all_messages) : newValue);
    }

    private CheckBoxPreference updateConnected() {
        CheckBoxPreference connected = (CheckBoxPreference) getPreferenceManager()
              .findPreference(PrefStore.PREF_CONNECTED);

        boolean hasTokens = PrefStore.hasOauthTokens(this);
        connected.setEnabled(PrefStore.getAuthMode(this) == PrefStore.AuthMode.XOAUTH);
        connected.setChecked(hasTokens);
        connected.setSummary(hasTokens ? R.string.gmail_already_connected : R.string.gmail_needs_connecting);

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
                } catch (Exception e) {
                    Log.e(TAG, "error requesting token", e);

                    notifyUser(android.R.drawable.stat_sys_warning, "Error",
                        getString(R.string.gmail_connected_fail),
                        e.getMessage());

                    return null;
                }
            }
        }

        @Override
        protected void onPostExecute(String authorizeUrl) {
            dismiss(Dialogs.REQUEST_TOKEN);

            if (authorizeUrl != null) {
                SmsSync.this.authorizeUri = Uri.parse(authorizeUrl);

                show(Dialogs.CONNECT);
            } else {
                SmsSync.this.authorizeUri = null;

                show(Dialogs.CONNECT_TOKEN_ERROR);
            }
        }
    }

    class OAuthCallbackTask extends android.os.AsyncTask<Uri, Void, XOAuthConsumer> {

        @Override
        protected void onPreExecute() {
            Toast.makeText(SmsSync.this, R.string.gmail_processing, Toast.LENGTH_LONG).show();
        }

        protected XOAuthConsumer doInBackground(Uri... callbackUri) {
            Log.d(TAG, "oauth callback: " + callbackUri[0]);
            XOAuthConsumer consumer = PrefStore.getOAuthConsumer(SmsSync.this);
            CommonsHttpOAuthProvider provider = consumer.getProvider(SmsSync.this);
            String verifier = callbackUri[0].getQueryParameter(OAuth.OAUTH_VERIFIER);
            try {
                provider.retrieveAccessToken(consumer, verifier);
                // might need to retrieve user's login
                if (consumer.getUsername() == null) {
                  String ownerEmail = consumer.getOwnerEmail();
                  if (ownerEmail != null) {
                    consumer.setUsername(ownerEmail);
                  } else {
                    throw new IllegalStateException("Could not obtain user login");
                  }
               }
            } catch (Exception e) {
                Log.e(TAG, "error", e);

                notifyUser(android.R.drawable.stat_sys_warning, "Error",
                    getString(R.string.gmail_connected_fail),
                    e.getMessage());

                return null;
            }
            return consumer;
        }

        @Override
        protected void onPostExecute(XOAuthConsumer consumer) {
            if (consumer != null) {
                PrefStore.setOauthTokens(SmsSync.this, consumer.getToken(), consumer.getTokenSecret());
                PrefStore.setLoginUsername(SmsSync.this, consumer.getUsername());

                Log.d(TAG, "updated tokens");
                updateConnected();
                updateUsernameLabelFromPref();
            }
        }
    }

    private void notifyUser(int icon, String shortText, String title, String text) {
        Notification n = new Notification(icon, shortText, System.currentTimeMillis());
        n.setLatestEventInfo(this,
            title,
            text,
            PendingIntent.getActivity(this, 0, new Intent(this, SmsSync.class), 0));

        getNotifier().notify(0, n);
    }

    private NotificationManager getNotifier() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private void show(Dialogs d) {
        showDialog(d.ordinal());
    }

    private void dismiss(Dialogs d) {
        dismissDialog(d.ordinal());
    }

    private void setPreferenceListeners(PreferenceManager prefMgr) {
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

        prefMgr.findPreference(PrefStore.PREF_LOGIN_PASSWORD)
               .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (PrefStore.isFirstSync(SmsSync.this) && PrefStore.isLoginUsernameSet(SmsSync.this)) {
                   show(Dialogs.NEED_FIRST_MANUAL_SYNC);
                }
                return true;
            }
        });

        prefMgr.findPreference(PrefStore.PREF_SERVER_AUTHENTICATION)
               .setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
               if (PrefStore.AuthMode.valueOf(newValue.toString().toUpperCase()) ==
                   PrefStore.AuthMode.PLAIN) {
                  updateConnected().setEnabled(false);
               } else {
                  updateConnected().setEnabled(true);
               }
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

        prefMgr.findPreference(PrefStore.PREF_SERVER_ADDRESS).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                preference.setTitle(newValue.toString());
                SharedPreferences prefs = preference.getSharedPreferences();
                final String oldValue = prefs.getString(PrefStore.PREF_SERVER_ADDRESS, null);
                if (!newValue.equals(oldValue)) {
                    // We need to post the reset of sync state such that we do not interfere
                    // with the current transaction of the SharedPreference.
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PrefStore.clearSyncData(SmsSync.this);
                            if (oldValue != null) {
                                show(Dialogs.SYNC_DATA_RESET);
                            }
                        }
                    });
                }
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
    }

    StatusPreference getStatusPreference() {
        return mStatusPref;
    }
}
