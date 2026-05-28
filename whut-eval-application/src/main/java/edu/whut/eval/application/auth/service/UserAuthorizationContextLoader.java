package edu.whut.eval.application.auth.service;

import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;

/**
 * 在请求进入业务层前，按用户主键重新加载最新授权上下文。
 */
public interface UserAuthorizationContextLoader {

    /**
     * 根据最小身份信息补齐最新的权限与范围规则。
     */
    UserAuthorizationContext load(UserAuthorizationContextLoadRequest request);
}
