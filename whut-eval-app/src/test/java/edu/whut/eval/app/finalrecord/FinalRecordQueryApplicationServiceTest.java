package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordDetailView;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordListItemView;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreListView;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.application.finalrecord.service.FinalRecordAccessValidator;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

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
