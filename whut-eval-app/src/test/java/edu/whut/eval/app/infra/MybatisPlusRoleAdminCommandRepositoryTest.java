package edu.whut.eval.app.infra;

import edu.whut.eval.domain.iam.model.IamRoleDetail;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.mapper.IamPermissionLookupMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAdminCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MybatisPlusRoleAdminCommandRepositoryTest {

    @Mock
    private IamRoleMapper iamRoleMapper;

    @Mock
    private IamRolePermissionMapper iamRolePermissionMapper;

    @Mock
    private IamPermissionLookupMapper iamPermissionLookupMapper;

    @InjectMocks
    private MybatisPlusRoleAdminCommandRepository repository;

    @Test
    void shouldCreateRole() {
        given(iamRoleMapper.insert(any(IamRoleDO.class))).willAnswer(invocation -> {
            IamRoleDO item = invocation.getArgument(0);
            item.setId(31L);
            return 1;
        });

        IamRoleDetail created = repository.create("COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE");

        assertThat(created.id()).isEqualTo(31L);
        assertThat(created.roleCode()).isEqualTo("COUNSELOR");
        assertThat(created.roleScope()).isEqualTo("ORG_SUBTREE");
        verify(iamRoleMapper).insert(any(IamRoleDO.class));
    }

    @Test
    void shouldUpdateRoleWithSnapshot() {
        given(iamRoleMapper.update(any(IamRoleDO.class), any())).willReturn(1);

        boolean updated = repository.updateWithSnapshot(
                21L,
                "辅导员(新)",
                "ORG_SUBTREE",
                "ACTIVE",
                "辅导员",
                "ORG_SUBTREE",
                "ACTIVE"
        );

        assertThat(updated).isTrue();
        verify(iamRoleMapper).update(any(IamRoleDO.class), any());
    }

    @Test
    void shouldReturn404WhenReplacingPermissionsWithUnknownPermissionCode() {
        given(iamPermissionLookupMapper.selectIdsByCodes(List.of("missing.permission")))
                .willReturn(List.of());

        assertThatThrownBy(() -> repository.replacePermissions(21L, List.of("missing.permission")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("权限码不存在: missing.permission");

        verify(iamRolePermissionMapper, never()).delete(any());
    }

    @Test
    void shouldReplacePermissionsWithResolvedPermissionIds() {
        given(iamPermissionLookupMapper.selectIdsByCodes(List.of("user.manage", "role.manage")))
                .willReturn(List.of(
                        new edu.whut.eval.infra.persistence.repository.row.PermissionIdCodeRow(5014L, "user.manage"),
                        new edu.whut.eval.infra.persistence.repository.row.PermissionIdCodeRow(5016L, "role.manage")
                ));

        repository.replacePermissions(21L, List.of("user.manage", "role.manage"));

        verify(iamRolePermissionMapper).delete(any());
        verify(iamRolePermissionMapper, times(2)).insert(any(IamRolePermissionDO.class));
    }

    @Test
    void shouldClearPermissionsWhenReplacingWithEmptyPermissionCodes() {
        repository.replacePermissions(21L, List.of());

        verify(iamRolePermissionMapper).delete(any());
        verify(iamPermissionLookupMapper, never()).selectIdsByCodes(any());
        verify(iamRolePermissionMapper, never()).insert(any(IamRolePermissionDO.class));
    }
}
