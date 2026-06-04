package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.iam.RoleAdminController;
import edu.whut.eval.interfaces.iam.request.CreateRoleRequest;
import edu.whut.eval.interfaces.iam.request.ReplaceRolePermissionsRequest;
import edu.whut.eval.interfaces.iam.request.UpdateRoleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAdminControllerSecurityAnnotationTest {

    @Test
    void shouldUseRoleManageForRoleTemplateManagement() throws Exception {
        Method pageRoles = RoleAdminController.class.getMethod("pageRoles", long.class, long.class, String.class, String.class);
        Method createRole = RoleAdminController.class.getMethod("createRole", CreateRoleRequest.class);
        Method updateRole = RoleAdminController.class.getMethod("updateRole", Long.class, UpdateRoleRequest.class);

        String expected = "hasAuthority(T(" + AuthorizationPermissionCodes.class.getName() + ").ROLE_MANAGE)";
        assertThat(pageRoles.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
        assertThat(createRole.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
        assertThat(updateRole.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
    }

    @Test
    void shouldUsePermissionManageForRolePermissionBinding() throws Exception {
        Method replacePermissions = RoleAdminController.class.getMethod("replacePermissions", Long.class, ReplaceRolePermissionsRequest.class);

        assertThat(replacePermissions.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority(T(" + AuthorizationPermissionCodes.class.getName() + ").PERMISSION_MANAGE)");
    }
}
