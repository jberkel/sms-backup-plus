package com.zegoggles.smssync.mail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static com.zegoggles.smssync.mail.BackupImapStore.isValidUri;
import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(RobolectricTestRunner.class)
public class BackupImapStoreTest {
    @Test
    public void shouldTestForValidUri() throws Exception {
        assertThat(isValidUri("imap+ssl+://xoauth:foooo@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("imap://xoauth:foooo@imap.gmail.com")).isTrue();
        assertThat(isValidUri("imap+ssl+://xoauth:user:token@:993")).isFalse();
        assertThat(isValidUri("imap+ssl://user%40domain:password@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("imap+tls+://user:password@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("imap+tls://user:password@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("imap://user:password@imap.gmail.com:993")).isTrue();
        assertThat(isValidUri("http://xoauth:foooo@imap.gmail.com:993")).isFalse();
    }

    @Test
    public void testAccountHasStoreUri() throws Exception {
        String uri = "imap://xoauth:foooo@imap.gmail.com";
        BackupImapStore store = new BackupImapStore(Robolectric.application, uri);
        assertThat(store.getAccount().getStoreUri()).isEqualTo(uri);
    }

    @Test
    public void shouldHaveToStringWithObfuscatedStoreURI() throws Exception {
        String uri = "imap://xoauth:foooo@imap.gmail.com";
        BackupImapStore store = new BackupImapStore(Robolectric.application, uri);
        assertThat(store.toString()).isEqualTo("BackupImapStore{uri=imap://xoauth:XXXXX@imap.gmail.com}");
    }
}
