package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.LoginSessionCreateCommand;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class LoginSessionService {

    private final IamSessionRepository sessionRepository;

    public LoginSessionService(IamSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public void createLoginSession(LoginSessionCreateCommand command) {
        LocalDateTime now = LocalDateTime.now();
        IamSession session = new IamSession(
                null,
                command.sessionNo(),
                command.userId(),
                command.accessTokenId(),
                command.refreshTokenId(),
                "WEB",
                command.clientIp(),
                LocalDateTime.ofInstant(command.refreshTokenExpiresAt(), ZoneId.systemDefault()),
                null,
                IamSession.SessionStatus.ACTIVE,
                now
        );
        sessionRepository.create(session);
    }
}
