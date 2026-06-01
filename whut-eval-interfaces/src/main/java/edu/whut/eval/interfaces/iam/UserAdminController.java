package edu.whut.eval.interfaces.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.command.ImportUsersCommand;
import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserCreatedView;
import edu.whut.eval.application.iam.query.UserImportResultView;
import edu.whut.eval.application.iam.service.UserAdminApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.iam.request.CreateUserRequest;
import edu.whut.eval.interfaces.iam.request.UpdateUserStatusRequest;
import edu.whut.eval.interfaces.iam.response.UserCreatedResponse;
import edu.whut.eval.interfaces.iam.response.UserImportFailedRowResponse;
import edu.whut.eval.interfaces.iam.response.UserImportResultResponse;
import edu.whut.eval.interfaces.iam.response.UserPageItemResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.multipart.MultipartFile;

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
            @RequestParam(defaultValue = "1") @Positive long pageNo,
            @RequestParam(defaultValue = "20") @Positive long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long orgUnitId) {

        UserAdminPageQuery query = new UserAdminPageQuery(pageNo, pageSize, keyword, status, orgUnitId);
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

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_IMPORT)")
    @PostMapping("/import")
    public ApiResponse<UserImportResultResponse> importUsers(@RequestParam("file") MultipartFile file,
                                                             @RequestParam(value = "importMode", defaultValue = "UPSERT") String importMode)
            throws java.io.IOException {
        if (importMode == null || (!"UPSERT".equals(importMode) && !"INSERT_ONLY".equals(importMode))) {
            throw new ValidationException("importMode 仅允许 UPSERT 或 INSERT_ONLY");
        }
        UserImportResultView view = userAdminApplicationService.importUsers(new ImportUsersCommand(file.getBytes(), importMode));
        return ApiResponse.success(new UserImportResultResponse(
                view.totalCount(),
                view.successCount(),
                view.failedCount(),
                view.failedRows().stream().map(item -> new UserImportFailedRowResponse(item.rowNo(), item.reason())).toList()
        ));
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