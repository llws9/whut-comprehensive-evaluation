package edu.whut.eval.interfaces.iam;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAdminPageQuery;
import edu.whut.eval.application.iam.query.RoleCreatedView;
import edu.whut.eval.application.iam.service.RoleAdminApplicationService;
import edu.whut.eval.application.iam.service.RoleAdminQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.iam.request.CreateRoleRequest;
import edu.whut.eval.interfaces.iam.request.UpdateRoleRequest;
import edu.whut.eval.interfaces.iam.response.RoleAdminPageItemResponse;
import edu.whut.eval.interfaces.iam.response.RoleCreatedResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/roles")
public class RoleAdminController {

    private final RoleAdminQueryApplicationService roleAdminQueryApplicationService;
    private final RoleAdminApplicationService roleAdminApplicationService;

    public RoleAdminController(RoleAdminQueryApplicationService roleAdminQueryApplicationService,
                               RoleAdminApplicationService roleAdminApplicationService) {
        this.roleAdminQueryApplicationService = roleAdminQueryApplicationService;
        this.roleAdminApplicationService = roleAdminApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")
    @GetMapping
    public ApiResponse<PageResult<RoleAdminPageItemResponse>> pageRoles(@RequestParam(defaultValue = "1") long pageNo,
                                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) String status) {
        PageResult<RoleAdminPageItemView> page = roleAdminQueryApplicationService.pageRoles(
                new RoleAdminPageQuery(pageNo, pageSize, keyword, status)
        );
        return ApiResponse.success(new PageResult<>(
                page.total(),
                page.records().stream().map(this::toResponse).toList()
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")
    @PostMapping
    public ApiResponse<RoleCreatedResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleCreatedView view = roleAdminApplicationService.createRole(
                new CreateRoleCommand(request.getRoleCode(), request.getRoleName())
        );
        return ApiResponse.success(new RoleCreatedResponse(
                view.roleId(),
                view.roleCode(),
                view.roleName(),
                view.status()
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)")
    @PatchMapping("/{roleId}")
    public ApiResponse<RoleCreatedResponse> updateRole(@PathVariable Long roleId,
                                                       @Valid @RequestBody UpdateRoleRequest request) {
        RoleCreatedView view = roleAdminApplicationService.updateRole(
                new UpdateRoleCommand(roleId, request.getRoleName(), request.getStatus())
        );
        return ApiResponse.success(new RoleCreatedResponse(
                view.roleId(),
                view.roleCode(),
                view.roleName(),
                view.status()
        ));
    }

    private RoleAdminPageItemResponse toResponse(RoleAdminPageItemView view) {
        return new RoleAdminPageItemResponse(
                view.roleId(),
                view.roleCode(),
                view.roleName(),
                view.roleScope(),
                view.status(),
                view.permissionCount(),
                view.createdAt()
        );
    }
}
