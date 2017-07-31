package com.zegoggles.smssync.preferences;

import com.fsck.k9.mail.AuthType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class AuthPreferencesTest {
    private AuthPreferences authPreferences;
    private @Mock ServerPreferences serverPreferences;

    @Before public void before() {
        initMocks(this);
        authPreferences = new AuthPreferences(Robolectric.application, serverPreferences);
    }

    @Test public void testStoreUri() throws Exception {
        when(serverPreferences.getServerAddress()).thenReturn("foo.com:993");
        when(serverPreferences.getServerProtocol()).thenReturn("+ssl+");

        authPreferences.setImapUser("a:user");
        authPreferences.setImapPassword("password:has:colons");
        assertThat(authPreferences.getStoreUri()).isEqualTo("imap+ssl+://PLAIN:a%253Auser:password%253Ahas%253Acolons@foo.com:993");
    }

    @Test public void testStoreUriWithXOAuth2() throws Exception {
        when(serverPreferences.getServerAddress()).thenReturn(ServerPreferences.Defaults.SERVER_ADDRESS);
        when(serverPreferences.isGmail()).thenReturn(true);

        authPreferences.setOauth2Token("user", "token", null);
        authPreferences.setServerAuthMode(AuthType.XOAUTH2);

        assertThat(authPreferences.getStoreUri()).isEqualTo("imap+ssl+://XOAUTH2:user:dXNlcj11c2VyAWF1dGg9QmVhcmVyIHRva2VuAQE%253D@imap.gmail.com:993");
    }
}
