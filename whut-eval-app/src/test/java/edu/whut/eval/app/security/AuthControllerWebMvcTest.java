package edu.whut.eval.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;
import edu.whut.eval.application.auth.model.LoginSessionCreateCommand;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.LoginAuthenticationService;
import edu.whut.eval.application.auth.service.IamSessionAccessService;
import edu.whut.eval.application.auth.service.LoginSessionCommandService;
import edu.whut.eval.application.auth.service.LogoutSessionCommandService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.application.auth.service.RefreshTokenCurrentUserLoader;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityConfiguration;
import edu.whut.eval.infra.security.context.CurrentUser;
import edu.whut.eval.infra.security.context.CurrentUserProvider;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationFilter;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtClaimsToCurrentUserMapper;
import edu.whut.eval.infra.security.jwt.JwtTokenIssuer;
import edu.whut.eval.infra.security.jwt.JwtTokenResolver;
import edu.whut.eval.infra.security.jwt.RefreshTokenClaimsMapper;
import edu.whut.eval.infra.security.web.RestAccessDeniedHandler;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = AuthControllerWebMvcTest.TestApplication.class)
@Import({
        AuthController.class,
        AuthTokenResponseAssembler.class,
        SecurityConfiguration.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        JwtAuthenticationFilter.class,
        JwtTokenResolver.class,
        JwtClaimsParser.class,
        JwtClaimsToCurrentUserMapper.class,
        JwtTokenIssuer.class,
        RefreshTokenClaimsMapper.class,
        JwtConfigurationValidator.class,
        GlobalExceptionHandler.class
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
class AuthControllerWebMvcTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefreshTokenCurrentUserLoader refreshTokenCurrentUserLoader;

    @MockBean
    private LoginAuthenticationService loginAuthenticationService;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private LoginSessionCommandService loginSessionCommandService;

    @MockBean
    private IamSessionAccessService iamSessionAccessService;

    @MockBean
    private LogoutSessionCommandService logoutSessionCommandService;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void shouldIssueTokenPairWhenLoginSucceedsAndCreateSession() throws Exception {
        given(loginAuthenticationService.authenticate(eq("2024305999"), eq("secret")))
                .willReturn(new AuthenticatedUserSnapshot(
                        1001L,
                        "2024305999",
                        "Test User",
                        "student",
                        Set.of("student", "class-monitor"),
                        Set.of("application.view.self", "application.view.assigned"),
                        List.of(new IamScopeRule(
                                101L,
                                "application.view.self",
                                "SELF",
                                null,
                                null,
                                null,
                                "{\"applicantUserId\":{\"eq\":\"currentUser.userId\"}}",
                                10,
                                "ACTIVE"
                        ))
                ));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .header("User-Agent", "JUnit-Agent")
                        .content("{\"credential\":\"2024305999\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.accessTokenType").value("access"))
                .andExpect(jsonPath("$.data.refreshTokenType").value("refresh"))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String refreshTokenExpiresAt = objectMapper.readTree(responseJson)
                .path("data")
                .path("refreshTokenExpiresAt")
                .asText();
        LocalDateTime expectedExpiredAt = LocalDateTime.ofInstant(
                Instant.parse(refreshTokenExpiresAt),
                ZoneId.systemDefault()
        );

        org.mockito.ArgumentCaptor<LoginSessionCreateCommand> captor =
                org.mockito.ArgumentCaptor.forClass(LoginSessionCreateCommand.class);
        verify(loginSessionCommandService).create(captor.capture());
        assertThat(captor.getValue()).satisfies(command -> {
            assertThat(command.userId()).isEqualTo(1001L);
            assertThat(command.sessionId()).isNotBlank();
            assertThat(command.loginIp()).isEqualTo("203.0.113.10");
            assertThat(command.userAgent()).isEqualTo("JUnit-Agent");
            assertThat(command.expiredAt()).isEqualTo(expectedExpiredAt);
        });
    }

    @Test
    void shouldReturn401WhenLoginFails() throws Exception {
        given(loginAuthenticationService.authenticate(eq("2024305999"), eq("bad-secret")))
                .willThrow(new AuthenticationFailedException("登录账号或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"2024305999\",\"password\":\"bad-secret\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4010"))
                .andExpect(jsonPath("$.message").value("登录账号或密码错误"));
    }

    @Test
    void shouldReturn401WhenRefreshTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"));
    }

    @Test
    void shouldReturn401WhenRefreshEndpointReceivesAccessToken() throws Exception {
        String accessToken = createAccessToken();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"))
                .andExpect(jsonPath("$.message").value("refresh token 校验失败: JWT token type is not refresh"));
    }

    @Test
    void shouldIssueNewTokenPairWhenRefreshTokenIsValid() throws Exception {
        String refreshToken = createRefreshToken();
        given(refreshTokenCurrentUserLoader.load(argThat(context ->
                context != null && "sid-1001".equals(context.sessionId())
        ))).willReturn(new AuthenticatedUserSnapshot(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student", "class-monitor"),
                Set.of("evaluation:apply:create", "evaluation:record:query"),
                List.of(new IamScopeRule(
                        101L,
                        "application.view.assigned",
                        "ORG_UNIT",
                        3001L,
                        null,
                        null,
                        null,
                        10,
                        "ACTIVE"
                ))
        ));

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessTokenType").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.refreshTokenType").value("refresh"))
                .andReturn();

        String refreshTokenExpiresAt = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("refreshTokenExpiresAt")
                .asText();
        verify(iamSessionAccessService).extendExpiration(
                eq("sid-1001"),
                eq(LocalDateTime.ofInstant(Instant.parse(refreshTokenExpiresAt), ZoneId.systemDefault()))
        );
    }

    @Test
    void shouldReturn4012WhenRefreshTokenDoesNotContainSid() throws Exception {
        String legacyRefreshToken = createLegacyRefreshTokenWithoutSid();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + legacyRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"));
    }

    @Test
    void shouldReturn4012WhenRefreshSessionIsInvalid() throws Exception {
        String refreshToken = createRefreshToken();
        org.mockito.BDDMockito.willThrow(new edu.whut.eval.infra.security.jwt.JwtAuthenticationException("session is invalid"))
                .given(iamSessionAccessService)
                .assertActive("sid-1001");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"));
    }

    @Test
    void shouldReturn4010WhenRefreshReloadAuthenticationFails() throws Exception {
        String refreshToken = createRefreshToken();
        given(refreshTokenCurrentUserLoader.load(argThat(context ->
                context != null && "sid-1001".equals(context.sessionId())
        ))).willThrow(new AuthenticationFailedException("refresh token 对应用户已失效"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4010"))
                .andExpect(jsonPath("$.message").value("refresh token 对应用户已失效"));
    }

    @Test
    void shouldReturn4010WhenLogoutWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4010"));

        verify(logoutSessionCommandService, never()).logout(any());
    }

    @Test
    void shouldLogoutCurrentSession() throws Exception {
        given(userAuthorizationContextLoader.load(argThat(request ->
                request != null && "sid-logout".equals(request.getSessionId())
        ))).willReturn(new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                "sid-logout",
                Set.of("student"),
                Set.of("system:security:probe"),
                List.of()
        ));
        given(currentUserProvider.requiredCurrentUser()).willReturn(new CurrentUser(
                1001L,
                "2024305999",
                "Test User",
                "student",
                "sid-logout",
                Set.of("student"),
                Set.of("system:security:probe"),
                List.of()
        ));

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createLogoutAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(logoutSessionCommandService).logout("sid-logout");
    }

    private String createAccessToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("1001")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("token_type", "access")
                .claim("uid", 1001L)
                .claim("uno", "2024305999")
                .claim("uname", "Test User")
                .claim("identity", "student")
                .claim("roles", List.of("student", "class-monitor"))
                .claim("authorities", List.of("system:security:probe"))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String createLogoutAccessToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("1001")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("token_type", "access")
                .claim("uid", 1001L)
                .claim("uno", "2024305999")
                .claim("uname", "Test User")
                .claim("identity", "student")
                .claim("sid", "sid-logout")
                .claim("roles", List.of("student"))
                .claim("authorities", List.of("system:security:probe"))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String createRefreshToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("1001")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("token_type", "refresh")
                .claim("uid", 1001L)
                .claim("uno", "2024305999")
                .claim("identity", "student")
                .claim("sid", "sid-1001")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String createLegacyRefreshTokenWithoutSid() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("1001")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("token_type", "refresh")
                .claim("uid", 1001L)
                .claim("uno", "2024305999")
                .claim("identity", "student")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
