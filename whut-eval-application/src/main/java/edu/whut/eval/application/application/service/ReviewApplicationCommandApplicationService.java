package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.command.ApproveReviewCommand;
import edu.whut.eval.application.application.command.BatchApproveReviewCommand;
import edu.whut.eval.application.application.command.RejectReviewCommand;
import edu.whut.eval.application.application.command.ReturnReviewCommand;
import edu.whut.eval.application.application.query.BatchReviewApproveFailedItemView;
import edu.whut.eval.application.application.query.BatchReviewApproveResultView;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReviewApplicationCommandApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ReviewApplicationQueryRepository reviewApplicationQueryRepository;
    private final ReviewApplicationAccessValidator reviewApplicationAccessValidator;
    private final ApplicationSubmissionRepository applicationSubmissionRepository;
    private final ApplicationReviewLogRepository applicationReviewLogRepository;

    public ReviewApplicationCommandApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                      ReviewApplicationQueryRepository reviewApplicationQueryRepository,
                                                      ReviewApplicationAccessValidator reviewApplicationAccessValidator,
                                                      ApplicationSubmissionRepository applicationSubmissionRepository,
                                                      ApplicationReviewLogRepository applicationReviewLogRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.reviewApplicationQueryRepository = reviewApplicationQueryRepository;
        this.reviewApplicationAccessValidator = reviewApplicationAccessValidator;
        this.applicationSubmissionRepository = applicationSubmissionRepository;
        this.applicationReviewLogRepository = applicationReviewLogRepository;
    }

    @Transactional
    public ReviewActionResultView approve(ApproveReviewCommand command) {
        return review(command.applicationId(), command.expectedVersion(), ApplicationReviewAction.APPROVE, command.comment());
    }

    @Transactional
    public BatchReviewApproveResultView batchApprove(BatchApproveReviewCommand command) {
        List<Long> applicationIds = requireBatchApplicationIds(command.applicationIds());
        UserAuthorizationContext reviewer = requiredReviewer();
        List<BatchReviewApproveFailedItemView> failedItems = new ArrayList<>();
        long successCount = 0;
        for (Long applicationId : applicationIds) {
            try {
                reviewWithReviewer(reviewer, applicationId, null, ApplicationReviewAction.APPROVE, command.comment(), true);
                successCount++;
            } catch (ResourceNotFoundException | AccessDeniedAppException | ConflictException ex) {
                failedItems.add(new BatchReviewApproveFailedItemView(
                        applicationId,
                        ex.getErrorCode().code(),
                        ex.getMessage()
                ));
            }
        }
        return new BatchReviewApproveResultView(
                applicationIds.size(),
                successCount,
                failedItems.size(),
                List.copyOf(failedItems),
                Instant.now()
        );
    }

    @Transactional
    public ReviewActionResultView returnForFix(ReturnReviewCommand command) {
        requireReason(command.reason());
        return review(command.applicationId(), command.expectedVersion(), ApplicationReviewAction.RETURN, command.reason());
    }

    @Transactional
    public ReviewActionResultView reject(RejectReviewCommand command) {
        requireReason(command.reason());
        return review(command.applicationId(), command.expectedVersion(), ApplicationReviewAction.REJECT, command.reason());
    }

    private ReviewActionResultView review(Long applicationId,
                                          Long expectedVersion,
                                          ApplicationReviewAction action,
                                          String reason) {
        return reviewWithReviewer(requiredReviewer(), applicationId, expectedVersion, action, reason, false);
    }

    private ReviewActionResultView reviewWithReviewer(UserAuthorizationContext reviewer,
                                                      Long applicationId,
                                                      Long expectedVersion,
                                                      ApplicationReviewAction action,
                                                      String reason,
                                                      boolean useCurrentVersion) {
        ReviewApplicationQueryRow resource = reviewApplicationQueryRepository.findReviewApplicationDetail(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        reviewApplicationAccessValidator.requireAccess(reviewer, resource);
        ApplicationSubmission submission = applicationSubmissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        long requiredVersion = useCurrentVersion
                ? currentVersion(submission)
                : requiredExpectedVersion(expectedVersion);
        ApplicationSubmission reviewed = switch (action) {
            case APPROVE -> submission.approve(requiredVersion);
            case RETURN -> submission.returnForFix(requiredVersion);
            case REJECT -> submission.reject(requiredVersion);
        };
        ApplicationSubmission saved = applicationSubmissionRepository.save(reviewed);
        Instant reviewedAt = Instant.now();
        ApplicationReviewLog log = applicationReviewLogRepository.append(new ApplicationReviewLog(
                null,
                applicationId,
                action,
                reviewer.getUserId(),
                resolveReviewRole(reviewer),
                normalizeReason(reason),
                reviewedAt
        ));
        return new ReviewActionResultView(saved.getApplicationId(), saved.getStatus(), saved.getVersion(), log.getId(), log.getReviewedAt());
    }

    private UserAuthorizationContext requiredReviewer() {
        UserAuthorizationContext reviewer = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (!reviewer.hasAuthority(AuthorizationPermissionCodes.APPLICATION_REVIEW)) {
            throw new AccessDeniedAppException("当前审核人无审核权限");
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

    private long requiredExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ValidationException("expectedVersion 不能为空");
        }
        return expectedVersion;
    }

    private long currentVersion(ApplicationSubmission submission) {
        if (submission.getVersion() == null) {
            throw new ConflictException("申请版本已变更，请刷新后重试");
        }
        return submission.getVersion();
    }

    private List<Long> requireBatchApplicationIds(List<Long> applicationIds) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            throw new ValidationException("applicationIds 不能为空");
        }
        Set<Long> seen = new HashSet<>();
        for (Long applicationId : applicationIds) {
            if (applicationId == null) {
                throw new ValidationException("applicationIds 不能为空");
            }
            if (!seen.add(applicationId)) {
                throw new ValidationException("applicationIds 不允许重复");
            }
        }
        return List.copyOf(applicationIds);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason 不能为空");
        }
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private String resolveReviewRole(UserAuthorizationContext reviewer) {
        if (reviewer.getIdentity() != null && !reviewer.getIdentity().isBlank()) {
            return reviewer.getIdentity();
        }
        return reviewer.getRoles().stream()
                .filter(role -> role != null && !role.isBlank())
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElse("UNKNOWN");
    }
}
