package edu.whut.eval.application.auth.service;

import edu.whut.eval.common.log.AppLog;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Logout 默认实现。
 * 按访问令牌 ID 撤销会话。
 */
@Service
public class DefaultLogoutService implements LogoutService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLogoutService.class);

    private final IamSessionRepository sessionRepository;
    private final SessionRevocationService sessionRevocationService;

    public DefaultLogoutService(IamSessionRepository sessionRepository,
                                 SessionRevocationService sessionRevocationService) {
        this.sessionRepository = sessionRepository;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Override
    public boolean logoutByAccessTokenId(String accessTokenId) {
        if (accessTokenId == null || accessTokenId.isBlank()) {
            AppLog.warn(log, "security.auth.logout.accessTokenId.null");
            return false;
        }

        IamSession session = sessionRepository.findByAccessTokenId(accessTokenId);
        if (session == null) {
            AppLog.warn(log, "security.auth.logout.session.not-found",
                    "accessTokenId", accessTokenId);
            return false;
        }

        if (!session.isActive()) {
            AppLog.warn(log, "security.auth.logout.session.inactive",
                    "accessTokenId", accessTokenId,
                    "sessionId", session.getId(),
                    "status", session.getStatus());
            return false;
        }

        boolean revoked = sessionRevocationService.revokeSession(session.getId(), "logout");
        if (revoked) {
            AppLog.info(log, "security.auth.logout.by-access-token.completed",
                    "accessTokenId", accessTokenId,
                    "sessionId", session.getId());
        }
        return revoked;
    }
}