package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.SubmitApplicationCommand;
import edu.whut.eval.application.application.command.UpdateApplicationDraftCommand;
import edu.whut.eval.application.application.command.WithdrawApplicationCommand;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.application.service.ActiveSubmissionPolicy;
import edu.whut.eval.domain.application.service.ApplicationSubmissionWindowPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 学生侧申请写入应用服务。
 */
@Service
public class ApplicationSubmissionCommandApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ApplicationSubmissionRepository applicationSubmissionRepository;
    private final ApplicationSubmissionWindowPolicy applicationSubmissionWindowPolicy;
    private final ActiveSubmissionPolicy activeSubmissionPolicy;
    private final ApplicationAttachmentResolver applicationAttachmentResolver;

    public ApplicationSubmissionCommandApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                          ApplicationSubmissionRepository applicationSubmissionRepository,
                                                          ApplicationSubmissionWindowPolicy applicationSubmissionWindowPolicy,
                                                          ActiveSubmissionPolicy activeSubmissionPolicy,
                                                          ApplicationAttachmentResolver applicationAttachmentResolver) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.applicationSubmissionRepository = applicationSubmissionRepository;
        this.applicationSubmissionWindowPolicy = applicationSubmissionWindowPolicy;
        this.activeSubmissionPolicy = activeSubmissionPolicy;
        this.applicationAttachmentResolver = applicationAttachmentResolver;
    }

    /**
     * 为当前登录学生创建一条申请草稿。
     */
    @Transactional
    public ApplicationSubmissionView createDraft(CreateApplicationDraftCommand command) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        if (activeSubmissionPolicy.hasActiveSubmission(
                authorizationContext.getUserId(),
                command.getItemCode(),
                command.getAcademicYear(),
                command.getTerm(),
                null
        )) {
            throw new ConflictException("当前项目在该学年学期下已存在活跃申请");
        }
        List<AttachmentRef> attachments = resolveAttachments(command.getAttachmentFileIds(), authorizationContext.getUserId());
        ApplicationSubmission saved = applicationSubmissionRepository.save(ApplicationSubmission.createDraft(
                authorizationContext.getUserId(),
                command.getOrgUnitId(),
                command.getCategoryCode(),
                command.getItemCode(),
                command.getAcademicYear(),
                command.getTerm(),
                command.getTitle(),
                command.getDescription(),
                attachments
        ));
        return toView(saved);
    }

    /**
     * 更新当前学生的草稿或退回申请。
     */
    @Transactional
    public ApplicationSubmissionView updateDraft(UpdateApplicationDraftCommand command) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ApplicationSubmission submission = loadOwnedSubmission(command.getApplicationId(), authorizationContext.getUserId());
        List<AttachmentRef> attachments = resolveAttachments(command.getAttachmentFileIds(), authorizationContext.getUserId());
        ApplicationSubmission saved = applicationSubmissionRepository.save(submission.updateDraft(
                command.getTitle(),
                command.getDescription(),
                attachments,
                requiredExpectedVersion(command.getExpectedVersion())
        ));
        return toView(saved);
    }

    /**
     * 提交当前学生的申请。
     */
    @Transactional
    public ApplicationSubmissionView submit(SubmitApplicationCommand command) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ApplicationSubmission submission = loadOwnedSubmission(command.getApplicationId(), authorizationContext.getUserId());
        if (!applicationSubmissionWindowPolicy.isWindowOpen(
                submission.getOrgUnitId(),
                submission.getCategoryCode(),
                submission.getItemCode(),
                submission.getAcademicYear(),
                submission.getTerm()
        )) {
            throw new ValidationException("当前申请窗口未开放");
        }
        ApplicationSubmission saved = applicationSubmissionRepository.save(submission.submit(
                requiredExpectedVersion(command.getExpectedVersion())
        ));
        return toView(saved);
    }

    /**
     * 撤回当前学生的申请。
     */
    @Transactional
    public ApplicationSubmissionView withdraw(WithdrawApplicationCommand command) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ApplicationSubmission submission = loadOwnedSubmission(command.getApplicationId(), authorizationContext.getUserId());
        ApplicationSubmission saved = applicationSubmissionRepository.save(submission.withdraw(
                requiredExpectedVersion(command.getExpectedVersion())
        ));
        return toView(saved);
    }

    private ApplicationSubmission loadOwnedSubmission(Long applicationId, Long currentUserId) {
        ApplicationSubmission submission = applicationSubmissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        if (!currentUserId.equals(submission.getApplicantUserId())) {
            throw new ValidationException("当前用户无权操作该申请");
        }
        return submission;
    }

    private List<AttachmentRef> resolveAttachments(List<String> attachmentFileIds, Long currentUserId) {
        if (attachmentFileIds == null || attachmentFileIds.isEmpty()) {
            return List.of();
        }
        Set<String> fileIds = new LinkedHashSet<>();
        List<String> deduplicatedFileIds = attachmentFileIds.stream()
                .map(fileId -> validateAttachmentFileId(fileId, fileIds))
                .toList();
        return applicationAttachmentResolver.resolveForBinding(deduplicatedFileIds, currentUserId);
    }

    private String validateAttachmentFileId(String fileId, Set<String> fileIds) {
        if (fileId == null || fileId.isBlank()) {
            throw new ValidationException("附件 fileId 不能为空");
        }
        if (!fileIds.add(fileId)) {
            throw new ConflictException("同一申请内不允许重复附件 fileId");
        }
        return fileId;
    }

    private long requiredExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null) {
            throw new ValidationException("expectedVersion 不能为空");
        }
        return expectedVersion;
    }

    private ApplicationSubmissionView toView(ApplicationSubmission submission) {
        return new ApplicationSubmissionView(
                submission.getApplicationId(),
                submission.getStatus(),
                submission.getTitle(),
                submission.getDescription(),
                submission.getEvidenceAttachments().size(),
                submission.getVersion()
        );
    }
}
