package edu.whut.eval.infra.security.sql;

import edu.whut.eval.domain.auth.model.ScoreScopeClause;
import edu.whut.eval.domain.auth.model.ScoreScopePredicate;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.ScopeRuleExpressionInterpreter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScoreScopeSqlTranslator extends AbstractScopeSqlTranslator {

    private static final Map<String, String> CUSTOM_FIELD_MAPPING = Map.of(
            "scoreId", "score_id",
            "studentUserId", "student_user_id",
            "ownerUserId", "student_user_id",
            "orgUnitId", "org_unit_id",
            "orgPath", "org_path",
            "categoryCode", "category_code",
            "itemCode", "item_code",
            "academicYear", "academic_year"
    );

    public ScoreScopeSqlTranslator(ScopeRuleExpressionInterpreter scopeRuleExpressionInterpreter) {
        super(scopeRuleExpressionInterpreter);
    }

    public SqlPredicateFragment translate(UserAuthorizationContext authorizationContext,
                                          ScoreScopePredicate predicate) {
        if (authorizationContext == null) {
            throw new IllegalArgumentException("authorizationContext must not be null");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("predicate must not be null");
        }
        if (!predicate.isGranted() || predicate.isEmptyResult()) {
            return SqlPredicateFragment.denyAll();
        }
        if (predicate.isAllowAll()) {
            return SqlPredicateFragment.allowAll();
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();
        for (ScoreScopeClause clause : predicate.getClauses()) {
            clauses.add(translateClause(authorizationContext, clause, parameters));
        }
        if (clauses.isEmpty()) {
            return SqlPredicateFragment.denyAll();
        }
        return new SqlPredicateFragment("(" + String.join(" OR ", clauses) + ")", parameters);
    }

    private String translateClause(UserAuthorizationContext authorizationContext,
                                   ScoreScopeClause clause,
                                   Map<String, Object> parameters) {
        List<String> parts = new ArrayList<>();
        if (clause.getStudentUserId() != null) {
            parts.add("student_user_id = " + addParameter(parameters, clause.getStudentUserId()));
        }
        if (clause.getOrgUnitId() != null) {
            parts.add("org_unit_id = " + addParameter(parameters, clause.getOrgUnitId()));
        }
        if (clause.getOrgSubtreeRootId() != null) {
            parts.add(translateOrgSubtreePathPredicate("org_path", clause.getOrgSubtreeRootId(), parameters));
        }
        if (clause.getCategoryCode() != null && !clause.getCategoryCode().isBlank()) {
            parts.add("category_code = " + addParameter(parameters, clause.getCategoryCode()));
        }
        if (clause.getItemCode() != null && !clause.getItemCode().isBlank()) {
            parts.add("item_code = " + addParameter(parameters, clause.getItemCode()));
        }
        if (clause.getAcademicYear() != null && !clause.getAcademicYear().isBlank()) {
            parts.add("academic_year = " + addParameter(parameters, clause.getAcademicYear()));
        }
        if (clause.getExpressionJson() != null && !clause.getExpressionJson().isBlank()) {
            parts.add(translateCustomExpression(
                    authorizationContext,
                    clause.getExpressionJson(),
                    CUSTOM_FIELD_MAPPING,
                    parameters
            ));
        }
        if (parts.isEmpty()) {
            return "1 = 0";
        }
        return "(" + String.join(" AND ", parts) + ")";
    }
}
