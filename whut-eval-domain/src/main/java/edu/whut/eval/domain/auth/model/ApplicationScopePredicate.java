package edu.whut.eval.domain.auth.model;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 应用范围谓词。
 * 将抽象范围集合转换成申请查询可消费的谓词对象。
 */
public class ApplicationScopePredicate {

    private final String permissionCode;
    private final boolean granted;
    private final boolean allowAll;
    private final List<ApplicationScopeClause> clauses;

    public ApplicationScopePredicate(String permissionCode,
                                     boolean granted,
                                     boolean allowAll,
                                     List<ApplicationScopeClause> clauses) {
        this.permissionCode = permissionCode;
        this.granted = granted;
        this.allowAll = allowAll;
        this.clauses = clauses == null ? Collections.emptyList() : List.copyOf(clauses);
    }

    public static ApplicationScopePredicate denied(String permissionCode) {
        return new ApplicationScopePredicate(permissionCode, false, false, List.of());
    }

    public static ApplicationScopePredicate allowAll(String permissionCode) {
        return new ApplicationScopePredicate(permissionCode, true, true, List.of());
    }

    public static ApplicationScopePredicate restricted(String permissionCode, List<ApplicationScopeClause> clauses) {
        return new ApplicationScopePredicate(permissionCode, true, false, clauses);
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

    public List<ApplicationScopeClause> getClauses() {
        return clauses;
    }

    public boolean hasClauses() {
        return !clauses.isEmpty();
    }

    public boolean isEmptyResult() {
        return granted && !allowAll && clauses.isEmpty();
    }

    public boolean hasCustomExpressionClause() {
        return clauses.stream().anyMatch(clause -> isScopeType(clause, "CUSTOM_EXPRESSION"));
    }

    public List<ApplicationScopeClause> findClausesByScopeType(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            return List.of();
        }
        String normalized = normalize(scopeType);
        return clauses.stream()
                .filter(clause -> normalized.equals(normalize(clause.getScopeType())))
                .toList();
    }

    private boolean isScopeType(ApplicationScopeClause clause, String scopeType) {
        return normalize(clause.getScopeType()).equals(normalize(scopeType));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
