package edu.whut.eval.application.auth.model;

public record RefreshTokenReloadContext(
        Long userId,
        String userNo,
        String identity,
        String sessionId
) {
    public RefreshTokenReloadContext(Long userId, String userNo, String identity) {
        this(userId, userNo, identity, null);
    }
}
