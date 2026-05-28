package edu.whut.eval.domain.auth.model;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 成绩范围谓词。
 * 将抽象范围集合转换成成绩查询可消费的谓词对象。
 */
public class ScoreScopePredicate {

    private final String permissionCode;
    private final boolean granted;
    private final boolean allowAll;
    private final List<ScoreScopeClause> clauses;

    public ScoreScopePredicate(String permissionCode,
                               boolean granted,
                               boolean allowAll,
                               List<ScoreScopeClause> clauses) {
        this.permissionCode = permissionCode;
        this.granted = granted;
        this.allowAll = allowAll;
        this.clauses = clauses == null ? Collections.emptyList() : List.copyOf(clauses);
    }

    public static ScoreScopePredicate denied(String permissionCode) {
        return new ScoreScopePredicate(permissionCode, false, false, List.of());
    }

    public static ScoreScopePredicate allowAll(String permissionCode) {
        return new ScoreScopePredicate(permissionCode, true, true, List.of());
    }

    public static ScoreScopePredicate restricted(String permissionCode, List<ScoreScopeClause> clauses) {
        return new ScoreScopePredicate(permissionCode, true, false, clauses);
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public boolean isGranted() {
        return granted;
    }

    public boolean isAllowAll() {
        return allowAll;
    }

    public List<ScoreScopeClause> getClauses() {
        return clauses;
    }

    public boolean hasClauses() {
        return !clauses.isEmpty();
    }

    public boolean isEmptyResult() {
        return granted && !allowAll && clauses.isEmpty();
    }

    public boolean hasCustomExpressionClause() {
        return clauses.stream().anyMatch(clause -> normalize(clause.getScopeType()).equals("CUSTOM_EXPRESSION"));
    }

    public List<ScoreScopeClause> findClausesByScopeType(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            return List.of();
        }
        String normalized = normalize(scopeType);
        return clauses.stream()
                .filter(clause -> normalized.equals(normalize(clause.getScopeType())))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
