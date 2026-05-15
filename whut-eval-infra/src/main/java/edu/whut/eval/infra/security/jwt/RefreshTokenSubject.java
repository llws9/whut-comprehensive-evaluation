package edu.whut.eval.infra.security.jwt;

public class RefreshTokenSubject {

    private final Long userId;
    private final String userNo;
    private final String identity;

    public RefreshTokenSubject(Long userId, String userNo, String identity) {
        this.userId = userId;
        this.userNo = userNo;
        this.identity = identity;
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
}
