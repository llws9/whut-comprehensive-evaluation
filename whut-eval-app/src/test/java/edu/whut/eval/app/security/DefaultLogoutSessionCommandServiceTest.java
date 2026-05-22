package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.service.DefaultLogoutSessionCommandService;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultLogoutSessionCommandServiceTest {

    @Mock
    private IamSessionRepository iamSessionRepository;

    @InjectMocks
    private DefaultLogoutSessionCommandService service;

    @Test
    void shouldRevokeCurrentSession() {
        service.logout("sid-logout");

        verify(iamSessionRepository).revoke(eq("sid-logout"), any(LocalDateTime.class));
    }
}
