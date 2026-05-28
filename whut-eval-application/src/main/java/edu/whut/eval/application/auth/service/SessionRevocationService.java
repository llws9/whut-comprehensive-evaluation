package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话撤销服务。
 * 负责在用户状态变更时撤销所有活跃会话，强制踢出所有设备。
 */
@Service
public class SessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationService.class);

    private final IamSessionRepository sessionRepository;

    public SessionRevocationService(IamSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 撤销指定用户的所有活跃会话。
     * 用于用户被禁用（DISABLED）或锁定（LOCKED）时。
     *
     * @param userId 用户 ID
     * @param reason  撤销原因（如 "user_disabled", "user_locked"）
     * @return 被撤销的会话数量
     */
    @Transactional
    public int revokeAllActiveSessions(Long userId, String reason) {
        if (userId == null) {
            log.warn("session.revocation.userId.null reason={}", reason);
            return 0;
        }

        List<IamSession> activeSessions = sessionRepository.findActiveByUserId(userId);
        if (activeSessions.isEmpty()) {
            log.info("session.revocation.no-active-sessions userId={} reason={}", userId, reason);
            return 0;
        }

        int revokedCount = sessionRepository.revokeAllActiveByUserId(userId);
        log.info("session.revocation.completed userId={} revokedCount={} reason={} deviceTypes={}",
                userId, revokedCount, reason, activeSessions.stream().map(IamSession::getDeviceType).toList());

        return revokedCount;
    }

    /**
     * 撤销指定会话。
     * 用于踢出单个设备或 token 刷新时的旧会话清理。
     *
     * @param sessionId 会话 ID
     * @param reason    撤销原因
     * @return 是否成功撤销
     */
    @Transactional
    public boolean revokeSession(Long sessionId, String reason) {
        if (sessionId == null) {
            log.warn("session.revocation.sessionId.null reason={}", reason);
            return false;
        }

        boolean revoked = sessionRepository.revokeById(sessionId);
        if (revoked) {
            log.info("session.revocation.single.completed sessionId={} reason={}", sessionId, reason);
        } else {
            log.warn("session.revocation.single.failed sessionId={} reason={} (session may already be revoked)", sessionId, reason);
        }
        return revoked;
    }

    /**
     * 按 refresh token ID 撤销会话。
     * 用于 refresh token 重载时清理旧会话。
     */
    @Transactional
    public boolean revokeByRefreshTokenId(String refreshTokenId, String reason) {
        if (refreshTokenId == null || refreshTokenId.isBlank()) {
            return false;
        }

        IamSession session = sessionRepository.findByRefreshTokenId(refreshTokenId);
        if (session == null) {
            log.debug("session.revocation.by-refresh-token.not-found refreshTokenId={} reason={}", refreshTokenId, reason);
            return false;
        }

        return revokeSession(session.getId(), reason);
    }
}