package edu.whut.eval.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.whut.eval.domain.iam.model.IamRoleDetail;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamPermissionLookupMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.repository.row.PermissionIdCodeRow;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class MybatisPlusRoleAdminCommandRepository implements RoleAdminCommandRepository {

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
    public Optional<IamRoleDetail> findById(Long roleId) {
        IamRoleDO item = iamRoleMapper.selectById(roleId);
        return Optional.ofNullable(item).map(this::toDetail);
    }

    @Override
    public Optional<IamRoleDetail> findByRoleCode(String roleCode) {
        IamRoleDO item = iamRoleMapper.selectOne(new LambdaQueryWrapper<IamRoleDO>()
                .eq(IamRoleDO::getRoleCode, roleCode)
                .last("limit 1"));
        return Optional.ofNullable(item).map(this::toDetail);
    }

    @Override
    public IamRoleDetail create(String roleCode, String roleName, String roleScope, String status) {
        IamRoleDO item = new IamRoleDO();
        item.setRoleCode(roleCode);
        item.setRoleName(roleName);
        item.setRoleScope(roleScope);
        item.setStatus(status);
        item.setCreatedAt(LocalDateTime.now());
        iamRoleMapper.insert(item);
        return toDetail(item);
    }

    @Override
    public boolean updateWithSnapshot(Long roleId,
                                      String roleName,
                                      String roleScope,
                                      String status,
                                      String snapshotRoleName,
                                      String snapshotRoleScope,
                                      String snapshotStatus) {
        LambdaQueryWrapper<IamRoleDO> wrapper = new LambdaQueryWrapper<IamRoleDO>()
                .eq(IamRoleDO::getId, roleId)
                .eq(IamRoleDO::getRoleName, snapshotRoleName)
                .eq(IamRoleDO::getRoleScope, snapshotRoleScope)
                .eq(IamRoleDO::getStatus, snapshotStatus);

        IamRoleDO update = new IamRoleDO();
        update.setRoleName(roleName);
        update.setRoleScope(roleScope);
        update.setStatus(status);

        return iamRoleMapper.update(update, wrapper) > 0;
    }


    @Override
    public void replacePermissions(Long roleId, List<String> permissionCodes) {
        if (permissionCodes.isEmpty()) {
            iamRolePermissionMapper.delete(new LambdaQueryWrapper<IamRolePermissionDO>()
                    .eq(IamRolePermissionDO::getRoleId, roleId));
            return;
        }

        List<PermissionIdCodeRow> permissionRows = iamPermissionLookupMapper.selectIdsByCodes(permissionCodes);
        Set<String> foundCodes = new HashSet<>();
        for (PermissionIdCodeRow row : permissionRows) {
            foundCodes.add(row.permissionCode());
        }
        List<String> missingCodes = permissionCodes.stream()
                .filter(code -> !foundCodes.contains(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new ResourceNotFoundException("权限码不存在: " + String.join(",", missingCodes));
        }

        iamRolePermissionMapper.delete(new LambdaQueryWrapper<IamRolePermissionDO>()
                .eq(IamRolePermissionDO::getRoleId, roleId));

        List<IamRolePermissionDO> rows = new ArrayList<>();
        for (PermissionIdCodeRow permissionRow : permissionRows) {
            IamRolePermissionDO row = new IamRolePermissionDO();
            row.setRoleId(roleId);
            row.setPermissionId(permissionRow.permissionId());
            rows.add(row);
        }
        for (IamRolePermissionDO row : rows) {
            iamRolePermissionMapper.insert(row);
        }
    }

    private IamRoleDetail toDetail(IamRoleDO item) {
        return new IamRoleDetail(
                item.getId(),
                item.getRoleCode(),
                item.getRoleName(),
                item.getRoleScope(),
                item.getStatus()
        );
    }
}
