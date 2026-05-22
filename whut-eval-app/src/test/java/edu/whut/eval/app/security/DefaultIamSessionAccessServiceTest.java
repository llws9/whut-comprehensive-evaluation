package edu.whut.eval.app.security;

import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.model.IamSessionStatus;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationException;
import edu.whut.eval.infra.security.session.DefaultIamSessionAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DefaultIamSessionAccessServiceTest {

    @Mock
    private IamSessionRepository iamSessionRepository;

    @InjectMocks
    private DefaultIamSessionAccessService service;

    @Test
    void shouldAllowActiveSession() {
        given(iamSessionRepository.findBySessionId("sid-1001")).willReturn(Optional.of(new IamSession(
                1L,
                1001L,
                "sid-1001",
                "127.0.0.1",
                "JUnit",
                LocalDateTime.now().plusMinutes(10),
                null,
                IamSessionStatus.ACTIVE,
                LocalDateTime.now().minusMinutes(1)
        )));

        assertThatCode(() -> service.assertActive("sid-1001"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWhenSessionIsRevoked() {
        given(iamSessionRepository.findBySessionId("sid-revoked")).willReturn(Optional.of(new IamSession(
                1L,
                1001L,
                "sid-revoked",
                "127.0.0.1",
                "JUnit",
                LocalDateTime.now().plusMinutes(10),
                LocalDateTime.now().minusMinutes(1),
                IamSessionStatus.REVOKED,
                LocalDateTime.now().minusHours(1)
        )));

        assertThatThrownBy(() -> service.assertActive("sid-revoked"))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessage("session is invalid");
    }

    @Test
    void shouldExtendSessionExpiration() {
        LocalDateTime expiredAt = LocalDateTime.of(2026, 5, 22, 20, 0, 0);

        service.extendExpiration("sid-1001", expiredAt);

        verify(iamSessionRepository).extendExpiration("sid-1001", expiredAt);
    }
}
