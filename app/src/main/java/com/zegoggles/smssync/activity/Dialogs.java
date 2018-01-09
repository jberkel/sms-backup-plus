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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.MainActivity.Actions;
import com.zegoggles.smssync.activity.events.DisconnectAccountEvent;
import com.zegoggles.smssync.activity.events.FallbackAuthEvent;
import com.zegoggles.smssync.activity.events.SettingsResetEvent;
import com.zegoggles.smssync.tasks.OAuth2CallbackTask;
import com.zegoggles.smssync.utils.AppLog;

import static android.R.drawable.ic_dialog_alert;
import static android.R.drawable.ic_dialog_info;
import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT;
import static android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME;
import static com.zegoggles.smssync.activity.MainActivity.Actions.Backup;
import static com.zegoggles.smssync.activity.MainActivity.Actions.BackupSkip;

public class Dialogs {
    public enum Type {
        MISSING_CREDENTIALS(MissingCredentials.class),
        FIRST_SYNC(FirstSync.class),
        INVALID_IMAP_FOLDER(InvalidImapFolder.class),
        ABOUT(About.class),
        RESET(Reset.class),
        DISCONNECT(Disconnect.class),
        REQUEST_TOKEN(RequestToken.class),
        ACCESS_TOKEN(AccessToken.class),
        ACCESS_TOKEN_ERROR(AccessTokenError.class),
        CONNECT(Connect.class),
        CONNECT_TOKEN_ERROR(ConnectTokenError.class),
        ACCOUNT_MANAGER_TOKEN_ERROR(AccountManagerTokenError.class),
        UPGRADE_FROM_SMSBACKUP(Upgrade.class),
        VIEW_LOG(ViewLog.class),
        CONFIRM_ACTION(ConfirmAction.class),
        SMS_DEFAULT_PACKAGE_CHANGE(SmsDefaultPackage.class);

        final Class<? extends BaseFragment> fragment;

        Type(Class<? extends BaseFragment> fragment) {
            this.fragment = fragment;
        }

        public BaseFragment instantiate(Context context, @Nullable Bundle args) {
            return (BaseFragment) Fragment.instantiate(context, fragment.getName(), args);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class BaseFragment extends AppCompatDialogFragment {
        Dialog createMessageDialog(String title, String msg, int icon) {
            return new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(msg)
                .setIcon(icon)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        }
    }

    public static class MissingCredentials extends BaseFragment {
        static final String USE_XOAUTH = "use_xoauth";

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final boolean useXOAuth = getArguments().getBoolean(USE_XOAUTH);
            final String title = getString(R.string.ui_dialog_missing_credentials_title);
            final String msg = useXOAuth ?
                    getString(R.string.ui_dialog_missing_credentials_msg_xoauth) :
                    getString(R.string.ui_dialog_missing_credentials_msg_plain);

            return createMessageDialog(title, msg, ic_dialog_alert);
        }
    }

