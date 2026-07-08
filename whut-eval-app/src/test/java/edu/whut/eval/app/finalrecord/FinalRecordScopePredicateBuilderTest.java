package edu.whut.eval.app.finalrecord;

import edu.whut.eval.domain.auth.model.ApplicationScopeClause;
import edu.whut.eval.domain.auth.model.ApplicationScopePredicate;
import edu.whut.eval.domain.auth.model.AuthorizationScope;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.service.FinalRecordScopePredicateBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FinalRecordScopePredicateBuilderTest {

    @Test
    void shouldBuildAllowAllPredicateForAllScope() {
        FinalRecordScopePredicateBuilder builder = new FinalRecordScopePredicateBuilder();
        AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("score.view.assigned", List.of(
                new AuthorizationScope("score.view.assigned", "ALL", null, null, null, null, 100)
        ));

        ApplicationScopePredicate predicate = builder.buildForFinalRecord(context(), scopeSet);

        assertThat(predicate.isGranted()).isTrue();
        assertThat(predicate.isEmptyResult()).isFalse();
        assertThat(predicate.getClauses()).isEmpty();
    }

    @Test
    void shouldBuildPredicateFromOnlyWholeRecordScopes() {
        FinalRecordScopePredicateBuilder builder = new FinalRecordScopePredicateBuilder();
        AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("score.view.assigned", List.of(
                new AuthorizationScope("score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null, null, 90),
                new AuthorizationScope("score.view.assigned", "ORG_UNIT", 2010L, null, null, null, 80),
                new AuthorizationScope("score.view.assigned", "ORG_SUBTREE", 2002L, null, null, "{\"scoreRole\":\"counselor\"}", 70)
        ));

        ApplicationScopePredicate predicate = builder.buildForFinalRecord(context(), scopeSet);

        assertThat(predicate.isGranted()).isTrue();
        assertThat(predicate.isEmptyResult()).isFalse();
        assertThat(predicate.getClauses()).hasSize(2);
        assertThat(predicate.getClauses()).extracting(ApplicationScopeClause::getOrgUnitId).contains(2010L);
        assertThat(predicate.getClauses()).extracting(ApplicationScopeClause::getOrgSubtreeRootId).contains(2002L);
    }

    @Test
    void shouldReturnEmptyResultPredicateForUnsupportedScopesOnly() {
        FinalRecordScopePredicateBuilder builder = new FinalRecordScopePredicateBuilder();
        AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("score.view.assigned", List.of(
                new AuthorizationScope("score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null, null, 90),
                new AuthorizationScope("score.view.assigned", "ITEM", null, "INTELLECTUAL", "INTELLECTUAL_PAPER", null, 80)
        ));

        ApplicationScopePredicate predicate = builder.buildForFinalRecord(context(), scopeSet);

        assertThat(predicate.isGranted()).isTrue();
        assertThat(predicate.isEmptyResult()).isTrue();
    }

    private UserAuthorizationContext context() {
        return new UserAuthorizationContext(
                1010L,
                "T1010",
                "Counselor",
                "teacher",
                Set.of("counselor"),
                Set.of("score.view.assigned"),
                List.of()
        );
    }
}
