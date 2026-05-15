package edu.whut.eval.application.auth.service;

import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;

/**
 * 负责账号密码登录认证，并在成功后返回可直接签发 token 的完整用户快照。
 */
public interface LoginAuthenticationService {

    /**
     * 对登录凭证做认证，失败时抛出认证异常，成功时返回角色、权限、范围已补齐的用户快照。
     */
    AuthenticatedUserSnapshot authenticate(String credential, String rawPassword);
}
