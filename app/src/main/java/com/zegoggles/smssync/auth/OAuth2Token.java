package com.zegoggles.smssync.auth;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;

import static com.zegoggles.smssync.App.TAG;

public class OAuth2Token {
    public final String accessToken;
    public final String tokenType;
    public final String refreshToken;
    public final int expiresIn;
    public final String userName;

    OAuth2Token(String accessToken, String tokenType, String refreshToken, int expiresIn, String userName) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.userName = userName;
    }

    public static OAuth2Token fromJSON(String string) throws IOException {
        try {
            Object value = new JSONTokener(string).nextValue();
            if (value instanceof JSONObject) {
                return fromJSON((JSONObject) value);
            } else {
                throw new IOException("Invalid JSON data: "+value);
            }
        } catch (JSONException e) {
            Log.w(TAG, "JSON parse error", e);
            throw new IOException("Error parsing data: "+e.getMessage());
        }
    }

    private static OAuth2Token fromJSON(JSONObject object) throws IOException {
        try {
            return new OAuth2Token(
                object.getString("access_token"),
                object.optString("token_type", null),
                object.optString("refresh_token", null),
                object.optInt("expires_in", -1),
                null);
        } catch (JSONException e) {
            Log.w(TAG, "JSON parse error", e);
            throw new IOException("parse error");
        }
    }

    @Override
    public String toString() {
        return getTokenForLogging();
    }

    @SuppressWarnings("ReplaceAllDot")
    public String getTokenForLogging() {
        return "Token{" +
                "accessToken='" + (accessToken != null ? accessToken.replaceAll(".", "X") : null) + '\'' +
                ", tokenType='" + tokenType + '\'' +
                ", refreshToken='" + (refreshToken != null ? refreshToken.replaceAll(".", "X") : null) + '\'' +
                ", expiresIn=" + expiresIn +
                ", userName='" + userName + '\'' +
                '}';
    }
}
