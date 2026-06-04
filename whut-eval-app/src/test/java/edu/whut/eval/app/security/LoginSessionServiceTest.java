package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.LoginSessionCreateCommand;
import edu.whut.eval.application.auth.service.LoginSessionService;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;

class LoginSessionServiceTest {

    @Test
    void shouldCreateActiveLoginSessionFromIssuedTokenPair() {
        IamSessionRepository repository = mock(IamSessionRepository.class);
        LoginSessionService service = new LoginSessionService(repository);
        Instant refreshExpiresAt = Instant.now().plusSeconds(604800);
        LoginSessionCreateCommand command = new LoginSessionCreateCommand(
                1001L,
                "session-no-123",
                "access-jti-123",
                "refresh-jti-456",
                refreshExpiresAt,
                "127.0.0.1",
                "JUnit"
        );

        service.createLoginSession(command);

        ArgumentCaptor<IamSession> captor = ArgumentCaptor.forClass(IamSession.class);
        then(repository).should().create(captor.capture());
        IamSession session = captor.getValue();
        assertThat(session.getSessionNo()).isEqualTo("session-no-123");
        assertThat(session.getUserId()).isEqualTo(1001L);
        assertThat(session.getAccessTokenId()).isEqualTo("access-jti-123");
        assertThat(session.getRefreshTokenId()).isEqualTo("refresh-jti-456");
        assertThat(session.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(session.getDeviceType()).isEqualTo("WEB");
        assertThat(session.getStatus()).isEqualTo(IamSession.SessionStatus.ACTIVE);
        assertThat(session.getExpiredAt()).isAfter(LocalDateTime.now().plusDays(6));
    }
}
