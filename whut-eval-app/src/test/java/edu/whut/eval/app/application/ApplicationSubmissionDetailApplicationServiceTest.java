package edu.whut.eval.app.application;

import edu.whut.eval.application.application.query.ApplicationSubmissionDetailView;
import edu.whut.eval.application.application.service.ApplicationSubmissionDetailApplicationService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.application.model.ApplicationScoringSnapshot;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ApplicationSubmissionDetailApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ApplicationSubmissionRepository applicationSubmissionRepository = mock(ApplicationSubmissionRepository.class);
    private final ApplicationSubmissionDetailApplicationService service =
            new ApplicationSubmissionDetailApplicationService(userAuthorizationContextAssembler, applicationSubmissionRepository);

    @Test
    void shouldReturnOwnedApplicationDetailWithoutStorageFields() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(submittedWithSnapshot()));

        ApplicationSubmissionDetailView detail = service.getOwnedDetail(1L);

        assertThat(detail.getApplicationId()).isEqualTo(1L);
        assertThat(detail.getOptionCode()).isEqualTo("OPTION_A");
        assertThat(detail.getAppliedPoints()).isEqualByComparingTo("2.00");
        assertThat(detail.getEvidenceCount()).isEqualTo(1);
        assertThat(detail.getAttachments()).hasSize(1);
        assertThat(detail.getAttachments().get(0).getFileId()).isEqualTo("file-1");
    }

    @Test
    void shouldRejectDetailForAnotherUser() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
        given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(otherUsersDraft()));

        assertThatThrownBy(() -> service.getOwnedDetail(1L))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无权查看该申请");
    }

    private UserAuthorizationContext currentUser() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.self"),
                List.of()
        );
    }

    private ApplicationSubmission submittedWithSnapshot() {
        return application(
                1001L,
                ApplicationSubmissionStatus.SUBMITTED,
                Instant.parse("2026-07-06T10:00:00Z"),
                1L,
                List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 128L, 1001L)),
                new ApplicationScoringSnapshot("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null)
        );
    }

    private ApplicationSubmission otherUsersDraft() {
        return application(2002L, ApplicationSubmissionStatus.DRAFT, null, 0L, List.of(), null);
    }

    private ApplicationSubmission application(Long applicantUserId,
                                              ApplicationSubmissionStatus status,
                                              Instant submittedAt,
                                              Long version,
                                              List<AttachmentRef> attachments,
                                              ApplicationScoringSnapshot scoringSnapshot) {
        return new ApplicationSubmission(
                1L,
                applicantUserId,
                2010L,
                "INTELLECTUAL",
                "INTELLECTUAL_PAPER",
                "2025-2026",
                "上学期",
                "申请标题",
                "申请说明",
                attachments,
                status,
                submittedAt,
                Instant.parse("2026-07-06T09:00:00Z"),
                Instant.parse("2026-07-06T09:00:00Z"),
                version,
                scoringSnapshot
        );
    }
}
