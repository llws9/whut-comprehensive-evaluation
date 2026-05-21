package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.query.RoleAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAdminPageQuery;
import edu.whut.eval.application.iam.service.DefaultRoleAdminQueryApplicationService;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleAdminPageItem;
import edu.whut.eval.domain.iam.repository.RoleAdminQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DefaultRoleAdminQueryApplicationServiceTest {

    @Mock
    private RoleAdminQueryRepository roleAdminQueryRepository;

    @InjectMocks
    private DefaultRoleAdminQueryApplicationService service;

    @Test
    void shouldPageRoles() {
        given(roleAdminQueryRepository.pageRoles(argThat(query -> query != null
                && query.pageNo() == 1
                && query.pageSize() == 10
                && "辅导".equals(query.keyword())
                && "ACTIVE".equals(query.status()))))
                .willReturn(new PageResult<>(1, List.of(
                        new IamRoleAdminPageItem(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", 6, "2026-05-20T10:00:00")
                )));

        PageResult<RoleAdminPageItemView> result = service.pageRoles(new RoleAdminPageQuery(1, 10, "辅导", "ACTIVE"));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(item -> {
            assertThat(item.roleCode()).isEqualTo("COUNSELOR");
            assertThat(item.roleScope()).isEqualTo("ORG_SUBTREE");
            assertThat(item.permissionCount()).isEqualTo(6);
        });
    }

    @Test
    void shouldRejectIllegalStatus() {
        assertThatThrownBy(() -> service.pageRoles(new RoleAdminPageQuery(1, 10, null, "LOCKED")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 ACTIVE 或 DISABLED");
    }
}
