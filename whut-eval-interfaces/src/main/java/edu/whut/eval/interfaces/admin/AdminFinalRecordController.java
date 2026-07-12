package edu.whut.eval.interfaces.admin;

import edu.whut.eval.application.finalrecord.command.ConfirmFinalRecordCommand;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordDetailView;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordListItemView;
import edu.whut.eval.application.finalrecord.query.ConfirmFinalRecordResultView;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentView;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.admin.request.ConfirmFinalRecordRequest;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    @GetMapping("/unsubmitted")
    public ApiResponse<PageResult<UnsubmittedStudentView>> pageUnsubmittedFinalRecords(
            String academicYear,
            String grade,
            String pageNo,
            String pageSize,
            HttpServletRequest request) {
        rejectMultiValueOrArrayStyleParameter(request, "academicYear");
        rejectMultiValueOrArrayStyleParameter(request, "grade");
        rejectMultiValueOrArrayStyleParameter(request, "pageNo");
        rejectMultiValueOrArrayStyleParameter(request, "pageSize");
        return ApiResponse.success(queryApplicationService.pageUnsubmittedStudents(
                new UnsubmittedFinalRecordQuery(
                        academicYear,
                        grade,
                        mergeClassParameters(request),
                        parsePageParameter(pageNo, 1L, "pageNo"),
                        parsePageParameter(pageSize, 20L, "pageSize")
                )
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

    private void rejectMultiValueOrArrayStyleParameter(HttpServletRequest request, String name) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap.containsKey(name + "[]")) {
            throw new ValidationException(name + " 不合法");
        }
        String[] values = parameterMap.get(name);
        if (values != null && values.length > 1) {
            throw new ValidationException(name + " 不合法");
        }
    }

    private List<String> mergeClassParameters(HttpServletRequest request) {
        List<String> values = new ArrayList<>();
        appendParameterValues(values, request.getParameterValues("classes"));
        appendParameterValues(values, request.getParameterValues("classes[]"));
        return values;
    }

    private void appendParameterValues(List<String> values, String[] parameterValues) {
        if (parameterValues == null) {
            return;
        }
        values.addAll(List.of(parameterValues));
    }

    private long parsePageParameter(String value, long defaultValue, String name) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new ValidationException(name + " 不合法");
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            throw new ValidationException(name + " 不合法");
        }
    }
}
