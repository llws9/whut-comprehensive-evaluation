package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.query.UserIdentityMembershipView;
import edu.whut.eval.application.iam.query.UserIdentityView;
import edu.whut.eval.application.iam.service.UserIdentityQueryApplicationService;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAssignmentQueryRepository;
import edu.whut.eval.domain.org.model.OrgMembership;
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
class UserIdentityQueryApplicationServiceTest {

    @Mock
    private IamUserQueryRepository iamUserQueryRepository;

    @Mock
    private RoleAssignmentQueryRepository roleAssignmentQueryRepository;

    @Mock
    private OrgQueryRepository orgQueryRepository;

    @InjectMocks
    private UserIdentityQueryApplicationService service;

    @Test
    void shouldMapMembershipsToDedicatedIdentityView() {
        given(iamUserQueryRepository.findByUserNo("2024305001")).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));
        given(roleAssignmentQueryRepository.findActiveAssignmentsByUserId(1010L)).willReturn(List.of(
                new IamRoleAssignment(70021L, 21L, "COUNSELOR", "辅导员", 2002L, "ACTIVE")
        ));
        given(orgQueryRepository.findMembershipsByUserId(1010L)).willReturn(List.of(
                new OrgMembership(80001L, 1010L, 2002L, "IMPORT", true, "ACTIVE", "2024-01-01T00:00:00", null)
        ));

        UserIdentityView result = service.getUserIdentityByUserNo("2024305001");

        assertThat(result.memberships()).singleElement().isInstanceOf(UserIdentityMembershipView.class)
                .satisfies(item -> {
                    UserIdentityMembershipView membership = (UserIdentityMembershipView) item;
                    assertThat(membership.id()).isEqualTo(80001L);
                    assertThat(membership.userId()).isEqualTo(1010L);
                    assertThat(membership.orgUnitId()).isEqualTo(2002L);
                    assertThat(membership.membershipType()).isEqualTo("IMPORT");
                    assertThat(membership.status()).isEqualTo("ACTIVE");
                });
        assertThat(result.memberships().get(0).getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("id", "userId", "orgUnitId", "membershipType", "status");
    }
}
