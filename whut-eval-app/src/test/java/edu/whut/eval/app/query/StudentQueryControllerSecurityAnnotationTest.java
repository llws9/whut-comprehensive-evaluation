package edu.whut.eval.app.query;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.student.StudentQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StudentQueryControllerSecurityAnnotationTest {

    @Test
    void shouldDeclareApplicationQueryPermissionOnApplicationsEndpoint() throws NoSuchMethodException {
        Method method = StudentQueryController.class.getMethod(
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
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)"
        );
        assertThat(AuthorizationPermissionCodes.APPLICATION_VIEW_SELF).isEqualTo("application.view.self");
    }

    @Test
    void shouldDeclareScoreQueryPermissionOnScoresEndpoint() throws NoSuchMethodException {
        Method method = StudentQueryController.class.getMethod(
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
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_SELF)"
        );
        assertThat(AuthorizationPermissionCodes.SCORE_VIEW_SELF).isEqualTo("score.view.self");
    }
}
