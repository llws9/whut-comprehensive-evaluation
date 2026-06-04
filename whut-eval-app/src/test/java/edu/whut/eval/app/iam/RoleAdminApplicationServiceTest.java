package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.domain.iam.model.IamRoleAdminPageItem;
import edu.whut.eval.application.iam.query.RoleAdminView;
import edu.whut.eval.application.iam.service.RoleAdminApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;

class RoleAdminApplicationServiceTest {

    @Test
    void shouldCreateRoleTemplate() {
        RoleAdminCommandRepository repository = mock(RoleAdminCommandRepository.class);
        RoleAdminApplicationService service = new RoleAdminApplicationService(repository);
        given(repository.findByRoleCode("ACADEMIC_SECRETARY")).willReturn(Optional.empty());
        given(repository.createRole("ACADEMIC_SECRETARY", "教学秘书", "ORG_UNIT"))
                .willReturn(new IamRoleAdminPageItem(31L, "ACADEMIC_SECRETARY", "教学秘书", "ORG_UNIT", "ACTIVE", 0, "2026-06-04T10:00:00"));

        RoleAdminView view = service.createRole(new CreateRoleCommand("ACADEMIC_SECRETARY", "教学秘书", "ORG_UNIT"));

        assertThat(view.roleId()).isEqualTo(31L);
        assertThat(view.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectDuplicatedRoleCodeWhenCreateRole() {
        RoleAdminCommandRepository repository = mock(RoleAdminCommandRepository.class);
        RoleAdminApplicationService service = new RoleAdminApplicationService(repository);
        given(repository.findByRoleCode("COUNSELOR")).willReturn(Optional.of(
                new IamRoleAdminPageItem(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", 6, "2026-05-20T10:00:00")));

        assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员", "ORG_SUBTREE")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("角色编码已存在: COUNSELOR");
    }

    @Test
    void shouldUpdateRoleTemplateWithFreshSnapshot() {
        RoleAdminCommandRepository repository = mock(RoleAdminCommandRepository.class);
        RoleAdminApplicationService service = new RoleAdminApplicationService(repository);
        given(repository.updateRoleIfSnapshotMatches(31L, "教学秘书负责人", "ORG_SUBTREE", "ACTIVE", "教学秘书", "ORG_UNIT", "ACTIVE"))
                .willReturn(true);

        service.updateRole(31L, new UpdateRoleCommand("教学秘书负责人", "ORG_SUBTREE", "ACTIVE", "教学秘书", "ORG_UNIT", "ACTIVE"));

        then(repository).should().updateRoleIfSnapshotMatches(31L, "教学秘书负责人", "ORG_SUBTREE", "ACTIVE", "教学秘书", "ORG_UNIT", "ACTIVE");
    }

    @Test
    void shouldRejectStaleSnapshotWhenUpdateRole() {
        RoleAdminCommandRepository repository = mock(RoleAdminCommandRepository.class);
        RoleAdminApplicationService service = new RoleAdminApplicationService(repository);
        given(repository.existsById(31L)).willReturn(true);
        given(repository.updateRoleIfSnapshotMatches(31L, "教学秘书负责人", "ORG_SUBTREE", "ACTIVE", "教学秘书", "ORG_UNIT", "ACTIVE"))
                .willReturn(false);

        assertThatThrownBy(() -> service.updateRole(31L, new UpdateRoleCommand("教学秘书负责人", "ORG_SUBTREE", "ACTIVE", "教学秘书", "ORG_UNIT", "ACTIVE")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("角色模板已被他人更新，请重新拉取最新快照");
    }

    @Test
    void shouldReplacePermissionsWithEmptySet() {
        RoleAdminCommandRepository repository = mock(RoleAdminCommandRepository.class);
        RoleAdminApplicationService service = new RoleAdminApplicationService(repository);
        given(repository.existsById(31L)).willReturn(true);

        service.replacePermissions(31L, new ReplaceRolePermissionsCommand(List.of(), true));

        then(repository).should().replacePermissions(31L, List.of());
    }

    @Test
    void shouldRejectNullPermissionCodesWhenReplacePermissions() {
        RoleAdminCommandRepository repository = mock(RoleAdminCommandRepository.class);
        RoleAdminApplicationService service = new RoleAdminApplicationService(repository);

        assertThatThrownBy(() -> service.replacePermissions(31L, new ReplaceRolePermissionsCommand(null, true)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("permissionCodes 不能为空");
    }

    @Test
    void shouldRejectMissingRoleWhenReplacePermissions() {
        RoleAdminCommandRepository repository = mock(RoleAdminCommandRepository.class);
        RoleAdminApplicationService service = new RoleAdminApplicationService(repository);
        given(repository.existsById(999L)).willReturn(false);

        assertThatThrownBy(() -> service.replacePermissions(999L, new ReplaceRolePermissionsCommand(List.of("role.manage"), true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色不存在: 999");
    }
}
