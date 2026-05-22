package edu.whut.eval.application.auth.model;

import java.time.LocalDateTime;

public record LoginSessionCreateCommand(
        Long userId,
        String sessionId,
        String loginIp,
        String userAgent,
        LocalDateTime expiredAt
) {
}
