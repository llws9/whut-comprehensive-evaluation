package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.infra.security.context.CurrentUser;
import edu.whut.eval.infra.security.context.SecurityContextCurrentUserProvider;
import edu.whut.eval.infra.security.context.SecurityContextUserAuthorizationContextAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextUserAuthorizationContextAssemblerTest {

    private final SecurityContextUserAuthorizationContextAssembler assembler =
            new SecurityContextUserAuthorizationContextAssembler(new SecurityContextCurrentUserProvider());

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAssembleAuthorizationContextFromCurrentUser() {
        CurrentUser currentUser = new CurrentUser(
                1001L,
                "2024305999",
                "Test User",
                "student",
                "sid-1001",
                Set.of("student", "class-monitor"),
                Set.of("application.view.self", "score.view.self"),
                List.of(
                        new IamScopeRule(1L, "application.view.self", "SELF", null, null, null, null, 10, "ACTIVE"),
                        new IamScopeRule(2L, "score.view.self", "CLASS", 101L, null, null, null, 20, "ACTIVE")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, "N/A")
        );

        UserAuthorizationContext context = assembler.requiredAuthorizationContext();

        assertThat(context.getUserId()).isEqualTo(1001L);
        assertThat(context.getUserNo()).isEqualTo("2024305999");
        assertThat(context.getUserName()).isEqualTo("Test User");
        assertThat(context.getIdentity()).isEqualTo("student");
        assertThat(context.getSessionId()).isEqualTo("sid-1001");
        assertThat(context.getRoles()).containsExactlyInAnyOrder("student", "class-monitor");
        assertThat(context.getAuthorities()).containsExactlyInAnyOrder("application.view.self", "score.view.self");
        assertThat(context.hasRole("student")).isTrue();
        assertThat(context.hasAuthority("score.view.self")).isTrue();
        assertThat(context.findScopeRulesByPermissionCode("application.view.self")).hasSize(1);
        assertThat(context.findScopeRulesByPermissionCode("score.view.self")).hasSize(1);
        assertThat(context.findScopeRulesByPermissionCode("unknown.permission")).isEmpty();
    }

    @Test
    void shouldRejectWhenAuthorizationContextIsMissing() {
        assertThatThrownBy(assembler::requiredAuthorizationContext)
                .isInstanceOf(AuthenticationFailedException.class);
    }
}
