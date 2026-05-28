package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.auth.model.AuthorizationScope;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.ScoreScopeClause;
import edu.whut.eval.domain.auth.model.ScoreScopePredicate;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 默认成绩范围谓词构建器：把抽象范围集合翻译成成绩查询子句。
 */
@Service
public class DefaultScoreScopePredicateBuilder implements ScoreScopePredicateBuilder {

    /**
     * 输出的是成绩查询可消费的 clause 集合，后续再由 SQL translator 负责参数化拼接。
     */
    @Override
    public ScoreScopePredicate build(UserAuthorizationContext authorizationContext, AuthorizationScopeSet scopeSet) {
        if (authorizationContext == null) {
            throw new IllegalArgumentException("authorizationContext must not be null");
        }
        if (scopeSet == null) {
            throw new IllegalArgumentException("scopeSet must not be null");
        }
        if (!scopeSet.isGranted()) {
            return ScoreScopePredicate.denied(scopeSet.getPermissionCode());
        }
        if (scopeSet.allowsAll()) {
            return ScoreScopePredicate.allowAll(scopeSet.getPermissionCode());
        }

        List<ScoreScopeClause> clauses = new ArrayList<>();
        for (AuthorizationScope scope : scopeSet.getScopes()) {
            ScoreScopeClause clause = toScoreClause(authorizationContext, scope);
            if (clause != null) {
                clauses.add(clause);
            }
        }
        return ScoreScopePredicate.restricted(scopeSet.getPermissionCode(), clauses);
    }

    /**
     * 把统一范围模型翻译成成绩场景下的字段约束组合。
     */
    private ScoreScopeClause toScoreClause(UserAuthorizationContext authorizationContext, AuthorizationScope scope) {
        String scopeType = normalize(scope.getScopeType());
        if ("SELF".equals(scopeType)) {
            return new ScoreScopeClause(scopeType, authorizationContext.getUserId(), null, null, null, null, null, scope.getExpressionJson());
        }
        if ("ORG_UNIT".equals(scopeType)) {
            return new ScoreScopeClause(scopeType, null, scope.getOrgUnitId(), null, scope.getCategoryCode(), scope.getItemCode(), null, scope.getExpressionJson());
        }
        if ("ORG_SUBTREE".equals(scopeType)) {
            return new ScoreScopeClause(scopeType, null, null, scope.getOrgUnitId(), scope.getCategoryCode(), scope.getItemCode(), null, scope.getExpressionJson());
        }
        if ("CATEGORY".equals(scopeType)) {
            return new ScoreScopeClause(scopeType, null, null, null, scope.getCategoryCode(), null, null, scope.getExpressionJson());
        }
        if ("ITEM".equals(scopeType)) {
            return new ScoreScopeClause(scopeType, null, null, null, scope.getCategoryCode(), scope.getItemCode(), null, scope.getExpressionJson());
        }
        if ("ORG_UNIT_ITEM".equals(scopeType)) {
            return new ScoreScopeClause(scopeType, null, scope.getOrgUnitId(), null, scope.getCategoryCode(), scope.getItemCode(), null, scope.getExpressionJson());
        }
        if ("CUSTOM_EXPRESSION".equals(scopeType)) {
            return new ScoreScopeClause(scopeType, null, scope.getOrgUnitId(), null, scope.getCategoryCode(), scope.getItemCode(), null, scope.getExpressionJson());
        }
        if ("ALL".equals(scopeType)) {
            return null;
        }
        return null;
    }

    /**
     * 统一标准化 scopeType，避免不同数据源大小写不一致。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
