package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;
import edu.whut.eval.application.auth.model.RefreshTokenReloadContext;

/**
 * 基于 refresh token 的最小身份信息重载最新用户上下文。
 */
public interface RefreshTokenCurrentUserLoader {

    /**
     * 重新校验用户状态，并加载角色、权限、范围规则后返回新的用户快照。
     */
    AuthenticatedUserSnapshot load(RefreshTokenReloadContext context);
}
