package com.zegoggles.smssync.auth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class OAuth2TokenTest {
    @Test public void testFromJSON() throws Exception {
        final String response = "{\n" +
                "  \"access_token\":\"1/fFAGRNJru1FTz70BzhT3Zg\",\n" +
                "  \"expires_in\":3920,\n" +
                "  \"token_type\":\"Bearer\",\n" +
                "  \"refresh_token\":\"1/xEoDL4iW3cxlI7yDbSRFYNG01kVKM2C-259HOF2aQbI\"\n" +
                "}";


        final OAuth2Token token = OAuth2Token.fromJSON(response);

        assertThat(token.accessToken).isEqualTo("1/fFAGRNJru1FTz70BzhT3Zg");
        assertThat(token.tokenType).isEqualTo("Bearer");
        assertThat(token.refreshToken).isEqualTo("1/xEoDL4iW3cxlI7yDbSRFYNG01kVKM2C-259HOF2aQbI");
        assertThat(token.expiresIn).isEqualTo(3920);
    }

    @Test public void testFromJSONWithMissingFields() throws Exception {
        final String response = "{\n" +
                "  \"access_token\":\"1/fFAGRNJru1FTz70BzhT3Zg\"\n" +
                "}";
        final OAuth2Token token = OAuth2Token.fromJSON(response);

        assertThat(token.accessToken).isEqualTo("1/fFAGRNJru1FTz70BzhT3Zg");
        assertThat(token.tokenType).isNull();
        assertThat(token.refreshToken).isNull();
        assertThat(token.expiresIn).isEqualTo(-1);
    }

    @Test public void testFromJSONWithoutRefreshToken() throws Exception {
        final String response = "{\n" +
                "  \"access_token\":\"1/fFAGRNJru1FTz70BzhT3Zg\",\n" +
                "  \"expires_in\":3920,\n" +
                "  \"token_type\":\"Bearer\"\n" +
                "}";


        final OAuth2Token token = OAuth2Token.fromJSON(response);

        assertThat(token.accessToken).isEqualTo("1/fFAGRNJru1FTz70BzhT3Zg");
        assertThat(token.tokenType).isEqualTo("Bearer");
        assertThat(token.refreshToken).isNull();
        assertThat(token.expiresIn).isEqualTo(3920);
    }

    @Test public void testTokenForLogging() throws Exception {
        OAuth2Token token = new OAuth2Token("secret", "type", "secret", 100, "Test");
        assertThat(token.getTokenForLogging()).doesNotContain("secret");
        assertThat(token.toString()).doesNotContain("secret");
    }
}
