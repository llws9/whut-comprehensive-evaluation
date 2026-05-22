package edu.whut.eval.infra.security.context;

import edu.whut.eval.domain.iam.model.IamScopeRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CurrentUser {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String identity;
    private final String sessionId;
    private final Set<String> roles;
    private final Set<String> authorities;
    private final List<IamScopeRule> scopeRules;

    public CurrentUser(Long userId,
                       String userNo,
                       String userName,
                       String identity,
                       Set<String> roles,
                       Set<String> authorities,
                       List<IamScopeRule> scopeRules) {
        this(userId, userNo, userName, identity, null, roles, authorities, scopeRules);
    }

    public CurrentUser(Long userId,
                       String userNo,
                       String userName,
                       String identity,
                       String sessionId,
                       Set<String> roles,
                       Set<String> authorities,
                       List<IamScopeRule> scopeRules) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.identity = identity;
        this.sessionId = sessionId;
        this.roles = roles == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(roles));
        this.authorities = authorities == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(authorities));
        this.scopeRules = scopeRules == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(scopeRules));
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

    public String getSessionId() {
        return sessionId;
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
}
