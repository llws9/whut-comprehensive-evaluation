package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleCreatedView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleDetail;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultRoleAdminApplicationService implements RoleAdminApplicationService {

    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "DISABLED");

    private final RoleAdminCommandRepository roleAdminCommandRepository;

    public DefaultRoleAdminApplicationService(RoleAdminCommandRepository roleAdminCommandRepository) {
        this.roleAdminCommandRepository = roleAdminCommandRepository;
    }

    @Override
    @Transactional
    public RoleCreatedView createRole(CreateRoleCommand command) {
        String roleCode = normalize(command.roleCode());
        String roleName = normalize(command.roleName());
        String roleScope = normalize(command.roleScope());

        if (!"ORG_SUBTREE".equals(roleScope)) {
            throw new ValidationException("roleScope 仅允许 ORG_SUBTREE");
        }
        if (roleAdminCommandRepository.findByRoleCode(roleCode).isPresent()) {
            throw new ConflictException("角色编码已存在: " + roleCode);
        }

        IamRoleDetail created = roleAdminCommandRepository.create(roleCode, roleName, "ORG_SUBTREE", "ACTIVE");
        return new RoleCreatedView(
                created.id(),
                created.roleCode(),
                created.roleName(),
                created.roleScope(),
                created.status()
        );
    }

    @Override
    @Transactional
    public void updateRole(Long roleId, UpdateRoleCommand command) {
        IamRoleDetail current = roleAdminCommandRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + roleId));

        if (!equalsNormalized(current.roleName(), command.snapshotRoleName())
                || !equalsNormalized(current.roleScope(), command.snapshotRoleScope())
                || !equalsNormalized(current.status(), command.snapshotStatus())) {
            throw new ConflictException("角色模板已被更新，请刷新后重试");
        }

        String roleScope = normalize(command.roleScope());
        String status = normalize(command.status());

        if (!"ORG_SUBTREE".equals(roleScope)) {
            throw new ValidationException("roleScope 仅允许 ORG_SUBTREE");
        }
        if (!ALLOWED_STATUS.contains(status)) {
            throw new ValidationException("status 仅允许 ACTIVE 或 DISABLED");
        }

        boolean updated = roleAdminCommandRepository.updateWithSnapshot(
                roleId,
                normalize(command.roleName()),
                roleScope,
                status,
                normalize(command.snapshotRoleName()),
                normalize(command.snapshotRoleScope()),
                normalize(command.snapshotStatus())
        );
        if (!updated) {
            throw new ConflictException("角色模板已被更新，请刷新后重试");
        }
    }


    @Override
    @Transactional
    public void replacePermissions(Long roleId, ReplaceRolePermissionsCommand command) {
        if (roleId == null) {
            throw new ValidationException("roleId 不能为空");
        }
        if (!Boolean.TRUE.equals(command.replaceAll())) {
            throw new ValidationException("当前仅支持 replaceAll=true 的整集合替换");
        }
        if (command.permissionCodes() == null) {
            throw new ValidationException("permissionCodes 不能为空");
        }
        if (roleAdminCommandRepository.findById(roleId).isEmpty()) {
            throw new ResourceNotFoundException("角色不存在: " + roleId);
        }
        roleAdminCommandRepository.replacePermissions(roleId, normalizePermissionCodes(command.permissionCodes()));
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            String value = normalize(permissionCode);
            if (value == null || value.isBlank()) {
                throw new ValidationException("permissionCode 不能为空");
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean equalsNormalized(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft == null) {
            return normalizedRight == null;
        }
        return normalizedLeft.equals(normalizedRight);
    }
}
