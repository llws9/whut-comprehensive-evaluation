package edu.whut.eval.infra.security.jwt;

public class RefreshTokenSubject {

    private final Long userId;
    private final String userNo;
    private final String identity;
    private final String sessionNo;
    private final String refreshTokenId;

    public RefreshTokenSubject(Long userId, String userNo, String identity) {
        this(userId, userNo, identity, null, null);
    }

    public RefreshTokenSubject(Long userId, String userNo, String identity, String sessionNo, String refreshTokenId) {
        this.userId = userId;
        this.userNo = userNo;
        this.identity = identity;
        this.sessionNo = sessionNo;
        this.refreshTokenId = refreshTokenId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserNo() {
        return userNo;
    }

    public String getIdentity() {
        return identity;
    }

    public String getSessionNo() {
        return sessionNo;
    }

    public String getRefreshTokenId() {
        return refreshTokenId;
    }
}
