package edu.whut.eval.application.auth.service;

import java.time.LocalDateTime;

public interface IamSessionAccessService {

    void assertActive(String sessionId);

    void extendExpiration(String sessionId, LocalDateTime expiredAt);
}
