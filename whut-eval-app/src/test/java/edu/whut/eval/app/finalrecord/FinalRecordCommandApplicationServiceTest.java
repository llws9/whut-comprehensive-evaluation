package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.model.ScopeAccessDecision;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.command.ConfirmFinalRecordCommand;
import edu.whut.eval.application.finalrecord.command.SubmitFinalRecordCommand;
import edu.whut.eval.application.finalrecord.query.ConfirmFinalRecordResultView;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.application.finalrecord.service.FinalRecordAccessValidator;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.model.FinalRecord;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import edu.whut.eval.domain.finalrecord.repository.AggregatedFinalRecordSnapshot;
import edu.whut.eval.domain.finalrecord.repository.FinalRecordRepository;
import edu.whut.eval.domain.finalrecord.service.FinalSubmissionWindowPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FinalRecordCommandApplicationServiceTest {

    private final UserAuthorizationContextAssembler authorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final FinalRecordRepository repository = mock(FinalRecordRepository.class);
    private final FinalRecordQueryRepository queryRepository = mock(FinalRecordQueryRepository.class);
    private final FinalSubmissionWindowPolicy windowPolicy = mock(FinalSubmissionWindowPolicy.class);
    private final FinalRecordAccessValidator accessValidator = mock(FinalRecordAccessValidator.class);

    private final FinalRecordCommandApplicationService service = new FinalRecordCommandApplicationService(
            authorizationContextAssembler,
            repository,
            queryRepository,
            windowPolicy,
            accessValidator
    );

    @Test
    void shouldInvokeSubmitWindowPolicyBeforeMutation() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext(1001L));
        given(repository.findByStudentAndAcademicYear(1001L, "2025-2026")).willReturn(Optional.empty());
        given(repository.aggregateApprovedFacts(1001L, "2025-2026")).willReturn(snapshot());
        given(repository.insertDraft(any(FinalRecord.class))).willReturn(draftRecord(41001L));
        given(repository.updateTransition(any(FinalRecord.class))).willReturn(submittedRecord());

        FinalRecordStudentView result = service.submit(new SubmitFinalRecordCommand("2025-2026", 0L));

        assertThat(result.status()).isEqualTo(FinalRecordStatus.SUBMITTED);
        InOrder inOrder = inOrder(windowPolicy, repository);
        inOrder.verify(windowPolicy).assertSubmitAllowed(eq(1001L), eq("2025-2026"), any(Instant.class));
        inOrder.verify(repository).findByStudentAndAcademicYear(1001L, "2025-2026");
    }

    @Test
    void shouldRejectSubmitWhenAcademicYearOrExpectedVersionInvalid() {
        assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand(" ", 0L)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand("2025-2026", null)))
                .isInstanceOf(ValidationException.class);

        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext(1001L));
        given(repository.findByStudentAndAcademicYear(1001L, "2025-2026")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand("2025-2026", 1L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldCleanupStaleDraftBeforeRebuildingSubmission() {
        FinalRecord staleDraft = draftRecord(41000L);
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext(1001L));
        given(repository.findByStudentAndAcademicYear(1001L, "2025-2026")).willReturn(Optional.of(staleDraft));
        given(repository.aggregateApprovedFacts(1001L, "2025-2026")).willReturn(snapshot());
        given(repository.insertDraft(any(FinalRecord.class))).willReturn(draftRecord(41001L));
        given(repository.updateTransition(any(FinalRecord.class))).willReturn(submittedRecord());

        service.submit(new SubmitFinalRecordCommand("2025-2026", 0L));

        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).deleteComponents(41000L);
        inOrder.verify(repository).deleteDraft(41000L);
        inOrder.verify(repository).aggregateApprovedFacts(1001L, "2025-2026");
    }

    @Test
    void shouldRejectResubmitExistingSubmittedOrConfirmedRecord() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext(1001L));
        given(repository.findByStudentAndAcademicYear(1001L, "2025-2026"))
                .willReturn(Optional.of(submittedRecord()));

        assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand("2025-2026", 1L)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldConfirmSubmittedRecordAfterWholeRecordScopeCheck() {
        UserAuthorizationContext admin = adminContext();
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(admin);
        given(repository.findById(41001L)).willReturn(Optional.of(submittedRecord()));
        given(queryRepository.findAdminFinalRecordDetail(41001L)).willReturn(Optional.of(rowForStudentInScope()));
        given(repository.updateTransition(any(FinalRecord.class))).willAnswer(invocation -> invocation.getArgument(0));

        ConfirmFinalRecordResultView result = service.confirm(
                new ConfirmFinalRecordCommand(41001L, "辅导员已复核，无异议", 1L)
        );

        assertThat(result.status()).isEqualTo(FinalRecordStatus.CONFIRMED);
        assertThat(result.confirmComment()).isEqualTo("辅导员已复核，无异议");
        verify(accessValidator).requireAccess(eq(admin), any(FinalRecordQueryRow.class), eq("score.confirm.assigned"));
    }

    @Test
    void shouldReturnNotFoundWhenConfirmTargetDoesNotExist() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext());
        given(repository.findById(41001L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(new ConfirmFinalRecordCommand(41001L, null, 1L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldFailConfirmWhenProjectionIsIncomplete() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext());
        given(repository.findById(41001L)).willReturn(Optional.of(submittedRecord()));
        given(queryRepository.findAdminFinalRecordDetail(41001L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(new ConfirmFinalRecordCommand(41001L, null, 1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("projection");
    }

    @Test
    void shouldDeclareTransactionalBoundaries() throws Exception {
        Method submit = FinalRecordCommandApplicationService.class.getMethod("submit", SubmitFinalRecordCommand.class);
        Method confirm = FinalRecordCommandApplicationService.class.getMethod("confirm", ConfirmFinalRecordCommand.class);

        assertThat(submit.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(confirm.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private UserAuthorizationContext studentContext(long userId) {
        return new UserAuthorizationContext(userId, "S1001", "Student", "student", Set.of("student"),
                Set.of("final.submit.self", "final.view.self"), List.of());
    }

    private UserAuthorizationContext adminContext() {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("counselor"),
                Set.of("score.confirm.assigned"), List.of());
    }

    private AggregatedFinalRecordSnapshot snapshot() {
        return new AggregatedFinalRecordSnapshot(new BigDecimal("0.80"), new BigDecimal("2.00"),
                new BigDecimal("0.60"), new BigDecimal("1.20"), new BigDecimal("4.60"), List.of());
    }

    private FinalRecord draftRecord(Long id) {
        return FinalRecord.createDraft(id, 1001L, "2025-2026", new BigDecimal("0.80"), new BigDecimal("2.00"),
                new BigDecimal("0.60"), new BigDecimal("1.20"), new BigDecimal("4.60"), Instant.parse("2026-07-07T12:00:00Z"));
    }

    private FinalRecord submittedRecord() {
        return draftRecord(41001L).submit(0L);
    }

    private FinalRecordQueryRow rowForStudentInScope() {
        FinalRecordQueryRow row = new FinalRecordQueryRow();
        row.setFinalRecordId(41001L);
        row.setStudentUserId(1001L);
        row.setOrgUnitId(2010L);
        row.setOrgPath("/2001/2002/2010/");
        row.setAcademicYear("2025-2026");
        return row;
    }
}
