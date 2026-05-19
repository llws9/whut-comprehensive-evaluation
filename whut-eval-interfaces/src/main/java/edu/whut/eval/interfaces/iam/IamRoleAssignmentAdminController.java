package edu.whut.eval.interfaces.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.iam.command.CreateRoleAssignmentCommand;
import edu.whut.eval.application.iam.command.CreateScopeRuleCommand;
import edu.whut.eval.application.iam.command.UpdateRoleAssignmentCommand;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminPageQuery;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminView;
import edu.whut.eval.application.iam.query.ScopeRuleAdminView;
import edu.whut.eval.application.iam.service.RoleAssignmentAdminApplicationService;
import edu.whut.eval.application.iam.service.ScopeRuleAdminApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.iam.request.CreateRoleAssignmentRequest;
import edu.whut.eval.interfaces.iam.request.CreateScopeRuleRequest;
import edu.whut.eval.interfaces.iam.request.UpdateRoleAssignmentRequest;
import edu.whut.eval.interfaces.iam.response.RoleAssignmentPageItemResponse;
import edu.whut.eval.interfaces.iam.response.RoleAssignmentResponse;
import edu.whut.eval.interfaces.iam.response.ScopeRuleResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端角色分配与范围规则 Controller 草稿。
 */
@RestController
@Validated
@RequestMapping("/api/admin/role-assignments")
public class IamRoleAssignmentAdminController {

    private final RoleAssignmentAdminApplicationService roleAssignmentAdminApplicationService;
    private final ScopeRuleAdminApplicationService scopeRuleAdminApplicationService;

    public IamRoleAssignmentAdminController(RoleAssignmentAdminApplicationService roleAssignmentAdminApplicationService,
                                            ScopeRuleAdminApplicationService scopeRuleAdminApplicationService) {
        this.roleAssignmentAdminApplicationService = roleAssignmentAdminApplicationService;
        this.scopeRuleAdminApplicationService = scopeRuleAdminApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ASSIGNMENT_MANAGE)")
    @GetMapping
    public ApiResponse<PageResult<RoleAssignmentPageItemResponse>> pageRoleAssignments(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long orgUnitId) {
        PageResult<RoleAssignmentAdminPageItemView> page = roleAssignmentAdminApplicationService.pageAssignments(
                new RoleAssignmentAdminPageQuery(pageNo, pageSize, userId, roleCode, status, orgUnitId)
        );
        return ApiResponse.success(new PageResult<>(
                page.total(),
                page.records().stream().map(this::toRoleAssignmentPageItemResponse).toList()
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ASSIGNMENT_MANAGE)")
    @PostMapping
    public ApiResponse<RoleAssignmentResponse> createRoleAssignment(@Valid @RequestBody CreateRoleAssignmentRequest request) {
        RoleAssignmentAdminView view = roleAssignmentAdminApplicationService.createAssignment(
                new CreateRoleAssignmentCommand(
                        request.getUserId(),
                        request.getRoleCode(),
                        request.getOrgUnitId(),
                        request.getEffectiveFrom(),
                        request.getEffectiveTo(),
                        request.getSourceType()
                )
        );
        return ApiResponse.success(toRoleAssignmentResponse(view));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ASSIGNMENT_MANAGE)")
    @PatchMapping("/{assignmentId}")
    public ApiResponse<RoleAssignmentResponse> updateRoleAssignment(@PathVariable Long assignmentId,
                                                                    @Valid @RequestBody UpdateRoleAssignmentRequest request) {
        RoleAssignmentAdminView view = roleAssignmentAdminApplicationService.updateAssignment(
                assignmentId,
                new UpdateRoleAssignmentCommand(
                        request.getStatus(),
                        request.getOrgUnitId(),
                        request.getEffectiveFrom(),
                        request.getEffectiveTo()
                )
        );
        return ApiResponse.success(toRoleAssignmentResponse(view));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ASSIGNMENT_MANAGE)")
    @GetMapping("/{assignmentId}/scope-rules")
    public ApiResponse<List<ScopeRuleResponse>> listScopeRules(@PathVariable Long assignmentId) {
        List<ScopeRuleAdminView> views = scopeRuleAdminApplicationService.listScopeRules(assignmentId);
        return ApiResponse.success(views.stream().map(this::toScopeRuleResponse).toList());
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ASSIGNMENT_MANAGE)")
    @PostMapping("/{assignmentId}/scope-rules")
    public ApiResponse<ScopeRuleResponse> createScopeRule(@PathVariable Long assignmentId,
                                                          @Valid @RequestBody CreateScopeRuleRequest request) {
        ScopeRuleAdminView view = scopeRuleAdminApplicationService.createScopeRule(
                assignmentId,
                new CreateScopeRuleCommand(
                        request.getPermissionCode(),
                        request.getScopeType(),
                        request.getOrgUnitId(),
                        request.getCategoryCode(),
                        request.getItemCode(),
                        request.getExpressionJson(),
                        request.getPriority()
                )
        );
        return ApiResponse.success(toScopeRuleResponse(view));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ASSIGNMENT_MANAGE)")
    @DeleteMapping("/{assignmentId}")
    public ApiResponse<Void> revokeRoleAssignment(@PathVariable Long assignmentId) {
        roleAssignmentAdminApplicationService.revokeAssignment(assignmentId);
        return ApiResponse.success(null);
    }

    private RoleAssignmentResponse toRoleAssignmentResponse(RoleAssignmentAdminView view) {
        return new RoleAssignmentResponse(
                view.assignmentId(),
                view.userId(),
                view.roleCode(),
                view.roleName(),
                view.orgUnitId(),
                view.orgUnitName(),
                view.status(),
                view.effectiveFrom(),
                view.effectiveTo(),
                view.sourceType(),
                view.updatedAt()
        );
    }

    private RoleAssignmentPageItemResponse toRoleAssignmentPageItemResponse(RoleAssignmentAdminPageItemView view) {
        return new RoleAssignmentPageItemResponse(
                view.assignmentId(),
                view.userId(),
                view.userNo(),
                view.userName(),
                view.roleCode(),
                view.roleName(),
                view.orgUnitId(),
                view.orgUnitName(),
                view.status(),
                view.effectiveFrom(),
                view.effectiveTo()
        );
    }

    private ScopeRuleResponse toScopeRuleResponse(ScopeRuleAdminView view) {
        return new ScopeRuleResponse(
                view.scopeRuleId(),
                view.assignmentId(),
                view.permissionCode(),
                view.scopeType(),
                view.orgUnitId(),
                view.orgUnitName(),
                view.categoryCode(),
                view.itemCode(),
                view.expressionJson(),
                view.priority(),
                view.status(),
                view.createdAt()
        );
    }
}
