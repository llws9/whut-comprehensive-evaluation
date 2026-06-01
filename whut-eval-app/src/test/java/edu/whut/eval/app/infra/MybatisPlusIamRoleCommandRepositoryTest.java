package edu.whut.eval.app.infra;

import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamRoleCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MybatisPlusIamRoleCommandRepositoryTest {

    @Mock
    private IamRoleMapper iamRoleMapper;

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
}
