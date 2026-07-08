package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.FinalRecordResourceContext;
import edu.whut.eval.domain.auth.model.AuthorizationScope;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.model.ScopeResourceContext;
import edu.whut.eval.application.auth.model.ScoreResourceContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 默认单条资源范围判定器：按统一 scope 语义判断一条资源是否命中。
 */
@Service
public class DefaultResourceScopeAccessEvaluator implements ResourceScopeAccessEvaluator {

    private final AuthorizationScopeEvaluator authorizationScopeEvaluator;
    private final ScopeRuleExpressionInterpreter scopeRuleExpressionInterpreter;
    private final OrgUnitLookupRepository orgUnitLookupRepository;

    @Autowired
    public DefaultResourceScopeAccessEvaluator(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                               ScopeRuleExpressionInterpreter scopeRuleExpressionInterpreter,
                                               OrgUnitLookupRepository orgUnitLookupRepository) {
        this.authorizationScopeEvaluator = authorizationScopeEvaluator;
        this.scopeRuleExpressionInterpreter = scopeRuleExpressionInterpreter;
        this.orgUnitLookupRepository = orgUnitLookupRepository;
    }

    /**
     * 申请资源与成绩资源共用同一套匹配逻辑，只在资源上下文类型上区分。
     */
    @Override
    public ScopeAccessDecision canAccessApplication(UserAuthorizationContext authorizationContext,
                                                    String permissionCode,
                                                    ApplicationResourceContext resourceContext) {
        return evaluate(authorizationContext, permissionCode, resourceContext);
    }

    /**
     * 成绩资源与申请资源共用同一套匹配逻辑，只在资源上下文类型上区分。
     */
    @Override
    public ScopeAccessDecision canAccessScore(UserAuthorizationContext authorizationContext,
                                              String permissionCode,
                                              ScoreResourceContext resourceContext) {
        return evaluate(authorizationContext, permissionCode, resourceContext);
    }

    /**
     * 最终成绩聚合按整条记录授权，只允许 ALL / ORG_UNIT / ORG_SUBTREE 命中。
     */
    @Override
    public ScopeAccessDecision canAccessFinalRecord(UserAuthorizationContext authorizationContext,
                                                    String permissionCode,
                                                    FinalRecordResourceContext resourceContext) {
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(authorizationContext, permissionCode);
        if (!scopeSet.isGranted()) {
            return ScopeAccessDecision.deny("permission-not-granted");
        }
        if (scopeSet.allowsAll()) {
            return ScopeAccessDecision.allow("ALL", "matched allow-all scope");
        }
        for (AuthorizationScope scope : scopeSet.getScopes()) {
            if (matchesFinalRecordScope(resourceContext, scope)) {
                return ScopeAccessDecision.allow(scope.getScopeType(), "matched scope rule");
            }
        }
        return ScopeAccessDecision.deny("no-scope-matched");
    }

    /**
     * 先评估权限下的范围集合，再逐条匹配资源；一旦命中立即返回 allow。
     */
    private ScopeAccessDecision evaluate(UserAuthorizationContext authorizationContext,
                                         String permissionCode,
                                         ScopeResourceContext resourceContext) {
        AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(authorizationContext, permissionCode);
        if (!scopeSet.isGranted()) {
            return ScopeAccessDecision.deny("permission-not-granted");
        }
        if (scopeSet.allowsAll()) {
            return ScopeAccessDecision.allow("ALL", "matched allow-all scope");
        }
        for (AuthorizationScope scope : scopeSet.getScopes()) {
            if (matchesScope(authorizationContext, resourceContext, scope)) {
                return ScopeAccessDecision.allow(scope.getScopeType(), "matched scope rule");
            }
        }
        return ScopeAccessDecision.deny("no-scope-matched");
    }

