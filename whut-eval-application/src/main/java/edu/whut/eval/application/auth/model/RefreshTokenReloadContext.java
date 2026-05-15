package edu.whut.eval.application.auth.model;

public record RefreshTokenReloadContext(
        Long userId,
        String userNo,
        String identity
) {
}
