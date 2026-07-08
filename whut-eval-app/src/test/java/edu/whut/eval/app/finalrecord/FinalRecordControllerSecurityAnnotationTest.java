package edu.whut.eval.app.finalrecord;

import edu.whut.eval.interfaces.admin.AdminFinalRecordController;
import edu.whut.eval.interfaces.student.StudentFinalRecordController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinalRecordControllerSecurityAnnotationTest {

    private static final String FINAL_VIEW_SELF =
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_VIEW_SELF)";
    private static final String FINAL_SUBMIT_SELF =
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_SUBMIT_SELF)";
    private static final String SCORE_VIEW_ASSIGNED =
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)";
    private static final String SCORE_CONFIRM_ASSIGNED =
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_CONFIRM_ASSIGNED)";

    @Test
    void shouldRequireStudentFinalRecordAuthorities() {
        Map<String, String> expected = Map.of(
                "getFinalRecord", FINAL_VIEW_SELF,
                "listComponents", FINAL_VIEW_SELF,
                "submit", FINAL_SUBMIT_SELF
        );

        expected.forEach((methodName, expression) -> assertPreAuthorize(StudentFinalRecordController.class, methodName, expression));
    }

    @Test
    void shouldRequireAdminFinalRecordAuthorities() {
        Map<String, String> expected = Map.of(
                "pageFinalRecords", SCORE_VIEW_ASSIGNED,
                "getFinalRecord", SCORE_VIEW_ASSIGNED,
                "confirm", SCORE_CONFIRM_ASSIGNED
        );

        expected.forEach((methodName, expression) -> assertPreAuthorize(AdminFinalRecordController.class, methodName, expression));
    }

    private void assertPreAuthorize(Class<?> controllerType, String methodName, String expression) {
        PreAuthorize annotation = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow()
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(methodName + " must declare @PreAuthorize").isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }
}
