package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleAdminView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleAdminPageItem;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleAdminApplicationService {

    private static final Set<String> ALLOWED_ROLE_STATUS = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> ALLOWED_ROLE_SCOPE = Set.of("SELF", "ORG_UNIT", "ORG_SUBTREE", "ALL");

    private final RoleAdminCommandRepository roleAdminCommandRepository;

    public RoleAdminApplicationService(RoleAdminCommandRepository roleAdminCommandRepository) {
        this.roleAdminCommandRepository = roleAdminCommandRepository;
    }

    @Transactional
    public RoleAdminView createRole(CreateRoleCommand command) {
        String roleCode = requireNonBlank(command.roleCode(), "roleCode");
        String roleName = requireNonBlank(command.roleName(), "roleName");
        String roleScope = requireRoleScope(command.roleScope());
        roleAdminCommandRepository.findByRoleCode(roleCode).ifPresent(existing -> {
            throw new ConflictException("角色编码已存在: " + roleCode);
        });
        return toView(roleAdminCommandRepository.createRole(roleCode, roleName, roleScope));
    }

    @Transactional
    public void updateRole(Long roleId, UpdateRoleCommand command) {
        if (roleId == null) {
            throw new ValidationException("roleId 不能为空");
        }
        String roleName = requireNonBlank(command.roleName(), "roleName");
        String roleScope = requireRoleScope(command.roleScope());
        String status = requireStatus(command.status());
        String expectedRoleName = requireNonBlank(command.expectedRoleName(), "expectedRoleName");
        String expectedRoleScope = requireRoleScope(command.expectedRoleScope());
        String expectedStatus = requireStatus(command.expectedStatus());
        boolean updated = roleAdminCommandRepository.updateRoleIfSnapshotMatches(
                roleId, roleName, roleScope, status, expectedRoleName, expectedRoleScope, expectedStatus);
        if (!updated) {
            if (!roleAdminCommandRepository.existsById(roleId)) {
                throw new ResourceNotFoundException("角色不存在: " + roleId);
            }
            throw new ConflictException("角色模板已被他人更新，请重新拉取最新快照");
        }
    }

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
        if (!roleAdminCommandRepository.existsById(roleId)) {
            throw new ResourceNotFoundException("角色不存在: " + roleId);
        }
        roleAdminCommandRepository.replacePermissions(roleId, normalizePermissionCodes(command.permissionCodes()));
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            String value = requireNonBlank(permissionCode, "permissionCode");
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private String requireRoleScope(String value) {
        String normalized = requireNonBlank(value, "roleScope");
        if (!ALLOWED_ROLE_SCOPE.contains(normalized)) {
            throw new ValidationException("roleScope 仅允许 SELF、ORG_UNIT、ORG_SUBTREE 或 ALL");
        }
        return normalized;
    }

    private String requireStatus(String value) {
        String normalized = requireNonBlank(value, "status");
        if (!ALLOWED_ROLE_STATUS.contains(normalized)) {
            throw new ValidationException("status 仅允许 ACTIVE 或 DISABLED");
        }
        return normalized;
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private RoleAdminView toView(IamRoleAdminPageItem item) {
        return new RoleAdminView(item.roleId(), item.roleCode(), item.roleName(), item.roleScope(),
                item.status(), item.permissionCount(), item.createdAt());
    }
}
