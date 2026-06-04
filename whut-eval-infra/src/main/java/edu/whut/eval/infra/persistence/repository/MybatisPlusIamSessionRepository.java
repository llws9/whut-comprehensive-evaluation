package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import edu.whut.eval.infra.persistence.entity.IamSessionDO;
import edu.whut.eval.infra.persistence.mapper.IamSessionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 MyBatis Plus 的会话仓储实现。
 */
@Repository
public class MybatisPlusIamSessionRepository implements IamSessionRepository {

    private final IamSessionMapper sessionMapper;

    public MybatisPlusIamSessionRepository(IamSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Override
    public void create(IamSession session) {
        IamSessionDO entity = new IamSessionDO();
        entity.setSessionNo(session.getSessionNo());
        entity.setUserId(session.getUserId());
        entity.setAccessTokenId(session.getAccessTokenId());
        entity.setRefreshTokenId(session.getRefreshTokenId());
        entity.setDeviceType(session.getDeviceType());
        entity.setClientIp(session.getClientIp());
        entity.setExpiredAt(session.getExpiredAt());
        entity.setRevokedAt(session.getRevokedAt());
        entity.setStatus(session.getStatus().name());
        entity.setCreatedAt(session.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(entity);
    }

    @Override
    public List<IamSession> findActiveByUserId(Long userId) {
        List<IamSessionDO> entities = sessionMapper.selectActiveByUserId(userId);
        return entities.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int revokeAllActiveByUserId(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return sessionMapper.revokeActiveSessionsByUserId(userId, now, now, now);
    }

    @Override
    public boolean revokeById(Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        return sessionMapper.revokeSessionById(sessionId, now, now) > 0;
    }

    @Override
    public IamSession findByAccessTokenId(String accessTokenId) {
        IamSessionDO entity = sessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IamSessionDO>()
                        .eq(IamSessionDO::getAccessTokenId, accessTokenId)
        );
        return entity != null ? toDomain(entity) : null;
    }

    @Override
    public IamSession findByRefreshTokenId(String refreshTokenId) {
        IamSessionDO entity = sessionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IamSessionDO>()
                        .eq(IamSessionDO::getRefreshTokenId, refreshTokenId)
        );
        return entity != null ? toDomain(entity) : null;
    }


    @Override
    public boolean continueRefreshSession(String sessionNo,
                                          String oldRefreshTokenId,
                                          String newAccessTokenId,
                                          String newRefreshTokenId,
                                          LocalDateTime expiredAt) {
        IamSessionDO update = new IamSessionDO();
        update.setAccessTokenId(newAccessTokenId);
        update.setRefreshTokenId(newRefreshTokenId);
        update.setExpiredAt(expiredAt);
        update.setUpdatedAt(LocalDateTime.now());
        return sessionMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IamSessionDO>()
                        .eq(IamSessionDO::getSessionNo, sessionNo)
                        .eq(IamSessionDO::getRefreshTokenId, oldRefreshTokenId)
                        .eq(IamSessionDO::getStatus, "ACTIVE")
                        .gt(IamSessionDO::getExpiredAt, LocalDateTime.now())) > 0;
    }

    private IamSession toDomain(IamSessionDO entity) {
        return new IamSession(
                entity.getId(),
                entity.getSessionNo(),
                entity.getUserId(),
                entity.getAccessTokenId(),
                entity.getRefreshTokenId(),
                entity.getDeviceType(),
                entity.getClientIp(),
                entity.getExpiredAt(),
                entity.getRevokedAt(),
                parseStatus(entity.getStatus()),
                entity.getCreatedAt()
        );
    }

    private IamSession.SessionStatus parseStatus(String status) {
        if (status == null) {
            return IamSession.SessionStatus.ACTIVE;
        }
        try {
            return IamSession.SessionStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return IamSession.SessionStatus.ACTIVE;
        }
    }
}