package edu.whut.eval.infra.security.jwt;

public class RefreshTokenSubject {

    private final Long userId;
    private final String userNo;
    private final String identity;
    private final String sessionId;

    public RefreshTokenSubject(Long userId, String userNo, String identity, String sessionId) {
        this.userId = userId;
        this.userNo = userNo;
        this.identity = identity;
        this.sessionId = sessionId;
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

    public String getSessionId() {
        return sessionId;
    }
}
