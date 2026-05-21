package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleAdminView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRole;
import edu.whut.eval.domain.iam.model.PermissionDictionaryEntry;
import edu.whut.eval.domain.iam.repository.PermissionDictionaryQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import edu.whut.eval.domain.iam.repository.RoleAdminQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultRoleAdminCommandApplicationService implements RoleAdminCommandApplicationService {

    private static final Set<String> ALLOWED_SCOPE = Set.of("SELF", "ORG_UNIT", "ORG_SUBTREE", "ALL");
    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "DISABLED");

    private final RoleAdminQueryRepository roleAdminQueryRepository;
    private final RoleAdminCommandRepository roleAdminCommandRepository;
    private final PermissionDictionaryQueryRepository permissionDictionaryQueryRepository;

    public DefaultRoleAdminCommandApplicationService(RoleAdminQueryRepository roleAdminQueryRepository,
                                                     RoleAdminCommandRepository roleAdminCommandRepository,
                                                     PermissionDictionaryQueryRepository permissionDictionaryQueryRepository) {
        this.roleAdminQueryRepository = roleAdminQueryRepository;
        this.roleAdminCommandRepository = roleAdminCommandRepository;
        this.permissionDictionaryQueryRepository = permissionDictionaryQueryRepository;
    }

    @Override
    @Transactional
    public RoleAdminView createRole(CreateRoleCommand command) {
        String roleCode = requireText(command.roleCode(), "roleCode");
        String roleName = requireText(command.roleName(), "roleName");
        String roleScope = validateRoleScope(command.roleScope());
        String status = validateStatus(command.status());

        roleAdminQueryRepository.findByRoleCode(roleCode)
                .ifPresent(item -> {
                    throw new ConflictException("roleCode 已存在: " + roleCode);
                });

        return toView(roleAdminCommandRepository.create(roleCode, roleName, roleScope, status));
    }

    @Override
    @Transactional
    public void updateRole(Long roleId, UpdateRoleCommand command) {
        IamRole existing = roleAdminCommandRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + roleId));
        String roleName = requireText(command.roleName(), "roleName");
        String roleScope = validateRoleScope(command.roleScope());
        String status = validateStatus(command.status());

        if (roleName.equals(existing.roleName())
                && roleScope.equals(existing.roleScope())
                && status.equals(existing.status())) {
            throw new ConflictException("角色未发生变化");
        }
        boolean updated = roleAdminCommandRepository.update(existing, roleName, roleScope, status);
        if (!updated) {
            throw new ConflictException("角色已被其他人更新，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void replaceRolePermissions(Long roleId, ReplaceRolePermissionsCommand command) {
        roleAdminCommandRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色不存在: " + roleId));
        if (!command.replaceAll()) {
            throw new ValidationException("replaceAll 仅支持 true");
        }
        List<String> normalizedCodes = normalizePermissionCodes(command.permissionCodes());
        if (normalizedCodes.isEmpty()) {
            roleAdminCommandRepository.replacePermissions(roleId, List.of());
            return;
        }
        Set<String> expectedCodes = new LinkedHashSet<>(normalizedCodes);
        List<PermissionDictionaryEntry> existingPermissions =
                permissionDictionaryQueryRepository.findByCodes(expectedCodes, "ACTIVE");
        Set<String> existingCodes = existingPermissions.stream()
                .map(PermissionDictionaryEntry::permissionCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String missingCode = expectedCodes.stream()
                .filter(code -> !existingCodes.contains(code))
                .findFirst()
                .orElse(null);
        if (missingCode != null) {
            throw new ResourceNotFoundException("权限码不存在: " + missingCode);
        }
        roleAdminCommandRepository.replacePermissions(roleId, normalizedCodes);
    }

    private String validateRoleScope(String roleScope) {
        String normalized = requireText(roleScope, "roleScope");
        if (!ALLOWED_SCOPE.contains(normalized)) {
            throw new ValidationException("roleScope 仅允许 SELF、ORG_UNIT、ORG_SUBTREE 或 ALL");
        }
        return normalized;
    }

    private String validateStatus(String status) {
        String normalized = requireText(status, "status");
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new ValidationException("status 仅允许 ACTIVE 或 DISABLED");
        }
        return normalized;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " 不能为空");
        }
        return value.trim();
    }

    private List<String> normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null) {
            throw new ValidationException("permissionCodes 不能为空");
        }
        if (permissionCodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            if (permissionCode == null || permissionCode.isBlank()) {
                throw new ValidationException("permissionCodes 不能包含空值");
            }
            normalized.add(permissionCode.trim());
        }
        if (normalized.isEmpty()) {
            throw new ValidationException("permissionCodes 不能为空");
        }
        return List.copyOf(normalized);
    }

    private RoleAdminView toView(IamRole role) {
        return new RoleAdminView(role.roleId(), role.roleCode(), role.roleName(), role.roleScope(), role.status());
    }
}
