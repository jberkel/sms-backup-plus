package com.zegoggles.smssync.auth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.os.Bundle;
import android.os.Handler;
import com.zegoggles.smssync.preferences.AuthPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.AUTH_TOKEN_TYPE;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.GOOGLE_TYPE;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class TokenRefresherTest {

    @Mock AccountManager accountManager;
    @Mock AuthPreferences authPreferences;
    TokenRefresher refresher;

    @Before public void before() {
        initMocks(this);
        refresher = new TokenRefresher(accountManager, authPreferences);
    }

    @Test public void shouldInvalidateTokenManually() throws Exception {
        refresher.invalidateToken("token");
        verify(accountManager).invalidateAuthToken(GOOGLE_TYPE, "token");
    }

    @Test public void shouldInvalidateTokenOnRefresh() throws Exception {
        when(authPreferences.getOauth2Token()).thenReturn("token");
        when(authPreferences.getUsername()).thenReturn("username");

        when(accountManager.getAuthToken(notNull(Account.class),
                anyString(),
                anyBoolean(),
                any(AccountManagerCallback.class),
                any(Handler.class))).thenReturn(mock(AccountManagerFuture.class));

        assertThat(refresher.refreshOAuth2Token()).isFalse();
        verify(accountManager).invalidateAuthToken(GOOGLE_TYPE, "token");
    }

    @Test public void shouldHandleExceptionsThrownByFuture() throws Exception {
        when(authPreferences.getOauth2Token()).thenReturn("token");
        when(authPreferences.getUsername()).thenReturn("username");


        AccountManagerFuture<Bundle> future = mock(AccountManagerFuture.class);
        when(accountManager.getAuthToken(notNull(Account.class),
                anyString(),
                anyBoolean(),
                any(AccountManagerCallback.class),
                any(Handler.class))).thenReturn(future);
        when(future.getResult()).thenThrow(new AuthenticatorException());

        assertThat(refresher.refreshOAuth2Token()).isFalse();

        verify(accountManager).invalidateAuthToken(GOOGLE_TYPE, "token");
    }


    @Test public void shouldSetNewTokenAfterRefresh() throws Exception {
        when(authPreferences.getOauth2Token()).thenReturn("token");
        when(authPreferences.getUsername()).thenReturn("username");

        AccountManagerFuture<Bundle> future = mock(AccountManagerFuture.class);

        when(accountManager.getAuthToken(
                new Account("username", GOOGLE_TYPE),
                AUTH_TOKEN_TYPE, true, null, null)
        ).thenReturn(future);

        Bundle bundle = new Bundle();
        bundle.putString(AccountManager.KEY_AUTHTOKEN, "newToken");

        when(future.getResult()).thenReturn(bundle);

        assertThat(refresher.refreshOAuth2Token()).isTrue();

        verify(authPreferences).setOauth2Token("username", "newToken");
    }
}
