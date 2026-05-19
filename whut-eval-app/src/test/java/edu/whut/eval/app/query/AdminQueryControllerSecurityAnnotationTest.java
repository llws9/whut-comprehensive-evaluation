package edu.whut.eval.app.query;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.admin.AdminQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AdminQueryControllerSecurityAnnotationTest {

    @Test
    void shouldDeclareApplicationQueryPermissionOnApplicationsEndpoint() throws NoSuchMethodException {
        Method method = AdminQueryController.class.getMethod(
                "pageApplications",
                long.class,
                long.class,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)"
        );
        assertThat(AuthorizationPermissionCodes.APPLICATION_REVIEW).isEqualTo("application.review");
    }

    @Test
    void shouldDeclareScoreQueryPermissionOnScoresEndpoint() throws NoSuchMethodException {
        Method method = AdminQueryController.class.getMethod(
                "pageScores",
                long.class,
                long.class,
                Long.class,
                Long.class,
                Long.class,
                String.class,
                String.class,
                String.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)"
        );
        assertThat(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED).isEqualTo("score.view.assigned");
    }

    @Test
    void shouldDeclarePermissionManageOnPermissionsEndpoint() throws NoSuchMethodException {
        Method method = AdminQueryController.class.getMethod(
                "listPermissions",
                String.class,
                String.class,
                String.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).PERMISSION_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.PERMISSION_MANAGE).isEqualTo("permission.manage");
    }

    @Test
    void shouldDeclareOrgManageOnOrgTreeEndpoint() throws NoSuchMethodException {
        Method method = AdminQueryController.class.getMethod(
                "listOrgUnitTree",
                Long.class,
                String.class,
                boolean.class
        );

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ORG_MANAGE)"
        );
        assertThat(AuthorizationPermissionCodes.ORG_MANAGE).isEqualTo("org.manage");
    }
}
