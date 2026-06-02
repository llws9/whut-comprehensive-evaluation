package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.iam.UserIdentityQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdentityQueryControllerSecurityAnnotationTest {

    @Test
    void shouldRequireUserManageAuthorityOnIdentityEndpoint() throws NoSuchMethodException {
        Method method = UserIdentityQueryController.class.getMethod(
                "getUserIdentity",
                String.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.USER_MANAGE).isEqualTo("user.manage");
    }
}
