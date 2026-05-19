package edu.whut.eval.interfaces.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.iam.command.CreateRoleAssignmentCommand;
import edu.whut.eval.application.iam.command.CreateScopeRuleCommand;
import edu.whut.eval.application.iam.command.UpdateRoleAssignmentCommand;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminView;
import edu.whut.eval.application.iam.query.ScopeRuleAdminView;
import edu.whut.eval.application.iam.service.RoleAssignmentAdminApplicationService;
import edu.whut.eval.application.iam.service.ScopeRuleAdminApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.interfaces.iam.request.CreateRoleAssignmentRequest;
import edu.whut.eval.interfaces.iam.request.CreateScopeRuleRequest;
import edu.whut.eval.interfaces.iam.request.UpdateRoleAssignmentRequest;
import edu.whut.eval.interfaces.iam.response.RoleAssignmentResponse;
import edu.whut.eval.interfaces.iam.response.ScopeRuleResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
