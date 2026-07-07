package edu.whut.eval.app.review;

import edu.whut.eval.application.application.command.ApproveReviewCommand;
import edu.whut.eval.application.application.command.ReturnReviewCommand;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.application.service.ReviewApplicationAccessValidator;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.model.ApplicationScoringSnapshot;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ReviewApplicationCommandApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ReviewApplicationQueryRepository reviewApplicationQueryRepository = mock(ReviewApplicationQueryRepository.class);
    private final ReviewApplicationAccessValidator reviewApplicationAccessValidator = mock(ReviewApplicationAccessValidator.class);
    private final ApplicationSubmissionRepository applicationSubmissionRepository = mock(ApplicationSubmissionRepository.class);
    private final ApplicationReviewLogRepository applicationReviewLogRepository = mock(ApplicationReviewLogRepository.class);

    private final ReviewApplicationCommandApplicationService service = new ReviewApplicationCommandApplicationService(
            userAuthorizationContextAssembler,
            reviewApplicationQueryRepository,
            reviewApplicationAccessValidator,
            applicationSubmissionRepository,
            applicationReviewLogRepository
    );

    @Test
    void shouldApproveSubmittedApplicationAppendLogAndReturnGeneratedLogId() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(any(), any())).willReturn(Optional.of(resourceRow()));
        given(applicationSubmissionRepository.findById(21013L)).willReturn(Optional.of(submittedApplication()));
        given(applicationSubmissionRepository.save(any(ApplicationSubmission.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(applicationReviewLogRepository.append(any(ApplicationReviewLog.class))).willAnswer(invocation -> {
            ApplicationReviewLog log = invocation.getArgument(0);
            return new ApplicationReviewLog(31001L, log.getApplicationId(), log.getAction(), log.getReviewerId(),
                    log.getReviewRole(), log.getReason(), log.getReviewedAt());
        });

        ReviewActionResultView result = service.approve(new ApproveReviewCommand(21013L, 1L, "同意"));

        assertThat(result.applicationId()).isEqualTo(21013L);
        assertThat(result.status()).isEqualTo(ApplicationSubmissionStatus.APPROVED);
        assertThat(result.version()).isEqualTo(2L);
        assertThat(result.reviewLogId()).isEqualTo(31001L);
        ArgumentCaptor<ApplicationSubmission> submissionCaptor = forClass(ApplicationSubmission.class);
        verify(applicationSubmissionRepository).save(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getScoringSnapshot()).isNotNull();
        assertThat(submissionCaptor.getValue().getScoringSnapshot().optionCode()).isEqualTo("OPTION_A");
        ArgumentCaptor<ApplicationReviewLog> logCaptor = forClass(ApplicationReviewLog.class);
        verify(applicationReviewLogRepository).append(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo(ApplicationReviewAction.APPROVE);
        assertThat(logCaptor.getValue().getReason()).isEqualTo("同意");
    }

    @Test
    void shouldRejectBlankReasonForReturn() {
        assertThatThrownBy(() -> service.returnForFix(new ReturnReviewCommand(21013L, 1L, " ")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("reason 不能为空");
    }

    @Test
    void shouldRejectOutOfScopeApplication() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(any(), any())).willReturn(Optional.of(resourceRow()));
        willThrow(new AccessDeniedAppException("当前审核人无权访问该申请"))
                .given(reviewApplicationAccessValidator).requireAccess(any(), any());

        assertThatThrownBy(() -> service.approve(new ApproveReviewCommand(21013L, 1L, null)))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前审核人无权访问该申请");
        verifyNoInteractions(applicationSubmissionRepository, applicationReviewLogRepository);
    }

    @Test
    void shouldDeclareTransactionalBoundaryOnReviewActions() throws Exception {
        Method approve = ReviewApplicationCommandApplicationService.class.getMethod("approve", ApproveReviewCommand.class);
        Method returnForFix = ReviewApplicationCommandApplicationService.class.getMethod("returnForFix", ReturnReviewCommand.class);

        assertThat(approve.isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(returnForFix.isAnnotationPresent(Transactional.class)).isTrue();
    }

    private UserAuthorizationContext reviewer() {
        return new UserAuthorizationContext(
                1010L,
                "A0010",
                "Counselor",
                "COUNSELOR",
                Set.of("COUNSELOR"),
                Set.of("application.review"),
                List.of()
        );
    }

    private ReviewApplicationQueryRow resourceRow() {
        ReviewApplicationQueryRow row = new ReviewApplicationQueryRow();
        row.setApplicationId(21013L);
        row.setApplicantUserId(1001L);
        row.setOrgUnitId(2010L);
        row.setOrgPath("/WHUT/CS/CLASS1");
        row.setCategoryCode("INTELLECTUAL");
        row.setItemCode("INTELLECTUAL_PAPER");
        row.setStatus("SUBMITTED");
        return row;
    }

    private ApplicationSubmission submittedApplication() {
        return new ApplicationSubmission(
                21013L,
                1001L,
                2010L,
                "INTELLECTUAL",
                "INTELLECTUAL_PAPER",
                "2025-2026",
                "上学期",
                "论文申请",
                "申请说明",
                List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 128L, 1001L)),
                ApplicationSubmissionStatus.SUBMITTED,
                Instant.parse("2026-07-07T10:00:00Z"),
                Instant.parse("2026-07-07T09:00:00Z"),
                Instant.parse("2026-07-07T10:00:00Z"),
                1L,
                new ApplicationScoringSnapshot("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null)
        );
    }
}
