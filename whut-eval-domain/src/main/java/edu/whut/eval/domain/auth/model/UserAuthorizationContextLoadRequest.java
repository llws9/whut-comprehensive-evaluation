package edu.whut.eval.domain.auth.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户授权上下文加载请求。
 * 用于加载用户授权上下文的请求数据。
 */
public class UserAuthorizationContextLoadRequest {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String identity;
    private final Set<String> roles;

    public UserAuthorizationContextLoadRequest(Long userId,
                                               String userNo,
                                               String userName,
                                               String identity,
                                               Set<String> roles) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.identity = identity;
        this.roles = roles == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
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
}
