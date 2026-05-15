package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAuthorizationScopeEvaluatorTest {

    private final DefaultAuthorizationScopeEvaluator evaluator = new DefaultAuthorizationScopeEvaluator();

    @Test
    void shouldEvaluateMatchedScopesForPermission() {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student", "department-minister"),
                Set.of("application.view.assigned", "application.view.self"),
                List.of(
                        new IamScopeRule(1L, "application.view.assigned", "ORG_UNIT_ITEM", 3001L, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 20, "ACTIVE"),
                        new IamScopeRule(2L, "application.view.assigned", "ALL", null, null, null, null, 5, "ACTIVE"),
                        new IamScopeRule(3L, "application.view.assigned", "ITEM", null, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 10, "ACTIVE"),
                        new IamScopeRule(4L, "application.view.assigned", "ITEM", null, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 10, "ACTIVE"),
                        new IamScopeRule(5L, "application.view.assigned", "ITEM", null, "INTELLECTUAL", "THESIS", null, 30, "INACTIVE"),
                        new IamScopeRule(6L, "application.view.self", "SELF", null, null, null, "{\"userNo\":\"2024305999\"}", 10, "ACTIVE")
                )
        );

        AuthorizationScopeSet scopeSet = evaluator.evaluate(authorizationContext, "application.view.assigned");

        assertThat(scopeSet.isGranted()).isTrue();
        assertThat(scopeSet.hasScopes()).isTrue();
        assertThat(scopeSet.allowsAll()).isTrue();
        assertThat(scopeSet.allowsSelf()).isFalse();
        assertThat(scopeSet.getScopes()).hasSize(3);
        assertThat(scopeSet.getScopes().get(0).getScopeType()).isEqualTo("ALL");
        assertThat(scopeSet.getOrgUnitIds()).containsExactly(3001L);
        assertThat(scopeSet.getCategoryCodes()).containsExactly("INTELLECTUAL");
        assertThat(scopeSet.getItemCodes()).containsExactlyInAnyOrder("ACADEMIC_LECTURE");
        assertThat(scopeSet.findByScopeType("ORG_UNIT_ITEM")).hasSize(1);
        assertThat(scopeSet.findByScopeType("ITEM")).hasSize(1);
    }

    @Test
    void shouldReturnDeniedWhenPermissionIsNotGranted() {
        UserAuthorizationContext authorizationContext = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.self"),
                List.of(
                        new IamScopeRule(1L, "application.view.self", "SELF", null, null, null, "{\"userNo\":\"2024305999\"}", 10, "ACTIVE")
                )
        );

        AuthorizationScopeSet scopeSet = evaluator.evaluate(authorizationContext, "application.review");

        assertThat(scopeSet.isGranted()).isFalse();
        assertThat(scopeSet.hasScopes()).isFalse();
        assertThat(scopeSet.getScopes()).isEmpty();
    }

    @Test
    void shouldRejectInvalidInput() {
        assertThatThrownBy(() -> evaluator.evaluate(null, "application.view.assigned"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authorizationContext must not be null");

        assertThatThrownBy(() -> evaluator.evaluate(new UserAuthorizationContext(
                        1001L,
                        "2024305999",
                        "Test User",
                        "student",
                        Set.of("student"),
                        Set.of("application.view.self"),
                        List.of()
                ), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("permissionCode must not be blank");
    }
}
