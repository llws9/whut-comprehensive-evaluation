package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.ApplicationScopeClause;
import edu.whut.eval.application.auth.model.ApplicationScopePredicate;
import edu.whut.eval.application.auth.model.AuthorizationScope;
import edu.whut.eval.application.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 默认申请范围谓词构建器：把抽象范围集合翻译成申请查询子句。
 */
@Service
public class DefaultScopePredicateBuilder implements ScopePredicateBuilder {

    /**
     * 输出的是带 OR 语义的 clause 集合，而不是已经拼好的 SQL。
     */
    @Override
    public ApplicationScopePredicate buildForApplication(UserAuthorizationContext authorizationContext,
                                                         AuthorizationScopeSet scopeSet) {
        if (authorizationContext == null) {
            throw new IllegalArgumentException("authorizationContext must not be null");
        }
        if (scopeSet == null) {
            throw new IllegalArgumentException("scopeSet must not be null");
        }
        if (!scopeSet.isGranted()) {
            return ApplicationScopePredicate.denied(scopeSet.getPermissionCode());
        }
        if (scopeSet.allowsAll()) {
            return ApplicationScopePredicate.allowAll(scopeSet.getPermissionCode());
        }

        List<ApplicationScopeClause> clauses = new ArrayList<>();
        for (AuthorizationScope scope : scopeSet.getScopes()) {
            ApplicationScopeClause clause = toApplicationClause(authorizationContext, scope);
            if (clause != null) {
                clauses.add(clause);
            }
        }
        return ApplicationScopePredicate.restricted(scopeSet.getPermissionCode(), clauses);
    }

    /**
     * 按 scopeType 把统一范围模型映射成申请查询上下文可识别的字段组合。
     */
    private ApplicationScopeClause toApplicationClause(UserAuthorizationContext authorizationContext,
                                                       AuthorizationScope scope) {
        String normalizedScopeType = normalize(scope.getScopeType());
        switch (normalizedScopeType) {
            case "SELF":
                return new ApplicationScopeClause(
                        normalizedScopeType,
                        authorizationContext.getUserId(),
                        null,
                        null,
                        null,
                        null,
                        scope.getExpressionJson()
                );
            case "ORG_UNIT":
                return new ApplicationScopeClause(
                        normalizedScopeType,
                        null,
                        scope.getOrgUnitId(),
                        null,
                        scope.getCategoryCode(),
                        scope.getItemCode(),
                        scope.getExpressionJson()
                );
            case "ORG_SUBTREE":
                return new ApplicationScopeClause(
                        normalizedScopeType,
                        null,
                        null,
                        scope.getOrgUnitId(),
                        scope.getCategoryCode(),
                        scope.getItemCode(),
                        scope.getExpressionJson()
                );
            case "CATEGORY":
                return new ApplicationScopeClause(
                        normalizedScopeType,
                        null,
                        null,
                        null,
                        scope.getCategoryCode(),
                        null,
                        scope.getExpressionJson()
                );
            case "ITEM":
                return new ApplicationScopeClause(
                        normalizedScopeType,
                        null,
                        null,
                        null,
                        scope.getCategoryCode(),
                        scope.getItemCode(),
                        scope.getExpressionJson()
                );
            case "ORG_UNIT_ITEM":
                return new ApplicationScopeClause(
                        normalizedScopeType,
                        null,
                        scope.getOrgUnitId(),
                        null,
                        scope.getCategoryCode(),
                        scope.getItemCode(),
                        scope.getExpressionJson()
                );
            case "CUSTOM_EXPRESSION":
                return new ApplicationScopeClause(
                        normalizedScopeType,
                        null,
                        scope.getOrgUnitId(),
                        null,
                        scope.getCategoryCode(),
                        scope.getItemCode(),
                        scope.getExpressionJson()
                );
            case "ALL":
                return null;
            default:
                return null;
        }
    }

    /**
     * 统一标准化 scopeType，避免不同数据源大小写不一致。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
