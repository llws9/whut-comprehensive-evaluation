package edu.whut.eval.domain.iam.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public record IamSession(
        Long id,
        Long userId,
        String sessionId,
        String loginIp,
        String userAgent,
        LocalDateTime expiredAt,
        LocalDateTime revokedAt,
        IamSessionStatus status,
        LocalDateTime createdAt
) implements Serializable {

    public boolean isActive(LocalDateTime now) {
        return status == IamSessionStatus.ACTIVE
                && revokedAt == null
                && expiredAt != null
                && expiredAt.isAfter(now);
    }

    public IamSession revoke(LocalDateTime revokedAt) {
        return new IamSession(
                id,
                userId,
                sessionId,
                loginIp,
                userAgent,
                expiredAt,
                revokedAt,
                IamSessionStatus.REVOKED,
                createdAt
        );
    }

    public IamSession extendTo(LocalDateTime expiredAt) {
        return new IamSession(
                id,
                userId,
                sessionId,
                loginIp,
                userAgent,
                expiredAt,
                revokedAt,
                status,
                createdAt
        );
    }
}
