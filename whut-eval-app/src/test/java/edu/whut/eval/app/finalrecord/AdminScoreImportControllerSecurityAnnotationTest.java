package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.admin.AdminScoreImportController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class AdminScoreImportControllerSecurityAnnotationTest {

    @Test
    void shouldRequireScoreImportAuthority() throws Exception {
        PreAuthorize preAuthorize = AdminScoreImportController.class
                .getMethod("importMentorScores", MultipartFile.class, String.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)"
        );
        assertThat(AuthorizationPermissionCodes.SCORE_IMPORT).isEqualTo("score.import");
    }
}
