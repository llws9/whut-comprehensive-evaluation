package edu.whut.eval.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.whut.eval.infra.persistence.entity.IamSessionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录会话 Mapper。
 */
@Mapper
public interface IamSessionMapper extends BaseMapper<IamSessionDO> {

    /**
     * 撤销指定用户的所有活跃会话。
     * 用于用户被禁用或锁定时，强制踢出所有设备。
     */
    @Update({
            "UPDATE iam_session ",
            "SET status = 'REVOKED', revoked_at = #{revokedAt}, updated_at = #{updatedAt} ",
            "WHERE user_id = #{userId} ",
            "AND status = 'ACTIVE' ",
            "AND expired_at > #{now}"
    })
    int revokeActiveSessionsByUserId(@Param("userId") Long userId,
                                     @Param("revokedAt") LocalDateTime revokedAt,
                                     @Param("updatedAt") LocalDateTime updatedAt,
                                     @Param("now") LocalDateTime now);

    /**
     * 撤销指定会话。
     * 用于踢出单个设备或 token 刷新时的旧会话清理。
     */
    @Update({
            "UPDATE iam_session ",
            "SET status = 'REVOKED', revoked_at = #{revokedAt}, updated_at = #{updatedAt} ",
            "WHERE id = #{sessionId} ",
            "AND status = 'ACTIVE'"
    })
    int revokeSessionById(@Param("sessionId") Long sessionId,
                          @Param("revokedAt") LocalDateTime revokedAt,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 查询用户当前的活跃会话列表。
     */
    default List<IamSessionDO> selectActiveByUserId(Long userId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IamSessionDO>()
                .eq(IamSessionDO::getUserId, userId)
                .eq(IamSessionDO::getStatus, "ACTIVE")
                .gt(IamSessionDO::getExpiredAt, LocalDateTime.now())
                .orderByDesc(IamSessionDO::getCreatedAt));
    }
}