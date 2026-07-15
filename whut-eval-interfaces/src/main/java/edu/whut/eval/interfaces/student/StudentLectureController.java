package edu.whut.eval.interfaces.student;

import edu.whut.eval.application.application.query.LectureCandidateView;
import edu.whut.eval.application.application.service.LectureCandidateQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.application.query.LectureCandidatePageQuery;
import edu.whut.eval.domain.shared.PageResult;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/student/lectures")
public class StudentLectureController {

    private final LectureCandidateQueryApplicationService lectureCandidateQueryApplicationService;

    public StudentLectureController(LectureCandidateQueryApplicationService lectureCandidateQueryApplicationService) {
        this.lectureCandidateQueryApplicationService = lectureCandidateQueryApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")
    @GetMapping
    public ApiResponse<PageResult<LectureCandidateView>> pageLectures(
            @RequestParam @NotBlank(message = "academicYear 不能为空") String academicYear,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(lectureCandidateQueryApplicationService.pageCurrentStudentLectures(
                new LectureCandidatePageQuery(academicYear, keyword, pageNo, pageSize)
        ));
    }
}
