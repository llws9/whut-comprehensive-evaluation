package edu.whut.eval.app.infra;

import edu.whut.eval.domain.iam.model.IamRoleDetail;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAdminCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MybatisPlusRoleAdminCommandRepositoryTest {

    @Mock
    private IamRoleMapper iamRoleMapper;

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
}