    public static class FirstSync extends BaseFragment {
        static final String MAX_ITEMS_PER_SYNC = "max_items_per_sync";

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            DialogInterface.OnClickListener firstSyncListener =
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            App.post(which == BUTTON_NEGATIVE ? BackupSkip : Backup);
                        }
                    };
            final int maxItems = getArguments().getInt(MAX_ITEMS_PER_SYNC);
            final String syncMsg = maxItems < 0 ?
                    getString(R.string.ui_dialog_first_sync_msg) :
                    getString(R.string.ui_dialog_first_sync_msg_batched, maxItems);

            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.ui_dialog_first_sync_title)
                    .setMessage(syncMsg)
                    .setPositiveButton(R.string.ui_sync, firstSyncListener)
                    .setNegativeButton(R.string.ui_skip, firstSyncListener)
                    .create();
        }
    }

    public static class InvalidImapFolder extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String title = getString(R.string.ui_dialog_invalid_imap_folder_title);
            final String msg = getString(R.string.ui_dialog_invalid_imap_folder_msg);
            return createMessageDialog(title, msg, ic_dialog_alert);
        }
    }

    public static class About extends BaseFragment {
        @Override @NonNull @SuppressLint("InflateParams")
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final View contentView = getActivity().getLayoutInflater().inflate(R.layout.about_dialog, null, false);
            final WebView webView = (WebView) contentView.findViewById(R.id.about_content);
            webView.setWebViewClient(new WebViewClient() {
                @Override @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)));
                    return true;
                }
            });
            webView.loadUrl("file:///android_asset/about.html");
            return new AlertDialog.Builder(getContext())
                    .setPositiveButton(android.R.string.ok, null)
                    .setIcon(R.drawable.ic_launcher)
                    .setTitle(R.string.menu_info)
                    .setView(contentView)
                    .create();
        }
    }

    public static class Reset extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.ui_dialog_reset_title)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            App.post(new SettingsResetEvent());
                        }
                    })
                    .setMessage(R.string.ui_dialog_reset_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
        }
    }

    public static class Disconnect extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setIcon(ic_dialog_alert)
                    .setTitle(R.string.ui_dialog_confirm_action_title)
                    .setMessage(R.string.ui_dialog_disconnect_msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            App.post(new DisconnectAccountEvent());
                        }
                    }).create();
        }
    }

    public static class RequestToken extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog req = new ProgressDialog(getContext());
            req.setTitle(null);
            req.setMessage(getString(R.string.ui_dialog_request_token_msg));
            req.setIndeterminate(true);
            req.setCancelable(false);
            return req;
        }
    }

    public static class AccessToken extends BaseFragment {
        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            App.register(this);
        }

        @Override
        public void onDetach() {
            super.onDetach();
            App.unregister(this);
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog progress = new ProgressDialog(getContext());
            progress.setTitle(null);
            progress.setMessage(getString(R.string.ui_dialog_access_token_msg));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            return progress;
        }

        @Subscribe public void onOAuth2Callback(OAuth2CallbackTask.OAuth2CallbackEvent event) {
            dismiss();
        }
    }

    public static class AccessTokenError extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String title = getString(R.string.ui_dialog_access_token_error_title);
            final String msg = getString(R.string.ui_dialog_access_token_error_msg);
            return createMessageDialog(title, msg, ic_dialog_alert);
        }
    }

    public static class Connect extends BaseFragment {
        static final int REQUEST_WEB_AUTH = 3;
        static final String INTENT = "intent";

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setMessage(getString(R.string.ui_dialog_connect_msg, getString(R.string.app_name)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            final Intent intent = getArguments().getParcelable(INTENT);
                            getActivity().startActivityForResult(intent, REQUEST_WEB_AUTH);
                        }
                    }).create();
        }
    }

    public static class ConnectTokenError extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                .setTitle(R.string.status_unknown_error)
                .setIcon(ic_dialog_alert)
                .setMessage(R.string.ui_dialog_connect_token_error)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        }
    }

    public static class AccountManagerTokenError extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                .setTitle(R.string.status_unknown_error)
                .setIcon(ic_dialog_alert)
                .setMessage(R.string.ui_dialog_account_manager_token_error)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        App.post(new FallbackAuthEvent());
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .create();
        }
    }

    public static class Upgrade extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String title = getString(R.string.ui_dialog_upgrade_title);
            final String msg = getString(R.string.ui_dialog_upgrade_msg);
            return createMessageDialog(title, msg, ic_dialog_info);
        }
    }

    public static class ViewLog extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return AppLog.displayAsDialog(App.LOG, getContext());
        }
    }

    public static class ConfirmAction extends BaseFragment {
        static final String ACTION = "action";

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.ui_dialog_confirm_action_title)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            App.post(Actions.valueOf(getArguments().getString(ACTION)));
                        }
                    })
                    .setMessage(R.string.ui_dialog_confirm_action_msg)
                    .setIcon(ic_dialog_alert)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
        }
    }

    public static class SmsDefaultPackage extends BaseFragment {
        static final int REQUEST_CHANGE_DEFAULT_SMS_PACKAGE = 1;

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.ui_dialog_sms_default_package_change_title)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            requestDefaultSmsPackageChange();
                        }
                    })
                    .setMessage(R.string.ui_dialog_sms_default_package_change_msg)
                    .create();
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        private void requestDefaultSmsPackageChange() {
            final Intent changeIntent = new Intent(ACTION_CHANGE_DEFAULT)
                    .putExtra(EXTRA_PACKAGE_NAME, getContext().getPackageName());

            startActivityForResult(changeIntent, REQUEST_CHANGE_DEFAULT_SMS_PACKAGE);
        }
    }
}

