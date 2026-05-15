package edu.whut.eval.infra.security.jwt;

import java.time.Instant;

public class JwtTokenPair {

    private final String accessToken;
    private final Instant accessTokenExpiresAt;
    private final String refreshToken;
    private final Instant refreshTokenExpiresAt;

    public JwtTokenPair(String accessToken,
                        Instant accessTokenExpiresAt,
                        String refreshToken,
                        Instant refreshTokenExpiresAt) {
        this.accessToken = accessToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshToken = refreshToken;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getRefreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }
}
