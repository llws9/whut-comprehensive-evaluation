package edu.whut.eval.interfaces.review;

import edu.whut.eval.application.application.query.ReviewMetaGradesView;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review/meta")
public class ReviewMetaController {

    private final ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService;

    public ReviewMetaController(ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService) {
        this.reviewApplicationQueryApplicationService = reviewApplicationQueryApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).REVIEW_TASK_VIEW)")
    @GetMapping("/grades")
    public ApiResponse<ReviewMetaGradesView> getGrades() {
        return ApiResponse.success(reviewApplicationQueryApplicationService.getReviewGradeMetadata());
    }
}
