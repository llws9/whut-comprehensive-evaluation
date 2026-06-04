package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.AccessSessionValidationCommand;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.infra.security.config.SecurityProperties;
import edu.whut.eval.infra.security.context.CurrentUser;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationFilter;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtClaimsToCurrentUserMapper;
import edu.whut.eval.infra.security.jwt.JwtTokenIssuer;
import edu.whut.eval.infra.security.jwt.JwtTokenPair;
import edu.whut.eval.infra.security.jwt.JwtTokenResolver;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;

class JwtAuthenticationFilterSessionClaimTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Test
    void shouldReadSessionClaimFromJwtProperties() throws Exception {
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setSecret(SECRET);
        properties.getJwt().setSessionIdClaim("session_id");
        JwtTokenIssuer issuer = new JwtTokenIssuer(properties);
        CurrentUser currentUser = new CurrentUser(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of("application.view.self"),
                List.of()
        );
        JwtTokenPair tokenPair = issuer.issueTokenPair(currentUser, "session-no-123");
        UserAuthorizationContextLoader contextLoader = mock(UserAuthorizationContextLoader.class);
        AccessSessionService accessSessionService = mock(AccessSessionService.class);
        given(contextLoader.load(any(UserAuthorizationContextLoadRequest.class))).willReturn(
                new UserAuthorizationContext(
                        1001L,
                        "2024305999",
                        "Test User",
                        "student",
                        Set.of("student"),
                        Set.of("application.view.self"),
                        List.of()
                )
        );
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                new JwtTokenResolver(properties),
                new JwtClaimsParser(properties),
                new JwtClaimsToCurrentUserMapper(properties),
                contextLoader,
                accessSessionService,
                new RestAuthenticationEntryPoint(),
                properties
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/security/me");
        request.addHeader("Authorization", "Bearer " + tokenPair.getAccessToken());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        then(accessSessionService).should().validateAccessSession(
                new AccessSessionValidationCommand(1001L, "session-no-123", tokenPair.getAccessTokenId())
        );
    }
}
