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

import static com.google.common.truth.Truth.assertThat;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.AUTH_TOKEN_TYPE;
import static com.zegoggles.smssync.activity.auth.AccountManagerAuthActivity.GOOGLE_TYPE;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class TokenRefresherTest {

    @Mock AccountManager accountManager;
    @Mock AuthPreferences authPreferences;
    @Mock OAuth2Client oauth2Client;
    TokenRefresher refresher;

    @Before public void before() {
        initMocks(this);
        refresher = new TokenRefresher(accountManager, oauth2Client, authPreferences);
    }

    @Test public void shouldInvalidateTokenManually() throws Exception {
        assertThat(refresher.invalidateToken("token")).isTrue();
        verify(accountManager).invalidateAuthToken(GOOGLE_TYPE, "token");
    }

    @Test public void shouldHandleSecurityExceptionWhenInvalidatingToken() throws Exception {
        doThrow(new SecurityException()).when(accountManager).invalidateAuthToken(GOOGLE_TYPE, "token");
        assertThat(refresher.invalidateToken("token")).isFalse();
    }

    @Test public void shouldInvalidateTokenOnRefresh() throws Exception {
        when(authPreferences.getOauth2Token()).thenReturn("token");
        when(authPreferences.getOauth2Username()).thenReturn("username");

        when(accountManager.getAuthToken(notNull(Account.class),
                anyString(),
                isNull(Bundle.class),
                anyBoolean(),
                any(AccountManagerCallback.class),
                any(Handler.class))).thenReturn(mock(AccountManagerFuture.class));

        try {
            refresher.refreshOAuth2Token();
            fail("expected error ");
        } catch (TokenRefreshException e) {
            assertThat(e.getMessage()).isEqualTo("no bundle received from accountmanager");
        }
        verify(accountManager).invalidateAuthToken(GOOGLE_TYPE, "token");
    }

    @Test public void shouldHandleExceptionsThrownByFuture() throws Exception {
        when(authPreferences.getOauth2Token()).thenReturn("token");
        when(authPreferences.getOauth2Username()).thenReturn("username");


        AccountManagerFuture<Bundle> future = mock(AccountManagerFuture.class);
        when(accountManager.getAuthToken(notNull(Account.class),
                anyString(),
                isNull(Bundle.class),
                anyBoolean(),
                any(AccountManagerCallback.class),
                any(Handler.class))).thenReturn(future);
        AuthenticatorException exception = new AuthenticatorException();
        when(future.getResult()).thenThrow(exception);

        try {
            refresher.refreshOAuth2Token();
            fail("expected exception");
        } catch (TokenRefreshException e) {

            assertThat(e.getCause()).isSameAs(exception);
        }

        verify(accountManager).invalidateAuthToken(GOOGLE_TYPE, "token");
    }


    @Test public void shouldSetNewTokenAfterRefresh() throws Exception {
        when(authPreferences.getOauth2Token()).thenReturn("token");
        when(authPreferences.getOauth2Username()).thenReturn("username");

        AccountManagerFuture<Bundle> future = mock(AccountManagerFuture.class);

        when(accountManager.getAuthToken(
                new Account("username", GOOGLE_TYPE),
                AUTH_TOKEN_TYPE, null,true, null, null)
        ).thenReturn(future);

        Bundle bundle = new Bundle();
        bundle.putString(AccountManager.KEY_AUTHTOKEN, "newToken");

        when(future.getResult()).thenReturn(bundle);

        refresher.refreshOAuth2Token();

        verify(authPreferences).setOauth2Token("username", "newToken", null);
    }

    @Test public void shouldUseOAuth2ClientWhenRefreshTokenIsPresent() throws Exception {
        when(authPreferences.getOauth2Token()).thenReturn("token");
        when(authPreferences.getOauth2RefreshToken()).thenReturn("refresh");
        when(authPreferences.getOauth2Username()).thenReturn("username");

        when(oauth2Client.refreshToken("refresh")).thenReturn(new OAuth2Token("newToken", "type", null, 0, null));

        refresher.refreshOAuth2Token();

        verify(authPreferences).setOauth2Token("username", "newToken", "refresh");
    }

    @Test public void shouldUpdateRefreshTokenIfPresentInResponse() throws Exception {
        when(authPreferences.getOauth2Token()).thenReturn("token");
        when(authPreferences.getOauth2RefreshToken()).thenReturn("refresh");
        when(authPreferences.getOauth2Username()).thenReturn("username");

        when(oauth2Client.refreshToken("refresh")).thenReturn(new OAuth2Token("newToken", "type", "newRefresh", 0, null));

        refresher.refreshOAuth2Token();

        verify(authPreferences).setOauth2Token("username", "newToken", "newRefresh");
    }
}
