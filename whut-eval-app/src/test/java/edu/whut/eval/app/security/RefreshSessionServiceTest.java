package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.RefreshSessionContinueCommand;
import edu.whut.eval.application.auth.model.RefreshSessionValidationCommand;
import edu.whut.eval.application.auth.service.RefreshSessionService;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;

class RefreshSessionServiceTest {

    @Test
    void shouldValidateActiveRefreshSession() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        RefreshSessionService service = new RefreshSessionService(repository);
        given(repository.findByRefreshTokenId("refresh-jti-456")).willReturn(activeSession());

        service.validateRefreshSession(new RefreshSessionValidationCommand(
                1001L, "session-no-123", "refresh-jti-456"
        ));

        then(repository).should().findByRefreshTokenId("refresh-jti-456");
    }

    @Test
    void shouldRejectMissingRefreshSession() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        RefreshSessionService service = new RefreshSessionService(repository);
        given(repository.findByRefreshTokenId("missing-refresh-jti")).willReturn(null);

        assertThatThrownBy(() -> service.validateRefreshSession(new RefreshSessionValidationCommand(
                1001L, "session-no-123", "missing-refresh-jti"
        )))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("refresh token 会话不存在或已失效");
    }

    @Test
    void shouldRejectSessionNoMismatch() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        RefreshSessionService service = new RefreshSessionService(repository);
        given(repository.findByRefreshTokenId("refresh-jti-456")).willReturn(activeSession());

        assertThatThrownBy(() -> service.validateRefreshSession(new RefreshSessionValidationCommand(
                1001L, "other-session", "refresh-jti-456"
        )))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("refresh token 会话不匹配");
    }

    @Test
    void shouldContinueRefreshSessionWithNewTokenIds() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        RefreshSessionService service = new RefreshSessionService(repository);

        given(repository.continueRefreshSession(
                org.mockito.ArgumentMatchers.eq("session-no-123"),
                org.mockito.ArgumentMatchers.eq("old-refresh-jti"),
                org.mockito.ArgumentMatchers.eq("new-access-jti"),
                org.mockito.ArgumentMatchers.eq("new-refresh-jti"),
                any(LocalDateTime.class)
        )).willReturn(true);

        service.continueRefreshSession(new RefreshSessionContinueCommand(
                "session-no-123",
                "old-refresh-jti",
                "new-access-jti",
                "new-refresh-jti",
                Instant.now().plusSeconds(604800)
        ));

        then(repository).should().continueRefreshSession(
                org.mockito.ArgumentMatchers.eq("session-no-123"),
                org.mockito.ArgumentMatchers.eq("old-refresh-jti"),
                org.mockito.ArgumentMatchers.eq("new-access-jti"),
                org.mockito.ArgumentMatchers.eq("new-refresh-jti"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
    }

    private IamSession activeSession() {
        return new IamSession(
                91L,
                "session-no-123",
                1001L,
                "access-jti-123",
                "refresh-jti-456",
                "WEB",
                "127.0.0.1",
                LocalDateTime.now().plusDays(7),
                null,
                IamSession.SessionStatus.ACTIVE,
                LocalDateTime.now().minusMinutes(1)
        );
    }
}
