package edu.whut.eval.interfaces.student;

import edu.whut.eval.application.application.query.StudentEvaluationItemView;
import edu.whut.eval.application.application.query.StudentEvaluationPointsView;
import edu.whut.eval.application.application.service.StudentEvaluationApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.interfaces.student.request.CalculateStudentEvaluationPointsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/student/evaluation")
public class StudentEvaluationController {

    private final StudentEvaluationApplicationService studentEvaluationApplicationService;

    public StudentEvaluationController(StudentEvaluationApplicationService studentEvaluationApplicationService) {
        this.studentEvaluationApplicationService = studentEvaluationApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")
    @GetMapping("/items")
    public ApiResponse<List<StudentEvaluationItemView>> listItems(
            @RequestParam @NotBlank(message = "categoryCode 不能为空") String categoryCode) {
        return ApiResponse.success(studentEvaluationApplicationService.listItems(categoryCode));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")
    @PostMapping("/calculate-points")
    public ApiResponse<StudentEvaluationPointsView> calculatePoints(
            @Valid @RequestBody CalculateStudentEvaluationPointsRequest request) {
        return ApiResponse.success(studentEvaluationApplicationService.calculatePoints(
                request.getItemCode(),
                request.getOptionCode()
        ));
    }
}
