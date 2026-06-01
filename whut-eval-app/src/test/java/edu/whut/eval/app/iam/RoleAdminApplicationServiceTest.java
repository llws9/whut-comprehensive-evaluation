package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleCreatedView;
import edu.whut.eval.application.iam.service.RoleAdminApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.domain.iam.repository.IamRoleCommandRepository;
import edu.whut.eval.domain.iam.repository.IamRoleQueryRepository;
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
class RoleAdminApplicationServiceTest {

    @Mock
    private IamRoleQueryRepository iamRoleQueryRepository;

    @Mock
    private IamRoleCommandRepository iamRoleCommandRepository;

    @InjectMocks
    private RoleAdminApplicationService service;

    @Test
    void shouldCreateRoleWhenRoleCodeNotExists() {
        given(iamRoleQueryRepository.findByRoleCode("COUNSELOR_NEW")).willReturn(Optional.empty());
        given(iamRoleCommandRepository.createRole("COUNSELOR_NEW", "新辅导员", "ORG_SUBTREE", "ACTIVE"))
                .willReturn(new IamRoleDefinition(31L, "COUNSELOR_NEW", "新辅导员", "ACTIVE"));

        RoleCreatedView view = service.createRole(new CreateRoleCommand("COUNSELOR_NEW", "新辅导员"));

        assertThat(view.roleId()).isEqualTo(31L);
        assertThat(view.roleCode()).isEqualTo("COUNSELOR_NEW");
        assertThat(view.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectCreateRoleWhenRoleCodeAlreadyExists() {
        given(iamRoleQueryRepository.findByRoleCode("COUNSELOR"))
                .willReturn(Optional.of(new IamRoleDefinition(21L, "COUNSELOR", "辅导员", "ACTIVE")));

        assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("COUNSELOR", "辅导员")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("角色编码已存在: COUNSELOR");
    }

    @Test
    void shouldRejectCreateRoleWhenRoleCodeBlank() {
        assertThatThrownBy(() -> service.createRole(new CreateRoleCommand("   ", "辅导员")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("roleCode 不能为空");
    }

    @Test
    void shouldRejectUpdateRoleWhenStatusIllegal() {
        assertThatThrownBy(() -> service.updateRole(new UpdateRoleCommand(21L, "辅导员", "LOCKED")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 ACTIVE 或 DISABLED");
    }

    @Test
    void shouldUpdateRoleWhenInputValid() {
        given(iamRoleCommandRepository.updateRole(21L, "新辅导员", "DISABLED"))
                .willReturn(new IamRoleDefinition(21L, "COUNSELOR", "新辅导员", "DISABLED"));

        RoleCreatedView view = service.updateRole(new UpdateRoleCommand(21L, "新辅导员", "DISABLED"));

        assertThat(view.roleId()).isEqualTo(21L);
        assertThat(view.roleCode()).isEqualTo("COUNSELOR");
        assertThat(view.roleName()).isEqualTo("新辅导员");
        assertThat(view.status()).isEqualTo("DISABLED");
        verify(iamRoleCommandRepository).updateRole(21L, "新辅导员", "DISABLED");
    }
}
