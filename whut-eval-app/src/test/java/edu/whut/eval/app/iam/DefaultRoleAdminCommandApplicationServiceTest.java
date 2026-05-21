package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleAdminView;
import edu.whut.eval.application.iam.service.DefaultRoleAdminCommandApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRole;
import edu.whut.eval.domain.iam.model.PermissionDictionaryEntry;
import edu.whut.eval.domain.iam.repository.PermissionDictionaryQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import edu.whut.eval.domain.iam.repository.RoleAdminQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultRoleAdminCommandApplicationServiceTest {

    @Mock
    private RoleAdminQueryRepository roleAdminQueryRepository;

    @Mock
    private RoleAdminCommandRepository roleAdminCommandRepository;

    @Mock
    private PermissionDictionaryQueryRepository permissionDictionaryQueryRepository;

    @InjectMocks
    private DefaultRoleAdminCommandApplicationService service;

    @Test
    void shouldCreateRoleWhenCommandIsValid() {
        given(roleAdminQueryRepository.findByRoleCode("COUNSELOR")).willReturn(Optional.empty());
        given(roleAdminCommandRepository.create("COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE"))
                .willReturn(new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00"));

        RoleAdminView result = service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE"));

        assertThat(result.roleId()).isEqualTo(21L);
        assertThat(result.roleCode()).isEqualTo("COUNSELOR");
        assertThat(result.roleScope()).isEqualTo("ORG_SUBTREE");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectCreateWhenRoleCodeExists() {
        given(roleAdminQueryRepository.findByRoleCode("COUNSELOR")).willReturn(Optional.of(
                new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00")
        ));

        assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("roleCode 已存在: COUNSELOR");
    }

    @Test
    void shouldRejectCreateWhenRoleScopeIsIllegal() {
        assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员", "CATEGORY", "ACTIVE")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("roleScope 仅允许 SELF、ORG_UNIT、ORG_SUBTREE 或 ALL");
    }

    @Test
    void shouldRejectUpdateWhenRoleDoesNotExist() {
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(21L, new UpdateRoleCommand("学院辅导员", "ORG_UNIT", "DISABLED")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色不存在: 21");
    }

    @Test
    void shouldRejectUpdateWhenRoleDoesNotChange() {
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.of(
                new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00")
        ));

        assertThatThrownBy(() -> service.updateRole(21L, new UpdateRoleCommand("辅导员", "ORG_SUBTREE", "ACTIVE")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("角色未发生变化");
    }

    @Test
    void shouldUpdateRoleWhenCommandIsValid() {
        IamRole existing = new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00");
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.of(existing));
        given(roleAdminCommandRepository.update(existing, "学院辅导员", "ORG_UNIT", "DISABLED")).willReturn(true);

        service.updateRole(21L, new UpdateRoleCommand("学院辅导员", "ORG_UNIT", "DISABLED"));

        verify(roleAdminCommandRepository).update(existing, "学院辅导员", "ORG_UNIT", "DISABLED");
    }

    @Test
    void shouldRejectUpdateWhenRoleWasChangedConcurrently() {
        IamRole existing = new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00");
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.of(existing));
        given(roleAdminCommandRepository.update(existing, "学院辅导员", "ORG_UNIT", "DISABLED")).willReturn(false);

        assertThatThrownBy(() -> service.updateRole(21L, new UpdateRoleCommand("学院辅导员", "ORG_UNIT", "DISABLED")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("角色已被其他人更新，请刷新后重试");
    }

    @Test
    void shouldRejectReplacePermissionsWhenRoleDoesNotExist() {
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceRolePermissions(21L,
                new ReplaceRolePermissionsCommand(java.util.List.of("permission.manage"), true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色不存在: 21");
    }

    @Test
    void shouldAllowReplacePermissionsWithEmptyCollection() {
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.of(
                new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00")
        ));

        service.replaceRolePermissions(21L,
                new ReplaceRolePermissionsCommand(java.util.List.of(), true));

        verify(roleAdminCommandRepository).replacePermissions(21L, java.util.List.of());
    }

    @Test
    void shouldRejectReplacePermissionsWhenPermissionCodesContainsBlank() {
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.of(
                new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00")
        ));

        assertThatThrownBy(() -> service.replaceRolePermissions(21L,
                new ReplaceRolePermissionsCommand(java.util.List.of("permission.manage", " "), true)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("permissionCodes 不能包含空值");
    }

    @Test
    void shouldRejectReplacePermissionsWhenPermissionCodeDoesNotExist() {
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.of(
                new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00")
        ));
        given(permissionDictionaryQueryRepository.findByCodes(java.util.Set.of("permission.manage", "role.manage"), "ACTIVE"))
                .willReturn(java.util.List.of(
                        new PermissionDictionaryEntry("permission.manage", "权限管理", "manage", "权限管理", "ACTIVE")
                ));

        assertThatThrownBy(() -> service.replaceRolePermissions(21L,
                new ReplaceRolePermissionsCommand(java.util.List.of("permission.manage", "role.manage"), true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("权限码不存在: role.manage");
    }

    @Test
    void shouldReplaceRolePermissionsWhenCommandIsValid() {
        IamRole existing = new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00");
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.of(existing));
        given(permissionDictionaryQueryRepository.findByCodes(java.util.Set.of("permission.manage", "role.manage"), "ACTIVE"))
                .willReturn(java.util.List.of(
                        new PermissionDictionaryEntry("permission.manage", "权限管理", "manage", "权限管理", "ACTIVE"),
                        new PermissionDictionaryEntry("role.manage", "角色管理", "manage", "角色管理", "ACTIVE")
                ));

        service.replaceRolePermissions(21L,
                new ReplaceRolePermissionsCommand(java.util.List.of("permission.manage", "role.manage"), true));

        verify(roleAdminCommandRepository).replacePermissions(21L, java.util.List.of("permission.manage", "role.manage"));
    }
}
