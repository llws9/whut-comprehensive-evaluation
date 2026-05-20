package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.iam.UserMembershipAdminController;
import edu.whut.eval.interfaces.iam.request.ReplaceUserMembershipsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserMembershipAdminControllerSecurityAnnotationTest {

    @Test
    void shouldDeclareOrgManageOnGetMembershipsEndpoint() throws NoSuchMethodException {
        Method method = UserMembershipAdminController.class.getMethod("listMemberships", Long.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ORG_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.ORG_MANAGE).isEqualTo("org.manage");
    }

    @Test
    void shouldDeclareOrgManageOnReplaceMembershipsEndpoint() throws NoSuchMethodException {
        Method method = UserMembershipAdminController.class.getMethod(
                "replaceMemberships",
                Long.class,
                ReplaceUserMembershipsRequest.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ORG_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.ORG_MANAGE).isEqualTo("org.manage");
    }
}
