package com.zegoggles.smssync.auth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import com.zegoggles.smssync.preferences.AuthPreferences;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static android.text.TextUtils.isEmpty;
import static com.zegoggles.smssync.App.TAG;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.AUTH_TOKEN_TYPE;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.GOOGLE_TYPE;

@TargetApi(5)
public class TokenRefresher {
    private @Nullable final AccountManager accountManager;
    private final OAuth2Client oauth2Client;
    private AuthPreferences authPreferences;

    public TokenRefresher(Context context, OAuth2Client oauth2Client, AuthPreferences authPreferences) {
        this(Build.VERSION.SDK_INT >= 5 ? AccountManager.get(context) : null, oauth2Client, authPreferences);
    }

    TokenRefresher(@Nullable AccountManager accountManager, OAuth2Client oauth2Client,
                   AuthPreferences authPreferences) {
        this.authPreferences = authPreferences;
        this.oauth2Client = oauth2Client;
        this.accountManager = accountManager;
    }

    public void refreshOAuth2Token() throws TokenRefreshException{
        final String token = authPreferences.getOauth2Token();
        final String refreshToken = authPreferences.getOauth2RefreshToken();
        final String name = authPreferences.getUsername();

        if (isEmpty(token)) {
            throw new TokenRefreshException("no current token set");
        }

        if (!isEmpty(refreshToken)) {
            // user authenticated using webflow
            refreshUsingOAuth2Client(name, refreshToken);
        } else {
            refreshUsingAccountManager(token, name);
        }
    }

    private void refreshUsingAccountManager(String token, String name) throws TokenRefreshException {
        if (accountManager == null) throw new TokenRefreshException("account manager is null");
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

                if (!isEmpty(newToken)) {
                    authPreferences.setOauth2Token(name, newToken, null);
                } else {
                    throw new TokenRefreshException("no new token obtained");
                }
            } else {
                throw new TokenRefreshException("no bundle received from accountmanager");
            }
        } catch (OperationCanceledException e) {
            Log.w(TAG, e);
            throw new TokenRefreshException(e);
        } catch (IOException e) {
            Log.w(TAG, e);
            throw new TokenRefreshException(e);
        } catch (AuthenticatorException e) {
            Log.w(TAG, e);
            throw new TokenRefreshException(e);
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

    private void refreshUsingOAuth2Client(String name, String refreshToken) throws TokenRefreshException {
        try {
            final OAuth2Token token = oauth2Client.refreshToken(refreshToken);
            authPreferences.setOauth2Token(name, token.accessToken, isEmpty(token.refreshToken) ? refreshToken : token.refreshToken);
        } catch (IOException e) {
            throw new TokenRefreshException(e);
        }
    }
}
