package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.domain.iam.repository.IamRoleCommandRepository;
import edu.whut.eval.infra.persistence.entity.IamPermissionDO;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.mapper.IamPermissionMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MybatisPlusIamRoleCommandRepository implements IamRoleCommandRepository {

    private final IamRoleMapper iamRoleMapper;
    private final IamRolePermissionMapper iamRolePermissionMapper;
    private final IamPermissionMapper iamPermissionMapper;

    public MybatisPlusIamRoleCommandRepository(IamRoleMapper iamRoleMapper,
                                                IamRolePermissionMapper iamRolePermissionMapper,
                                                IamPermissionMapper iamPermissionMapper) {
        this.iamRoleMapper = iamRoleMapper;
        this.iamRolePermissionMapper = iamRolePermissionMapper;
        this.iamPermissionMapper = iamPermissionMapper;
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

    @Override
    public void replaceRolePermissions(Long roleId, List<String> permissionCodes) {
        IamRoleDO role = iamRoleMapper.selectById(roleId);
        if (role == null) {
            throw new ResourceNotFoundException("角色不存在: " + roleId);
        }

        List<Long> permissionIds = new ArrayList<>();
        for (String permissionCode : permissionCodes) {
            IamPermissionDO permission = iamPermissionMapper.selectOne(new LambdaQueryWrapper<IamPermissionDO>()
                    .eq(IamPermissionDO::getPermissionCode, permissionCode)
                    .last("limit 1"));
            if (permission == null) {
                throw new ResourceNotFoundException("权限不存在: " + permissionCode);
            }
            permissionIds.add(permission.getId());
        }

        iamRolePermissionMapper.delete(new LambdaQueryWrapper<IamRolePermissionDO>()
                .eq(IamRolePermissionDO::getRoleId, roleId));

        for (Long permissionId : permissionIds) {
            IamRolePermissionDO relation = new IamRolePermissionDO();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreatedAt(LocalDateTime.now());
            iamRolePermissionMapper.insert(relation);
        }
    }
}
