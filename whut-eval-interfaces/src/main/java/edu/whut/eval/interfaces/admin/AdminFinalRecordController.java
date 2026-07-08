package edu.whut.eval.interfaces.admin;

import edu.whut.eval.application.finalrecord.command.ConfirmFinalRecordCommand;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordDetailView;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordListItemView;
import edu.whut.eval.application.finalrecord.query.ConfirmFinalRecordResultView;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.admin.request.ConfirmFinalRecordRequest;
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

@RestController
@Validated
@RequestMapping("/api/admin/final-records")
public class AdminFinalRecordController {

    private final FinalRecordQueryApplicationService queryApplicationService;
    private final FinalRecordCommandApplicationService commandApplicationService;

    public AdminFinalRecordController(FinalRecordQueryApplicationService queryApplicationService,
                                      FinalRecordCommandApplicationService commandApplicationService) {
        this.queryApplicationService = queryApplicationService;
        this.commandApplicationService = commandApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")
    @GetMapping
    public ApiResponse<PageResult<AdminFinalRecordListItemView>> pageFinalRecords(
            @RequestParam String academicYear,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(queryApplicationService.pageAdminFinalRecords(
                new FinalRecordPageQuery(academicYear, status, keyword, orgUnitId, pageNo, pageSize)
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")
    @GetMapping("/{recordId}")
    public ApiResponse<AdminFinalRecordDetailView> getFinalRecord(@PathVariable Long recordId) {
        return ApiResponse.success(queryApplicationService.getAdminFinalRecordDetail(recordId));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_CONFIRM_ASSIGNED)")
    @PostMapping("/{recordId}/confirm")
    public ApiResponse<ConfirmFinalRecordResultView> confirm(@PathVariable Long recordId,
                                                             @Valid @RequestBody ConfirmFinalRecordRequest request) {
        return ApiResponse.success(commandApplicationService.confirm(
                new ConfirmFinalRecordCommand(recordId, request.getComment(), request.getExpectedVersion())
        ));
    }
}
