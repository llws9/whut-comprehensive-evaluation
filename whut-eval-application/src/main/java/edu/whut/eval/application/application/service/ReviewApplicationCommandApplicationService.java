package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.command.ApproveReviewCommand;
import edu.whut.eval.application.application.command.RejectReviewCommand;
import edu.whut.eval.application.application.command.ReturnReviewCommand;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
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
import java.util.Comparator;

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
        UserAuthorizationContext reviewer = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (!reviewer.hasAuthority(AuthorizationPermissionCodes.APPLICATION_REVIEW)) {
            throw new AccessDeniedAppException("当前审核人无审核权限");
        }
        ReviewApplicationQueryRow resource = reviewApplicationQueryRepository.findReviewApplicationDetail(toAccessContext(reviewer), applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        reviewApplicationAccessValidator.requireAccess(reviewer, resource);
        ApplicationSubmission submission = applicationSubmissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        ApplicationSubmission reviewed = switch (action) {
            case APPROVE -> submission.approve(requiredExpectedVersion(expectedVersion));
            case RETURN -> submission.returnForFix(requiredExpectedVersion(expectedVersion));
            case REJECT -> submission.reject(requiredExpectedVersion(expectedVersion));
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
