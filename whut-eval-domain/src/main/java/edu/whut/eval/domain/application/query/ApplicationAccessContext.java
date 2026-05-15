package edu.whut.eval.domain.application.query;

import edu.whut.eval.domain.iam.model.IamScopeRule;

import java.util.List;
import java.util.Set;

/**
 * 领域查询侧使用的访问上下文快照。
 * 它与 application 层的 UserAuthorizationContext 解耦，避免 domain 反向依赖 application 模块。
 */
public class ApplicationAccessContext {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String identity;
    private final Set<String> roles;
    private final Set<String> authorities;
    private final List<IamScopeRule> scopeRules;
    private final String permissionCode;

    public ApplicationAccessContext(Long userId,
                                    String userNo,
                                    String userName,
                                    String identity,
                                    Set<String> roles,
                                    Set<String> authorities,
                                    List<IamScopeRule> scopeRules,
                                    String permissionCode) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.identity = identity;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        this.scopeRules = scopeRules == null ? List.of() : List.copyOf(scopeRules);
        this.permissionCode = permissionCode;
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

    public String getPermissionCode() {
        return permissionCode;
    }
}
