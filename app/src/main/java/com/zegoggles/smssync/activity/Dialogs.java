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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.events.AccountRemovedEvent;
import com.zegoggles.smssync.activity.events.FallbackAuthEvent;
import com.zegoggles.smssync.activity.events.PerformAction;
import com.zegoggles.smssync.activity.events.PerformAction.Actions;
import com.zegoggles.smssync.activity.events.SettingsResetEvent;
import com.zegoggles.smssync.tasks.OAuth2CallbackTask;
import com.zegoggles.smssync.utils.AppLog;

import static android.R.drawable.ic_dialog_alert;
import static android.R.drawable.ic_dialog_info;
import static android.R.string.cancel;
import static android.R.string.ok;
import static android.R.string.yes;
import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static com.zegoggles.smssync.activity.MainActivity.REQUEST_WEB_AUTH;
import static com.zegoggles.smssync.activity.events.PerformAction.Actions.Backup;
import static com.zegoggles.smssync.activity.events.PerformAction.Actions.BackupSkip;

public class Dialogs {
    public enum Type {
        FIRST_SYNC(FirstSync.class),
        MISSING_CREDENTIALS(MissingCredentials.class),
        INVALID_IMAP_FOLDER(InvalidImapFolder.class),
        CONFIRM_ACTION(ConfirmAction.class),
        SMS_DEFAULT_PACKAGE_CHANGE(SmsRequestDefaultPackage.class),
        // menu
        ABOUT(About.class),
        RESET(Reset.class),
        VIEW_LOG(ViewLog.class),
        // connect flow
        WEB_CONNECT(WebConnect.class),
        OAUTH2_ACCESS_TOKEN_PROGRESS(OAuth2AccessTokenProgress.class),
        OAUTH2_ACCESS_TOKEN_ERROR(OAuth2AccessTokenError.class),
        ACCOUNT_MANAGER_TOKEN_ERROR(AccountManagerTokenError.class),
        DISCONNECT(Disconnect.class);

        final Class<? extends BaseFragment> fragment;
        Type(Class<? extends BaseFragment> fragment) {
            this.fragment = fragment;
        }

        public BaseFragment instantiate(FragmentManager fragmentManager, @Nullable Bundle args) {
            Fragment fragment = fragmentManager.getFragmentFactory().instantiate(
                    getClass().getClassLoader(),
                    this.fragment.getName());
            fragment.setArguments(args);
            return (BaseFragment) fragment;
        }
    }

    public static class BaseFragment extends AppCompatDialogFragment {
        Dialog createMessageDialog(String title, String msg, int icon) {
            return new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(msg)
                .setIcon(icon)
                .setPositiveButton(ok, null)
                .create();
        }
    }

