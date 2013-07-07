package com.zegoggles.smssync.activity.auth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.zegoggles.smssync.R;
import com.zegoggles.smssync.activity.MainActivity;

import static com.zegoggles.smssync.App.TAG;

@TargetApi(5)
public class AccountManagerAuthActivity extends Activity {
    private static final int DIALOG_ACCOUNTS = 0;

    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_DENIED = "denied";
    public static final String EXTRA_ACCOUNT = "account";

    public static final String ACTION_ADD_ACCOUNT = "addAccount";
    public static final String ACTION_FALLBACKAUTH = "fallBackAuth";

    public static final String AUTH_TOKEN_TYPE = "oauth2:https://mail.google.com/";
    public static final String GOOGLE_TYPE = "com.google";

    private AccountManager accountManager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accountManager = AccountManager.get(this);

        Account[] accounts = accountManager.getAccountsByType(GOOGLE_TYPE);
        if (accounts == null || accounts.length == 0) {
            Log.d(TAG, "no google accounts found on this device, using standard auth");
            startActivity(new Intent(this, MainActivity.class).setAction(ACTION_FALLBACKAUTH));
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isFinishing()) {
            showDialog(DIALOG_ACCOUNTS);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_ACCOUNTS:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.select_google_account);
                final Account[] accounts = accountManager.getAccountsByType(GOOGLE_TYPE);
                final int size = accounts.length;
                String[] names = new String[size];
                for (int i = 0; i < size; i++) {
                    names[i] = accounts[i].name;
                }
                builder.setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        onAccountSelected(accounts[which]);
                        dialog.dismiss();
                    }
                });

                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                });
                return builder.create();
        }
        return null;
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

    // should really use setResult + finish for all callbacks, but SmsSync is singleInstance (for some reason)
    private void onAccessDenied() {
        startActivity(new Intent(this, MainActivity.class)
                .setAction(ACTION_ADD_ACCOUNT)
                .putExtra(EXTRA_DENIED, true));
        finish();
    }

    private void handleException(Exception e) {
        Log.w(TAG, e);
        startActivity(new Intent(this, MainActivity.class)
                .setAction(ACTION_ADD_ACCOUNT)
                .putExtra(EXTRA_ERROR, e.getMessage()));

        finish();
    }

    private void useToken(Account account, String token) {
        startActivity(new Intent(this, MainActivity.class)
                .setAction(ACTION_ADD_ACCOUNT)
                .putExtra(EXTRA_ACCOUNT, account.name)
                .putExtra(EXTRA_TOKEN, token));
        finish();
    }
}
