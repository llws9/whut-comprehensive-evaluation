package edu.whut.eval.interfaces.student;

import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.SubmitApplicationCommand;
import edu.whut.eval.application.application.command.UpdateApplicationDraftCommand;
import edu.whut.eval.application.application.command.WithdrawApplicationCommand;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.interfaces.student.request.CreateApplicationDraftRequest;
import edu.whut.eval.interfaces.student.request.SubmitApplicationRequest;
import edu.whut.eval.interfaces.student.request.UpdateApplicationDraftRequest;
import edu.whut.eval.interfaces.student.request.WithdrawApplicationRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生侧申请写接口。
 */
@RestController
@Validated
@RequestMapping("/api/student/applications")
public class StudentApplicationSubmissionController {

    private final ApplicationSubmissionCommandApplicationService applicationSubmissionCommandApplicationService;

    public StudentApplicationSubmissionController(ApplicationSubmissionCommandApplicationService applicationSubmissionCommandApplicationService) {
        this.applicationSubmissionCommandApplicationService = applicationSubmissionCommandApplicationService;
    }

    /**
     * 创建学生申请草稿。
     */
    @PostMapping("/drafts")
    public ApiResponse<ApplicationSubmissionView> createDraft(@Valid @RequestBody CreateApplicationDraftRequest request) {
        ApplicationSubmissionView view = applicationSubmissionCommandApplicationService.createDraft(new CreateApplicationDraftCommand(
                request.getOrgUnitId(),
                request.getCategoryCode(),
                request.getItemCode(),
                request.getAcademicYear(),
                request.getTerm(),
                request.getTitle(),
                request.getDescription(),
                request.getAttachmentFileIds()
        ));
        return ApiResponse.success(view);
    }

    /**
     * 更新指定申请草稿。
     */
    @PutMapping("/{applicationId}/draft")
    public ApiResponse<ApplicationSubmissionView> updateDraft(@PathVariable Long applicationId,
                                                              @Valid @RequestBody UpdateApplicationDraftRequest request) {
        ApplicationSubmissionView view = applicationSubmissionCommandApplicationService.updateDraft(new UpdateApplicationDraftCommand(
                applicationId,
                request.getTitle(),
                request.getDescription(),
                request.getAttachmentFileIds(),
                request.getExpectedVersion()
        ));
        return ApiResponse.success(view);
    }

    /**
     * 提交指定申请。
     * 当申请分值超过最大分值限制时，允许申请但触发警告提示。
     */
    @PostMapping("/{applicationId}/submit")
    public ApiResponse<ApplicationSubmissionView> submit(@PathVariable Long applicationId,
                                                         @Valid @RequestBody SubmitApplicationRequest request) {
        ApplicationSubmissionView view = applicationSubmissionCommandApplicationService.submit(
                new SubmitApplicationCommand(applicationId, request.getExpectedVersion(), request.getAppliedPoints(), request.getOptionCode())
        );
        return ApiResponse.success(view);
    }

    /**
     * 撤回指定申请。
     */
    @PostMapping("/{applicationId}/withdraw")
    public ApiResponse<ApplicationSubmissionView> withdraw(@PathVariable Long applicationId,
                                                           @Valid @RequestBody WithdrawApplicationRequest request) {
        ApplicationSubmissionView view = applicationSubmissionCommandApplicationService.withdraw(
                new WithdrawApplicationCommand(applicationId, request.getReason(), request.getExpectedVersion())
        );
        return ApiResponse.success(view);
    }
}
