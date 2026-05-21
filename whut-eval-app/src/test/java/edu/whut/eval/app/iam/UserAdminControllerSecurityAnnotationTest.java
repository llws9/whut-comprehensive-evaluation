package edu.whut.eval.app.iam;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.iam.UserAdminController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserAdminControllerSecurityAnnotationTest {

    @Test
    void shouldDeclareUserImportOnImportUsersEndpoint() throws NoSuchMethodException {
        Method method = UserAdminController.class.getMethod("importUsers", MultipartFile.class, String.class);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).USER_IMPORT)"
        );
        assertThat(AuthorizationPermissionCodes.USER_IMPORT).isEqualTo("user.import");
    }
}
