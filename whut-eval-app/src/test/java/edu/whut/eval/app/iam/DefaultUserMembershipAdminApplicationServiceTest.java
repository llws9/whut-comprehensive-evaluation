package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.command.ReplaceUserMembershipItemCommand;
import edu.whut.eval.application.iam.command.ReplaceUserMembershipsCommand;
import edu.whut.eval.application.iam.query.UserMembershipAdminView;
import edu.whut.eval.application.iam.service.DefaultUserMembershipAdminApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultUserMembershipAdminApplicationServiceTest {

    @Mock
    private IamUserQueryRepository iamUserQueryRepository;

    @Mock
    private OrgUnitLookupRepository orgUnitLookupRepository;

    @Mock
    private UserMembershipAdminRepository userMembershipAdminRepository;

    @InjectMocks
    private DefaultUserMembershipAdminApplicationService service;

    @Test
    void shouldListMembershipsForExistingUser() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));
        given(userMembershipAdminRepository.findActiveMembershipsByUserId(1010L)).willReturn(List.of(
                new OrgMembership(70021L, 1010L, 2002L, "IMPORT", true, "ACTIVE", "2024-01-01T00:00:00", null),
                new OrgMembership(70022L, 1010L, 2009L, "MANUAL", false, "ACTIVE", "2024-02-01T00:00:00", null)
        ));
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findById(2009L)).willReturn(Optional.of(
                new OrgUnit(2009L, 2002L, "CLASS", "CS2201", "计科2201", "/1/2002/2009/", "ACTIVE")
        ));

        List<UserMembershipAdminView> result = service.listMemberships(1010L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).membershipId()).isEqualTo(70021L);
        assertThat(result.get(0).orgUnitName()).isEqualTo("计算机与人工智能学院");
        assertThat(result.get(0).isPrimary()).isTrue();
        assertThat(result.get(1).orgUnitType()).isEqualTo("CLASS");
    }

    @Test
    void shouldRejectReplaceWhenUserDoesNotExist() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceMemberships(1010L, new ReplaceUserMembershipsCommand(List.of())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("用户不存在: 1010");
    }

    @Test
    void shouldRejectReplaceWhenOrgUnitRepeated() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));

        assertThatThrownBy(() -> service.replaceMemberships(1010L, new ReplaceUserMembershipsCommand(List.of(
                new ReplaceUserMembershipItemCommand(2002L, true),
                new ReplaceUserMembershipItemCommand(2002L, false)
        ))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("orgUnitId 不允许重复");
    }

    @Test
    void shouldRejectReplaceWhenMoreThanOnePrimaryExists() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));

        assertThatThrownBy(() -> service.replaceMemberships(1010L, new ReplaceUserMembershipsCommand(List.of(
                new ReplaceUserMembershipItemCommand(2002L, true),
                new ReplaceUserMembershipItemCommand(2009L, true)
        ))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("最多只能有一个主组织");
    }

    @Test
    void shouldRejectReplaceWhenOrgUnitDoesNotExist() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.replaceMemberships(1010L, new ReplaceUserMembershipsCommand(List.of(
                new ReplaceUserMembershipItemCommand(2002L, true)
        ))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("组织不存在: 2002");
    }

    @Test
    void shouldPreserveActiveMembershipTypeAndDeactivateRemovedMemberships() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findById(2010L)).willReturn(Optional.of(
                new OrgUnit(2010L, 2002L, "MAJOR", "SE", "软件工程", "/1/2002/2010/", "ACTIVE")
        ));
        given(userMembershipAdminRepository.findActiveMembershipsByUserId(1010L)).willReturn(List.of(
                new OrgMembership(70021L, 1010L, 2002L, "IMPORT", true, "ACTIVE", "2024-01-01T00:00:00", null),
                new OrgMembership(70022L, 1010L, 2009L, "SYNC", false, "ACTIVE", "2024-02-01T00:00:00", null)
        ));

        service.replaceMemberships(1010L, new ReplaceUserMembershipsCommand(List.of(
                new ReplaceUserMembershipItemCommand(2002L, false),
                new ReplaceUserMembershipItemCommand(2010L, true)
        )));

        ArgumentCaptor<List<OrgMembership>> activeCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<OrgMembership>> inactiveCaptor = ArgumentCaptor.forClass(List.class);
        verify(userMembershipAdminRepository).replaceMemberships(eq(1010L), activeCaptor.capture(), inactiveCaptor.capture());

        assertThat(activeCaptor.getValue()).hasSize(2);
        assertThat(activeCaptor.getValue()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(70021L);
            assertThat(item.orgUnitId()).isEqualTo(2002L);
            assertThat(item.membershipType()).isEqualTo("IMPORT");
            assertThat(item.isPrimary()).isFalse();
            assertThat(item.joinedAt()).isEqualTo("2024-01-01T00:00:00");
        });
        assertThat(activeCaptor.getValue()).anySatisfy(item -> {
            assertThat(item.id()).isNull();
            assertThat(item.orgUnitId()).isEqualTo(2010L);
            assertThat(item.membershipType()).isEqualTo("MANUAL");
            assertThat(item.isPrimary()).isTrue();
            assertThat(item.status()).isEqualTo("ACTIVE");
            assertThat(item.joinedAt()).isNotBlank();
        });
        assertThat(inactiveCaptor.getValue()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(70022L);
            assertThat(item.membershipType()).isEqualTo("SYNC");
            assertThat(item.status()).isEqualTo("INACTIVE");
            assertThat(item.leftAt()).isNotBlank();
        });
    }
}
