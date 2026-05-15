package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.model.IamUserCredential;
import edu.whut.eval.domain.iam.query.UserPageQuery;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.cache.UserCacheGateway;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisPlusIamUserQueryRepository implements IamUserQueryRepository {

    private final IamUserMapper iamUserMapper;
    private final UserCacheGateway userCacheGateway;

    public MybatisPlusIamUserQueryRepository(IamUserMapper iamUserMapper, UserCacheGateway userCacheGateway) {
        this.iamUserMapper = iamUserMapper;
        this.userCacheGateway = userCacheGateway;
    }

    @Override
    public Optional<IamUser> findById(Long id) {
        return Optional.ofNullable(iamUserMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<IamUser> findByUserNo(String userNo) {
        Optional<IamUser> cached = userCacheGateway.getByUserNo(userNo);
        if (cached.isPresent()) {
            return cached;
        }
        LambdaQueryWrapper<IamUserDO> wrapper = new LambdaQueryWrapper<IamUserDO>()
                .eq(IamUserDO::getUserNo, userNo)
                .last("limit 1");
        IamUserDO user = iamUserMapper.selectOne(wrapper);
        if (user == null) {
            return Optional.empty();
        }
        IamUser domain = toDomain(user);
        userCacheGateway.put(domain);
        return Optional.of(domain);
    }

    @Override
    public Optional<IamUserCredential> findCredentialByUserNo(String userNo) {
        LambdaQueryWrapper<IamUserDO> wrapper = new LambdaQueryWrapper<IamUserDO>()
                .eq(IamUserDO::getUserNo, userNo)
                .last("limit 1");
        return Optional.ofNullable(iamUserMapper.selectOne(wrapper)).map(this::toCredential);
    }

    @Override
    public PageResult<IamUser> pageUsers(UserPageQuery query) {
        Page<IamUserDO> page = Page.of(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<IamUserDO> wrapper = new LambdaQueryWrapper<IamUserDO>()
                .like(query.getUserName() != null && !query.getUserName().isBlank(), IamUserDO::getUserName, query.getUserName())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(), IamUserDO::getStatus, query.getStatus())
                .orderByAsc(IamUserDO::getId);
        Page<IamUserDO> result = iamUserMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toDomain).toList());
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

    private IamUserCredential toCredential(IamUserDO userDO) {
        return new IamUserCredential(
                userDO.getId(),
                userDO.getUserNo(),
                userDO.getUserName(),
                userDO.getPasswordHash(),
                userDO.getStatus()
        );
    }
}
