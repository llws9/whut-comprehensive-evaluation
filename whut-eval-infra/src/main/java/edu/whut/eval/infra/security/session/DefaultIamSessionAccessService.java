package edu.whut.eval.infra.security.session;

import edu.whut.eval.application.auth.service.IamSessionAccessService;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class DefaultIamSessionAccessService implements IamSessionAccessService {

    private final IamSessionRepository iamSessionRepository;

    public DefaultIamSessionAccessService(IamSessionRepository iamSessionRepository) {
        this.iamSessionRepository = iamSessionRepository;
    }

    @Override
    public void assertActive(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new JwtAuthenticationException("session is invalid");
        }
        IamSession session = iamSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new JwtAuthenticationException("session is invalid"));
        if (!session.isActive(LocalDateTime.now())) {
            throw new JwtAuthenticationException("session is invalid");
        }
    }

    @Override
    public void extendExpiration(String sessionId, LocalDateTime expiredAt) {
        iamSessionRepository.extendExpiration(sessionId, expiredAt);
    }
}
