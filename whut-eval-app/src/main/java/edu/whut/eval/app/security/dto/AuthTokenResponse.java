package edu.whut.eval.app.security.dto;

import java.time.Instant;

public class AuthTokenResponse {

    private final String accessToken;
    private final String accessTokenType;
    private final Instant accessTokenExpiresAt;
    private final String refreshToken;
    private final String refreshTokenType;
    private final Instant refreshTokenExpiresAt;

    public AuthTokenResponse(String accessToken,
                             String accessTokenType,
                             Instant accessTokenExpiresAt,
                             String refreshToken,
                             String refreshTokenType,
                             Instant refreshTokenExpiresAt) {
        this.accessToken = accessToken;
        this.accessTokenType = accessTokenType;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshToken = refreshToken;
        this.refreshTokenType = refreshTokenType;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getAccessTokenType() {
        return accessTokenType;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getRefreshTokenType() {
        return refreshTokenType;
    }

    public Instant getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }
}
