package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.iam.model.IamRoleAdminPageItem;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.mapper.IamPermissionLookupMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import edu.whut.eval.infra.persistence.repository.row.PermissionIdCodeRow;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MybatisPlusRoleAdminCommandRepository implements RoleAdminCommandRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final IamRoleMapper iamRoleMapper;
    private final IamRolePermissionMapper iamRolePermissionMapper;
    private final IamPermissionLookupMapper iamPermissionLookupMapper;

    public MybatisPlusRoleAdminCommandRepository(IamRoleMapper iamRoleMapper,
                                                 IamRolePermissionMapper iamRolePermissionMapper,
                                                 IamPermissionLookupMapper iamPermissionLookupMapper) {
        this.iamRoleMapper = iamRoleMapper;
        this.iamRolePermissionMapper = iamRolePermissionMapper;
        this.iamPermissionLookupMapper = iamPermissionLookupMapper;
    }

    @Override
    public Optional<IamRoleAdminPageItem> findByRoleCode(String roleCode) {
        IamRoleDO role = iamRoleMapper.selectOne(new LambdaQueryWrapper<IamRoleDO>()
                .eq(IamRoleDO::getRoleCode, roleCode)
                .last("LIMIT 1"));
        return Optional.ofNullable(role).map(this::toDomain);
    }

    @Override
    public boolean existsById(Long roleId) {
        return iamRoleMapper.selectById(roleId) != null;
    }

    @Override
    public IamRoleAdminPageItem createRole(String roleCode, String roleName, String roleScope) {
        IamRoleDO role = new IamRoleDO();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setRoleScope(roleScope);
        role.setStatus("ACTIVE");
        role.setCreatedAt(LocalDateTime.now());
        iamRoleMapper.insert(role);
        return toDomain(role);
    }

    @Override
    public boolean updateRoleIfSnapshotMatches(Long roleId,
                                               String roleName,
                                               String roleScope,
                                               String status,
                                               String expectedRoleName,
                                               String expectedRoleScope,
                                               String expectedStatus) {
        IamRoleDO update = new IamRoleDO();
        update.setRoleName(roleName);
        update.setRoleScope(roleScope);
        update.setStatus(status);
        return iamRoleMapper.update(update, new LambdaUpdateWrapper<IamRoleDO>()
                .eq(IamRoleDO::getId, roleId)
                .eq(IamRoleDO::getRoleName, expectedRoleName)
                .eq(IamRoleDO::getRoleScope, expectedRoleScope)
                .eq(IamRoleDO::getStatus, expectedStatus)) > 0;
    }

    @Override
    public void replacePermissions(Long roleId, List<String> permissionCodes) {
        iamRolePermissionMapper.deleteByRoleId(roleId);
        if (permissionCodes.isEmpty()) {
            return;
        }
        Map<String, Long> permissionIdByCode = new LinkedHashMap<>();
        for (PermissionIdCodeRow row : iamPermissionLookupMapper.selectIdsByCodes(permissionCodes)) {
            permissionIdByCode.put(row.permissionCode(), row.permissionId());
        }
        for (String permissionCode : permissionCodes) {
            Long permissionId = permissionIdByCode.get(permissionCode);
            if (permissionId == null) {
                throw new ResourceNotFoundException("权限不存在: " + permissionCode);
            }
            IamRolePermissionDO item = new IamRolePermissionDO();
            item.setRoleId(roleId);
            item.setPermissionId(permissionId);
            item.setCreatedAt(LocalDateTime.now());
            iamRolePermissionMapper.insert(item);
        }
    }

    private IamRoleAdminPageItem toDomain(IamRoleDO role) {
        return new IamRoleAdminPageItem(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleScope(),
                role.getStatus(),
                0,
                role.getCreatedAt() == null ? null : TIME_FORMATTER.format(role.getCreatedAt())
        );
    }
}
