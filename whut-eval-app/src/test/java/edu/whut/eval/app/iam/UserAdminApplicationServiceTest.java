package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.service.SessionRevocationService;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.application.iam.service.UserAdminApplicationService;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.query.UserPageQuery;
import edu.whut.eval.domain.iam.repository.IamUserCommandRepository;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.when;

class UserAdminApplicationServiceTest {

    @Test
    void shouldQueryRepositoryForPagedUsers() {
        IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
        IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
        SessionRevocationService revocationService = mock(SessionRevocationService.class);

        UserAdminApplicationService service = new UserAdminApplicationService(
                queryRepository,
                commandRepository,
                revocationService
        );

        when(queryRepository.pageUsers(any(UserPageQuery.class))).thenReturn(new PageResult<>(1L, List.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        )));

        PageResult<UserAdminPageItemView> result = service.pageUsers(
                new UserAdminPageQuery(1, 20, "王", "ACTIVE", 2002L)
        );

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().getFirst().userNo()).isEqualTo("2024305001");
        verify(queryRepository).pageUsers(any(UserPageQuery.class));
    }

    @Test
    void shouldRevokeSessionsWhenStatusBecomesDisabled() {
        IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
        IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
        SessionRevocationService revocationService = mock(SessionRevocationService.class);

        UserAdminApplicationService service = new UserAdminApplicationService(
                queryRepository,
                commandRepository,
                revocationService
        );

        when(commandRepository.updateStatus(1010L, "DISABLED")).thenReturn(true);

        service.updateStatus(1010L, new UpdateUserStatusCommand("DISABLED", "manual disable"));

        verify(revocationService).revokeAllActiveSessions(1010L, "user_disabled");
    }

    @Test
    void shouldRevokeSessionsWhenStatusBecomesLocked() {
        IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
        IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
        SessionRevocationService revocationService = mock(SessionRevocationService.class);

        UserAdminApplicationService service = new UserAdminApplicationService(
                queryRepository,
                commandRepository,
                revocationService
        );

        when(commandRepository.updateStatus(1010L, "LOCKED")).thenReturn(true);

        service.updateStatus(1010L, new UpdateUserStatusCommand("LOCKED", "manual lock"));

        verify(revocationService).revokeAllActiveSessions(1010L, "user_locked");
    }

    @Test
    void shouldNotRevokeSessionsWhenStatusBecomesActive() {
        IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
        IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
        SessionRevocationService revocationService = mock(SessionRevocationService.class);

        UserAdminApplicationService service = new UserAdminApplicationService(
                queryRepository,
                commandRepository,
                revocationService
        );

        when(commandRepository.updateStatus(1010L, "ACTIVE")).thenReturn(true);

        service.updateStatus(1010L, new UpdateUserStatusCommand("ACTIVE", "re-activate"));

        verify(revocationService, org.mockito.Mockito.never()).revokeAllActiveSessions(
                org.mockito.Mockito.anyLong(),
                org.mockito.Mockito.anyString()
        );
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundForStatusUpdate() {
        IamUserQueryRepository queryRepository = mock(IamUserQueryRepository.class);
        IamUserCommandRepository commandRepository = mock(IamUserCommandRepository.class);
        SessionRevocationService revocationService = mock(SessionRevocationService.class);

        UserAdminApplicationService service = new UserAdminApplicationService(
                queryRepository,
                commandRepository,
                revocationService
        );

        when(commandRepository.updateStatus(999L, "DISABLED")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () ->
                service.updateStatus(999L, new UpdateUserStatusCommand("DISABLED", "manual disable"))
        );
    }
}