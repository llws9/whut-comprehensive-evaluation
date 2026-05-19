package edu.whut.eval.app.infra;

import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamRoleQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MybatisPlusIamRoleQueryRepositoryTest {

    @Mock
    private IamRoleMapper iamRoleMapper;

    @InjectMocks
    private MybatisPlusIamRoleQueryRepository repository;

    @Test
    void shouldFindRoleDefinitionByRoleCode() {
        IamRoleDO roleDO = new IamRoleDO();
        roleDO.setId(21L);
        roleDO.setRoleCode("COUNSELOR");
        roleDO.setRoleName("辅导员");
        roleDO.setStatus("ACTIVE");
        given(iamRoleMapper.selectOne(any())).willReturn(roleDO);

        Optional<IamRoleDefinition> result = repository.findByRoleCode("COUNSELOR");

        assertThat(result).isPresent();
        assertThat(result.get().roleId()).isEqualTo(21L);
        assertThat(result.get().roleName()).isEqualTo("辅导员");
    }
}
