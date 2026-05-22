package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.LoginSessionCreateCommand;
import edu.whut.eval.application.auth.service.DefaultLoginSessionCommandService;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.model.IamSessionStatus;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultLoginSessionCommandServiceTest {

    @Mock
    private IamSessionRepository iamSessionRepository;

    @InjectMocks
    private DefaultLoginSessionCommandService service;

    @Test
    void shouldCreateActiveSession() {
        LocalDateTime expiredAt = LocalDateTime.of(2026, 5, 22, 18, 0, 0);

        service.create(new LoginSessionCreateCommand(
                1001L,
                "sid-1001",
                "203.0.113.10",
                "JUnit-Agent",
                expiredAt
        ));

        ArgumentCaptor<IamSession> captor = ArgumentCaptor.forClass(IamSession.class);
        verify(iamSessionRepository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(session -> {
            assertThat(session.userId()).isEqualTo(1001L);
            assertThat(session.sessionId()).isEqualTo("sid-1001");
            assertThat(session.loginIp()).isEqualTo("203.0.113.10");
            assertThat(session.userAgent()).isEqualTo("JUnit-Agent");
            assertThat(session.expiredAt()).isEqualTo(expiredAt);
            assertThat(session.status()).isEqualTo(IamSessionStatus.ACTIVE);
            assertThat(session.revokedAt()).isNull();
            assertThat(session.createdAt()).isNotNull();
        });
    }
}
