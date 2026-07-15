package edu.whut.eval.app.platform;

import edu.whut.eval.interfaces.platform.PlatformReadController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformReadControllerSecurityAnnotationTest {

    @Test
    void shouldProtectPlatformRuleWriteEndpointsWithSwitchManageAuthority() throws Exception {
        Map<String, String> expectedExpressions = Map.of(
                "updateMenuStatus", "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).PLATFORM_SWITCH_MANAGE)",
                "replaceMenuDeadline", "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).PLATFORM_SWITCH_MANAGE)",
                "createEvaluationItem", "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).EVALUATION_ITEM_MANAGE)",
                "patchEvaluationItem", "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).EVALUATION_ITEM_MANAGE)"
        );

        for (Map.Entry<String, String> expectedExpression : expectedExpressions.entrySet()) {
            Method method = findMethod(expectedExpression.getKey());
            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).isEqualTo(expectedExpression.getValue());
        }
    }

    private Method findMethod(String methodName) {
        return java.util.Arrays.stream(PlatformReadController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }
}
