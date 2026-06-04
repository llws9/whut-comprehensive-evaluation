package edu.whut.eval.infra.security.jwt;

import java.time.Instant;

public class JwtTokenPair {

    private final String accessToken;
    private final Instant accessTokenExpiresAt;
    private final String refreshToken;
    private final Instant refreshTokenExpiresAt;
    private final String sessionNo;
    private final String accessTokenId;
    private final String refreshTokenId;

    public JwtTokenPair(String accessToken,
                        Instant accessTokenExpiresAt,
                        String refreshToken,
                        Instant refreshTokenExpiresAt) {
        this(accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt, null, null, null);
    }

    public JwtTokenPair(String accessToken,
                        Instant accessTokenExpiresAt,
                        String refreshToken,
                        Instant refreshTokenExpiresAt,
                        String sessionNo,
                        String accessTokenId,
                        String refreshTokenId) {
        this.accessToken = accessToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.refreshToken = refreshToken;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.sessionNo = sessionNo;
        this.accessTokenId = accessTokenId;
        this.refreshTokenId = refreshTokenId;
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

    public String getSessionNo() {
        return sessionNo;
    }

    public String getAccessTokenId() {
        return accessTokenId;
    }

    public String getRefreshTokenId() {
        return refreshTokenId;
    }
}
