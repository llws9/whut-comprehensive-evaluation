package edu.whut.eval.domain.iam.model;

import java.time.LocalDateTime;

/**
 * 登录会话领域模型。
 */
public class IamSession {

    private final Long id;
    private final String sessionNo;
    private final Long userId;
    private final String accessTokenId;
    private final String refreshTokenId;
    private final String deviceType;
    private final String clientIp;
    private final LocalDateTime expiredAt;
    private final LocalDateTime revokedAt;
    private final SessionStatus status;
    private final LocalDateTime createdAt;

    public IamSession(Long id,
                      String sessionNo,
                      Long userId,
                      String accessTokenId,
                      String refreshTokenId,
                      String deviceType,
                      String clientIp,
                      LocalDateTime expiredAt,
                      LocalDateTime revokedAt,
                      SessionStatus status,
                      LocalDateTime createdAt) {
        this.id = id;
        this.sessionNo = sessionNo;
        this.userId = userId;
        this.accessTokenId = accessTokenId;
        this.refreshTokenId = refreshTokenId;
        this.deviceType = deviceType;
        this.clientIp = clientIp;
        this.expiredAt = expiredAt;
        this.revokedAt = revokedAt;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSessionNo() {
        return sessionNo;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAccessTokenId() {
        return accessTokenId;
    }

    public String getRefreshTokenId() {
        return refreshTokenId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getClientIp() {
        return clientIp;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE && expiredAt != null && expiredAt.isAfter(LocalDateTime.now());
    }

    public boolean isRevoked() {
        return status == SessionStatus.REVOKED || revokedAt != null;
    }

    public boolean isExpired() {
        return status == SessionStatus.EXPIRED || (expiredAt != null && expiredAt.isBefore(LocalDateTime.now()));
    }

    /**
     * 会话状态枚举。
     */
    public enum SessionStatus {
        ACTIVE,
        REVOKED,
        EXPIRED
    }
}