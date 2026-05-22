package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamSession;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IamSessionRepository {

    IamSession save(IamSession session);

    Optional<IamSession> findBySessionId(String sessionId);

    void revoke(String sessionId, LocalDateTime revokedAt);

    void extendExpiration(String sessionId, LocalDateTime expiredAt);
}
