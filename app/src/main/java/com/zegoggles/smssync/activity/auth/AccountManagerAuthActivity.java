package com.zegoggles.smssync.activity.auth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDialogFragment;
import android.util.Log;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.Dialogs;
import com.zegoggles.smssync.activity.MainActivity;

import static com.zegoggles.smssync.App.TAG;

public class AccountManagerAuthActivity extends AppCompatActivity {
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_ERROR = "error";
    private static final String EXTRA_DENIED = "denied";
    public static final String EXTRA_ACCOUNT = "account";

    public static final String ACTION_ADD_ACCOUNT = "addAccount";
    public static final String ACTION_FALLBACK_AUTH = "fallBackAuth";

    public static final String AUTH_TOKEN_TYPE = "oauth2:https://mail.google.com/";
    public static final String GOOGLE_TYPE = "com.google";

    private AccountManager accountManager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accountManager = AccountManager.get(this);

        Account[] accounts = accountManager.getAccountsByType(GOOGLE_TYPE);
        if (accounts == null || accounts.length == 0) {
            Log.d(TAG, "no google accounts found on this device, using standard auth");
            setResult(RESULT_OK, new Intent(ACTION_FALLBACK_AUTH));
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing()) {
            Bundle args = new Bundle();
            args.putParcelableArray(AccountDialogs.ACCOUNTS, accountManager.getAccountsByType(GOOGLE_TYPE));
            ((AppCompatDialogFragment)Fragment.instantiate(this, AccountDialogs.class.getName(), args))
                .show(getSupportFragmentManager(), null);
        }
    }

    private void onAccountSelected(final Account account) {
        accountManager.getAuthToken(account, AUTH_TOKEN_TYPE, null, this, new AccountManagerCallback<Bundle>() {
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    String token = future.getResult().getString(AccountManager.KEY_AUTHTOKEN);
                    useToken(account, token);
                } catch (OperationCanceledException e) {
                    onAccessDenied();
                } catch (Exception e) {
                    handleException(e);
                }
            }
        }, null);
    }

    private void onAccessDenied() {
        Intent result = new Intent(this, MainActivity.class)
                .setAction(ACTION_ADD_ACCOUNT)
                .putExtra(EXTRA_DENIED, true);
        setResult(RESULT_OK, result);
        finish();
    }

    private void handleException(Exception e) {
        Log.w(TAG, e);
        Intent result = new Intent(this, MainActivity.class)
                .setAction(ACTION_ADD_ACCOUNT)
                .putExtra(EXTRA_ERROR, e.getMessage());
        setResult(RESULT_OK, result);
        finish();
    }

    private void useToken(Account account, String token) {
        Intent result = new Intent(ACTION_ADD_ACCOUNT)
                .putExtra(EXTRA_ACCOUNT, account.name)
                .putExtra(EXTRA_TOKEN, token);
        setResult(RESULT_OK, result);
        finish();
    }

    public static class AccountDialogs extends Dialogs.BaseFragment {
        static final String ACCOUNTS = "accounts";

        @Override @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.select_google_account);
            final Account[] accounts = (Account[]) getArguments().getParcelableArray(ACCOUNTS);
            String[] names = new String[accounts.length];
            for (int i = 0; i < accounts.length; i++) {
                names[i] = accounts[i].name;
            }
            return builder.setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    ((AccountManagerAuthActivity)getActivity()).onAccountSelected(accounts[which]);
                    dialog.dismiss();
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    getActivity().finish();
                }
            })
              .create();
        }
    }
}
