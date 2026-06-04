package edu.whut.eval.app.infra;

import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.mapper.IamPermissionLookupMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAdminCommandRepository;
import edu.whut.eval.infra.persistence.repository.row.PermissionIdCodeRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
    void shouldCreateRoleWithActiveStatus() {
        given(iamRoleMapper.insert(any(IamRoleDO.class))).willAnswer(invocation -> {
            IamRoleDO role = invocation.getArgument(0);
            role.setId(31L);
            return 1;
        });

        var view = repository.createRole("ACADEMIC_SECRETARY", "教学秘书", "ORG_UNIT");

        assertThat(view.roleId()).isEqualTo(31L);
        assertThat(view.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldReplacePermissionsByCode() {
        given(iamPermissionLookupMapper.selectIdsByCodes(List.of("role.manage", "permission.manage")))
                .willReturn(List.of(new PermissionIdCodeRow(5012L, "role.manage"), new PermissionIdCodeRow(5010L, "permission.manage")));

        repository.replacePermissions(31L, List.of("role.manage", "permission.manage"));

        then(iamRolePermissionMapper).should().deleteByRoleId(31L);
        ArgumentCaptor<IamRolePermissionDO> captor = ArgumentCaptor.forClass(IamRolePermissionDO.class);
        then(iamRolePermissionMapper).should(org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(IamRolePermissionDO::getPermissionId)
                .containsExactly(5012L, 5010L);
    }
}
