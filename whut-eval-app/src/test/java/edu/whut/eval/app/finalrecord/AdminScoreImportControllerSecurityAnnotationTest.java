package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.interfaces.admin.AdminScoreImportController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Test
    void shouldRequireScoreImportAuthorityForLectureImport() throws Exception {
        PreAuthorize preAuthorize = AdminScoreImportController.class
                .getMethod("importLectures", MultipartFile.class, String.class, String.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)"
        );
    }

    @Test
    void shouldExposeLectureImportOnExactMultipartRoute() throws Exception {
        PostMapping postMapping = AdminScoreImportController.class
                .getMethod("importLectures", MultipartFile.class, String.class, String.class, String.class)
                .getAnnotation(PostMapping.class);

        assertThat(postMapping).isNotNull();
        assertThat(postMapping.value()).containsExactly("/lectures");
        assertThat(postMapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(AdminScoreImportController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/admin/imports");
    }

    @Test
    void shouldRequireScoreImportAuthorityForActivityImport() throws Exception {
        PreAuthorize preAuthorize = AdminScoreImportController.class
                .getMethod("importActivities", MultipartFile.class, String.class, String.class, String.class, String.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo(
                "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)"
        );
    }

    @Test
    void shouldExposeActivityImportOnExactMultipartRoute() throws Exception {
        PostMapping postMapping = AdminScoreImportController.class
                .getMethod("importActivities", MultipartFile.class, String.class, String.class, String.class, String.class, String.class)
                .getAnnotation(PostMapping.class);

        assertThat(postMapping).isNotNull();
        assertThat(postMapping.value()).containsExactly("/cas-activities");
        assertThat(postMapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(AdminScoreImportController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/api/admin/imports");
    }
}
