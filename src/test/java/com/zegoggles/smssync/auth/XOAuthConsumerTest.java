package com.zegoggles.smssync.auth;

import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.fest.assertions.api.Assertions.assertThat;

@Ignore
@RunWith(RobolectricTestRunner.class)
public class XOAuthConsumerTest {

    @Test
    public void shouldGenerateString() throws Exception {
        XOAuthConsumer consumer = new XOAuthConsumer("username");
        assertThat(consumer.generateXOAuthString()).isNotEmpty();
    }

    @Test
    public void shouldgetProvider() throws Exception {
        XOAuthConsumer consumer = new XOAuthConsumer("username");
        CommonsHttpOAuthProvider provider = consumer.getProvider(Robolectric.application);

        assertThat(provider).isNotNull();
        assertThat(provider.getAccessTokenEndpointUrl()).isEqualTo("https://www.google.com/accounts/OAuthGetAccessToken");
        assertThat(provider.getRequestTokenEndpointUrl()).isEqualTo("https://www.google.com/accounts/OAuthGetRequestToken?scope=https%3A%2F%2Fmail.google.com%2F+https%3A%2F%2Fwww.google.com%2Fm8%2Ffeeds%2F&xoauth_displayname=SMS+Backup%2B");
        assertThat(provider.getAuthorizationWebsiteUrl()).isEqualTo("https://www.google.com/accounts/OAuthAuthorizeToken?btmpl=mobile");
        assertThat(provider.isOAuth10a()).isTrue();
    }
}
