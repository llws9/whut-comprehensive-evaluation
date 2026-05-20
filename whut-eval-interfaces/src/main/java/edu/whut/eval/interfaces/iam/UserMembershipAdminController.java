package edu.whut.eval.interfaces.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.iam.command.ReplaceUserMembershipItemCommand;
import edu.whut.eval.application.iam.command.ReplaceUserMembershipsCommand;
import edu.whut.eval.application.iam.query.UserMembershipAdminView;
import edu.whut.eval.application.iam.service.UserMembershipAdminApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.interfaces.iam.request.ReplaceUserMembershipsRequest;
import edu.whut.eval.interfaces.iam.response.UserMembershipResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/users")
public class UserMembershipAdminController {

    private final UserMembershipAdminApplicationService userMembershipAdminApplicationService;

    public UserMembershipAdminController(UserMembershipAdminApplicationService userMembershipAdminApplicationService) {
        this.userMembershipAdminApplicationService = userMembershipAdminApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ORG_MANAGE)")
    @GetMapping("/{userId}/memberships")
    public ApiResponse<List<UserMembershipResponse>> listMemberships(@PathVariable Long userId) {
        return ApiResponse.success(userMembershipAdminApplicationService.listMemberships(userId)
                .stream()
                .map(this::toResponse)
                .toList());
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ORG_MANAGE)")
    @PutMapping("/{userId}/memberships")
    public ApiResponse<Void> replaceMemberships(@PathVariable Long userId,
                                                @Valid @RequestBody ReplaceUserMembershipsRequest request) {
        userMembershipAdminApplicationService.replaceMemberships(userId, new ReplaceUserMembershipsCommand(
                request.getMemberships().stream()
                        .map(item -> new ReplaceUserMembershipItemCommand(item.getOrgUnitId(), Boolean.TRUE.equals(item.getIsPrimary())))
                        .toList()
        ));
        return ApiResponse.success(null);
    }

    private UserMembershipResponse toResponse(UserMembershipAdminView view) {
        return new UserMembershipResponse(
                view.membershipId(),
                view.orgUnitId(),
                view.orgUnitName(),
                view.orgUnitType(),
                view.isPrimary(),
                view.status()
        );
    }
}
