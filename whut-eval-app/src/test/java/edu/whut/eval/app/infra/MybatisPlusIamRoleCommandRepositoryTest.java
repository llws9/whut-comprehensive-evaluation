package edu.whut.eval.app.infra;

import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.infra.persistence.entity.IamPermissionDO;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.mapper.IamPermissionMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamRoleCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MybatisPlusIamRoleCommandRepositoryTest {

    @Mock
    private IamRoleMapper iamRoleMapper;

    @Mock
    private IamRolePermissionMapper iamRolePermissionMapper;

    @Mock
    private IamPermissionMapper iamPermissionMapper;

    @InjectMocks
    private MybatisPlusIamRoleCommandRepository repository;

    @Test
    void shouldCreateRole() {
        given(iamRoleMapper.insert(any(IamRoleDO.class))).willAnswer(invocation -> {
            IamRoleDO entity = invocation.getArgument(0);
            entity.setId(31L);
            return 1;
        });

        IamRoleDefinition result = repository.createRole("COUNSELOR_NEW", "新辅导员", "ORG_SUBTREE", "ACTIVE");

        assertThat(result.roleId()).isEqualTo(31L);
        assertThat(result.roleCode()).isEqualTo("COUNSELOR_NEW");
        assertThat(result.roleName()).isEqualTo("新辅导员");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldUpdateRole() {
        IamRoleDO updated = new IamRoleDO();
        updated.setId(21L);
        updated.setRoleCode("COUNSELOR");
        updated.setRoleName("新辅导员");
        updated.setStatus("DISABLED");
        given(iamRoleMapper.selectById(21L)).willReturn(updated);

        IamRoleDefinition result = repository.updateRole(21L, "新辅导员", "DISABLED");

        assertThat(result.roleId()).isEqualTo(21L);
        assertThat(result.roleCode()).isEqualTo("COUNSELOR");
        assertThat(result.roleName()).isEqualTo("新辅导员");
        assertThat(result.status()).isEqualTo("DISABLED");
    }

    @Test
    void shouldReplaceRolePermissions() {
        IamRoleDO role = new IamRoleDO();
        role.setId(21L);
        given(iamRoleMapper.selectById(21L)).willReturn(role);

        IamPermissionDO permission = new IamPermissionDO();
        permission.setId(5010L);
        permission.setPermissionCode("permission.manage");
        given(iamPermissionMapper.selectOne(any())).willReturn(permission);

        repository.replaceRolePermissions(21L, java.util.List.of("permission.manage"));

        verify(iamRolePermissionMapper).delete(any());
        verify(iamRolePermissionMapper).insert(any(IamRolePermissionDO.class));
    }
}
