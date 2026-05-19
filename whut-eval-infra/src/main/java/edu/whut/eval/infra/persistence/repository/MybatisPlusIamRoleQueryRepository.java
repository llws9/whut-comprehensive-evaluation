package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.domain.iam.repository.IamRoleQueryRepository;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisPlusIamRoleQueryRepository implements IamRoleQueryRepository {

    private final IamRoleMapper iamRoleMapper;

    public MybatisPlusIamRoleQueryRepository(IamRoleMapper iamRoleMapper) {
        this.iamRoleMapper = iamRoleMapper;
    }

    @Override
    public Optional<IamRoleDefinition> findByRoleCode(String roleCode) {
        IamRoleDO roleDO = iamRoleMapper.selectOne(new LambdaQueryWrapper<IamRoleDO>()
                .eq(IamRoleDO::getRoleCode, roleCode)
                .last("limit 1"));
        return Optional.ofNullable(roleDO).map(this::toDomain);
    }

    private IamRoleDefinition toDomain(IamRoleDO roleDO) {
        return new IamRoleDefinition(
                roleDO.getId(),
                roleDO.getRoleCode(),
                roleDO.getRoleName(),
                roleDO.getStatus()
        );
    }
}
