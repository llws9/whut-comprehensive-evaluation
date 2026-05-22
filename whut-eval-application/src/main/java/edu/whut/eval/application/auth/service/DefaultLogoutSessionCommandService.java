package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DefaultLogoutSessionCommandService implements LogoutSessionCommandService {

    private final IamSessionRepository iamSessionRepository;

    public DefaultLogoutSessionCommandService(IamSessionRepository iamSessionRepository) {
        this.iamSessionRepository = iamSessionRepository;
    }

    @Override
    public void logout(String sessionId) {
        iamSessionRepository.revoke(sessionId, LocalDateTime.now());
    }
}