    /**
     * 把不同 scopeType 的命中语义统一收口到一个入口，避免业务侧散落范围判断分支。
     */
    private boolean matchesScope(UserAuthorizationContext authorizationContext,
                                 ScopeResourceContext resourceContext,
                                 AuthorizationScope scope) {
        String scopeType = normalize(scope.getScopeType());
        if ("SELF".equals(scopeType)) {
            return valuesEqual(resourceContext.getOwnerUserId(), authorizationContext.getUserId());
        }
        if ("ORG_UNIT".equals(scopeType)) {
            return matchesOrgUnit(resourceContext, scope) && matchesCategoryAndItem(resourceContext, scope);
        }
        if ("ORG_SUBTREE".equals(scopeType)) {
            return matchesOrgSubtree(resourceContext, scope) && matchesCategoryAndItem(resourceContext, scope);
        }
        if ("CATEGORY".equals(scopeType)) {
            return valuesEqual(resourceContext.getCategoryCode(), scope.getCategoryCode());
        }
        if ("ITEM".equals(scopeType)) {
            return matchesCategoryAndItem(resourceContext, scope);
        }
        if ("ORG_UNIT_ITEM".equals(scopeType)) {
            return matchesOrgUnit(resourceContext, scope) && matchesCategoryAndItem(resourceContext, scope);
        }
        if ("CUSTOM_EXPRESSION".equals(scopeType)) {
            return matchesCustomExpressionStaticFields(resourceContext, scope) && scopeRuleExpressionInterpreter.matches(
                    authorizationContext,
                    scope.getExpressionJson(),
                    resourceContext
            );
        }
        return false;
    }

    private boolean matchesFinalRecordScope(FinalRecordResourceContext resourceContext, AuthorizationScope scope) {
        String scopeType = normalize(scope.getScopeType());
        if ("ALL".equals(scopeType)) {
            return true;
        }
        if ("ORG_UNIT".equals(scopeType)) {
            return matchesOrgUnit(resourceContext, scope);
        }
        if ("ORG_SUBTREE".equals(scopeType)) {
            return matchesOrgSubtree(resourceContext, scope);
        }
        return false;
    }

    /**
     * ORG_UNIT 语义要求资源组织单元与规则组织单元完全一致。
     */
    private boolean matchesOrgUnit(ScopeResourceContext resourceContext, AuthorizationScope scope) {
        return valuesEqual(resourceContext.getOrgUnitId(), scope.getOrgUnitId());
    }

    /**
     * ORG_SUBTREE 通过 A 组组织表中的真实 path 判断前缀关系，path 存储的是组织编码链路。
     */
    private boolean matchesOrgSubtree(ScopeResourceContext resourceContext, AuthorizationScope scope) {
        if (scope.getOrgUnitId() == null) {
            return false;
        }
        String orgPath = resourceContext.getOrgPath();
        if (orgPath == null || orgPath.isBlank()) {
            return false;
        }
        return orgUnitLookupRepository.findById(scope.getOrgUnitId())
                .map(orgUnit -> isSameOrDescendantPath(orgPath, orgUnit.path()))
                .orElse(false);
    }

    private boolean isSameOrDescendantPath(String resourcePath, String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return false;
        }
        String normalizedResourcePath = normalizePath(resourcePath);
        String normalizedRootPath = normalizePath(rootPath);
        return normalizedResourcePath.equals(normalizedRootPath)
                || normalizedResourcePath.startsWith(normalizedRootPath + "/");
    }

    private String normalizePath(String path) {
        String normalized = path.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 对组合范围统一处理 category/item 的可选约束，避免多个 scopeType 重复拼条件。
     */
    private boolean matchesCategoryAndItem(ScopeResourceContext resourceContext, AuthorizationScope scope) {
        boolean categoryMatched = scope.getCategoryCode() == null || scope.getCategoryCode().isBlank()
                || valuesEqual(resourceContext.getCategoryCode(), scope.getCategoryCode());
        boolean itemMatched = scope.getItemCode() == null || scope.getItemCode().isBlank()
                || valuesEqual(resourceContext.getItemCode(), scope.getItemCode());
        return categoryMatched && itemMatched;
    }

    /**
     * CUSTOM_EXPRESSION 在当前实现中允许同时携带组织/类别/项目等静态约束，
     * 单条资源校验必须与 SQL translator 保持同样的收窄语义。
     */
    private boolean matchesCustomExpressionStaticFields(ScopeResourceContext resourceContext, AuthorizationScope scope) {
        boolean orgMatched = scope.getOrgUnitId() == null || matchesOrgUnit(resourceContext, scope);
        return orgMatched && matchesCategoryAndItem(resourceContext, scope);
    }

    /**
     * 范围命中比较统一按字符串无大小写差异处理，兼容数字和编码类字段。
     */
    private boolean valuesEqual(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        return String.valueOf(left).trim().equalsIgnoreCase(String.valueOf(right).trim());
    }

    /**
     * 统一标准化 scopeType 字段，避免因大小写差异导致规则失效。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
