package edu.whut.eval.application.auth.model;

public record RefreshSessionValidationCommand(
        Long userId,
        String sessionNo,
        String refreshTokenId
) {
}
