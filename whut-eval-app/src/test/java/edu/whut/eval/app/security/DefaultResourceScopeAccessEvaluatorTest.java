package edu.whut.eval.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.model.ScoreResourceContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultResourceScopeAccessEvaluatorTest {

    private final DefaultResourceScopeAccessEvaluator evaluator = new DefaultResourceScopeAccessEvaluator(
            new DefaultAuthorizationScopeEvaluator(),
            new JsonScopeRuleExpressionInterpreter(new ObjectMapper())
    );

    @Test
    void shouldAllowApplicationWhenSelfScopeMatches() {
        UserAuthorizationContext context = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.self"),
                List.of(
                        new IamScopeRule(101L, "application.view.self", "SELF", null, null, null, null, 10, "ACTIVE")
                )
        );

        ScopeAccessDecision decision = evaluator.canAccessApplication(
                context,
                "application.view.self",
                new ApplicationResourceContext(9001L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE")
        );

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getMatchedScopeType()).isEqualTo("SELF");
    }

    @Test
    void shouldAllowApplicationWhenCustomExpressionMatches() {
        UserAuthorizationContext context = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student", "department-minister"),
                Set.of("application.review"),
                List.of(
                        new IamScopeRule(
                                201L,
                                "application.review",
                                "CUSTOM_EXPRESSION",
                                null,
                                null,
                                null,
                                "{\"allOf\":[{\"field\":\"orgUnitId\",\"operator\":\"EQ\",\"value\":3001},{\"field\":\"applicantUserId\",\"operator\":\"EQ\",\"valueFrom\":\"currentUser.userId\"}]}",
                                10,
                                "ACTIVE"
                        )
                )
        );

        ScopeAccessDecision decision = evaluator.canAccessApplication(
                context,
                "application.review",
                new ApplicationResourceContext(9002L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE")
        );

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getMatchedScopeType()).isEqualTo("CUSTOM_EXPRESSION");
    }

    @Test
    void shouldDenyApplicationWhenCustomExpressionStaticFieldsDoNotMatch() {
        UserAuthorizationContext context = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student", "department-minister"),
                Set.of("application.review"),
                List.of(
                        new IamScopeRule(
                                202L,
                                "application.review",
                                "CUSTOM_EXPRESSION",
                                3001L,
                                "INTELLECTUAL",
                                null,
                                "{\"allOf\":[{\"field\":\"applicantUserId\",\"operator\":\"EQ\",\"valueFrom\":\"currentUser.userId\"}]}",
                                10,
                                "ACTIVE"
                        )
                )
        );

        ScopeAccessDecision decision = evaluator.canAccessApplication(
                context,
                "application.review",
                new ApplicationResourceContext(9003L, 1001L, 4001L, "/1/4001/", "INTELLECTUAL", "ACADEMIC_LECTURE")
        );

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).isEqualTo("no-scope-matched");
    }

    @Test
    void shouldDenyScoreWhenNoScopeMatches() {
        UserAuthorizationContext context = new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student", "class-monitor"),
                Set.of("score.view.assigned"),
                List.of(
                        new IamScopeRule(301L, "score.view.assigned", "ORG_UNIT_ITEM", 3001L, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 10, "ACTIVE")
                )
        );

        ScopeAccessDecision decision = evaluator.canAccessScore(
                context,
                "score.view.assigned",
                new ScoreResourceContext(8001L, 2002L, 4001L, "/1/4001/", "INTELLECTUAL", "THESIS", "2025-2026")
        );

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).isEqualTo("no-scope-matched");
    }
}
