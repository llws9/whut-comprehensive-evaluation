package edu.whut.eval.app.security;

import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.application.auth.model.AccessSessionValidationCommand;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityConfiguration;
import edu.whut.eval.infra.security.context.SecurityContextCurrentUserProvider;
import edu.whut.eval.infra.security.context.SecurityContextUserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationFilter;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtClaimsToCurrentUserMapper;
import edu.whut.eval.infra.security.jwt.JwtTokenResolver;
import edu.whut.eval.infra.security.web.RestAccessDeniedHandler;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityProbeController.class)
@ContextConfiguration(classes = SecurityProbeControllerWebMvcTest.TestApplication.class)
@Import({
        SecurityProbeController.class,
        SecurityConfiguration.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        JwtAuthenticationFilter.class,
        JwtTokenResolver.class,
        JwtClaimsParser.class,
        JwtClaimsToCurrentUserMapper.class,
        SecurityContextCurrentUserProvider.class,
        SecurityContextUserAuthorizationContextAssembler.class,
        JwtConfigurationValidator.class
})
@TestPropertySource(properties = {
        "infra.security.jwt.enabled=true",
        "infra.security.jwt.algorithm=HS256",
        "infra.security.jwt.issuer=whut-eval",
        "infra.security.jwt.audience=whut-eval-api",
        "infra.security.jwt.access-token-ttl-seconds=7200",
        "infra.security.jwt.refresh-token-ttl-seconds=604800",
        "infra.security.jwt.clock-skew-seconds=60",
        "infra.security.jwt.secret=test-jwt-secret-should-be-long-enough-1234567890",
        "infra.security.jwt.user-id-claim=uid",
        "infra.security.jwt.user-no-claim=uno",
        "infra.security.jwt.user-name-claim=uname",
        "infra.security.jwt.identity-claim=identity",
        "infra.security.jwt.roles-claim=roles",
        "infra.security.jwt.authorities-claim=authorities",
        "infra.security.jwt.token-type-claim=token_type",
        "infra.security.jwt.access-token-type=access",
        "infra.security.jwt.refresh-token-type=refresh"
})
class SecurityProbeControllerWebMvcTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private AccessSessionService accessSessionService;

    @Test
    void shouldReturn401WhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/security/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4010"));
    }

    @Test
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/api/security/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"));
    }

    @Test
    void shouldReturnCurrentUserWhenTokenIsValid() throws Exception {
        String token = createValidToken();
        given(userAuthorizationContextLoader.load(any(UserAuthorizationContextLoadRequest.class))).willReturn(
                new UserAuthorizationContext(
                        1001L,
                        "2024305999",
                        "Test User",
                        "student",
                        Set.of("student", "class-monitor"),
                        Set.of("system:security:probe", "application.view.self"),
                        List.of(new IamScopeRule(
                                101L,
                                "application.view.self",
                                "SELF",
                                null,
                                null,
                                null,
                                null,
                                10,
                                "ACTIVE"
                        ))
                )
        );

        mockMvc.perform(get("/api/security/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1001))
                .andExpect(jsonPath("$.data.userNo").value("2024305999"))
                .andExpect(jsonPath("$.data.userName").value("Test User"))
                .andExpect(jsonPath("$.data.identity").value("student"))
                .andExpect(jsonPath("$.data.roles", containsInAnyOrder("student", "class-monitor")))
                .andExpect(jsonPath("$.data.authorities", containsInAnyOrder("system:security:probe", "application.view.self")))
                .andExpect(jsonPath("$.data.scopeRules", hasSize(1)))
                .andExpect(jsonPath("$.data.scopeRules[0].permissionCode").value("application.view.self"))
                .andExpect(jsonPath("$.data.scopeRules[0].scopeType").value("SELF"));

        then(accessSessionService).should().validateAccessSession(
                new AccessSessionValidationCommand(1001L, "session-no-123", "access-jti-123")
        );
    }

    @Test
    void shouldReturn401WhenSessionIsRevokedOrExpired() throws Exception {
        String token = createValidToken();
        org.mockito.BDDMockito.willThrow(new AuthenticationFailedException("access token 会话不存在或已失效"))
                .given(accessSessionService)
                .validateAccessSession(new AccessSessionValidationCommand(1001L, "session-no-123", "access-jti-123"));

        mockMvc.perform(get("/api/security/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"));

        then(userAuthorizationContextLoader).should(never()).load(any(UserAuthorizationContextLoadRequest.class));
    }

    private String createValidToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .id("access-jti-123")
                .subject("1001")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("uid", 1001L)
                .claim("uno", "2024305999")
                .claim("uname", "Test User")
                .claim("identity", "student")
                .claim("sid", "session-no-123")
                .claim("roles", List.of("student", "class-monitor"))
                .claim("authorities", List.of("system:security:probe"))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
