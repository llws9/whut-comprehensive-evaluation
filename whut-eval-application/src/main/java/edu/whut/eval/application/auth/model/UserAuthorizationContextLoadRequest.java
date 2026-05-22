package edu.whut.eval.application.auth.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class UserAuthorizationContextLoadRequest {

    private final Long userId;
    private final String userNo;
    private final String userName;
    private final String identity;
    private final String sessionId;
    private final Set<String> roles;

    public UserAuthorizationContextLoadRequest(Long userId,
                                               String userNo,
                                               String userName,
                                               String identity,
                                               Set<String> roles) {
        this(userId, userNo, userName, identity, null, roles);
    }

    public UserAuthorizationContextLoadRequest(Long userId,
                                               String userNo,
                                               String userName,
                                               String identity,
                                               String sessionId,
                                               Set<String> roles) {
        this.userId = userId;
        this.userNo = userNo;
        this.userName = userName;
        this.identity = identity;
        this.sessionId = sessionId;
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

    public String getSessionId() {
        return sessionId;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
