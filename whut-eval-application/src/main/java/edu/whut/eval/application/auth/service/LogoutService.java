package edu.whut.eval.application.auth.service;

/**
 * Logout 应用服务接口。
 * 按访问令牌 ID 撤销会话。
 */
public interface LogoutService {

    /**
     * 按访问令牌 ID 登出。
     * 撤销与该访问令牌关联的会话。
     *
     * @param accessTokenId 访问令牌 ID（JWT jti claim）
     * @return 是否成功撤销
     */
    boolean logoutByAccessTokenId(String accessTokenId);
}