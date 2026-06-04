package edu.whut.eval.application.auth.model;

import java.time.Instant;

public record RefreshSessionContinueCommand(
        String sessionNo,
        String oldRefreshTokenId,
        String newAccessTokenId,
        String newRefreshTokenId,
        Instant refreshTokenExpiresAt
) {
}