    public static class MissingCredentials extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return createMessageDialog(
                getString(R.string.ui_dialog_missing_credentials_title),
                getString(R.string.ui_dialog_missing_credentials_msg_plain),
                ic_dialog_alert);
        }
    }

    public static class FirstSync extends BaseFragment {
        static final String MAX_ITEMS_PER_SYNC = "max_items_per_sync";
        static final int SKIP_BUTTON = BUTTON_NEGATIVE;

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            OnClickListener firstSyncListener =
                    new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            App.post(which == SKIP_BUTTON ? BackupSkip : Backup);
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
        private static final String SCROLL_POSITION = "scrollPosition";
        private static final String ABOUT_HTML = "file:///android_asset/about.html";
        private WebView webView;

        @Override @NonNull @SuppressLint("InflateParams")
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            final View contentView = getActivity().getLayoutInflater().inflate(R.layout.about_dialog, null, false);
            webView = (WebView) contentView.findViewById(R.id.about_content);
            final float scrollPosition = savedInstanceState == null ? 0f : savedInstanceState.getFloat(SCROLL_POSITION);

            webView.setWebViewClient(new WebViewClient() {
                @Override @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)));
                    return true;
                }

                @Override @SuppressWarnings("deprecation") public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (scrollPosition > 0 &&
                        ABOUT_HTML.equals(url) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        view.setScrollY((int) (view.getContentHeight() * view.getScale() * scrollPosition));
                    }
                }
            });
            webView.loadUrl(ABOUT_HTML);
            return new AlertDialog.Builder(getContext())
                .setPositiveButton(ok, null)
                .setIcon(R.drawable.ic_sms_backup)
                .setTitle(getString(R.string.app_name) + " " + App.getVersionName(getContext()))
                .setView(contentView)
                .create();
        }

        @Override @SuppressWarnings("deprecation")
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            final float position = webView.getScrollY() / (webView.getContentHeight() * webView.getScale());
            outState.putFloat(SCROLL_POSITION, position);
        }
    }

    public static class Reset extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                .setIcon(ic_dialog_alert)
                .setTitle(R.string.ui_dialog_reset_title)
                .setPositiveButton(ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        App.post(new SettingsResetEvent());
                    }
                })
                .setMessage(R.string.ui_dialog_reset_message)
                .setNegativeButton(cancel, null)
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
                .setNegativeButton(cancel, null)
                .setPositiveButton(ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        App.post(new AccountRemovedEvent());
                    }
                }).create();
        }
    }

    public static class AccessTokenProgress extends BaseFragment {
        @SuppressWarnings("deprecation")
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // NB: progress dialog is not AppCompat-ready, and will not appear themed
            //     correctly on older devices
            android.app.ProgressDialog progress = new android.app.ProgressDialog(getContext());
            progress.setTitle(null);
            progress.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
            progress.setMessage(getString(R.string.ui_dialog_access_token_msg));
            progress.setIndeterminate(true);
            progress.setCancelable(false);
            return progress;
        }
    }

    public static class OAuth2AccessTokenError extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String title = getString(R.string.ui_dialog_access_token_error_title);
            final String msg = getString(R.string.ui_dialog_access_token_error_msg);
            return createMessageDialog(title, msg, ic_dialog_alert);
        }
    }

    public static class OAuth2AccessTokenProgress extends AccessTokenProgress {
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

        @Subscribe public void onOAuth2Callback(OAuth2CallbackTask.OAuth2CallbackEvent event) {
            dismissAllowingStateLoss();
        }
    }

    public static class AccountManagerTokenError extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.status_unknown_error)
                    .setIcon(ic_dialog_alert)
                    .setMessage(R.string.ui_dialog_account_manager_token_error)
                    .setPositiveButton(yes, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            App.post(new FallbackAuthEvent(false));
                        }
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .create();
        }
    }

    public static class WebConnect extends BaseFragment {
        static final String INTENT = "intent";

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                .setTitle(null)
                .setMessage(getString(R.string.ui_dialog_connect_msg, getString(R.string.app_name)))
                .setNegativeButton(cancel, null)
                .setPositiveButton(ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        final Intent intent = getArguments().getParcelable(INTENT);
                        getActivity().startActivityForResult(intent, REQUEST_WEB_AUTH);
                    }
                }).create();
        }
    }

    public static class ViewLog extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return AppLog.displayAsDialog(getContext());
        }
    }

    public static class ConfirmAction extends BaseFragment {
        static final String ACTION = "action";

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                .setTitle(R.string.ui_dialog_confirm_action_title)
                .setPositiveButton(ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        App.post(new PerformAction(Actions.valueOf(getArguments().getString(ACTION)), false));
                    }
                })
                .setMessage(R.string.ui_dialog_confirm_action_msg)
                .setIcon(ic_dialog_alert)
                .setNegativeButton(cancel, null)
                .create();
        }
    }

    public static class SmsRequestDefaultPackage extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                .setTitle(R.string.ui_dialog_sms_default_package_change_title)
                .setIcon(ic_dialog_info)
                .setPositiveButton(ok, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity)getActivity()).requestDefaultSmsPackageChange();
                        }
                    }
                })
                .setMessage(R.string.ui_dialog_sms_default_package_change_msg)
                .create();
        }
    }
}

