package edu.whut.eval.interfaces.review;

import edu.whut.eval.application.application.command.ApproveReviewCommand;
import edu.whut.eval.application.application.command.RejectReviewCommand;
import edu.whut.eval.application.application.command.ReturnReviewCommand;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationListItemView;
import edu.whut.eval.application.application.query.ReviewLogView;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.review.request.ApproveReviewRequest;
import edu.whut.eval.interfaces.review.request.RejectReviewRequest;
import edu.whut.eval.interfaces.review.request.ReturnReviewRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/review/applications")
public class ReviewApplicationController {

    private final ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService;
    private final ReviewApplicationCommandApplicationService reviewApplicationCommandApplicationService;

    public ReviewApplicationController(ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService,
                                       ReviewApplicationCommandApplicationService reviewApplicationCommandApplicationService) {
        this.reviewApplicationQueryApplicationService = reviewApplicationQueryApplicationService;
        this.reviewApplicationCommandApplicationService = reviewApplicationCommandApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @GetMapping
    public ApiResponse<PageResult<ReviewApplicationListItemView>> pageApplications(@RequestParam(defaultValue = "1") long pageNo,
                                                                                   @RequestParam(defaultValue = "20") long pageSize,
                                                                                   @RequestParam(required = false) String academicYear,
                                                                                   @RequestParam(required = false) String categoryCode,
                                                                                   @RequestParam(required = false) String itemCode,
                                                                                   @RequestParam(required = false) String status,
                                                                                   @RequestParam(required = false) String keyword,
                                                                                   @RequestParam(required = false) Long orgUnitId) {
        return ApiResponse.success(reviewApplicationQueryApplicationService.pageReviewApplications(
                new ReviewApplicationPageQuery(pageNo, pageSize, academicYear, categoryCode, itemCode, status, keyword, orgUnitId)
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @GetMapping("/{applicationId}")
    public ApiResponse<ReviewApplicationDetailView> getDetail(@PathVariable Long applicationId) {
        return ApiResponse.success(reviewApplicationQueryApplicationService.getReviewDetail(applicationId));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @GetMapping("/{applicationId}/logs")
    public ApiResponse<List<ReviewLogView>> listLogs(@PathVariable Long applicationId) {
        return ApiResponse.success(reviewApplicationQueryApplicationService.listReviewLogs(applicationId));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @PostMapping("/{applicationId}/approve")
    public ApiResponse<ReviewActionResultView> approve(@PathVariable Long applicationId,
                                                       @Valid @RequestBody ApproveReviewRequest request) {
        return ApiResponse.success(reviewApplicationCommandApplicationService.approve(
                new ApproveReviewCommand(applicationId, request.getExpectedVersion(), request.getComment())
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @PostMapping("/{applicationId}/return")
    public ApiResponse<ReviewActionResultView> returnForFix(@PathVariable Long applicationId,
                                                            @Valid @RequestBody ReturnReviewRequest request) {
        return ApiResponse.success(reviewApplicationCommandApplicationService.returnForFix(
                new ReturnReviewCommand(applicationId, request.getExpectedVersion(), request.getReason())
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @PostMapping("/{applicationId}/reject")
    public ApiResponse<ReviewActionResultView> reject(@PathVariable Long applicationId,
                                                      @Valid @RequestBody RejectReviewRequest request) {
        return ApiResponse.success(reviewApplicationCommandApplicationService.reject(
                new RejectReviewCommand(applicationId, request.getExpectedVersion(), request.getReason())
        ));
    }
}
