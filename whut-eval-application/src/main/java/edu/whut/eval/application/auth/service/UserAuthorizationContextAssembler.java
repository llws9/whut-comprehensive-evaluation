package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.common.exception.AuthenticationFailedException;

import java.util.Optional;

/**
 * 从运行时认证上下文装配业务侧可直接消费的授权上下文。
 */
public interface UserAuthorizationContextAssembler {

    /**
     * 尝试读取当前请求的授权上下文；匿名或未认证请求返回空。
     */
    Optional<UserAuthorizationContext> currentAuthorizationContext();

    /**
     * 强制要求当前请求已经具备授权上下文，否则直接抛出认证异常。
     */
    default UserAuthorizationContext requiredAuthorizationContext() {
        return currentAuthorizationContext().orElseThrow(AuthenticationFailedException::new);
    }
}
