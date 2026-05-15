package edu.whut.eval.application.auth.model;

import edu.whut.eval.domain.iam.model.IamScopeRule;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UserAuthorizationContext {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String identity;
    private final Set<String> roles;
    private final Set<String> authorities;
    private final List<IamScopeRule> scopeRules;

    public UserAuthorizationContext(Long userId,
                                    String userNo,
                                    String userName,
                                    String identity,
                                    Set<String> roles,
                                    Set<String> authorities,
                                    List<IamScopeRule> scopeRules) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.identity = identity;
        this.roles = roles == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        this.authorities = authorities == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
        this.scopeRules = scopeRules == null
                ? Collections.emptyList()
                : List.copyOf(scopeRules);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserNo() {
        return userNo;
    }

    public String getUserName() {
        return userName;
    }

    public String getIdentity() {
        return identity;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getAuthorities() {
        return authorities;
    }

    public List<IamScopeRule> getScopeRules() {
        return scopeRules;
    }

    public boolean hasRole(String roleCode) {
        return roleCode != null && roles.contains(roleCode);
    }

    public boolean hasAuthority(String authorityCode) {
        return authorityCode != null && authorities.contains(authorityCode);
    }

    public List<IamScopeRule> findScopeRulesByPermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return List.of();
        }
        return scopeRules.stream()
                .filter(rule -> permissionCode.equals(rule.permissionCode()))
                .toList();
    }
}
