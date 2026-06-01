package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.iam.UserAdminController;
import edu.whut.eval.interfaces.iam.request.CreateUserRequest;
import edu.whut.eval.interfaces.iam.request.UpdateUserStatusRequest;
import org.springframework.web.multipart.MultipartFile;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserAdminControllerSecurityAnnotationTest {

    @Test
    void shouldRequireUserManageAuthorityOnPageEndpoint() throws NoSuchMethodException {
        Method method = UserAdminController.class.getMethod(
                "pageUsers",
                long.class,
                long.class,
                String.class,
                String.class,
                Long.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.USER_MANAGE).isEqualTo("user.manage");
    }

    @Test
    void shouldRequireUserManageAuthorityOnCreateEndpoint() throws NoSuchMethodException {
        Method method = UserAdminController.class.getMethod(
                "createUser",
                CreateUserRequest.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.USER_MANAGE).isEqualTo("user.manage");
    }

    @Test
    void shouldRequireUserManageAuthorityOnUpdateStatusEndpoint() throws NoSuchMethodException {
        Method method = UserAdminController.class.getMethod(
                "updateStatus",
                Long.class,
                UpdateUserStatusRequest.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.USER_MANAGE).isEqualTo("user.manage");
    }

    @Test
    void shouldRequireUserImportAuthorityOnImportEndpoint() throws NoSuchMethodException {
        Method method = UserAdminController.class.getMethod(
                "importUsers",
                MultipartFile.class,
                String.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_IMPORT)"
        );
        assertThat(AuthorizationPermissionCodes.USER_IMPORT).isEqualTo("user.import");
    }
}