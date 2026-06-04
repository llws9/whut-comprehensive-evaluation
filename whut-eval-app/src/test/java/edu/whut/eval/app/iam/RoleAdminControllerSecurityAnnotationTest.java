package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.iam.RoleAdminController;
import edu.whut.eval.interfaces.iam.request.CreateRoleRequest;
import edu.whut.eval.interfaces.iam.request.UpdateRoleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAdminControllerSecurityAnnotationTest {

    @Test
    void shouldDeclareRoleManageOnCreateRoleEndpoint() throws NoSuchMethodException {
        Method method = RoleAdminController.class.getMethod("createRole", CreateRoleRequest.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.ROLE_MANAGE).isEqualTo("role.manage");
    }

    @Test
    void shouldDeclareRoleManageOnUpdateRoleEndpoint() throws NoSuchMethodException {
        Method method = RoleAdminController.class.getMethod("updateRole", Long.class, UpdateRoleRequest.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ROLE_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.ROLE_MANAGE).isEqualTo("role.manage");
    }
}
