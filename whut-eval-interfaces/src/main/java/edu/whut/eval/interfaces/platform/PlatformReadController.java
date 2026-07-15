package edu.whut.eval.interfaces.platform;

import edu.whut.eval.application.platform.command.ReplacePlatformDeadlineCommand;
import edu.whut.eval.application.platform.command.UpdatePlatformMenuStatusCommand;
import edu.whut.eval.application.platform.query.EvaluationItemResponse;
import edu.whut.eval.application.platform.query.PlatformMenuDeadline;
import edu.whut.eval.application.platform.query.PlatformMenuDeadlineUpdateResult;
import edu.whut.eval.application.platform.query.PlatformMenuStatus;
import edu.whut.eval.application.platform.query.PlatformMenuStatusUpdateResult;
import edu.whut.eval.application.platform.service.PlatformReadApplicationService;
import edu.whut.eval.application.platform.service.PlatformRuleCommandApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.interfaces.platform.request.ReplacePlatformDeadlineRequest;
import edu.whut.eval.interfaces.platform.request.UpdatePlatformMenuStatusRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("isAuthenticated()")
public class PlatformReadController {

    private final PlatformReadApplicationService platformReadApplicationService;
    private final PlatformRuleCommandApplicationService platformRuleCommandApplicationService;

    public PlatformReadController(PlatformReadApplicationService platformReadApplicationService,
                                  PlatformRuleCommandApplicationService platformRuleCommandApplicationService) {
        this.platformReadApplicationService = platformReadApplicationService;
        this.platformRuleCommandApplicationService = platformRuleCommandApplicationService;
    }

    @GetMapping("/menu/status")
    public ApiResponse<PlatformMenuStatus> getMenuStatus() {
        return ApiResponse.success(platformReadApplicationService.getMenuStatus());
    }

    @GetMapping("/menu/deadline")
    public ApiResponse<PlatformMenuDeadline> getMenuDeadline() {
        return ApiResponse.success(platformReadApplicationService.getMenuDeadline());
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).PLATFORM_SWITCH_MANAGE)")
    @PatchMapping("/menu/status")
    public ApiResponse<PlatformMenuStatusUpdateResult> updateMenuStatus(
            @Valid @RequestBody UpdatePlatformMenuStatusRequest request) {
        return ApiResponse.success(platformRuleCommandApplicationService.updateMenuStatus(
                new UpdatePlatformMenuStatusCommand(
                        request.getStudentApplyEnabled(),
                        request.getFinalSubmitEnabled(),
                        request.getReason()
                )
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).PLATFORM_SWITCH_MANAGE)")
    @PutMapping("/menu/deadline")
    public ApiResponse<PlatformMenuDeadlineUpdateResult> replaceMenuDeadline(
            @Valid @RequestBody ReplacePlatformDeadlineRequest request) {
        return ApiResponse.success(platformRuleCommandApplicationService.replaceDeadline(
                new ReplacePlatformDeadlineCommand(
                        request.getStudentApplyDeadline(),
                        request.getFinalSubmitDeadline(),
                        request.getReason()
                )
        ));
    }

    @GetMapping("/evaluation-items")
    public ApiResponse<List<EvaluationItemResponse>> listEvaluationItems(
            @RequestParam(required = false) String categoryCode) {
        return ApiResponse.success(platformReadApplicationService.listEvaluationItems(categoryCode));
    }
}
