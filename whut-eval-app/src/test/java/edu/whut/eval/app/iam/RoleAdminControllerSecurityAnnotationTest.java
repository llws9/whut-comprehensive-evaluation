package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.iam.RoleAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAdminControllerSecurityAnnotationTest {

    @Test
    void shouldDeclareExpectedPermissionsOnEndpoints() throws NoSuchMethodException {
        Method pageMethod = RoleAdminController.class.getMethod("pageRoles", long.class, long.class, String.class, String.class);
        Method createMethod = RoleAdminController.class.getMethod("createRole", edu.whut.eval.interfaces.iam.request.CreateRoleRequest.class);
        Method updateMethod = RoleAdminController.class.getMethod("updateRole", Long.class, edu.whut.eval.interfaces.iam.request.UpdateRoleRequest.class);
        Method replacePermissionsMethod = RoleAdminController.class.getMethod(
                "replaceRolePermissions",
                Long.class,
                edu.whut.eval.interfaces.iam.request.ReplaceRolePermissionsRequest.class
        );

        assertThat(pageMethod.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(pageMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)"
        );
        assertThat(createMethod.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(createMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)"
        );
        assertThat(updateMethod.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(updateMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)"
        );
        assertThat(replacePermissionsMethod.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(replacePermissionsMethod.getAnnotation(PreAuthorize.class).value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).PERMISSION_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.ROLE_MANAGE).isEqualTo("role.manage");
        assertThat(AuthorizationPermissionCodes.PERMISSION_MANAGE).isEqualTo("permission.manage");
    }
}
