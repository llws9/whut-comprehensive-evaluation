package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.RefreshSessionContinueCommand;
import edu.whut.eval.application.auth.model.RefreshSessionValidationCommand;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class RefreshSessionService {

    private final IamSessionRepository sessionRepository;

    public RefreshSessionService(IamSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void validateRefreshSession(RefreshSessionValidationCommand command) {
        if (command.refreshTokenId() == null || command.refreshTokenId().isBlank()) {
            throw new AuthenticationFailedException("refresh token 缺少唯一标识");
        }
        if (command.sessionNo() == null || command.sessionNo().isBlank()) {
            throw new AuthenticationFailedException("refresh token 缺少会话标识");
        }
        IamSession session = sessionRepository.findByRefreshTokenId(command.refreshTokenId());
        if (session == null || !session.isActive()) {
            throw new AuthenticationFailedException("refresh token 会话不存在或已失效");
        }
        if (!command.userId().equals(session.getUserId()) || !command.sessionNo().equals(session.getSessionNo())) {
            throw new AuthenticationFailedException("refresh token 会话不匹配");
        }
    }

    @Transactional
    public void continueRefreshSession(RefreshSessionContinueCommand command) {
        LocalDateTime expiredAt = LocalDateTime.ofInstant(command.refreshTokenExpiresAt(), ZoneId.systemDefault());
        boolean updated = sessionRepository.continueRefreshSession(
                command.sessionNo(),
                command.oldRefreshTokenId(),
                command.newAccessTokenId(),
                command.newRefreshTokenId(),
                expiredAt
        );
        if (!updated) {
            throw new AuthenticationFailedException("refresh token 会话延续失败");
        }
    }
}
