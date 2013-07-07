package com.zegoggles.smssync.auth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
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

    private TokenRefresher(@Nullable AccountManager accountManager, AuthPreferences authPreferences) {
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
                String newToken = accountManager.getAuthToken(new Account(name, GOOGLE_TYPE),
                        AUTH_TOKEN_TYPE, true, null, null).getResult().getString(AccountManager.KEY_AUTHTOKEN);

                if (!TextUtils.isEmpty(newToken)) {
                    authPreferences.setOauth2Token(name, newToken);
                    return true;
                } else {
                    Log.w(TAG, "no new token obtained");
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

    public void invalidateToken(String token) {
        if (accountManager != null) {
            accountManager.invalidateAuthToken(GOOGLE_TYPE, token);
        }
    }
}
