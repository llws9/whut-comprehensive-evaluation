package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.UserAdminCommandRepository;
import edu.whut.eval.infra.cache.UserCacheGateway;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class MybatisPlusUserAdminCommandRepository implements UserAdminCommandRepository {

    private final IamUserMapper iamUserMapper;
    private final UserCacheGateway userCacheGateway;

    public MybatisPlusUserAdminCommandRepository(IamUserMapper iamUserMapper,
                                                 UserCacheGateway userCacheGateway) {
        this.iamUserMapper = iamUserMapper;
        this.userCacheGateway = userCacheGateway;
    }

    @Override
    public IamUser create(String userNo,
                          String userName,
                          String email,
                          String phone,
                          String passwordHash,
                          String status) {
        LocalDateTime now = LocalDateTime.now();
        IamUserDO userDO = new IamUserDO();
        userDO.setUserNo(userNo);
        userDO.setUserName(userName);
        userDO.setEmail(email);
        userDO.setPhone(phone);
        userDO.setPasswordHash(passwordHash);
        userDO.setStatus(status);
        userDO.setCreatedAt(now);
        userDO.setUpdatedAt(now);
        try {
            iamUserMapper.insert(userDO);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("userNo 已存在: " + userNo);
        }
        IamUser user = toDomain(userDO);
        userCacheGateway.put(user);
        return user;
    }

    @Override
    public IamUser updateProfile(Long userId,
                                 String userName,
                                 String email,
                                 String phone,
                                 String passwordHash) {
        IamUserDO existing = iamUserMapper.selectById(userId);
        if (existing == null) {
            throw new IllegalStateException("用户不存在: " + userId);
        }
        existing.setUserName(userName);
        existing.setEmail(email);
        existing.setPhone(phone);
        existing.setPasswordHash(passwordHash);
        existing.setUpdatedAt(LocalDateTime.now());
        iamUserMapper.updateById(existing);
        IamUser user = toDomain(existing);
        userCacheGateway.put(user);
        return user;
    }

    @Override
    public boolean updateStatus(Long userId, String expectedCurrentStatus, String targetStatus) {
        LambdaUpdateWrapper<IamUserDO> wrapper = new LambdaUpdateWrapper<IamUserDO>()
                .eq(IamUserDO::getId, userId)
                .eq(IamUserDO::getStatus, expectedCurrentStatus)
                .set(IamUserDO::getStatus, targetStatus)
                .set(IamUserDO::getUpdatedAt, LocalDateTime.now());
        int updated = iamUserMapper.update(null, wrapper);
        if (updated <= 0) {
            return false;
        }
        IamUserDO latest = iamUserMapper.selectById(userId);
        if (latest != null) {
            userCacheGateway.put(toDomain(latest));
        }
        return true;
    }

    private IamUser toDomain(IamUserDO userDO) {
        return new IamUser(
                userDO.getId(),
                userDO.getUserNo(),
                userDO.getUserName(),
                userDO.getEmail(),
                userDO.getPhone(),
                userDO.getStatus()
        );
    }
}
