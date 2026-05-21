package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.iam.model.IamRole;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import edu.whut.eval.infra.persistence.entity.IamPermissionDO;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.mapper.IamPermissionMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class MybatisPlusRoleAdminCommandRepository implements RoleAdminCommandRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final IamRoleMapper iamRoleMapper;
    private final IamRolePermissionMapper iamRolePermissionMapper;
    private final IamPermissionMapper iamPermissionMapper;

    public MybatisPlusRoleAdminCommandRepository(IamRoleMapper iamRoleMapper,
                                                IamRolePermissionMapper iamRolePermissionMapper,
                                                IamPermissionMapper iamPermissionMapper) {
        this.iamRoleMapper = iamRoleMapper;
        this.iamRolePermissionMapper = iamRolePermissionMapper;
        this.iamPermissionMapper = iamPermissionMapper;
    }

    @Override
    public IamRole create(String roleCode, String roleName, String roleScope, String status) {
        IamRoleDO roleDO = new IamRoleDO();
        roleDO.setRoleCode(roleCode);
        roleDO.setRoleName(roleName);
        roleDO.setRoleScope(roleScope);
        roleDO.setStatus(status);
        roleDO.setCreatedAt(LocalDateTime.now());
        try {
            iamRoleMapper.insert(roleDO);
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("roleCode 已存在: " + roleCode);
        }
        return toDomain(roleDO);
    }

    @Override
    public Optional<IamRole> findById(Long roleId) {
        return Optional.ofNullable(iamRoleMapper.selectById(roleId)).map(this::toDomain);
    }

    @Override
    public boolean update(IamRole existingRole, String roleName, String roleScope, String status) {
        LambdaUpdateWrapper<IamRoleDO> wrapper = new LambdaUpdateWrapper<IamRoleDO>()
                .eq(IamRoleDO::getId, existingRole.roleId())
                .eq(IamRoleDO::getRoleName, existingRole.roleName())
                .eq(IamRoleDO::getRoleScope, existingRole.roleScope())
                .eq(IamRoleDO::getStatus, existingRole.status())
                .set(IamRoleDO::getRoleName, roleName)
                .set(IamRoleDO::getRoleScope, roleScope)
                .set(IamRoleDO::getStatus, status);
        return iamRoleMapper.update(null, wrapper) > 0;
    }

    @Override
    public void replacePermissions(Long roleId, List<String> permissionCodes) {
        iamRolePermissionMapper.delete(new LambdaQueryWrapper<IamRolePermissionDO>()
                .eq(IamRolePermissionDO::getRoleId, roleId));
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        Map<String, Long> permissionIdByCode = iamPermissionMapper.selectList(new LambdaQueryWrapper<IamPermissionDO>()
                        .in(IamPermissionDO::getPermissionCode, permissionCodes))
                .stream()
                .collect(Collectors.toMap(IamPermissionDO::getPermissionCode, IamPermissionDO::getId, (left, right) -> left));
        LocalDateTime now = LocalDateTime.now();
        permissionCodes.forEach(permissionCode -> {
            IamRolePermissionDO relation = new IamRolePermissionDO();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionIdByCode.get(permissionCode));
            relation.setCreatedAt(now);
            iamRolePermissionMapper.insert(relation);
        });
    }

    private IamRole toDomain(IamRoleDO roleDO) {
        return new IamRole(
                roleDO.getId(),
                roleDO.getRoleCode(),
                roleDO.getRoleName(),
                roleDO.getRoleScope(),
                roleDO.getStatus(),
                roleDO.getCreatedAt() == null ? null : TIME_FORMATTER.format(roleDO.getCreatedAt())
        );
    }
}
