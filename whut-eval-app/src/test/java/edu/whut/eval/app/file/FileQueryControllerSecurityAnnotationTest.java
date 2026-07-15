package edu.whut.eval.app.file;

import edu.whut.eval.interfaces.file.FileQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FileQueryControllerSecurityAnnotationTest {

    private static final Map<String, String> EXPECTED_AUTH = Map.of(
            "publishPublicAttachment",
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ATTACHMENT_POOL_PUBLISH)",
            "offlinePublicAttachment",
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).ATTACHMENT_POOL_OFFLINE)"
    );

    @Test
    void shouldRequirePublicAttachmentCommandAuthorities() {
        Set<String> annotatedMethods = Arrays.stream(FileQueryController.class.getDeclaredMethods())
                .filter(method -> EXPECTED_AUTH.containsKey(method.getName()))
                .peek(method -> {
                    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                    assertThat(preAuthorize)
                            .as(method.getName() + " must declare @PreAuthorize")
                            .isNotNull();
                    assertThat(preAuthorize.value()).isEqualTo(EXPECTED_AUTH.get(method.getName()));
                })
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertThat(annotatedMethods).containsExactlyInAnyOrderElementsOf(EXPECTED_AUTH.keySet());
    }
}
