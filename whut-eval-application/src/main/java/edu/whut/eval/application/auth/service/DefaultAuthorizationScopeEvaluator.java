package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.auth.model.AuthorizationScope;
import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 默认范围评估器：把用户携带的 scope rules 过滤、排序并归一化成统一范围集合。
 */
@Service
public class DefaultAuthorizationScopeEvaluator implements AuthorizationScopeEvaluator {

    /**
     * 只有用户具备目标权限时才继续评估范围；否则直接返回 denied，避免范围规则越权生效。
     */
    @Override
    public AuthorizationScopeSet evaluate(UserAuthorizationContext authorizationContext, String permissionCode) {
        if (authorizationContext == null) {
            throw new IllegalArgumentException("authorizationContext must not be null");
        }
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
        String normalizedPermissionCode = permissionCode.trim();
        if (!authorizationContext.hasAuthority(normalizedPermissionCode)) {
            return AuthorizationScopeSet.denied(normalizedPermissionCode);
        }

        List<AuthorizationScope> scopes = authorizationContext.findScopeRulesByPermissionCode(normalizedPermissionCode).stream()
                .filter(this::isActive)
                .map(this::toAuthorizationScope)
                .sorted(Comparator
                        .comparing(AuthorizationScope::getPriority, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AuthorizationScope::getScopeType, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AuthorizationScope::getOrgUnitId, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(AuthorizationScope::getCategoryCode, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AuthorizationScope::getItemCode, Comparator.nullsLast(String::compareTo)))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
        return AuthorizationScopeSet.granted(normalizedPermissionCode, scopes);
    }

    /**
     * 仅允许 ACTIVE 规则参与后续范围计算。
     */
    private boolean isActive(IamScopeRule rule) {
        return rule != null && normalize(rule.status()).equals("ACTIVE");
    }

    /**
     * 把持久化规则转成授权层统一模型，隔离底层对象差异。
     */
    private AuthorizationScope toAuthorizationScope(IamScopeRule rule) {
        return new AuthorizationScope(
                rule.permissionCode(),
                normalize(rule.scopeType()),
                rule.orgUnitId(),
                rule.categoryCode(),
                rule.itemCode(),
                rule.expressionJson(),
                rule.priority()
        );
    }

    /**
     * 用统一大小写标准处理权限码、范围类型等枚举语义字段。
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
