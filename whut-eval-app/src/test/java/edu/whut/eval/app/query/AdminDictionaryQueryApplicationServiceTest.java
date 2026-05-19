package edu.whut.eval.app.query;

import edu.whut.eval.application.admin.query.OrgUnitTreeView;
import edu.whut.eval.application.admin.query.PermissionDictionaryView;
import edu.whut.eval.application.admin.service.AdminDictionaryQueryApplicationService;
import edu.whut.eval.domain.iam.model.PermissionDictionaryEntry;
import edu.whut.eval.domain.iam.repository.PermissionDictionaryQueryRepository;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminDictionaryQueryApplicationServiceTest {

    @Mock
    private PermissionDictionaryQueryRepository permissionDictionaryQueryRepository;

    @Mock
    private OrgQueryRepository orgQueryRepository;

    @InjectMocks
    private AdminDictionaryQueryApplicationService service;

    @Test
    void shouldDefaultPermissionStatusToActive() {
        given(permissionDictionaryQueryRepository.findPermissions("review", "manage", "ACTIVE"))
                .willReturn(List.of(new PermissionDictionaryEntry(
                        "permission.manage",
                        "权限管理",
                        "manage",
                        "权限管理",
                        "ACTIVE"
                )));

        List<PermissionDictionaryView> result = service.listPermissions("review", "manage", null);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.permissionCode()).isEqualTo("permission.manage");
            assertThat(item.description()).isEqualTo("权限管理");
            assertThat(item.status()).isEqualTo("ACTIVE");
        });
    }

    @Test
    void shouldBuildOrgTreeFromRepositoryResultsAndRetainAncestorsForUnitTypeFilter() {
        given(orgQueryRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(orgQueryRepository.findDescendants(2002L, false)).willReturn(List.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE"),
                new OrgUnit(2008L, 2002L, "MAJOR", "CS-SE", "软件工程", "/1/2002/2008/", "ACTIVE"),
                new OrgUnit(2009L, 2008L, "CLASS", "CS2201", "计科2201", "/1/2002/2008/2009/", "ACTIVE")
        ));

        List<OrgUnitTreeView> result = service.listOrgUnitTree(2002L, "CLASS", false);

        assertThat(result).singleElement().satisfies(root -> {
            assertThat(root.id()).isEqualTo(2002L);
            assertThat(root.children()).singleElement().satisfies(major -> {
                assertThat(major.unitType()).isEqualTo("MAJOR");
                assertThat(major.children()).singleElement().extracting(OrgUnitTreeView::id).isEqualTo(2009L);
            });
        });
    }
}
