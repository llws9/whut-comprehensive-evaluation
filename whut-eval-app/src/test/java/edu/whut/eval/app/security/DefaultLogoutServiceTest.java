package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.service.DefaultLogoutService;
import edu.whut.eval.application.auth.service.SessionRevocationService;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultLogoutServiceTest {

    @Mock
    private IamSessionRepository sessionRepository;

    @Mock
    private SessionRevocationService sessionRevocationService;

    @InjectMocks
    private DefaultLogoutService service;

    @Test
    void shouldLogoutByAccessTokenId() {
        IamSession session = new IamSession(
                123L,
                "SESSION-001",
                1001L,
                "access-token-jti-123",
                "refresh-token-jti-456",
                "web",
                "192.168.1.1",
                java.time.LocalDateTime.now().plusDays(7),
                null,
                IamSession.SessionStatus.ACTIVE,
                java.time.LocalDateTime.now().minusMinutes(1)
        );

        when(sessionRepository.findByAccessTokenId("access-token-jti-123")).thenReturn(session);
        when(sessionRevocationService.revokeSession(123L, "logout")).thenReturn(true);

        boolean result = service.logoutByAccessTokenId("access-token-jti-123");

        assertTrue(result);
        verify(sessionRepository).findByAccessTokenId("access-token-jti-123");
        verify(sessionRevocationService).revokeSession(123L, "logout");
    }

    @Test
    void shouldReturnFalseWhenAccessTokenIdIsNull() {
        boolean result = service.logoutByAccessTokenId(null);

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenAccessTokenIdIsBlank() {
        boolean result = service.logoutByAccessTokenId("");

        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenSessionNotFound() {
        when(sessionRepository.findByAccessTokenId("non-existent-jti")).thenReturn(null);

        boolean result = service.logoutByAccessTokenId("non-existent-jti");

        assertFalse(result);
        verify(sessionRepository).findByAccessTokenId("non-existent-jti");
    }
}