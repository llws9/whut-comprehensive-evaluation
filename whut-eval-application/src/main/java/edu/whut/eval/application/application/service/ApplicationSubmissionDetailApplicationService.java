package edu.whut.eval.application.application.service;

import edu.whut.eval.application.application.query.ApplicationAttachmentView;
import edu.whut.eval.application.application.query.ApplicationSubmissionDetailView;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.domain.application.model.ApplicationScoringSnapshot;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationSubmissionDetailApplicationService {

    private final UserAuthorizationContextAssembler userAuthorizationContextAssembler;
    private final ApplicationSubmissionRepository applicationSubmissionRepository;

    public ApplicationSubmissionDetailApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                         ApplicationSubmissionRepository applicationSubmissionRepository) {
        this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
        this.applicationSubmissionRepository = applicationSubmissionRepository;
    }

    public ApplicationSubmissionDetailView getOwnedDetail(Long applicationId) {
        UserAuthorizationContext authorizationContext = userAuthorizationContextAssembler.requiredAuthorizationContext();
        ApplicationSubmission submission = applicationSubmissionRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
        if (!authorizationContext.getUserId().equals(submission.getApplicantUserId())) {
            throw new AccessDeniedAppException("当前用户无权查看该申请");
        }
        if (submission.getStatus() == ApplicationSubmissionStatus.DELETED) {
            throw new ResourceNotFoundException("申请不存在");
        }
        return toDetailView(submission);
    }

    private ApplicationSubmissionDetailView toDetailView(ApplicationSubmission submission) {
        ApplicationSubmissionDetailView view = new ApplicationSubmissionDetailView();
        view.setApplicationId(submission.getApplicationId());
        view.setOrgUnitId(submission.getOrgUnitId());
        view.setCategoryCode(submission.getCategoryCode());
        view.setItemCode(submission.getItemCode());
        view.setAcademicYear(submission.getAcademicYear());
        view.setTerm(submission.getTerm());
        view.setTitle(submission.getTitle());
        view.setDescription(submission.getDescription());
        view.setStatus(submission.getStatus());
        view.setSubmittedAt(submission.getSubmittedAt());
        view.setCreatedAt(submission.getCreatedAt());
        view.setUpdatedAt(submission.getUpdatedAt());
        view.setVersion(submission.getVersion());
        view.setAttachments(toAttachmentViews(submission.getEvidenceAttachments()));

        ApplicationScoringSnapshot scoringSnapshot = submission.getScoringSnapshot();
        if (scoringSnapshot == null) {
            view.setEvidenceCount(submission.getEvidenceAttachments().size());
            return view;
        }
        view.setOptionCode(scoringSnapshot.optionCode());
        view.setAppliedPoints(scoringSnapshot.appliedPoints());
        view.setMaxPoints(scoringSnapshot.maxPoints());
        view.setEvidenceCount(scoringSnapshot.evidenceCount());
        view.setExceedsMaxPoints(scoringSnapshot.exceedsMaxPoints());
        view.setWarningMessage(scoringSnapshot.warningMessage());
        return view;
    }

    private List<ApplicationAttachmentView> toAttachmentViews(List<AttachmentRef> attachments) {
        List<ApplicationAttachmentView> views = new ArrayList<>();
        for (int i = 0; i < attachments.size(); i++) {
            AttachmentRef attachment = attachments.get(i);
            views.add(new ApplicationAttachmentView(
                    attachment.getFileId(),
                    attachment.getOriginalFilename(),
                    attachment.getContentType(),
                    attachment.getSize(),
                    i
            ));
        }
        return views;
    }
}
