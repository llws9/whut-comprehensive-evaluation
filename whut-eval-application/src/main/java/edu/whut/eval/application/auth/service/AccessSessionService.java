package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.AccessSessionValidationCommand;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class AccessSessionService {

    private final IamSessionRepository sessionRepository;

    public AccessSessionService(IamSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void validateAccessSession(AccessSessionValidationCommand command) {
        if (command.accessTokenId() == null || command.accessTokenId().isBlank()) {
            throw new AuthenticationFailedException("access token 缺少唯一标识");
        }
        if (command.sessionNo() == null || command.sessionNo().isBlank()) {
            throw new AuthenticationFailedException("access token 缺少会话标识");
        }
        IamSession session = sessionRepository.findByAccessTokenId(command.accessTokenId());
        if (session == null || !session.isActive()) {
            throw new AuthenticationFailedException("access token 会话不存在或已失效");
        }
        if (!command.userId().equals(session.getUserId()) || !command.sessionNo().equals(session.getSessionNo())) {
            throw new AuthenticationFailedException("access token 会话不匹配");
        }
    }
}
