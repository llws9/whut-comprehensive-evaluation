package edu.whut.eval.app.review;

import edu.whut.eval.interfaces.review.ReviewTaskController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskControllerSecurityAnnotationTest {

    private static final String REVIEW_TASK_VIEW_AUTH =
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).REVIEW_TASK_VIEW)";

    @Test
    void shouldRequireReviewTaskViewAuthorityOnTaskEndpoints() {
        Set<String> endpointMethods = Set.of("getSummary");
        Set<String> annotatedMethods = Arrays.stream(ReviewTaskController.class.getDeclaredMethods())
                .filter(method -> endpointMethods.contains(method.getName()))
                .peek(method -> {
                    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                    assertThat(preAuthorize)
                            .as(method.getName() + " must declare @PreAuthorize")
                            .isNotNull();
                    assertThat(preAuthorize.value()).isEqualTo(REVIEW_TASK_VIEW_AUTH);
                })
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toSet());

        assertThat(annotatedMethods).containsExactlyInAnyOrderElementsOf(endpointMethods);
    }
}
