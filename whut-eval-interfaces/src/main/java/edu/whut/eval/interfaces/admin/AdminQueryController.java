package edu.whut.eval.interfaces.admin;

import edu.whut.eval.application.application.query.ApplicationRecordView;
import edu.whut.eval.application.application.service.ApplicationQueryApplicationService;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.score.query.ScoreRecordView;
import edu.whut.eval.application.score.service.ScoreQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.score.query.ScorePageQuery;
import edu.whut.eval.domain.shared.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理侧查询 Controller。
 * 当前先承接既有申请/成绩查询能力，后续可继续按管理端业务边界细化。
 */
@RestController
@RequestMapping("/api/admin/query")
@Validated
public class AdminQueryController {

    private final ApplicationQueryApplicationService applicationQueryApplicationService;
    private final ScoreQueryApplicationService scoreQueryApplicationService;

    public AdminQueryController(ApplicationQueryApplicationService applicationQueryApplicationService,
                                ScoreQueryApplicationService scoreQueryApplicationService) {
        this.applicationQueryApplicationService = applicationQueryApplicationService;
        this.scoreQueryApplicationService = scoreQueryApplicationService;
    }

    /**
     * 查询当前用户可见的申请列表。
     */
    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
    @GetMapping("/applications")
    public ApiResponse<PageResult<ApplicationRecordView>> pageApplications(@RequestParam(defaultValue = "1") long pageNo,
                                                                           @RequestParam(defaultValue = "20") long pageSize,
                                                                           @RequestParam(required = false) Long applicationId,
                                                                           @RequestParam(required = false) Long applicantUserId,
                                                                           @RequestParam(required = false) Long orgUnitId,
                                                                           @RequestParam(required = false) String categoryCode,
                                                                           @RequestParam(required = false) String itemCode) {
        return ApiResponse.success(applicationQueryApplicationService.pageAccessibleApplications(
                new ApplicationPageQuery(pageNo, pageSize, applicationId, applicantUserId, orgUnitId, categoryCode, itemCode),
                AuthorizationPermissionCodes.APPLICATION_REVIEW
        ));
    }

    /**
     * 查询当前用户可见的成绩列表。
     */
    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")
    @GetMapping("/scores")
    public ApiResponse<PageResult<ScoreRecordView>> pageScores(@RequestParam(defaultValue = "1") long pageNo,
                                                               @RequestParam(defaultValue = "20") long pageSize,
                                                               @RequestParam(required = false) Long scoreId,
                                                               @RequestParam(required = false) Long studentUserId,
                                                               @RequestParam(required = false) Long orgUnitId,
                                                               @RequestParam(required = false) String categoryCode,
                                                               @RequestParam(required = false) String itemCode,
                                                               @RequestParam(required = false) String academicYear) {
        return ApiResponse.success(scoreQueryApplicationService.pageAccessibleScores(
                new ScorePageQuery(pageNo, pageSize, scoreId, studentUserId, orgUnitId, categoryCode, itemCode, academicYear),
                AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED
        ));
    }
}
