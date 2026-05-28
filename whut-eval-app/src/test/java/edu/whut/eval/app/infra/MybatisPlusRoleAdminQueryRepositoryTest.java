package edu.whut.eval.app.infra;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.whut.eval.domain.iam.query.RoleAdminPageQuery;
import edu.whut.eval.infra.persistence.entity.IamRoleDO;
import edu.whut.eval.infra.persistence.entity.IamRolePermissionDO;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAdminQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MybatisPlusRoleAdminQueryRepositoryTest {

    @Mock
    private IamRoleMapper iamRoleMapper;

    @Mock
    private IamRolePermissionMapper iamRolePermissionMapper;

    @InjectMocks
    private MybatisPlusRoleAdminQueryRepository repository;

    @Test
    void shouldCountPermissionsPerRole() {
        IamRoleDO role = new IamRoleDO();
        role.setId(21L);
        role.setRoleCode("COUNSELOR");
        role.setRoleName("辅导员");
        role.setRoleScope("ORG_SUBTREE");
        role.setStatus("ACTIVE");
        role.setCreatedAt(LocalDateTime.parse("2026-05-20T10:00:00"));
        Page<IamRoleDO> page = Page.of(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(role));
        given(iamRoleMapper.selectPage(any(Page.class), any())).willReturn(page);
        given(iamRolePermissionMapper.selectList(any())).willReturn(List.of(
                permission(1L, 21L, 5010L),
                permission(2L, 21L, 5011L)
        ));

        var result = repository.pageRoles(new RoleAdminPageQuery(1, 10, null, "ACTIVE"));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(item -> {
            assertThat(item.roleCode()).isEqualTo("COUNSELOR");
            assertThat(item.roleScope()).isEqualTo("ORG_SUBTREE");
            assertThat(item.permissionCount()).isEqualTo(2);
        });
    }

    private IamRolePermissionDO permission(Long id, Long roleId, Long permissionId) {
        IamRolePermissionDO item = new IamRolePermissionDO();
        item.setId(id);
        item.setRoleId(roleId);
        item.setPermissionId(permissionId);
        return item;
    }
}
