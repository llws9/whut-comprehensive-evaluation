package edu.whut.eval.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.FinalRecordResourceContext;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.model.ScoreResourceContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultResourceScopeAccessEvaluatorTest {

    private final DefaultResourceScopeAccessEvaluator evaluator = new DefaultResourceScopeAccessEvaluator(
            new DefaultAuthorizationScopeEvaluator(),
            new JsonScopeRuleExpressionInterpreter(new ObjectMapper()),
            new InMemoryOrgUnitLookupRepository()
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

    @Test
    void shouldAllowFinalRecordOnlyForAllOrgUnitAndOrgSubtreeScopes() {
        UserAuthorizationContext admin = new UserAuthorizationContext(
                1010L,
                "T1010",
                "Counselor",
                "teacher",
                Set.of("counselor"),
                Set.of("score.view.assigned"),
                List.of(
                        new IamScopeRule(401L, "score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null, null, 90, "ACTIVE"),
                        new IamScopeRule(402L, "score.view.assigned", "ITEM", null, "INTELLECTUAL", "INTELLECTUAL_PAPER", null, 80, "ACTIVE"),
                        new IamScopeRule(403L, "score.view.assigned", "ORG_SUBTREE", 2002L, null, null, null, 70, "ACTIVE")
                )
        );

        ScopeAccessDecision decision = evaluator.canAccessFinalRecord(
                admin,
                "score.view.assigned",
                new FinalRecordResourceContext(41001L, 1001L, 2010L, "/WHUT/CS/CS2022/CS2201", "2025-2026")
        );

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getMatchedScopeType()).isEqualTo("ORG_SUBTREE");
    }

    @Test
    void shouldDenyFinalRecordWhenCodePathIsOutsideOrgSubtreeRoot() {
        UserAuthorizationContext admin = new UserAuthorizationContext(
                1010L,
                "T1010",
                "Counselor",
                "teacher",
                Set.of("counselor"),
                Set.of("score.view.assigned"),
                List.of(
                        new IamScopeRule(404L, "score.view.assigned", "ORG_SUBTREE", 2002L, null, null, null, 70, "ACTIVE")
                )
        );

        ScopeAccessDecision decision = evaluator.canAccessFinalRecord(
                admin,
                "score.view.assigned",
                new FinalRecordResourceContext(41002L, 1007L, 2012L, "/WHUT/ART/ART2022/ART2201", "2025-2026")
        );

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).isEqualTo("no-scope-matched");
    }

    @Test
    void shouldDenyFinalRecordForCategoryItemAndCustomExpressionOnlyScopes() {
        UserAuthorizationContext admin = new UserAuthorizationContext(
                1010L,
                "T1010",
                "Counselor",
                "teacher",
                Set.of("counselor"),
                Set.of("score.view.assigned"),
                List.of(
                        new IamScopeRule(411L, "score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null, null, 90, "ACTIVE"),
                        new IamScopeRule(412L, "score.view.assigned", "ITEM", null, "INTELLECTUAL", "INTELLECTUAL_PAPER", null, 80, "ACTIVE"),
                        new IamScopeRule(413L, "score.view.assigned", "CUSTOM_EXPRESSION", 2010L, null, null, "{\"field\":\"orgUnitId\"}", 70, "ACTIVE")
                )
        );

        ScopeAccessDecision decision = evaluator.canAccessFinalRecord(
                admin,
                "score.view.assigned",
                new FinalRecordResourceContext(41001L, 1001L, 2010L, "/2001/2002/2010/", "2025-2026")
        );

        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    void shouldExposeOnlyFinalRecordSafeFieldsInResourceContext() {
        FinalRecordResourceContext context = new FinalRecordResourceContext(41001L, 1001L, 2010L, "/2001/2002/2010/", "2025-2026");

        assertThat(context.getOwnerUserId()).isEqualTo(1001L);
        assertThat(context.getCategoryCode()).isNull();
        assertThat(context.getItemCode()).isNull();
        assertThat(context.getFieldValue("finalRecordId")).isEqualTo(41001L);
        assertThat(context.getFieldValue("studentUserId")).isEqualTo(1001L);
        assertThat(context.getFieldValue("ownerUserId")).isEqualTo(1001L);
        assertThat(context.getFieldValue("unknown")).isNull();
    }

    private static class InMemoryOrgUnitLookupRepository implements OrgUnitLookupRepository {

        private final Map<Long, OrgUnit> units = Map.of(
                2002L, new OrgUnit(2002L, 2001L, "COLLEGE", "CS", "计算机与人工智能学院", "/WHUT/CS", "ACTIVE")
        );

        @Override
        public Optional<OrgUnit> findById(Long id) {
            return Optional.ofNullable(units.get(id));
        }
    }
}
