package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.ApplicationScopePredicate;
import edu.whut.eval.application.auth.model.AuthorizationScope;
import edu.whut.eval.application.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.DefaultScopePredicateBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultScopePredicateBuilderTest {

    private final DefaultScopePredicateBuilder builder = new DefaultScopePredicateBuilder();

    @Test
    void shouldBuildDeniedPredicateWhenPermissionIsNotGranted() {
        ApplicationScopePredicate predicate = builder.buildForApplication(
                createAuthorizationContext(),
                AuthorizationScopeSet.denied("application.view.assigned")
        );

        assertThat(predicate.isGranted()).isFalse();
        assertThat(predicate.isAllowAll()).isFalse();
        assertThat(predicate.getClauses()).isEmpty();
    }

    @Test
    void shouldBuildAllowAllPredicateWhenScopeSetContainsAll() {
        ApplicationScopePredicate predicate = builder.buildForApplication(
                createAuthorizationContext(),
                AuthorizationScopeSet.granted("application.view.assigned", List.of(
                        new AuthorizationScope("application.view.assigned", "ALL", null, null, null, null, 1),
                        new AuthorizationScope("application.view.assigned", "ITEM", null, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 10)
                ))
        );

        assertThat(predicate.isGranted()).isTrue();
        assertThat(predicate.isAllowAll()).isTrue();
        assertThat(predicate.getClauses()).isEmpty();
    }

    @Test
    void shouldBuildApplicationClausesFromScopeSet() {
        ApplicationScopePredicate predicate = builder.buildForApplication(
                createAuthorizationContext(),
                AuthorizationScopeSet.granted("application.view.assigned", List.of(
                        new AuthorizationScope("application.view.assigned", "SELF", null, null, null, "{\"userNo\":\"2024305999\"}", 5),
                        new AuthorizationScope("application.view.assigned", "ORG_UNIT", 3001L, null, null, null, 10),
                        new AuthorizationScope("application.view.assigned", "ITEM", null, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 20),
                        new AuthorizationScope("application.view.assigned", "ORG_UNIT_ITEM", 4001L, "MORAL", "VOLUNTEER_SERVICE", null, 30),
                        new AuthorizationScope("application.view.assigned", "ORG_SUBTREE", 5001L, null, null, null, 40),
                        new AuthorizationScope("application.view.assigned", "CUSTOM_EXPRESSION", 6001L, "PHYSICAL", "SPORTS_MEET", "{\"mode\":\"manual\"}", 50)
                ))
        );

        assertThat(predicate.isGranted()).isTrue();
        assertThat(predicate.isAllowAll()).isFalse();
        assertThat(predicate.hasClauses()).isTrue();
        assertThat(predicate.isEmptyResult()).isFalse();
        assertThat(predicate.findClausesByScopeType("SELF")).hasSize(1);
        assertThat(predicate.findClausesByScopeType("SELF").get(0).getApplicantUserId()).isEqualTo(1001L);
        assertThat(predicate.findClausesByScopeType("ORG_UNIT")).hasSize(1);
        assertThat(predicate.findClausesByScopeType("ORG_UNIT").get(0).getOrgUnitId()).isEqualTo(3001L);
        assertThat(predicate.findClausesByScopeType("ITEM")).hasSize(1);
        assertThat(predicate.findClausesByScopeType("ITEM").get(0).getCategoryCode()).isEqualTo("INTELLECTUAL");
        assertThat(predicate.findClausesByScopeType("ITEM").get(0).getItemCode()).isEqualTo("ACADEMIC_LECTURE");
        assertThat(predicate.findClausesByScopeType("ORG_UNIT_ITEM")).hasSize(1);
        assertThat(predicate.findClausesByScopeType("ORG_UNIT_ITEM").get(0).getOrgUnitId()).isEqualTo(4001L);
        assertThat(predicate.findClausesByScopeType("ORG_SUBTREE")).hasSize(1);
        assertThat(predicate.findClausesByScopeType("ORG_SUBTREE").get(0).getOrgSubtreeRootId()).isEqualTo(5001L);
        assertThat(predicate.hasCustomExpressionClause()).isTrue();
    }

    @Test
    void shouldRejectInvalidInput() {
        assertThatThrownBy(() -> builder.buildForApplication(null, AuthorizationScopeSet.denied("application.view.assigned")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authorizationContext must not be null");

        assertThatThrownBy(() -> builder.buildForApplication(createAuthorizationContext(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scopeSet must not be null");
    }

    private UserAuthorizationContext createAuthorizationContext() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.assigned"),
                List.of()
        );
    }
}
