package edu.whut.eval.interfaces.student;

import edu.whut.eval.application.finalrecord.command.SubmitFinalRecordCommand;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreListView;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.interfaces.student.request.SubmitFinalRecordRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/student/final-records")
public class StudentFinalRecordController {

    private final FinalRecordQueryApplicationService queryApplicationService;
    private final FinalRecordCommandApplicationService commandApplicationService;

    public StudentFinalRecordController(FinalRecordQueryApplicationService queryApplicationService,
                                        FinalRecordCommandApplicationService commandApplicationService) {
        this.queryApplicationService = queryApplicationService;
        this.commandApplicationService = commandApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_VIEW_SELF)")
    @GetMapping("/{academicYear}")
    public ApiResponse<FinalRecordStudentView> getFinalRecord(@PathVariable String academicYear) {
        return ApiResponse.success(queryApplicationService.getStudentFinalRecord(academicYear));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_VIEW_SELF)")
    @GetMapping("/{academicYear}/components")
    public ApiResponse<FinalComponentScoreListView> listComponents(@PathVariable String academicYear) {
        return ApiResponse.success(queryApplicationService.listStudentComponents(academicYear));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_SUBMIT_SELF)")
    @PostMapping("/submit")
    public ApiResponse<FinalRecordStudentView> submit(@Valid @RequestBody SubmitFinalRecordRequest request) {
        return ApiResponse.success(commandApplicationService.submit(
                new SubmitFinalRecordCommand(request.getAcademicYear(), request.getExpectedVersion())
        ));
    }
}
