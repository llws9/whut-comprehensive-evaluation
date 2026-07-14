package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.ApplicationAttachmentView;
import edu.whut.eval.application.application.query.ReviewApplicationAttachmentRow;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationListItemView;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.query.ReviewApplicationSummaryView;
import edu.whut.eval.application.application.query.ReviewApplicantView;
import edu.whut.eval.application.application.query.ReviewLogView;
import edu.whut.eval.application.application.query.ReviewScoringSnapshotView;
import edu.whut.eval.application.application.query.ReviewTaskSummaryCounts;
import edu.whut.eval.application.application.query.ReviewTaskSummaryView;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ReviewApplicationQueryApplicationService {

    private static final String SINGLE_REVIEW = "SINGLE_REVIEW";

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ReviewApplicationQueryRepository reviewApplicationQueryRepository;
    private final ReviewApplicationAccessValidator reviewApplicationAccessValidator;
    private final ApplicationReviewLogRepository applicationReviewLogRepository;

    public ReviewApplicationQueryApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                    ReviewApplicationQueryRepository reviewApplicationQueryRepository,
                                                    ReviewApplicationAccessValidator reviewApplicationAccessValidator,
                                                    ApplicationReviewLogRepository applicationReviewLogRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.reviewApplicationQueryRepository = reviewApplicationQueryRepository;
        this.reviewApplicationAccessValidator = reviewApplicationAccessValidator;
        this.applicationReviewLogRepository = applicationReviewLogRepository;
    }

    public PageResult<ReviewApplicationListItemView> pageReviewApplications(ReviewApplicationPageQuery query) {
        UserAuthorizationContext reviewer = requiredReviewer();
        PageResult<ReviewApplicationQueryRow> page = reviewApplicationQueryRepository.pageReviewApplications(toAccessContext(reviewer), query);
        return new PageResult<>(page.total(), page.records().stream().map(this::toListItem).toList());
    }

    public ReviewTaskSummaryView getReviewTaskSummary() {
        UserAuthorizationContext reviewer = requiredTaskViewer();
        LocalDateTime dayStart = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay();
        ReviewTaskSummaryCounts counts = reviewApplicationQueryRepository.countReviewTaskSummary(
                toAccessContext(reviewer),
                dayStart,
                dayStart.plusDays(1)
        );
        long processedToday = counts.approvedToday() + counts.returnedToday() + counts.rejectedToday();
        return new ReviewTaskSummaryView(
                counts.pendingCount(),
                counts.approvedToday(),
                counts.returnedToday(),
                counts.rejectedToday(),
                processedToday
        );
    }

    public ReviewApplicationDetailView getReviewDetail(Long applicationId) {
        UserAuthorizationContext reviewer = requiredReviewer();
        ReviewApplicationQueryRow row = reviewApplicationQueryRepository.findReviewApplicationDetail(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        reviewApplicationAccessValidator.requireAccess(reviewer, row);
        List<ReviewLogView> logs = applicationReviewLogRepository.listByApplicationId(applicationId)
                .stream()
                .map(this::toLogView)
                .toList();
        return toDetail(row, logs);
    }

    public List<ApplicationAttachmentView> listReviewAttachments(Long applicationId) {
        UserAuthorizationContext reviewer = requiredReviewer();
        ReviewApplicationQueryRow row = reviewApplicationQueryRepository.findReviewApplicationDetail(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        reviewApplicationAccessValidator.requireAccess(reviewer, row);
        return row.getAttachments().stream().map(this::toAttachmentView).toList();
    }

    public List<ReviewLogView> listReviewLogs(Long applicationId) {
        UserAuthorizationContext reviewer = requiredReviewer();
        ReviewApplicationQueryRow row = reviewApplicationQueryRepository.findReviewApplicationDetail(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        reviewApplicationAccessValidator.requireAccess(reviewer, row);
        return applicationReviewLogRepository.listByApplicationId(applicationId)
                .stream()
                .map(this::toLogView)
                .toList();
    }

    private UserAuthorizationContext requiredReviewer() {
        UserAuthorizationContext reviewer = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (!reviewer.hasAuthority(AuthorizationPermissionCodes.APPLICATION_REVIEW)) {
            throw new AccessDeniedAppException("当前审核人无审核权限");
        }
        return reviewer;
    }

    private UserAuthorizationContext requiredTaskViewer() {
        UserAuthorizationContext reviewer = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (!reviewer.hasAuthority(AuthorizationPermissionCodes.REVIEW_TASK_VIEW)) {
            throw new AccessDeniedAppException("当前用户无工作台查看权限");
        }
        return reviewer;
    }

    private ApplicationAccessContext toAccessContext(UserAuthorizationContext reviewer) {
        return new ApplicationAccessContext(
                reviewer.getUserId(),
                reviewer.getUserNo(),
                reviewer.getUserName(),
                reviewer.getIdentity(),
                reviewer.getRoles(),
                reviewer.getAuthorities(),
                reviewer.getScopeRules(),
                AuthorizationPermissionCodes.APPLICATION_REVIEW
        );
    }

    private ReviewApplicationListItemView toListItem(ReviewApplicationQueryRow row) {
        return new ReviewApplicationListItemView(
                row.getApplicationId(),
                row.getApplicantUserId(),
                row.getApplicantUserName(),
                row.getApplicantUserNo(),
                row.getOrgUnitId(),
                row.getOrgUnitName(),
                row.getCategoryCode(),
                row.getItemCode(),
                row.getTitle(),
                row.getStatus(),
                toInstant(row.getSubmittedAt()),
                SINGLE_REVIEW
        );
    }

    private ReviewApplicationDetailView toDetail(ReviewApplicationQueryRow row, List<ReviewLogView> logs) {
        ReviewScoringSnapshotView scoringSnapshot = new ReviewScoringSnapshotView(
                row.getOptionCode(),
                row.getAppliedPoints(),
                row.getMaxPoints(),
                row.getEvidenceCount() == null ? 0 : row.getEvidenceCount(),
                Boolean.TRUE.equals(row.getExceedsMaxPoints()),
                row.getWarningMessage()
        );
        return new ReviewApplicationDetailView(
                new ReviewApplicationSummaryView(
                        row.getApplicationId(),
                        row.getStatus(),
                        row.getTitle(),
                        row.getDescription(),
                        row.getCategoryCode(),
                        row.getItemCode(),
                        row.getAcademicYear(),
                        row.getTerm(),
                        toInstant(row.getSubmittedAt()),
                        row.getVersion(),
                        scoringSnapshot
                ),
                new ReviewApplicantView(
                        row.getApplicantUserId(),
                        row.getApplicantUserNo(),
                        row.getApplicantUserName(),
                        row.getOrgUnitId(),
                        row.getOrgUnitName()
                ),
                row.getAttachments().stream().map(this::toAttachmentView).toList(),
                logs,
                "SUBMITTED".equals(row.getStatus()) ? List.of("APPROVE", "RETURN", "REJECT") : List.of()
        );
    }

    private ApplicationAttachmentView toAttachmentView(ReviewApplicationAttachmentRow row) {
        return new ApplicationAttachmentView(
                row.getFileId(),
                row.getOriginalFilename(),
                row.getContentType(),
                row.getSize(),
                row.getSortNo() == null ? 0 : row.getSortNo()
        );
    }

    private ReviewLogView toLogView(ApplicationReviewLog log) {
        return new ReviewLogView(
                log.getId(),
                log.getAction().name(),
                log.getReviewerId(),
                null,
                log.getReviewRole(),
                log.getReason(),
                log.getReviewedAt()
        );
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
