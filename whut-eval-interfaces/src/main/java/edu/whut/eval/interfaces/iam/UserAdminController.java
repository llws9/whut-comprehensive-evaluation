package edu.whut.eval.interfaces.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserCreatedView;
import edu.whut.eval.application.iam.service.UserAdminApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.iam.request.CreateUserRequest;
import edu.whut.eval.interfaces.iam.request.UpdateUserStatusRequest;
import edu.whut.eval.interfaces.iam.response.UserCreatedResponse;
import edu.whut.eval.interfaces.iam.response.UserPageItemResponse;
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

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminApplicationService userAdminApplicationService;

    public UserAdminController(UserAdminApplicationService userAdminApplicationService) {
        this.userAdminApplicationService = userAdminApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
    @GetMapping
    public ApiResponse<PageResult<UserPageItemResponse>> pageUsers(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long orgUnitId) {

        UserAdminPageQuery query = new UserAdminPageQuery(pageNo, pageSize, userName, status, orgUnitId);
        PageResult<UserAdminPageItemView> result = userAdminApplicationService.pageUsers(query);
        PageResult<UserPageItemResponse> response = new PageResult<>(
                result.total(),
                result.records().stream().map(this::toPageItemResponse).toList()
        );
        return ApiResponse.success(response);
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
    @PostMapping
    public ApiResponse<UserCreatedResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserCommand command = new CreateUserCommand(
                request.getUserNo(),
                request.getUserName(),
                request.getPassword(),
                request.getEmail(),
                request.getPhone(),
                request.getPrimaryOrgUnitId()
        );
        UserCreatedView view = userAdminApplicationService.createUser(command);
        return ApiResponse.success(new UserCreatedResponse(
                view.userId(),
                view.userNo(),
                view.userName(),
                view.status()
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long userId,
                                          @Valid @RequestBody UpdateUserStatusRequest request) {
        userAdminApplicationService.updateStatus(userId, new UpdateUserStatusCommand(request.getStatus(), request.getReason()));
        return ApiResponse.success(null);
    }

    private UserPageItemResponse toPageItemResponse(UserAdminPageItemView view) {
        return new UserPageItemResponse(
                view.userId(),
                view.userNo(),
                view.userName(),
                view.status(),
                view.orgUnits(),
                view.roles(),
                view.createdAt()
        );
    }
}