package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.admin.AdminFinalScoreExportController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdminFinalScoreExportControllerSecurityAnnotationTest {

    private static final String SCORE_EXPORT_ASSIGNED =
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_EXPORT_ASSIGNED)";

    @Test
    void shouldDeclareRouteAndExportAuthority() throws NoSuchMethodException {
        RequestMapping requestMapping = AdminFinalScoreExportController.class.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api/admin/exports");

        var method = Arrays.stream(AdminFinalScoreExportController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("exportFinalScores"))
                .findFirst()
                .orElseThrow();

        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        assertThat(getMapping).isNotNull();
        assertThat(getMapping.value()).containsExactly("/final-scores");

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(SCORE_EXPORT_ASSIGNED);
        assertThat(AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED).isEqualTo("score.export.assigned");
    }
}
