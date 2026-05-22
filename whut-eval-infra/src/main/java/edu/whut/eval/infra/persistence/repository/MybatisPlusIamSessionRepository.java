package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.model.IamSessionStatus;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import edu.whut.eval.infra.persistence.entity.IamSessionDO;
import edu.whut.eval.infra.persistence.mapper.IamSessionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class MybatisPlusIamSessionRepository implements IamSessionRepository {

    private final IamSessionMapper iamSessionMapper;

    public MybatisPlusIamSessionRepository(IamSessionMapper iamSessionMapper) {
        this.iamSessionMapper = iamSessionMapper;
    }

    @Override
    public IamSession save(IamSession session) {
        IamSessionDO sessionDO = toDO(session);
        iamSessionMapper.insert(sessionDO);
        return toDomain(sessionDO);
    }

    @Override
    public Optional<IamSession> findBySessionId(String sessionId) {
        IamSessionDO sessionDO = iamSessionMapper.selectOne(new LambdaQueryWrapper<IamSessionDO>()
                .eq(IamSessionDO::getSessionId, sessionId)
                .last("limit 1"));
        return Optional.ofNullable(sessionDO).map(this::toDomain);
    }

    @Override
    public void revoke(String sessionId, LocalDateTime revokedAt) {
        IamSessionDO sessionDO = new IamSessionDO();
        sessionDO.setStatus(IamSessionStatus.REVOKED.name());
        sessionDO.setRevokedAt(revokedAt);
        iamSessionMapper.update(sessionDO, new LambdaUpdateWrapper<IamSessionDO>()
                .eq(IamSessionDO::getSessionId, sessionId));
    }

    @Override
    public void extendExpiration(String sessionId, LocalDateTime expiredAt) {
        IamSessionDO sessionDO = new IamSessionDO();
        sessionDO.setExpiredAt(expiredAt);
        iamSessionMapper.update(sessionDO, new LambdaUpdateWrapper<IamSessionDO>()
                .eq(IamSessionDO::getSessionId, sessionId));
    }

    private IamSessionDO toDO(IamSession session) {
        IamSessionDO sessionDO = new IamSessionDO();
        sessionDO.setId(session.id());
        sessionDO.setUserId(session.userId());
        sessionDO.setSessionId(session.sessionId());
        sessionDO.setLoginIp(session.loginIp());
        sessionDO.setUserAgent(session.userAgent());
        sessionDO.setExpiredAt(session.expiredAt());
        sessionDO.setRevokedAt(session.revokedAt());
        sessionDO.setStatus(session.status().name());
        sessionDO.setCreatedAt(session.createdAt());
        return sessionDO;
    }

    private IamSession toDomain(IamSessionDO sessionDO) {
        return new IamSession(
                sessionDO.getId(),
                sessionDO.getUserId(),
                sessionDO.getSessionId(),
                sessionDO.getLoginIp(),
                sessionDO.getUserAgent(),
                sessionDO.getExpiredAt(),
                sessionDO.getRevokedAt(),
                IamSessionStatus.valueOf(sessionDO.getStatus()),
                sessionDO.getCreatedAt()
        );
    }
}
