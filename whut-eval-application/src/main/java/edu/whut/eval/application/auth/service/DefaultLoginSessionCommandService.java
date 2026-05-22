package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.LoginSessionCreateCommand;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.model.IamSessionStatus;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DefaultLoginSessionCommandService implements LoginSessionCommandService {

    private final IamSessionRepository iamSessionRepository;

    public DefaultLoginSessionCommandService(IamSessionRepository iamSessionRepository) {
        this.iamSessionRepository = iamSessionRepository;
    }

    @Override
    public void create(LoginSessionCreateCommand command) {
        iamSessionRepository.save(new IamSession(
                null,
                command.userId(),
                command.sessionId(),
                command.loginIp(),
                command.userAgent(),
                command.expiredAt(),
                null,
                IamSessionStatus.ACTIVE,
                LocalDateTime.now()
        ));
    }
}
