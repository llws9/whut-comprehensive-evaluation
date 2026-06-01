package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.domain.iam.repository.IamRoleCommandRepository;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class MybatisPlusIamRoleCommandRepository implements IamRoleCommandRepository {

    private final IamRoleMapper iamRoleMapper;

    public MybatisPlusIamRoleCommandRepository(IamRoleMapper iamRoleMapper) {
        this.iamRoleMapper = iamRoleMapper;
    }

    @Override
    public IamRoleDefinition createRole(String roleCode, String roleName, String roleScope, String status) {
        IamRoleDO entity = new IamRoleDO();
        entity.setRoleCode(roleCode);
        entity.setRoleName(roleName);
        entity.setRoleScope(roleScope);
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.now());
        iamRoleMapper.insert(entity);
        return new IamRoleDefinition(entity.getId(), entity.getRoleCode(), entity.getRoleName(), entity.getStatus());
    }

    @Override
    public IamRoleDefinition updateRole(Long roleId, String roleName, String status) {
        IamRoleDO update = new IamRoleDO();
        update.setId(roleId);
        update.setRoleName(roleName);
        update.setStatus(status);
        iamRoleMapper.updateById(update);

        IamRoleDO entity = iamRoleMapper.selectById(roleId);
        return new IamRoleDefinition(entity.getId(), entity.getRoleCode(), entity.getRoleName(), entity.getStatus());
    }
}
