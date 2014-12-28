package com.zegoggles.smssync.mail;

import com.fsck.k9.mail.ssl.DefaultTrustedSocketFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static com.zegoggles.smssync.mail.BackupImapStore.isValidImapFolder;
import static com.zegoggles.smssync.mail.BackupImapStore.isValidUri;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class BackupImapStoreTest {
    @Test public void shouldTestForValidUri() throws Exception {
        assertThat(isValidUri("imap+ssl+://xoauth:foooo@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("imap://xoauth:foooo@imap.gmail.com")).isTrue();
        assertThat(isValidUri("imap+ssl+://xoauth:user:token@:993")).isFalse();
        assertThat(isValidUri("imap+ssl://user%40domain:password@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("imap+tls+://user:password@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("imap+tls://user:password@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("imap://user:password@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("http://xoauth:foooo@imap.gmail.com:993")).isFalse();
    }

    @Test public void shouldTestForValidFolder() throws Exception {
        assertThat(isValidImapFolder(null)).isFalse();
        assertThat(isValidImapFolder("")).isFalse();
        assertThat(isValidImapFolder("foo")).isTrue();
        assertThat(isValidImapFolder("foo bar")).isTrue();
        assertThat(isValidImapFolder(" foo")).isFalse();
        assertThat(isValidImapFolder("foo ")).isFalse();
        assertThat(isValidImapFolder("foo/nested")).isTrue();
        assertThat(isValidImapFolder("/foo/nested")).isFalse();
    }

    @Test public void testAccountHasStoreUri() throws Exception {
        String uri = "imap://xoauth:foooo@imap.gmail.com";
        BackupImapStore store = new BackupImapStore(Robolectric.application, uri);
        assertThat(store.getStoreUri()).isEqualTo(uri);
    }

    @Test public void testShouldCreateCorrectTrustFactoryForTrustedSSLUrl() throws Exception {
        String uri = "imap+ssl+://xoauth:foooo@imap.gmail.com";
        BackupImapStore store = new BackupImapStore(Robolectric.application, uri);
        assertThat(store.getTrustedSocketFactory()).isInstanceOf(DefaultTrustedSocketFactory.class);
    }

    @Test public void testShouldCreateCorrectTrustFactoryForTrustAllSSLUrl() throws Exception {
        String uri = "imap+ssl://xoauth:foooo@imap.gmail.com";
        BackupImapStore store = new BackupImapStore(Robolectric.application, uri);
        assertThat(store.getTrustedSocketFactory()).isInstanceOf(AllTrustedSocketFactory.class);
    }

    @Test public void testShouldCreateCorrectTrustFactoryForTrustedTLSUrl() throws Exception {
        String uri = "imap+tls+://xoauth:foooo@imap.gmail.com";
        BackupImapStore store = new BackupImapStore(Robolectric.application, uri);
        assertThat(store.getTrustedSocketFactory()).isInstanceOf(DefaultTrustedSocketFactory.class);
    }

    @Test public void testShouldCreateCorrectTrustFactoryForTrustAllTLSUrl() throws Exception {
        String uri = "imap+tls://xoauth:foooo@imap.gmail.com";
        BackupImapStore store = new BackupImapStore(Robolectric.application, uri);
        assertThat(store.getTrustedSocketFactory()).isInstanceOf(AllTrustedSocketFactory.class);
    }

    @Test public void shouldHaveToStringWithObfuscatedStoreURI() throws Exception {
        BackupImapStore store = new BackupImapStore(Robolectric.application, "imap://xoauth:foooo@imap.gmail.com");
        assertThat(store.getStoreUriForLogging()).isEqualTo("imap://xoauth:XXXXX@imap.gmail.com");
    }

    @Test public void shouldHaveToStringWithObfuscatedStoreURIWithPort() throws Exception {
        BackupImapStore store = new BackupImapStore(Robolectric.application, "imap://xoauth:foooo@imap.gmail.com:456");
        assertThat(store.getStoreUriForLogging()).isEqualTo("imap://xoauth:XXXXX@imap.gmail.com:456");
    }

    @Test public void shouldHaveToStringWithObfuscatedStoreURIWithoutUserInfo() throws Exception {
        BackupImapStore store = new BackupImapStore(Robolectric.application, "imap://imap.gmail.com:1234");
        assertThat(store.getStoreUriForLogging()).isEqualTo("imap://imap.gmail.com:1234");
    }

    @Test public void shouldHaveToStringWithStoreUriForLogging() throws Exception {
        BackupImapStore store = new BackupImapStore(Robolectric.application, "imap://xoauth:foooo@imap.gmail.com");
        assertThat(store.toString()).isEqualTo("BackupImapStore{uri=imap://xoauth:XXXXX@imap.gmail.com}");
    }
}
