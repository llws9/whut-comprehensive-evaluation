package edu.whut.eval.domain.iam.repository;

import edu.whut.eval.domain.iam.model.IamSession;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话仓储接口。
 */
public interface IamSessionRepository {

    /**
     * 创建登录会话。
     */
    void create(IamSession session);

    /**
     * 查询用户当前的活跃会话列表。
     */
    List<IamSession> findActiveByUserId(Long userId);

    /**
     * 撤销用户的所有活跃会话。
     * 返回被撤销的会话数量。
     */
    int revokeAllActiveByUserId(Long userId);

    /**
     * 撤销指定会话。
     */
    boolean revokeById(Long sessionId);

    /**
     * 按 access token ID 查找会话。
     */
    IamSession findByAccessTokenId(String accessTokenId);

    /**
     * 按 refresh token ID 查找会话。
     */
    IamSession findByRefreshTokenId(String refreshTokenId);

    /**
     * 延续 refresh 会话，轮换 token ID 并更新过期时间。
     */
    boolean continueRefreshSession(String sessionNo,
                                   String oldRefreshTokenId,
                                   String newAccessTokenId,
                                   String newRefreshTokenId,
                                   LocalDateTime expiredAt);
}