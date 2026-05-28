package edu.whut.eval.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.domain.auth.model.ApplicationScopePredicate;
import edu.whut.eval.domain.auth.model.AuthorizationScope;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.ScoreScopePredicate;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.DefaultScoreScopePredicateBuilder;
import edu.whut.eval.application.auth.service.DefaultScopePredicateBuilder;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.ScoreScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeSqlTranslatorTest {

    private final DefaultScopePredicateBuilder applicationBuilder = new DefaultScopePredicateBuilder();
    private final DefaultScoreScopePredicateBuilder scoreBuilder = new DefaultScoreScopePredicateBuilder();
    private final JsonScopeRuleExpressionInterpreter interpreter = new JsonScopeRuleExpressionInterpreter(new ObjectMapper());
    private final ApplicationScopeSqlTranslator applicationTranslator = new ApplicationScopeSqlTranslator(interpreter);
    private final ScoreScopeSqlTranslator scoreTranslator = new ScoreScopeSqlTranslator(interpreter);

    @Test
    void shouldTranslateApplicationPredicateToParameterizedSql() {
        UserAuthorizationContext context = createContext();
        AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("application.view.assigned", List.of(
                new AuthorizationScope("application.view.assigned", "SELF", null, null, null, null, 5),
                new AuthorizationScope("application.view.assigned", "ORG_UNIT_ITEM", 3001L, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 10),
                new AuthorizationScope(
                        "application.view.assigned",
                        "CUSTOM_EXPRESSION",
                        null,
                        null,
                        null,
                        "{\"allOf\":[{\"field\":\"orgUnitId\",\"operator\":\"EQ\",\"value\":3002},{\"field\":\"applicantUserId\",\"operator\":\"EQ\",\"valueFrom\":\"currentUser.userId\"}]}",
                        20
                )
        ));

        ApplicationScopePredicate predicate = applicationBuilder.buildForApplication(context, scopeSet);
        SqlPredicateFragment fragment = applicationTranslator.translate(context, predicate);

        assertThat(fragment.isAllowAll()).isFalse();
        assertThat(fragment.getExpression()).contains("applicant_user_id = #{parameters.p1}");
        assertThat(fragment.getExpression()).contains("org_unit_id = #{parameters.p2}");
        assertThat(fragment.getExpression()).contains("category_code = #{parameters.p3}");
        assertThat(fragment.getExpression()).contains("item_code = #{parameters.p4}");
        assertThat(fragment.getExpression()).contains("org_unit_id = #{parameters.p5}");
        assertThat(fragment.getParameters()).containsEntry("p1", 1001L);
        assertThat(fragment.getParameters()).containsEntry("p2", 3001L);
        assertThat(fragment.getParameters()).containsEntry("p3", "INTELLECTUAL");
        assertThat(fragment.getParameters()).containsEntry("p4", "ACADEMIC_LECTURE");
        assertThat(fragment.getParameters()).containsEntry("p5", 3002L);
        assertThat(fragment.getParameters()).containsEntry("p6", 1001L);
    }

    @Test
    void shouldTranslateScorePredicateToParameterizedSql() {
        UserAuthorizationContext context = createContext();
        AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("score.view.assigned", List.of(
                new AuthorizationScope("score.view.assigned", "SELF", null, null, null, null, 5),
                new AuthorizationScope(
                        "score.view.assigned",
                        "CUSTOM_EXPRESSION",
                        null,
                        null,
                        null,
                        "{\"allOf\":[{\"field\":\"academicYear\",\"operator\":\"EQ\",\"value\":\"2025-2026\"},{\"field\":\"studentUserId\",\"operator\":\"EQ\",\"valueFrom\":\"currentUser.userId\"}]}",
                        10
                )
        ));

        ScoreScopePredicate predicate = scoreBuilder.build(context, scopeSet);
        SqlPredicateFragment fragment = scoreTranslator.translate(context, predicate);

        assertThat(fragment.getExpression()).contains("student_user_id = #{parameters.p1}");
        assertThat(fragment.getExpression()).contains("academic_year = #{parameters.p2}");
        assertThat(fragment.getExpression()).contains("student_user_id = #{parameters.p3}");
        assertThat(fragment.getParameters()).containsEntry("p1", 1001L);
        assertThat(fragment.getParameters()).containsEntry("p2", "2025-2026");
        assertThat(fragment.getParameters()).containsEntry("p3", 1001L);
    }

    @Test
    void shouldReturnAllowAllFragment() {
        SqlPredicateFragment fragment = applicationTranslator.translate(
                createContext(),
                ApplicationScopePredicate.allowAll("application.view.assigned")
        );

        assertThat(fragment.isAllowAll()).isTrue();
        assertThat(fragment.getParameters()).isEmpty();
    }

    @Test
    void shouldIgnoreUnsupportedScopeTypeWhenBuildingApplicationPredicate() {
        UserAuthorizationContext context = createContext();
        AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("application.view.assigned", List.of(
                new AuthorizationScope("application.view.assigned", "UNKNOWN_SCOPE", 3001L, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 5)
        ));

        ApplicationScopePredicate predicate = applicationBuilder.buildForApplication(context, scopeSet);
        SqlPredicateFragment fragment = applicationTranslator.translate(context, predicate);

        assertThat(predicate.isEmptyResult()).isTrue();
        assertThat(fragment.getExpression()).isEqualTo("1 = 0");
        assertThat(fragment.getParameters()).isEmpty();
    }

    @Test
    void shouldIgnoreUnsupportedScopeTypeWhenBuildingScorePredicate() {
        UserAuthorizationContext context = createContext();
        AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("score.view.assigned", List.of(
                new AuthorizationScope("score.view.assigned", "UNKNOWN_SCOPE", 3001L, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 5)
        ));

        ScoreScopePredicate predicate = scoreBuilder.build(context, scopeSet);
        SqlPredicateFragment fragment = scoreTranslator.translate(context, predicate);

        assertThat(predicate.isEmptyResult()).isTrue();
        assertThat(fragment.getExpression()).isEqualTo("1 = 0");
        assertThat(fragment.getParameters()).isEmpty();
    }

    private UserAuthorizationContext createContext() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.assigned", "score.view.assigned"),
                List.of()
        );
    }
}
