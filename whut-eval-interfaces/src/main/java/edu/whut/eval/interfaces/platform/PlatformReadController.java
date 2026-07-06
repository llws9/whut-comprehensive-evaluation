package edu.whut.eval.interfaces.platform;

import edu.whut.eval.application.platform.query.EvaluationItemResponse;
import edu.whut.eval.application.platform.query.PlatformMenuDeadline;
import edu.whut.eval.application.platform.query.PlatformMenuStatus;
import edu.whut.eval.application.platform.service.PlatformReadApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("isAuthenticated()")
public class PlatformReadController {

    private final PlatformReadApplicationService platformReadApplicationService;

    public PlatformReadController(PlatformReadApplicationService platformReadApplicationService) {
        this.platformReadApplicationService = platformReadApplicationService;
    }

    @GetMapping("/menu/status")
    public ApiResponse<PlatformMenuStatus> getMenuStatus() {
        return ApiResponse.success(platformReadApplicationService.getMenuStatus());
    }

    @GetMapping("/menu/deadline")
    public ApiResponse<PlatformMenuDeadline> getMenuDeadline() {
        return ApiResponse.success(platformReadApplicationService.getMenuDeadline());
    }

    @GetMapping("/evaluation-items")
    public ApiResponse<List<EvaluationItemResponse>> listEvaluationItems(
            @RequestParam(required = false) String categoryCode) {
        return ApiResponse.success(platformReadApplicationService.listEvaluationItems(categoryCode));
    }
}
