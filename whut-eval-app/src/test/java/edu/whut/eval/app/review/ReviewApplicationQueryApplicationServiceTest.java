package edu.whut.eval.app.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.application.query.ApplicationAttachmentView;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationListItemView;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.query.ReviewMetaGradesView;
import edu.whut.eval.application.application.query.ReviewOrgUnitOptionView;
import edu.whut.eval.application.application.query.ReviewTaskSummaryCounts;
import edu.whut.eval.application.application.query.ReviewTaskSummaryView;
import edu.whut.eval.application.application.query.ReviewLogView;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.application.service.ReviewApplicationAccessValidator;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.file.query.FileAccessUrlResponse;
import edu.whut.eval.application.file.query.FileMetadataResponse;
import edu.whut.eval.application.file.service.FileQueryApplicationService;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.shared.PageResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ReviewApplicationQueryApplicationServiceTest {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final ReviewApplicationQueryRepository reviewApplicationQueryRepository = mock(ReviewApplicationQueryRepository.class);
    private final ReviewApplicationAccessValidator reviewApplicationAccessValidator = mock(ReviewApplicationAccessValidator.class);
    private final ApplicationReviewLogRepository applicationReviewLogRepository = mock(ApplicationReviewLogRepository.class);
    private final FileQueryApplicationService fileQueryApplicationService = mock(FileQueryApplicationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReviewApplicationQueryApplicationService service = new ReviewApplicationQueryApplicationService(
            userAuthorizationContextAssembler,
            reviewApplicationQueryRepository,
            reviewApplicationAccessValidator,
            applicationReviewLogRepository,
            fileQueryApplicationService
    );

    @Test
    void shouldReturnDedicatedReviewListView() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.pageReviewApplications(any(), any()))
                .willReturn(new PageResult<>(1, List.of(submittedRow())));

        PageResult<ReviewApplicationListItemView> result = service.pageReviewApplications(
                new ReviewApplicationPageQuery(1, 20, "2025-2026", "INTELLECTUAL", "INTELLECTUAL_PAPER", "SUBMITTED", "论文", 2010L)
        );

        assertThat(result.total()).isEqualTo(1);
        ReviewApplicationListItemView item = result.records().get(0);
        assertThat(item.applicationId()).isEqualTo(21013L);
        assertThat(item.applicantUserName()).isEqualTo("张三");
        assertThat(item.orgUnitName()).isEqualTo("计算机学院 1 班");
        assertThat(item.currentReviewNode()).isEqualTo("SINGLE_REVIEW");
    }

    @Test
    void shouldReturnReviewTaskSummaryUsingReviewTaskPermissionAndApplicationReviewScope() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(taskViewer());
        given(reviewApplicationQueryRepository.countReviewTaskSummary(any(), any(), any()))
                .willReturn(new ReviewTaskSummaryCounts(5, 2, 1, 1));

        ReviewTaskSummaryView result = service.getReviewTaskSummary();

        assertThat(result.pendingCount()).isEqualTo(5);
        assertThat(result.approvedToday()).isEqualTo(2);
        assertThat(result.returnedToday()).isEqualTo(1);
        assertThat(result.rejectedToday()).isEqualTo(1);
        assertThat(result.processedToday()).isEqualTo(4);
        org.mockito.ArgumentCaptor<ApplicationAccessContext> contextCaptor =
                org.mockito.ArgumentCaptor.forClass(ApplicationAccessContext.class);
        verify(reviewApplicationQueryRepository).countReviewTaskSummary(contextCaptor.capture(), any(), any());
        assertThat(contextCaptor.getValue().getPermissionCode()).isEqualTo(AuthorizationPermissionCodes.APPLICATION_REVIEW);
    }

    @Test
    void shouldDenyReviewTaskSummaryWithoutReviewTaskViewPermission() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());

        assertThatThrownBy(service::getReviewTaskSummary)
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无工作台查看权限");
        verifyNoInteractions(reviewApplicationQueryRepository, reviewApplicationAccessValidator, applicationReviewLogRepository);
    }

    @Test
    void shouldReturnReviewGradeMetadataUsingReviewTaskPermissionAndApplicationReviewScope() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(taskViewer());
        given(reviewApplicationQueryRepository.findReviewGradeMetadata(any()))
                .willReturn(new ReviewMetaGradesView(
                        List.of("2024-2025", "2025-2026"),
                        List.of(new ReviewOrgUnitOptionView(2010L, "计算机 2101 班", "CLASS")),
                        2010L
                ));

        ReviewMetaGradesView result = service.getReviewGradeMetadata();

        assertThat(result.gradeList()).containsExactly("2024-2025", "2025-2026");
        assertThat(result.orgUnitList()).containsExactly(new ReviewOrgUnitOptionView(2010L, "计算机 2101 班", "CLASS"));
        assertThat(result.defaultOrgUnitId()).isEqualTo(2010L);
        org.mockito.ArgumentCaptor<ApplicationAccessContext> contextCaptor =
                org.mockito.ArgumentCaptor.forClass(ApplicationAccessContext.class);
        verify(reviewApplicationQueryRepository).findReviewGradeMetadata(contextCaptor.capture());
        assertThat(contextCaptor.getValue().getPermissionCode()).isEqualTo(AuthorizationPermissionCodes.APPLICATION_REVIEW);
    }

    @Test
    void shouldDenyReviewGradeMetadataWithoutReviewTaskViewPermission() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());

        assertThatThrownBy(service::getReviewGradeMetadata)
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无元数据查询权限");
        verifyNoInteractions(reviewApplicationQueryRepository, reviewApplicationAccessValidator, applicationReviewLogRepository);
    }

    @Test
    void shouldReturnDetailWithC4TopLevelShapeLogsAndAllowedActions() throws Exception {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(21013L)).willReturn(Optional.of(submittedRow()));
        given(applicationReviewLogRepository.listByApplicationId(21013L)).willReturn(List.of(new ApplicationReviewLog(
                31000L,
                21013L,
                ApplicationReviewAction.RETURN,
                1010L,
                "COUNSELOR",
                "补充材料",
                Instant.parse("2026-07-07T11:00:00Z")
        )));

        ReviewApplicationDetailView result = service.getReviewDetail(21013L);

        assertThat(result.application().applicationId()).isEqualTo(21013L);
        assertThat(result.applicant().userName()).isEqualTo("张三");
        assertThat(result.attachments()).hasSize(1);
        assertThat(result.attachments().get(0).getFileId()).isEqualTo("file-1");
        assertThat(objectMapper.writeValueAsString(result.attachments().get(0))).doesNotContain("storageKey");
        assertThat(result.reviewLogs()).hasSize(1);
        assertThat(result.reviewLogs().get(0).action()).isEqualTo("RETURN");
        assertThat(result.allowedActions()).containsExactly("APPROVE", "RETURN", "REJECT");
        verify(reviewApplicationAccessValidator).requireAccess(any(), any());
    }

    @Test
    void shouldListReviewAttachmentsAfterAccessValidation() throws Exception {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(21013L)).willReturn(Optional.of(submittedRow()));

        List<ApplicationAttachmentView> result = service.listReviewAttachments(21013L);

        assertThat(result).hasSize(1);
        ApplicationAttachmentView attachment = result.get(0);
        assertThat(attachment.getFileId()).isEqualTo("file-1");
        assertThat(attachment.getOriginalFilename()).isEqualTo("a.pdf");
        assertThat(attachment.getContentType()).isEqualTo("application/pdf");
        assertThat(attachment.getSize()).isEqualTo(128L);
        assertThat(attachment.getSortNo()).isZero();
        assertThat(attachment.getSourceType()).isEqualTo("SELF_UPLOAD");
        assertThat(attachment.getAccessUrl()).isEqualTo("https://cdn.example.com/private/uploads/a.pdf");
        assertThat(attachment.getAccessMode()).isEqualTo("PUBLIC_URL");
        assertThat(attachment.getExpiresAt()).isNull();
        assertThat(objectMapper.writeValueAsString(attachment)).doesNotContain("storageKey");
        verify(reviewApplicationAccessValidator).requireAccess(any(), any());
        verify(fileQueryApplicationService).getMetadata("file-1");
        verify(fileQueryApplicationService).getAccessUrl("file-1");
        verifyNoInteractions(applicationReviewLogRepository);
    }

    @Test
    void shouldListReviewLogsAfterAccessValidation() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(21013L)).willReturn(Optional.of(submittedRow()));
        given(applicationReviewLogRepository.listByApplicationId(21013L)).willReturn(List.of(new ApplicationReviewLog(
                31000L,
                21013L,
                ApplicationReviewAction.RETURN,
                1010L,
                "COUNSELOR",
                "补充材料",
                Instant.parse("2026-07-07T11:00:00Z")
        )));

        List<ReviewLogView> result = service.listReviewLogs(21013L);

        assertThat(result).hasSize(1);
        ReviewLogView log = result.get(0);
        assertThat(log.reviewLogId()).isEqualTo(31000L);
        assertThat(log.action()).isEqualTo("RETURN");
        assertThat(log.reviewerId()).isEqualTo(1010L);
        assertThat(log.reviewRole()).isEqualTo("COUNSELOR");
        assertThat(log.reason()).isEqualTo("补充材料");
        assertThat(log.reviewedAt()).isEqualTo(Instant.parse("2026-07-07T11:00:00Z"));
        verify(reviewApplicationAccessValidator).requireAccess(any(), any());
    }

    @Test
    void shouldReturnEmptyAllowedActionsForApprovedDetail() {
        ReviewApplicationQueryRow row = submittedRow();
        row.setStatus("APPROVED");
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(21013L)).willReturn(Optional.of(row));
        given(applicationReviewLogRepository.listByApplicationId(21013L)).willReturn(List.of());

        ReviewApplicationDetailView result = service.getReviewDetail(21013L);

        assertThat(result.application().status()).isEqualTo("APPROVED");
        assertThat(result.allowedActions()).isEmpty();
    }

    @Test
    void shouldDenyExistingOutOfScopeDetailInsteadOfHidingItAsNotFound() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(21013L)).willReturn(Optional.of(submittedRow()));
        willThrow(new AccessDeniedAppException("当前审核人无权访问该申请"))
                .given(reviewApplicationAccessValidator).requireAccess(any(), any());

        assertThatThrownBy(() -> service.getReviewDetail(21013L))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前审核人无权访问该申请");
        verifyNoInteractions(applicationReviewLogRepository);
    }

    @Test
    void shouldDenyExistingOutOfScopeReviewAttachmentsInsteadOfHidingThemAsNotFound() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(21013L)).willReturn(Optional.of(submittedRow()));
        willThrow(new AccessDeniedAppException("当前审核人无权访问该申请"))
                .given(reviewApplicationAccessValidator).requireAccess(any(), any());

        assertThatThrownBy(() -> service.listReviewAttachments(21013L))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前审核人无权访问该申请");
        verifyNoInteractions(applicationReviewLogRepository);
    }

    @Test
    void shouldDenyExistingOutOfScopeReviewLogsInsteadOfHidingThemAsNotFound() {
        given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(reviewer());
        given(reviewApplicationQueryRepository.findReviewApplicationDetail(21013L)).willReturn(Optional.of(submittedRow()));
        willThrow(new AccessDeniedAppException("当前审核人无权访问该申请"))
                .given(reviewApplicationAccessValidator).requireAccess(any(), any());

        assertThatThrownBy(() -> service.listReviewLogs(21013L))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前审核人无权访问该申请");
        verifyNoInteractions(applicationReviewLogRepository);
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

    private UserAuthorizationContext taskViewer() {
        return new UserAuthorizationContext(
                1010L,
                "A0010",
                "Counselor",
                "COUNSELOR",
                Set.of("COUNSELOR"),
                Set.of("review.task.view", "application.review"),
                List.of()
        );
    }

    private ReviewApplicationQueryRow submittedRow() {
        ReviewApplicationQueryRow row = new ReviewApplicationQueryRow();
        row.setApplicationId(21013L);
        row.setApplicantUserId(1001L);
        row.setApplicantUserNo("2024305999");
        row.setApplicantUserName("张三");
        row.setOrgUnitId(2010L);
        row.setOrgUnitName("计算机学院 1 班");
        row.setOrgPath("/WHUT/CS/CLASS1");
        row.setCategoryCode("INTELLECTUAL");
        row.setItemCode("INTELLECTUAL_PAPER");
        row.setAcademicYear("2025-2026");
        row.setTerm("上学期");
        row.setTitle("论文申请");
        row.setDescription("申请说明");
        row.setStatus("SUBMITTED");
        row.setSubmittedAt(LocalDateTime.of(2026, 7, 7, 10, 0));
        row.setVersion(1L);
        row.setOptionCode("OPTION_A");
        row.setAppliedPoints(new BigDecimal("2.00"));
        row.setMaxPoints(new BigDecimal("6.00"));
        row.setEvidenceCount(1);
        row.setExceedsMaxPoints(false);
        row.setAttachments(List.of(attachment()));
        return row;
    }

    private edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow attachment() {
        edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow row =
                new edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow();
        row.setFileId("file-1");
        row.setStorageKey("private/uploads/a.pdf");
        row.setOriginalFilename("a.pdf");
        row.setContentType("application/pdf");
        row.setSize(128L);
        row.setSortNo(0);
        return row;
    }

    @org.junit.jupiter.api.BeforeEach
    void setUpFileQueries() {
        given(fileQueryApplicationService.getMetadata("file-1")).willReturn(new FileMetadataResponse(
                "file-1",
                "a.pdf",
                "application/pdf",
                128L,
                "ACTIVE",
                "SELF_UPLOAD",
                true,
                true,
                LocalDateTime.parse("2026-07-06T10:00:00")
        ));
        given(fileQueryApplicationService.getAccessUrl("file-1")).willReturn(new FileAccessUrlResponse(
                "file-1",
                "https://cdn.example.com/private/uploads/a.pdf",
                "PUBLIC_URL",
                null
        ));
    }
}
