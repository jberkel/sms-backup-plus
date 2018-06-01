package com.zegoggles.smssync.preferences;

import android.preference.PreferenceManager;
import com.fsck.k9.mail.AuthType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
public class AuthPreferencesTest {
    private AuthPreferences authPreferences;

    @Before public void before() {
        initMocks(this);
        authPreferences = new AuthPreferences(RuntimeEnvironment.application);
    }

    @Test public void testStoreUri() throws Exception {
        PreferenceManager
            .getDefaultSharedPreferences(RuntimeEnvironment.application)
            .edit()
            .putString("server_address", "foo.com:993")
            .putString("server_protocol", "+ssl+")
            .putString("server_authentication", "plain")
            .commit();

        authPreferences.setImapUser("a:user");
        authPreferences.setImapPassword("password:has:colons");
        assertThat(authPreferences.getStoreUri()).isEqualTo("imap+ssl+://PLAIN:a%253Auser:password%253Ahas%253Acolons@foo.com:993");
    }

    @Test public void testStoreUriWithXOAuth2() throws Exception {
        PreferenceManager
            .getDefaultSharedPreferences(RuntimeEnvironment.application)
            .edit()
            .putString("server_address", "imap.gmail.com:993")
            .putString("server_protocol", "+ssl+")
            .putString("server_authentication", "xoauth")
            .commit();

        authPreferences.setOauth2Token("user", "token", null);

        assertThat(authPreferences.getStoreUri()).isEqualTo("imap+ssl+://XOAUTH2:user:dXNlcj11c2VyAWF1dGg9QmVhcmVyIHRva2VuAQE%253D@imap.gmail.com:993");
    }
}
