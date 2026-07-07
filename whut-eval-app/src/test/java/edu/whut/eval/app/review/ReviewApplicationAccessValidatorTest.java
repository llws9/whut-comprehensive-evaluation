package edu.whut.eval.app.review;

import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.service.ReviewApplicationAccessValidator;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReviewApplicationAccessValidatorTest {

    private final ResourceScopeAccessEvaluator evaluator = mock(ResourceScopeAccessEvaluator.class);
    private final ReviewApplicationAccessValidator validator = new ReviewApplicationAccessValidator(evaluator);

    @Test
    void shouldAllowWhenResourceEvaluatorAllowsOrgSubtreeContext() {
        UserAuthorizationContext reviewer = reviewer();
        ReviewApplicationQueryRow row = rowWithOrgPath("/1/2002/2010/");
        given(evaluator.canAccessApplication(eq(reviewer), eq(AuthorizationPermissionCodes.APPLICATION_REVIEW), any()))
                .willReturn(ScopeAccessDecision.allow("ORG_SUBTREE", "matched scope rule"));

        validator.requireAccess(reviewer, row);

        ArgumentCaptor<ApplicationResourceContext> resourceCaptor = forClass(ApplicationResourceContext.class);
        verify(evaluator).canAccessApplication(
                eq(reviewer),
                eq(AuthorizationPermissionCodes.APPLICATION_REVIEW),
                resourceCaptor.capture()
        );
        assertThat(resourceCaptor.getValue().getApplicationId()).isEqualTo(21013L);
        assertThat(resourceCaptor.getValue().getApplicantUserId()).isEqualTo(1001L);
        assertThat(resourceCaptor.getValue().getOrgUnitId()).isEqualTo(2010L);
        assertThat(resourceCaptor.getValue().getOrgPath()).isEqualTo("/1/2002/2010/");
        assertThat(resourceCaptor.getValue().getCategoryCode()).isEqualTo("INTELLECTUAL");
        assertThat(resourceCaptor.getValue().getItemCode()).isEqualTo("INTELLECTUAL_PAPER");
    }

    @Test
    void shouldDenyWhenResourceEvaluatorDenies() {
        UserAuthorizationContext reviewer = reviewer();
        ReviewApplicationQueryRow row = rowWithOrgPath("/1/3000/");
        given(evaluator.canAccessApplication(eq(reviewer), eq(AuthorizationPermissionCodes.APPLICATION_REVIEW), any()))
                .willReturn(ScopeAccessDecision.deny("no-scope-matched"));

        assertThatThrownBy(() -> validator.requireAccess(reviewer, row))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前审核人无权访问该申请");
    }

    private UserAuthorizationContext reviewer() {
        return new UserAuthorizationContext(
                1010L,
                "reviewer-1",
                "Reviewer",
                "COUNSELOR",
                Set.of("COUNSELOR"),
                Set.of(AuthorizationPermissionCodes.APPLICATION_REVIEW),
                List.of()
        );
    }

    private ReviewApplicationQueryRow rowWithOrgPath(String orgPath) {
        ReviewApplicationQueryRow row = new ReviewApplicationQueryRow();
        row.setApplicationId(21013L);
        row.setApplicantUserId(1001L);
        row.setOrgUnitId(2010L);
        row.setOrgPath(orgPath);
        row.setCategoryCode("INTELLECTUAL");
        row.setItemCode("INTELLECTUAL_PAPER");
        return row;
    }
}
