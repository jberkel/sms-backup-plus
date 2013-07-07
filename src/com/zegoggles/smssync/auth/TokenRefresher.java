package com.zegoggles.smssync.auth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import com.zegoggles.smssync.preferences.AuthPreferences;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.AUTH_TOKEN_TYPE;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.GOOGLE_TYPE;

@TargetApi(5)
public class TokenRefresher {
    private @Nullable final AccountManager accountManager;
    private AuthPreferences authPreferences;


    public TokenRefresher(Context context, AuthPreferences authPreferences) {
        this(Build.VERSION.SDK_INT >= 5 ? AccountManager.get(context) : null, authPreferences);
    }

    TokenRefresher(@Nullable AccountManager accountManager, AuthPreferences authPreferences) {
        this.authPreferences = authPreferences;
        this.accountManager = accountManager;
    }

    public boolean refreshOAuth2Token() {
        if (accountManager == null) return false;

        final String token = authPreferences.getOauth2Token();
        final String name  = authPreferences.getUsername();

        if (!TextUtils.isEmpty(token)) {
            invalidateToken(token);
            try {
                Bundle bundle = accountManager.getAuthToken(
                        new Account(name, GOOGLE_TYPE),
                        AUTH_TOKEN_TYPE,
                        true,   /* notify on failure */
                        null,   /* callback */
                        null    /* handler */
                ).getResult();

                if (bundle != null) {
                    String newToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);

                    if (!TextUtils.isEmpty(newToken)) {
                        authPreferences.setOauth2Token(name, newToken);
                        return true;
                    } else {
                        Log.w(TAG, "no new token obtained");
                        return false;
                    }
                } else {
                    Log.w(TAG, "no bundle received from accountmanager");
                    return false;
                }
            } catch (OperationCanceledException e) {
                Log.w(TAG, e);
                return false;
            } catch (IOException e) {
                Log.w(TAG, e);
                return false;
            } catch (AuthenticatorException e) {
                Log.w(TAG, e);
                return false;
            }
        } else {
            Log.w(TAG, "no current token set");
            return false;
        }
    }

    public boolean invalidateToken(String token) {
        if (accountManager != null) {

            // USE_CREDENTIALS permission should be enough according to docs
            // but some systems require MANAGE_ACCOUNTS

            // java.lang.SecurityException: caller uid 10051 lacks android.permission.MANAGE_ACCOUNTS
            try {
                accountManager.invalidateAuthToken(GOOGLE_TYPE, token);
                return true;
            } catch (SecurityException e) {
                Log.w(TAG, e);
            }
        }
        return false;
    }
}
