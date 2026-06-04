package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.AccessSessionValidationCommand;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;

class AccessSessionServiceTest {

    @Test
    void shouldValidateActiveAccessSession() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        AccessSessionService service = new AccessSessionService(repository);
        given(repository.findByAccessTokenId("access-jti-123")).willReturn(activeSession());

        service.validateAccessSession(new AccessSessionValidationCommand(1001L, "session-no-123", "access-jti-123"));

        then(repository).should().findByAccessTokenId("access-jti-123");
    }

    @Test
    void shouldRejectRevokedSession() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        AccessSessionService service = new AccessSessionService(repository);
        given(repository.findByAccessTokenId("access-jti-123")).willReturn(revokedSession());

        assertThatThrownBy(() -> service.validateAccessSession(new AccessSessionValidationCommand(
                1001L, "session-no-123", "access-jti-123"
        )))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("access token 会话不存在或已失效");
    }

    @Test
    void shouldRejectExpiredSession() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        AccessSessionService service = new AccessSessionService(repository);
        given(repository.findByAccessTokenId("access-jti-123")).willReturn(expiredSession());

        assertThatThrownBy(() -> service.validateAccessSession(new AccessSessionValidationCommand(
                1001L, "session-no-123", "access-jti-123"
        )))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("access token 会话不存在或已失效");
    }

    @Test
    void shouldRejectSessionMismatch() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        AccessSessionService service = new AccessSessionService(repository);
        given(repository.findByAccessTokenId("access-jti-123")).willReturn(activeSession());

        assertThatThrownBy(() -> service.validateAccessSession(new AccessSessionValidationCommand(
                1001L, "other-session", "access-jti-123"
        )))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("access token 会话不匹配");
    }

    private IamSession activeSession() {
        return new IamSession(91L, "session-no-123", 1001L, "access-jti-123", "refresh-jti-456",
                "WEB", "127.0.0.1", LocalDateTime.now().plusDays(7), null,
                IamSession.SessionStatus.ACTIVE, LocalDateTime.now().minusMinutes(1));
    }

    private IamSession revokedSession() {
        return new IamSession(91L, "session-no-123", 1001L, "access-jti-123", "refresh-jti-456",
                "WEB", "127.0.0.1", LocalDateTime.now().plusDays(7), LocalDateTime.now(),
                IamSession.SessionStatus.REVOKED, LocalDateTime.now().minusMinutes(1));
    }

    private IamSession expiredSession() {
        return new IamSession(91L, "session-no-123", 1001L, "access-jti-123", "refresh-jti-456",
                "WEB", "127.0.0.1", LocalDateTime.now().minusMinutes(1), null,
                IamSession.SessionStatus.EXPIRED, LocalDateTime.now().minusDays(8));
    }
}
