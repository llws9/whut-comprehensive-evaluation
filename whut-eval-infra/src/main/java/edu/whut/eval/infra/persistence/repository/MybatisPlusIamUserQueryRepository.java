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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        w -> w.like(IamUserDO::getUserNo, query.getKeyword())
                                .or()
                                .like(IamUserDO::getUserName, query.getKeyword()))
                .eq(query.getStatus() != null && !query.getStatus().isBlank(), IamUserDO::getStatus, query.getStatus())
                .inSql(query.getOrgUnitId() != null,
                        IamUserDO::getId,
                        "SELECT user_id FROM org_membership WHERE status = 'ACTIVE' AND org_unit_id = " + query.getOrgUnitId())
                .orderByAsc(IamUserDO::getId);
        Page<IamUserDO> result = iamUserMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toDomain).toList());
    }

    @Override
    public Map<Long, List<String>> findActiveOrgUnitNamesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        iamUserMapper.selectActiveOrgUnitNamesByUserIds(userIds).forEach(row ->
                result.computeIfAbsent(row.userId(), ignored -> new java.util.ArrayList<>()).add(row.orgUnitName())
        );
        return result;
    }

    @Override
    public Map<Long, List<String>> findActiveRoleCodesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        iamUserMapper.selectActiveRoleCodesByUserIds(userIds).forEach(row ->
                result.computeIfAbsent(row.userId(), ignored -> new java.util.ArrayList<>()).add(row.roleCode())
        );
        return result;
    }

    private IamUser toDomain(IamUserDO userDO) {
        return new IamUser(
                userDO.getId(),
                userDO.getUserNo(),
                userDO.getUserName(),
                userDO.getEmail(),
                userDO.getPhone(),
                userDO.getStatus(),
                userDO.getCreatedAt() == null ? null : userDO.getCreatedAt().toString()
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
