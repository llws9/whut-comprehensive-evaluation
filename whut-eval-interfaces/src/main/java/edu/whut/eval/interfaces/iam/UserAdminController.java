package edu.whut.eval.interfaces.iam;

import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.command.ImportUsersCommand;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserImportResultView;
import edu.whut.eval.application.iam.query.UserAdminView;
import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.application.iam.service.UserAdminCommandApplicationService;
import edu.whut.eval.application.iam.service.UserAdminQueryApplicationService;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.iam.request.CreateUserRequest;
import edu.whut.eval.interfaces.iam.request.UpdateUserStatusRequest;
import edu.whut.eval.interfaces.iam.response.UserImportFailedRowResponse;
import edu.whut.eval.interfaces.iam.response.UserImportResponse;
import edu.whut.eval.interfaces.iam.response.UserAdminPageItemResponse;
import edu.whut.eval.interfaces.iam.response.UserAdminResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@Validated
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminCommandApplicationService userAdminCommandApplicationService;
    private final UserAdminQueryApplicationService userAdminQueryApplicationService;

    public UserAdminController(UserAdminCommandApplicationService userAdminCommandApplicationService,
                               UserAdminQueryApplicationService userAdminQueryApplicationService) {
        this.userAdminCommandApplicationService = userAdminCommandApplicationService;
        this.userAdminQueryApplicationService = userAdminQueryApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
    @PostMapping
    public ApiResponse<UserAdminResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserAdminView view = userAdminCommandApplicationService.createUser(new CreateUserCommand(
                request.getUserNo(),
                request.getUserName(),
                request.getPassword(),
                request.getEmail(),
                request.getPhone(),
                request.getPrimaryOrgUnitId()
        ));
        return ApiResponse.success(toUserResponse(view));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_IMPORT)")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserImportResponse> importUsers(@RequestPart("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "UPSERT") String importMode) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("导入文件不能为空");
        }
        try {
            UserImportResultView view = userAdminCommandApplicationService.importUsers(new ImportUsersCommand(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    file.getSize(),
                    importMode
            ));
            return ApiResponse.success(new UserImportResponse(
                    view.totalCount(),
                    view.successCount(),
                    view.failedCount(),
                    view.failedRows().stream()
                            .map(item -> new UserImportFailedRowResponse(item.rowNo(), item.userNo(), item.reason()))
                            .toList()
            ));
        } catch (IOException exception) {
            throw new FileStorageException("导入文件读取失败", exception);
        }
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
    @GetMapping
    public ApiResponse<PageResult<UserAdminPageItemResponse>> pageUsers(@RequestParam(defaultValue = "1") long pageNo,
                                                                        @RequestParam(defaultValue = "20") long pageSize,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) String status,
                                                                        @RequestParam(required = false) Long orgUnitId) {
        PageResult<UserAdminPageItemView> page = userAdminQueryApplicationService.pageUsers(
                new UserAdminPageQuery(pageNo, pageSize, keyword, status, orgUnitId)
        );
        return ApiResponse.success(new PageResult<>(
                page.total(),
                page.records().stream().map(this::toResponse).toList()
        ));
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)")
    @PatchMapping("/{userId}/status")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long userId,
                                              @Valid @RequestBody UpdateUserStatusRequest request) {
        userAdminCommandApplicationService.updateUserStatus(
                userId,
                new UpdateUserStatusCommand(request.getStatus(), request.getReason())
        );
        return ApiResponse.success(null);
    }

    private UserAdminPageItemResponse toResponse(UserAdminPageItemView view) {
        return new UserAdminPageItemResponse(
                view.userId(),
                view.userNo(),
                view.userName(),
                view.status(),
                view.orgUnits(),
                view.roleCodes(),
                view.createdAt()
        );
    }

    private UserAdminResponse toUserResponse(UserAdminView view) {
        return new UserAdminResponse(
                view.userId(),
                view.userNo(),
                view.userName(),
                view.status()
        );
    }
}
