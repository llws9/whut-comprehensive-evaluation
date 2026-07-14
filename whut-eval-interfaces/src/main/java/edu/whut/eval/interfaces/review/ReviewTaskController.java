package edu.whut.eval.interfaces.review;

import edu.whut.eval.application.application.query.ReviewTaskSummaryView;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review/tasks")
public class ReviewTaskController {

    private final ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService;

    public ReviewTaskController(ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService) {
        this.reviewApplicationQueryApplicationService = reviewApplicationQueryApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).REVIEW_TASK_VIEW)")
    @GetMapping("/summary")
    public ApiResponse<ReviewTaskSummaryView> getSummary() {
        return ApiResponse.success(reviewApplicationQueryApplicationService.getReviewTaskSummary());
    }
}
