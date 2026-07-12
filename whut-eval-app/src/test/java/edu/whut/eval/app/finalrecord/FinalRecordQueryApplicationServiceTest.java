package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordDetailView;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordListItemView;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreListView;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentRow;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentView;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.application.finalrecord.service.FinalRecordAccessValidator;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.finalrecord.query.FinalRecordAccessContext;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FinalRecordQueryApplicationServiceTest {

    private final UserAuthorizationContextAssembler authorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final FinalRecordQueryRepository queryRepository = mock(FinalRecordQueryRepository.class);
    private final FinalRecordAccessValidator accessValidator = mock(FinalRecordAccessValidator.class);
    private final FinalRecordQueryApplicationService service = new FinalRecordQueryApplicationService(
            authorizationContextAssembler,
            queryRepository,
            accessValidator
    );

    @Test
    void shouldReturnStudentFinalRecord() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext());
        given(queryRepository.findStudentFinalRecord(1001L, "2025-2026")).willReturn(Optional.of(row()));

        FinalRecordStudentView view = service.getStudentFinalRecord("2025-2026");

        assertThat(view.finalRecordId()).isEqualTo(41001L);
        assertThat(view.confirmedAt()).isNull();
    }

    @Test
    void shouldReturnNotFoundForAbsentOrDraftStudentRecord() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext());
        given(queryRepository.findStudentFinalRecord(1001L, "2025-2026")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStudentFinalRecord("2025-2026"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldReturnComponentsOnlyWhenHeaderExists() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext());
        given(queryRepository.findStudentFinalRecord(1001L, "2025-2026")).willReturn(Optional.of(row()));
        given(queryRepository.listStudentFinalRecordComponents(41001L)).willReturn(List.of());

        FinalComponentScoreListView view = service.listStudentComponents("2025-2026");

        assertThat(view.components()).isEmpty();
    }

    @Test
    void shouldPageAdminRecords() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext());
        given(queryRepository.pageAdminFinalRecords(any(), any()))
                .willReturn(new PageResult<>(1L, List.of(row())));

        PageResult<AdminFinalRecordListItemView> page = service.pageAdminFinalRecords(
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).extracting(AdminFinalRecordListItemView::finalRecordId).containsExactly(41001L);
    }

    @Test
    void shouldPageUnsubmittedStudentsWithFixedStatusAndStringFallbacks() {
        UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(admin);
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
        given(queryRepository.pageUnsubmittedStudents(any(), same(query)))
                .willReturn(new PageResult<>(2, List.of(
                        unsubmittedRow(1001L, "S001", "Alice", "2022级", "CS2201", Instant.parse("2026-07-12T10:15:30.123Z")),
                        unsubmittedRow(1002L, "S002", "Bob", null, "CS2202", null)
                )));

        PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).containsExactly(
                new UnsubmittedStudentView(1001L, "S001", "Alice", "2022级", "CS2201", "UNSUBMITTED", "2026-07-12T10:15:30.123Z"),
                new UnsubmittedStudentView(1002L, "S002", "Bob", "", "CS2202", "UNSUBMITTED", "")
        );
        ArgumentCaptor<FinalRecordAccessContext> captor = ArgumentCaptor.forClass(FinalRecordAccessContext.class);
        verify(queryRepository).pageUnsubmittedStudents(captor.capture(), same(query));
        assertThat(captor.getValue().getPermissionCode()).isEqualTo(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
    }

    @Test
    void shouldRenderMissingDraftLastUpdatedAtAsEmptyString() {
        UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(admin);
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
        given(queryRepository.pageUnsubmittedStudents(any(), same(query)))
                .willReturn(new PageResult<>(1, List.of(
                        unsubmittedRow(1001L, "S001", "Alice", "2022级", "CS2201", null)
                )));

        PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

        assertThat(page.records()).containsExactly(
                new UnsubmittedStudentView(1001L, "S001", "Alice", "2022级", "CS2201", "UNSUBMITTED", "")
        );
    }

    @Test
    void shouldRenderNullStringProjectionsAsEmptyStrings() {
        UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(admin);
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
        given(queryRepository.pageUnsubmittedStudents(any(), same(query)))
                .willReturn(new PageResult<>(1, List.of(
                        unsubmittedRow(1001L, null, null, "2022级", null, null)
                )));

        PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

        assertThat(page.records()).containsExactly(
                new UnsubmittedStudentView(1001L, "", "", "2022级", "", "UNSUBMITTED", "")
        );
    }

    @Test
    void shouldRenderLastUpdatedAtWithInstantStringPrecision() {
        UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(admin);
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
        given(queryRepository.pageUnsubmittedStudents(any(), same(query)))
                .willReturn(new PageResult<>(2, List.of(
                        unsubmittedRow(1001L, "S001", "Alice", "2022级", "CS2201", Instant.parse("2026-07-12T10:15:30Z")),
                        unsubmittedRow(1002L, "S002", "Bob", "2022级", "CS2201", Instant.parse("2026-07-12T10:15:30.456Z"))
                )));

        PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

        assertThat(page.records()).containsExactly(
                new UnsubmittedStudentView(1001L, "S001", "Alice", "2022级", "CS2201", "UNSUBMITTED", "2026-07-12T10:15:30Z"),
                new UnsubmittedStudentView(1002L, "S002", "Bob", "2022级", "CS2201", "UNSUBMITTED", "2026-07-12T10:15:30.456Z")
        );
    }

    @Test
    void shouldDenyUnsubmittedListWithoutScoreViewAssigned() {
        UserAuthorizationContext admin = adminWithAuthority("other.permission");
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(admin);

        assertThatThrownBy(() -> service.pageUnsubmittedStudents(
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无未提交最终成绩名单查询权限");
        verify(queryRepository, never()).pageUnsubmittedStudents(any(), any());
    }

    @Test
    void shouldReturnEmptyUnsubmittedPage() {
        UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(admin);
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
        given(queryRepository.pageUnsubmittedStudents(any(), same(query)))
                .willReturn(new PageResult<>(0, List.of()));

        PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

        assertThat(page.total()).isZero();
        assertThat(page.records()).isEmpty();
    }

    @Test
    void shouldReturnAdminDetailAfterAccessValidation() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext());
        given(queryRepository.findAdminFinalRecordDetail(41001L)).willReturn(Optional.of(row()));
        given(queryRepository.listAdminFinalRecordComponents(41001L)).willReturn(List.of());

        AdminFinalRecordDetailView detail = service.getAdminFinalRecordDetail(41001L);

        assertThat(detail.record().finalRecordId()).isEqualTo(41001L);
        assertThat(detail.components()).isEmpty();
    }

    @Test
    void shouldDenyOutOfScopeAdminDetail() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext());
        given(queryRepository.findAdminFinalRecordDetail(41001L)).willReturn(Optional.of(row()));
        willThrow(new AccessDeniedAppException("当前用户无权访问该最终成绩"))
                .given(accessValidator).requireAccess(any(), any(), any());

        assertThatThrownBy(() -> service.getAdminFinalRecordDetail(41001L))
                .isInstanceOf(AccessDeniedAppException.class);
    }

    private UserAuthorizationContext studentContext() {
        return new UserAuthorizationContext(1001L, "S1001", "Student", "student", Set.of("student"),
                Set.of(AuthorizationPermissionCodes.FINAL_VIEW_SELF), List.of());
    }

    private UserAuthorizationContext adminContext() {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("counselor"),
                Set.of(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED), List.of());
    }

    private UserAuthorizationContext adminWithAuthority(String authority) {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("counselor"),
                Set.of(authority), List.of());
    }

    private UnsubmittedStudentRow unsubmittedRow(Long studentUserId, String userNo, String userName,
                                                 String grade, String className, Instant lastUpdatedAt) {
        UnsubmittedStudentRow row = new UnsubmittedStudentRow();
        row.setStudentUserId(studentUserId);
        row.setUserNo(userNo);
        row.setUserName(userName);
        row.setGrade(grade);
        row.setClassName(className);
        row.setLastUpdatedAt(lastUpdatedAt);
        return row;
    }

    private FinalRecordQueryRow row() {
        FinalRecordQueryRow row = new FinalRecordQueryRow();
        row.setFinalRecordId(41001L);
        row.setStudentUserId(1001L);
        row.setStudentUserNo("S1001");
        row.setStudentUserName("Student");
        row.setOrgUnitId(2010L);
        row.setOrgUnitName("计科一班");
        row.setOrgPath("/2001/2002/2010/");
        row.setAcademicYear("2025-2026");
        row.setStatus("SUBMITTED");
        row.setMoralTotal(new BigDecimal("0.80"));
        row.setIntellectualTotal(new BigDecimal("2.00"));
        row.setPhysicalTotal(new BigDecimal("0.60"));
        row.setLaborTotal(new BigDecimal("1.20"));
        row.setGrandTotal(new BigDecimal("4.60"));
        row.setSubmittedAt(Instant.parse("2026-07-07T12:00:00Z"));
        row.setVersion(1L);
        return row;
    }
}
