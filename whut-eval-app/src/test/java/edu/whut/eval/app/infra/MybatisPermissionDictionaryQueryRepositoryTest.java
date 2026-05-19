package edu.whut.eval.app.infra;

import edu.whut.eval.domain.iam.model.PermissionDictionaryEntry;
import edu.whut.eval.infra.persistence.mapper.AdminPermissionDictionaryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPermissionDictionaryQueryRepository;
import edu.whut.eval.infra.persistence.repository.row.PermissionDictionaryRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MybatisPermissionDictionaryQueryRepositoryTest {

    @Mock
    private AdminPermissionDictionaryMapper adminPermissionDictionaryMapper;

    @InjectMocks
    private MybatisPermissionDictionaryQueryRepository repository;

    @Test
    void shouldPassKeywordModuleAndStatusToMapper() {
        given(adminPermissionDictionaryMapper.selectPermissions("review", "manage", "ACTIVE"))
                .willReturn(List.of(new PermissionDictionaryRow(
                        "permission.manage",
                        "权限管理",
                        "manage",
                        null,
                        "ACTIVE"
                )));

        List<PermissionDictionaryEntry> result = repository.findPermissions("review", "manage", "ACTIVE");

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.permissionCode()).isEqualTo("permission.manage");
            assertThat(item.module()).isEqualTo("manage");
            assertThat(item.description()).isNull();
            assertThat(item.status()).isEqualTo("ACTIVE");
        });
    }
}
