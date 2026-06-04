package edu.whut.eval.application.auth.model;

import java.time.Instant;

public record LoginSessionCreateCommand(
        Long userId,
        String sessionNo,
        String accessTokenId,
        String refreshTokenId,
        Instant refreshTokenExpiresAt,
        String clientIp,
        String userAgent
) {
}
