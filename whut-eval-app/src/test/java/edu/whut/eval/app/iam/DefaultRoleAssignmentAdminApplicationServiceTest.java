package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.iam.command.CreateRoleAssignmentCommand;
import edu.whut.eval.application.iam.command.UpdateRoleAssignmentCommand;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminPageQuery;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminView;
import edu.whut.eval.application.iam.service.DefaultRoleAssignmentAdminApplicationService;
import edu.whut.eval.application.iam.service.IamAdminAuditRecorder;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentPageItem;
import edu.whut.eval.domain.iam.model.IamRoleDefinition;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.query.RoleAssignmentPageQuery;
import edu.whut.eval.domain.iam.repository.IamRoleQueryRepository;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAssignmentAdminRepository;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultRoleAssignmentAdminApplicationServiceTest {

    @Mock
    private IamUserQueryRepository iamUserQueryRepository;

    @Mock
    private IamRoleQueryRepository iamRoleQueryRepository;

    @Mock
    private OrgUnitLookupRepository orgUnitLookupRepository;

    @Mock
    private RoleAssignmentAdminRepository roleAssignmentAdminRepository;

    @Mock
    private UserAuthorizationContextAssembler userAuthorizationContextAssembler;

    @Mock
    private IamAdminAuditRecorder iamAdminAuditRecorder;

    @InjectMocks
    private DefaultRoleAssignmentAdminApplicationService service;

    @Test
    void shouldCreateRoleAssignmentWhenCommandIsValid() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));
        given(iamRoleQueryRepository.findByRoleCode("COUNSELOR")).willReturn(Optional.of(
                new IamRoleDefinition(21L, "COUNSELOR", "辅导员", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(
                new UserAuthorizationContext(
                        9001L,
                        "A0001",
                        "系统管理员",
                        "ADMIN",
                        Set.of("SUPER_ADMIN"),
                        Set.of("assignment.manage"),
                        List.of()
                )
        );
        given(roleAssignmentAdminRepository.existsActiveAssignment(1010L, "COUNSELOR", 2002L, null)).willReturn(false);
        willReturn(new IamRoleAssignmentDetail(
                70021L,
                1010L,
                "COUNSELOR",
                "辅导员",
                2002L,
                "计算机与人工智能学院",
                "ACTIVE",
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                null
        )).given(roleAssignmentAdminRepository).create(
                anyLong(),
                anyString(),
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                anyString()
        );

        RoleAssignmentAdminView result = service.createAssignment(new CreateRoleAssignmentCommand(
                1010L,
                "COUNSELOR",
                2002L,
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL"
        ));

        assertThat(result.assignmentId()).isEqualTo(70021L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.roleName()).isEqualTo("辅导员");
    }

    @Test
    void shouldRejectCreateRoleAssignmentWhenOperatorContextMissing() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));
        given(iamRoleQueryRepository.findByRoleCode("COUNSELOR")).willReturn(Optional.of(
                new IamRoleDefinition(21L, "COUNSELOR", "辅导员", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(userAuthorizationContextAssembler.requiredAuthorizationContext())
                .willThrow(new edu.whut.eval.common.exception.AuthenticationFailedException());

        assertThatThrownBy(() -> service.createAssignment(new CreateRoleAssignmentCommand(
                1010L,
                "COUNSELOR",
                2002L,
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL"
        )))
                .isInstanceOf(edu.whut.eval.common.exception.AuthenticationFailedException.class);
    }

    @Test
    void shouldRejectCreateRoleAssignmentWhenDuplicateActiveAssignmentExists() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));
        given(iamRoleQueryRepository.findByRoleCode("COUNSELOR")).willReturn(Optional.of(
                new IamRoleDefinition(21L, "COUNSELOR", "辅导员", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(roleAssignmentAdminRepository.existsActiveAssignment(1010L, "COUNSELOR", 2002L, null)).willReturn(true);

        assertThatThrownBy(() -> service.createAssignment(new CreateRoleAssignmentCommand(
                1010L,
                "COUNSELOR",
                2002L,
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL"
        )))
                .isInstanceOf(ConflictException.class)
                .hasMessage("同一用户在该组织下已存在有效角色分配");
    }

    @Test
    void shouldRejectUpdateWhenStatusIsIllegal() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(
                new IamRoleAssignmentDetail(
                        70021L,
                        1010L,
                        "COUNSELOR",
                        "辅导员",
                        2002L,
                        "计算机与人工智能学院",
                        "ACTIVE",
                        "2026-05-18T00:00:00",
                        "2027-07-01T00:00:00",
                        "MANUAL",
                        null
                )
        ));

        assertThatThrownBy(() -> service.updateAssignment(70021L, new UpdateRoleAssignmentCommand(
                "EXPIRED",
                2009L,
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00"
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 ACTIVE 或 INACTIVE");
    }

    @Test
    void shouldRejectUpdateWhenAssignmentDoesNotExist() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAssignment(70021L, new UpdateRoleAssignmentCommand(
                "ACTIVE",
                2009L,
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00"
        )))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("角色分配不存在: 70021");
    }

    @Test
    void shouldAuditUpdateRoleAssignmentWithCurrentOperator() {
        IamRoleAssignmentDetail existing = new IamRoleAssignmentDetail(
                70021L,
                1010L,
                "COUNSELOR",
                "辅导员",
                2002L,
                "计算机与人工智能学院",
                "ACTIVE",
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                null
        );
        IamRoleAssignmentDetail updated = new IamRoleAssignmentDetail(
                70021L,
                1010L,
                "COUNSELOR",
                "辅导员",
                2009L,
                "计科 2201",
                "INACTIVE",
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                "2026-05-20T10:20:30"
        );
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(existing));
        given(orgUnitLookupRepository.findById(2009L)).willReturn(Optional.of(
                new OrgUnit(2009L, 2002L, "CLASS", "CS2201", "计科 2201", "/1/2002/2009/", "ACTIVE")
        ));
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(
                new UserAuthorizationContext(9001L, "A0001", "系统管理员", "ADMIN", Set.of("SUPER_ADMIN"), Set.of("assignment.manage"), List.of())
        );
        given(roleAssignmentAdminRepository.update(
                70021L,
                1010L,
                "COUNSELOR",
                "辅导员",
                2009L,
                "计科 2201",
                "INACTIVE",
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL"
        )).willReturn(updated);

        RoleAssignmentAdminView result = service.updateAssignment(70021L, new UpdateRoleAssignmentCommand(
                "INACTIVE",
                2009L,
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00"
        ));

        assertThat(result.orgUnitId()).isEqualTo(2009L);
        verify(iamAdminAuditRecorder).recordRoleAssignmentUpdated(9001L, existing, updated);
    }

    @Test
    void shouldReturnEmptyPageWhenNoRoleAssignmentsMatchFilters() {
        given(iamUserQueryRepository.findById(1010L)).willReturn(Optional.of(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE")
        ));
        given(orgUnitLookupRepository.findById(2002L)).willReturn(Optional.of(
                new OrgUnit(2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE")
        ));
        given(roleAssignmentAdminRepository.pageAssignments(eq(new RoleAssignmentPageQuery(
                1,
                20,
                1010L,
                "COUNSELOR",
                "ACTIVE",
                2002L
        )))).willReturn(new PageResult<>(0, List.of()));

        PageResult<RoleAssignmentAdminPageItemView> result = service.pageAssignments(new RoleAssignmentAdminPageQuery(
                1,
                20,
                1010L,
                "COUNSELOR",
                "ACTIVE",
                2002L
        ));

        assertThat(result.total()).isZero();
        assertThat(result.records()).isEmpty();
    }

    @Test
    void shouldMapRoleAssignmentPageItemsFromRepository() {
        given(roleAssignmentAdminRepository.pageAssignments(eq(new RoleAssignmentPageQuery(
                1,
                20,
                null,
                "COUNSELOR",
                null,
                null
        )))).willReturn(new PageResult<>(
                1,
                List.of(new IamRoleAssignmentPageItem(
                        70021L,
                        1010L,
                        "2024305001",
                        "王老师",
                        "COUNSELOR",
                        "辅导员",
                        2002L,
                        "计算机与人工智能学院",
                        "ACTIVE",
                        "2026-05-18T00:00:00",
                        "2027-07-01T00:00:00"
                ))
        ));

        PageResult<RoleAssignmentAdminPageItemView> result = service.pageAssignments(new RoleAssignmentAdminPageQuery(
                1,
                20,
                null,
                "COUNSELOR",
                null,
                null
        ));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement()
                .satisfies(item -> {
                    assertThat(item.userNo()).isEqualTo("2024305001");
                    assertThat(item.userName()).isEqualTo("王老师");
                    assertThat(item.roleCode()).isEqualTo("COUNSELOR");
                });
    }

    @Test
    void shouldRevokeActiveAssignmentToInactive() {
        IamRoleAssignmentDetail existing = new IamRoleAssignmentDetail(
                70021L,
                1010L,
                "COUNSELOR",
                "辅导员",
                2002L,
                "计算机与人工智能学院",
                "ACTIVE",
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                null
        );
        IamRoleAssignmentDetail revoked = new IamRoleAssignmentDetail(
                70021L,
                1010L,
                "COUNSELOR",
                "辅导员",
                2002L,
                "计算机与人工智能学院",
                "INACTIVE",
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                "2026-05-20T11:00:00"
        );
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(existing));
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(
                new UserAuthorizationContext(9001L, "A0001", "系统管理员", "ADMIN", Set.of("SUPER_ADMIN"), Set.of("assignment.manage"), List.of())
        );
        given(roleAssignmentAdminRepository.revoke(70021L)).willReturn(revoked);

        service.revokeAssignment(70021L);

        verify(roleAssignmentAdminRepository).revoke(70021L);
        verify(roleAssignmentAdminRepository, never()).update(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(iamAdminAuditRecorder).recordRoleAssignmentUpdated(9001L, existing, revoked);
    }

    @Test
    void shouldRejectRevokeWhenAssignmentHasExpiredTimeWindow() {
        given(roleAssignmentAdminRepository.findDetailById(70021L)).willReturn(Optional.of(
                new IamRoleAssignmentDetail(
                        70021L,
                        1010L,
                        "COUNSELOR",
                        "辅导员",
                        2002L,
                        "计算机与人工智能学院",
                        "ACTIVE",
                        "2024-05-18T00:00:00",
                        "2024-07-01T00:00:00",
                        "MANUAL",
                        null
                )
        ));
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(
                new UserAuthorizationContext(9001L, "A0001", "系统管理员", "ADMIN", Set.of("SUPER_ADMIN"), Set.of("assignment.manage"), List.of())
        );

        assertThatThrownBy(() -> service.revokeAssignment(70021L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("仅 ACTIVE 状态的角色分配允许撤销");
    }
}
