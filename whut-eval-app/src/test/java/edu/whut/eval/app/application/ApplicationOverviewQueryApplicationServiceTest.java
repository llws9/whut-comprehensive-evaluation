package edu.whut.eval.app.application;

import edu.whut.eval.application.application.query.ApplicationOverviewView;
import edu.whut.eval.application.application.service.ApplicationOverviewQueryApplicationService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.domain.application.query.ApplicationOverviewSummary;
import edu.whut.eval.domain.application.repository.ApplicationOverviewQueryRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationOverviewQueryApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ApplicationOverviewQueryRepository applicationOverviewQueryRepository = mock(ApplicationOverviewQueryRepository.class);
    private final ApplicationOverviewQueryApplicationService service =
            new ApplicationOverviewQueryApplicationService(userAuthorizationContextAssembler, applicationOverviewQueryRepository);

    @Test
    void shouldReturnCurrentStudentApplicationOverview() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationOverviewQueryRepository.getStudentOverview(1001L))
                .willReturn(new ApplicationOverviewSummary(2, 3, 1, 4, 5, "2025-2026"));

        ApplicationOverviewView overview = service.getCurrentStudentOverview();

        assertThat(overview.draftCount()).isEqualTo(2);
        assertThat(overview.submittedCount()).isEqualTo(3);
        assertThat(overview.returnedCount()).isEqualTo(1);
        assertThat(overview.approvedCount()).isEqualTo(4);
        assertThat(overview.rejectedCount()).isEqualTo(5);
        assertThat(overview.latestAcademicYear()).isEqualTo("2025-2026");
        verify(applicationOverviewQueryRepository).getStudentOverview(1001L);
    }

    @Test
    void shouldReturnZeroOverviewWhenCurrentStudentHasNoApplications() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationOverviewQueryRepository.getStudentOverview(1001L))
                .willReturn(new ApplicationOverviewSummary(0, 0, 0, 0, 0, null));

        ApplicationOverviewView overview = service.getCurrentStudentOverview();

        assertThat(overview.draftCount()).isZero();
        assertThat(overview.submittedCount()).isZero();
        assertThat(overview.returnedCount()).isZero();
        assertThat(overview.approvedCount()).isZero();
        assertThat(overview.rejectedCount()).isZero();
        assertThat(overview.latestAcademicYear()).isNull();
    }

    private UserAuthorizationContext currentUser() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.self"),
                List.of()
        );
    }
}
