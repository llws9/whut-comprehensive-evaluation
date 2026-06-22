package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.service.DefaultRoleAdminApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleDetail;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoleAdminApplicationServiceTest {

    @Mock
    private RoleAdminCommandRepository roleAdminCommandRepository;

    @InjectMocks
    private DefaultRoleAdminApplicationService service;

    @Test
    void shouldRejectIllegalRoleScopeOnCreate() {
        assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员", "ALL")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("roleScope 仅允许 ORG_SUBTREE");
    }

    @Test
    void shouldRejectDuplicateRoleCodeOnCreate() {
        given(roleAdminCommandRepository.findByRoleCode("COUNSELOR"))
                .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "旧辅导员", "ORG_SUBTREE", "ACTIVE")));

        assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员", "ORG_SUBTREE")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("角色编码已存在: COUNSELOR");
    }

    @Test
    void shouldReturn404WhenUpdateRoleNotFound() {
        given(roleAdminCommandRepository.findById(21L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(21L, updateCommand()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色不存在: 21");
    }

    @Test
    void shouldRejectUpdateWhenSnapshotConflict() {
        given(roleAdminCommandRepository.findById(21L))
                .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "辅导员-已变更", "ORG_SUBTREE", "ACTIVE")));

        assertThatThrownBy(() -> service.updateRole(21L, updateCommand()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("角色模板已被更新，请刷新后重试");

        verify(roleAdminCommandRepository, never()).updateWithSnapshot(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void shouldRejectIllegalStatusOnUpdate() {
        given(roleAdminCommandRepository.findById(21L))
                .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE")));

        assertThatThrownBy(() -> service.updateRole(21L,
                new UpdateRoleCommand("辅导员(新)", "ORG_SUBTREE", "LOCKED", "辅导员", "ORG_SUBTREE", "ACTIVE")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 ACTIVE 或 DISABLED");
    }

    @Test
    void shouldUpdateRoleWhenSnapshotMatches() {
        given(roleAdminCommandRepository.findById(21L))
                .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE")));
        given(roleAdminCommandRepository.updateWithSnapshot(
                eq(21L),
                eq("辅导员(新)"),
                eq("ORG_SUBTREE"),
                eq("ACTIVE"),
                eq("辅导员"),
                eq("ORG_SUBTREE"),
                eq("ACTIVE")
        )).willReturn(true);

        service.updateRole(21L, updateCommand());

        verify(roleAdminCommandRepository)
                .updateWithSnapshot(21L, "辅导员(新)", "ORG_SUBTREE", "ACTIVE", "辅导员", "ORG_SUBTREE", "ACTIVE");
    }

    @Test
    void shouldReturnConflictWhenAtomicUpdateNotMatched() {
        given(roleAdminCommandRepository.findById(21L))
                .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE")));
        given(roleAdminCommandRepository.updateWithSnapshot(
                any(), any(), any(), any(), any(), any(), any()
        )).willReturn(false);

        assertThatThrownBy(() -> service.updateRole(21L, updateCommand()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("角色模板已被更新，请刷新后重试");
    }

    @Test
    void shouldRejectReplacePermissionsWhenReplaceAllIsFalse() {
        assertThatThrownBy(() -> service.replacePermissions(21L,
                new ReplaceRolePermissionsCommand(List.of("user.manage"), false)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("当前仅支持 replaceAll=true 的整集合替换");

        verify(roleAdminCommandRepository, never()).replacePermissions(any(), any());
    }

    @Test
    void shouldReplacePermissionsWhenReplaceAllIsTrue() {
        given(roleAdminCommandRepository.findById(21L))
                .willReturn(Optional.of(new IamRoleDetail(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE")));

        service.replacePermissions(21L,
                new ReplaceRolePermissionsCommand(List.of(" user.manage ", "user.manage", "role.manage"), true));

        verify(roleAdminCommandRepository).replacePermissions(21L, List.of("user.manage", "role.manage"));
    }

    private UpdateRoleCommand updateCommand() {
        return new UpdateRoleCommand(
                "辅导员(新)",
                "ORG_SUBTREE",
                "ACTIVE",
                "辅导员",
                "ORG_SUBTREE",
                "ACTIVE"
        );
    }
}
