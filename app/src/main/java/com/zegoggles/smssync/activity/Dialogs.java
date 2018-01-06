package com.zegoggles.smssync.activity;


import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.squareup.otto.Subscribe;
import com.zegoggles.smssync.App;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.events.SettingsResetEvent;
import com.zegoggles.smssync.tasks.OAuth2CallbackTask;
import com.zegoggles.smssync.utils.AppLog;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static com.zegoggles.smssync.activity.MainActivity.Actions.Backup;
import static com.zegoggles.smssync.activity.MainActivity.Actions.BackupSkip;

public class Dialogs {
    enum Type {
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

        BaseFragment instantiate(Context context) {
            return (BaseFragment) Fragment.instantiate(context, fragment.getName());
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class BaseFragment extends AppCompatDialogFragment {
        Dialog createMessageDialog(String title, String msg) {
            return new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .create();
        }
    }

    public static class MissingCredentials extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String title = getString(R.string.ui_dialog_missing_credentials_title);
            final String msg = /* authPreferences.useXOAuth() */ true ?
                    getString(R.string.ui_dialog_missing_credentials_msg_xoauth) :
                    getString(R.string.ui_dialog_missing_credentials_msg_plain);

            return createMessageDialog(title, msg);
        }
    }

    public static class FirstSync extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            DialogInterface.OnClickListener firstSyncListener =
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            App.post(which == BUTTON_NEGATIVE ? BackupSkip : Backup);
                        }
                    };
            final int maxItems = /* preferences.getMaxItemsPerSync() */ 0;
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
            return createMessageDialog(title, msg);
        }
    }

    public static class About extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            View contentView = getActivity().getLayoutInflater().inflate(R.layout.about_dialog, null, false);
            WebView webView = (WebView) contentView.findViewById(R.id.about_content);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url)));
                    return true;

                }
            });
            webView.loadUrl("file:///android_asset/about.html");
            return new AlertDialog.Builder(getContext())
                    .setCustomTitle(null)
                    .setPositiveButton(android.R.string.ok, null)
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
                            dismiss();
                        }
                    })
                    .setMessage(R.string.ui_dialog_reset_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
        }
        /*
        private void reset() {
            preferences.getDataTypePreferences().clearLastSyncData();
            preferences.reset();
        }
        */
    }

    public static class Disconnect extends BaseFragment {

        public Disconnect() {
            super();
        }

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setCustomTitle(null)
                    .setMessage(R.string.ui_dialog_disconnect_msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            /*
                            authPreferences.clearOAuth1Data();
                            authPreferences.clearOauth2Data();
                            preferences.getDataTypePreferences().clearLastSyncData();
                            */
                            App.post(new SettingsResetEvent());
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
            ProgressDialog acc = new ProgressDialog(getContext());
            acc.setTitle(null);
            acc.setMessage(getString(R.string.ui_dialog_access_token_msg));
            acc.setIndeterminate(true);
            acc.setCancelable(false);
            return acc;
        }

        @Subscribe
        public void onOAuth2Callback(OAuth2CallbackTask.OAuth2CallbackEvent event) {
            dismiss();
        }
    }

    public static class AccessTokenError extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String title = getString(R.string.ui_dialog_access_token_error_title);
            final String msg = getString(R.string.ui_dialog_access_token_error_msg);
            return createMessageDialog(title, msg);
        }
    }

    public static class Connect extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setCustomTitle(null)
                    .setMessage(getString(R.string.ui_dialog_connect_msg, getString(R.string.app_name)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
//                            startActivityForResult(new Intent(MainActivity.this, OAuth2WebAuthActivity.class)
//                                    .setData(oauth2Client.requestUrl()), REQUEST_WEB_AUTH);
                            dismiss();
                        }
                    }).create();
        }
    }

    public static class ConnectTokenError extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setCustomTitle(null)
                    .setMessage(R.string.ui_dialog_connect_token_error)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).create();
        }
    }

    public static class AccountManagerTokenError extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setCustomTitle(null)
                    .setMessage(R.string.ui_dialog_account_manager_token_error)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
//                            handleFallbackAuth();
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();
        }
    }

    public static class Upgrade extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String title = getString(R.string.ui_dialog_upgrade_title);
            final String msg = getString(R.string.ui_dialog_upgrade_msg);
            return createMessageDialog(title, msg);
        }
    }

    public static class ViewLog extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return AppLog.displayAsDialog(App.LOG, getContext());
        }

        /*
        protected void onPrepareDialog(int id, Dialog dialog) {
            super.onPrepareDialog(id, dialog);
            switch (Dialogs.Type.values()[id]) {
                case VIEW_LOG:
                    View view = dialog.findViewById(AppLog.ID);
                    if (view instanceof TextView) {
                        AppLog.readLog(App.LOG, (TextView) view);
                    }
            }
        }
        */
    }

    public static class ConfirmAction extends BaseFragment {
        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getContext())
                    .setTitle(R.string.ui_dialog_confirm_action_title)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
//                            if (mActions != null) {
//                                performAction(mActions, false);
//                            }
                        }
                    })
                    .setMessage(R.string.ui_dialog_confirm_action_msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
        }
    }

    public static class SmsDefaultPackage extends BaseFragment {
        private static final int REQUEST_CHANGE_DEFAULT_SMS_PACKAGE = 1;

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
            final Intent changeIntent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                    .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getContext().getPackageName());

            startActivityForResult(changeIntent, REQUEST_CHANGE_DEFAULT_SMS_PACKAGE);
        }
    }

     /*
    @Override
    protected Dialog onCreateDialog(final int id) {
        String title, msg;
        switch (Dialogs.Type.values()[id]) {
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

            case ACCOUNT_MANAGER_TOKEN_ERROR:
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
                                preferences.getDataTypePreferences().clearLastSyncData();
                                App.post(new SettingsResetEvent());
                            }
                        }).create();
            case UPGRADE_FROM_SMSBACKUP:
                title = getString(R.string.ui_dialog_upgrade_title);
                msg = getString(R.string.ui_dialog_upgrade_msg);
                break;
            case CONFIRM_ACTION:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.ui_dialog_confirm_action_title)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mActions != null) {
                                    performAction(mActions, false);
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
        return createMessageDialog(title, msg);
    }
    */
}

