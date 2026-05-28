package edu.whut.eval.domain.auth.service;

import edu.whut.eval.domain.auth.model.AuthorizationScopeSet;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;

/**
 * 授权范围评估器。
 * 根据当前用户授权上下文计算某个权限码下最终可用的范围集合。
 */
public interface AuthorizationScopeEvaluator {

    /**
     * 先校验用户是否具备目标权限，再把对应 scope rules 规整为统一的范围集合。
     */
    AuthorizationScopeSet evaluate(UserAuthorizationContext authorizationContext, String permissionCode);
}
