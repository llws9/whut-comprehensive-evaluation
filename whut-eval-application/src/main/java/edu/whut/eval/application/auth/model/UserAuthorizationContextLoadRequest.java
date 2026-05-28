package edu.whut.eval.application.auth.model;

import java.util.Set;

/**
 * @deprecated 使用 {@link edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest} 代替。
 *             此类仅为向后兼容保留，将在未来版本中删除。
 */
@Deprecated
public class UserAuthorizationContextLoadRequest extends edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest {

    public UserAuthorizationContextLoadRequest(Long userId,
                                               String userNo,
                                               String userName,
                                               String identity,
                                               Set<String> roles) {
        super(userId, userNo, userName, identity, roles);
    }
}
