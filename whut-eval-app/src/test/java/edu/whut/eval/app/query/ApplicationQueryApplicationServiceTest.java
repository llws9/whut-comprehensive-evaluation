package edu.whut.eval.app.query;

import edu.whut.eval.application.application.query.ApplicationRecordView;
import edu.whut.eval.application.application.service.ApplicationQueryApplicationService;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.application.model.ApplicationRecord;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ApplicationQueryApplicationServiceTest {

    @Mock
    private UserAuthorizationContextAssembler userAuthorizationContextAssembler;

    @Mock
    private ApplicationQueryRepository applicationQueryRepository;

    @InjectMocks
    private ApplicationQueryApplicationService applicationQueryApplicationService;

    @Test
    void shouldPageAccessibleApplicationsWithDefaultPermission() {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(AuthorizationPermissionCodes.APPLICATION_REVIEW),
                List.of()
        );
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(authorizationContext);
        given(applicationQueryRepository.pageAccessibleApplications(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageResult<>(1, List.of(
                        new ApplicationRecord(9001L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE")
                )));

        PageResult<ApplicationRecordView> result = applicationQueryApplicationService.pageAccessibleApplications(
                new ApplicationPageQuery(1, 10, null, null, null, null, null)
        );

        ArgumentCaptor<edu.whut.eval.domain.application.query.ApplicationAccessContext> captor =
                ArgumentCaptor.forClass(edu.whut.eval.domain.application.query.ApplicationAccessContext.class);
        org.mockito.Mockito.verify(applicationQueryRepository).pageAccessibleApplications(
                captor.capture(),
                org.mockito.ArgumentMatchers.any(ApplicationPageQuery.class)
        );

        assertThat(captor.getValue().getPermissionCode()).isEqualTo(AuthorizationPermissionCodes.APPLICATION_REVIEW);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).extracting(ApplicationRecordView::getApplicationId).containsExactly(9001L);
    }

    @Test
    void shouldPageAccessibleApplicationsWithSpecifiedStudentPermission() {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(AuthorizationPermissionCodes.APPLICATION_VIEW_SELF),
                List.of()
        );
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(authorizationContext);
        given(applicationQueryRepository.pageAccessibleApplications(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageResult<>(1, List.of(
                        new ApplicationRecord(9001L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE")
                )));

        PageResult<ApplicationRecordView> result = applicationQueryApplicationService.pageAccessibleApplications(
                new ApplicationPageQuery(1, 10, null, null, null, null, null),
                AuthorizationPermissionCodes.APPLICATION_VIEW_SELF
        );

        ArgumentCaptor<edu.whut.eval.domain.application.query.ApplicationAccessContext> captor =
                ArgumentCaptor.forClass(edu.whut.eval.domain.application.query.ApplicationAccessContext.class);
        org.mockito.Mockito.verify(applicationQueryRepository).pageAccessibleApplications(
                captor.capture(),
                org.mockito.ArgumentMatchers.any(ApplicationPageQuery.class)
        );

        assertThat(captor.getValue().getPermissionCode()).isEqualTo(AuthorizationPermissionCodes.APPLICATION_VIEW_SELF);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).extracting(ApplicationRecordView::getApplicationId).containsExactly(9001L);
    }

    @Test
    void shouldRejectWhenCurrentUserDoesNotHaveApplicationPermission() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED),
                List.of()
        ));

        assertThatThrownBy(() -> applicationQueryApplicationService.pageAccessibleApplications(
                new ApplicationPageQuery(1, 10, null, null, null, null, null)
        ))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无权限访问申请列表");
    }

    @Test
    void shouldRejectWhenCurrentUserDoesNotHaveSpecifiedStudentPermission() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(AuthorizationPermissionCodes.APPLICATION_REVIEW),
                List.of()
        ));

        assertThatThrownBy(() -> applicationQueryApplicationService.pageAccessibleApplications(
                new ApplicationPageQuery(1, 10, null, null, null, null, null),
                AuthorizationPermissionCodes.APPLICATION_VIEW_SELF
        ))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无权限访问申请列表");
    }
}
