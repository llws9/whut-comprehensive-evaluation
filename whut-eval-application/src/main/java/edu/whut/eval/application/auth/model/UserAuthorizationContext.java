package edu.whut.eval.application.auth.model;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.UserAuthorizationContext} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class UserAuthorizationContext extends edu.whut.eval.domain.auth.model.UserAuthorizationContext {

    public UserAuthorizationContext(Long userId,
                                    String userNo,
                                    String userName,
                                    String identity,
                                    java.util.Set<String> roles,
                                    java.util.Set<String> authorities,
                                    java.util.List<edu.whut.eval.domain.iam.model.IamScopeRule> scopeRules) {
        super(userId, userNo, userName, identity, roles, authorities, scopeRules);
    }
}
