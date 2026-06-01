package edu.whut.eval.application.iam.service;

import edu.whut.eval.application.iam.command.BindRolePermissionsCommand;
import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleCreatedView;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.domain.iam.repository.IamRoleCommandRepository;
import edu.whut.eval.domain.iam.repository.IamRoleQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleAdminApplicationService {

    private static final java.util.Set<String> ALLOWED_STATUS = java.util.Set.of("ACTIVE", "DISABLED");

    private final IamRoleQueryRepository iamRoleQueryRepository;
    private final IamRoleCommandRepository iamRoleCommandRepository;

    public RoleAdminApplicationService(IamRoleQueryRepository iamRoleQueryRepository,
                                       IamRoleCommandRepository iamRoleCommandRepository) {
        this.iamRoleQueryRepository = iamRoleQueryRepository;
        this.iamRoleCommandRepository = iamRoleCommandRepository;
    }

    @Transactional
    public RoleCreatedView createRole(CreateRoleCommand command) {
        String roleCode = normalize(command.roleCode());
        String roleName = normalize(command.roleName());
        if (roleCode == null) {
            throw new ValidationException("roleCode 不能为空");
        }
        if (roleName == null) {
            throw new ValidationException("roleName 不能为空");
        }
        iamRoleQueryRepository.findByRoleCode(roleCode).ifPresent(role -> {
            throw new ConflictException("角色编码已存在: " + roleCode);
        });

        IamRoleDefinition created = iamRoleCommandRepository.createRole(roleCode, roleName, "ORG_SUBTREE", "ACTIVE");
        return new RoleCreatedView(created.roleId(), created.roleCode(), created.roleName(), created.status());
    }

    @Transactional
    public RoleCreatedView updateRole(UpdateRoleCommand command) {
        String roleName = normalize(command.roleName());
        String status = normalize(command.status());
        if (roleName == null) {
            throw new ValidationException("roleName 不能为空");
        }
        if (status == null || !ALLOWED_STATUS.contains(status)) {
            throw new ValidationException("status 仅允许 ACTIVE 或 DISABLED");
        }
        IamRoleDefinition updated = iamRoleCommandRepository.updateRole(command.roleId(), roleName, status);
        return new RoleCreatedView(updated.roleId(), updated.roleCode(), updated.roleName(), updated.status());
    }

    @Transactional
    public void bindRolePermissions(BindRolePermissionsCommand command) {
        List<String> permissionCodes = command.permissionCodes();
        if (permissionCodes == null) {
            throw new ValidationException("permissionCodes 不能为空");
        }
        for (String permissionCode : permissionCodes) {
            if (normalize(permissionCode) == null) {
                throw new ValidationException("permissionCodes 不允许包含空值");
            }
        }
        iamRoleCommandRepository.replaceRolePermissions(command.roleId(), permissionCodes);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
