package edu.whut.eval.app.security;

import edu.whut.eval.application.auth.model.AuthenticatedUserSnapshot;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.LoginAuthenticationService;
import edu.whut.eval.application.auth.model.LoginSessionCreateCommand;
import edu.whut.eval.application.auth.model.RefreshSessionContinueCommand;
import edu.whut.eval.application.auth.model.RefreshSessionValidationCommand;
import edu.whut.eval.application.auth.service.LoginSessionService;
import edu.whut.eval.application.auth.service.LogoutService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.application.auth.service.RefreshSessionService;
import edu.whut.eval.application.auth.service.RefreshTokenCurrentUserLoader;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityConfiguration;
import edu.whut.eval.infra.security.context.CurrentUser;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationFilter;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtClaimsToCurrentUserMapper;
import edu.whut.eval.infra.security.jwt.JwtTokenIssuer;
import edu.whut.eval.infra.security.jwt.JwtTokenResolver;
import edu.whut.eval.infra.security.jwt.RefreshTokenClaimsMapper;
import edu.whut.eval.infra.security.web.RestAccessDeniedHandler;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = AuthControllerWebMvcTest.TestApplication.class)
@ExtendWith(OutputCaptureExtension.class)
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
        "infra.security.jwt.refresh-token-type=refresh",
        "infra.security.permit-all-patterns[0]=/error",
        "infra.security.permit-all-patterns[1]=/actuator/health",
        "infra.security.permit-all-patterns[2]=/actuator/info",
        "infra.security.permit-all-patterns[3]=/api/auth/login",
        "infra.security.permit-all-patterns[4]=/api/auth/refresh",
        "infra.security.permit-all-patterns[5]=/api/auth/logout",
        "infra.security.permit-all-patterns[6]=/swagger-ui/**",
        "infra.security.permit-all-patterns[7]=/v3/api-docs/**"
})
class AuthControllerWebMvcTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefreshTokenCurrentUserLoader refreshTokenCurrentUserLoader;

    @MockBean
    private RefreshSessionService refreshSessionService;

    @MockBean
    private LoginAuthenticationService loginAuthenticationService;

    @MockBean
    private LoginSessionService loginSessionService;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private AccessSessionService accessSessionService;

    @MockBean
    private LogoutService logoutService;

    @Test
    void shouldIssueTokenPairWhenLoginSucceeds() throws Exception {
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

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"2024305999\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.accessTokenType").value("access"))
                .andExpect(jsonPath("$.data.refreshTokenType").value("refresh"));
    }

    @Test
    void shouldNotLogPasswordPresenceOnLogin(CapturedOutput output) throws Exception {
        given(loginAuthenticationService.authenticate(eq("2024305999"), eq("secret")))
                .willReturn(new AuthenticatedUserSnapshot(
                        1001L,
                        "2024305999",
                        "Test User",
                        "student",
                        Set.of("student"),
                        Set.of("application.view.self"),
                        List.of()
                ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"2024305999\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());

        assertThat(output).doesNotContain("passwordPresent");
    }

    @Test
    void shouldCreateServerSessionAndReturnTokensWithSidWhenLoginSucceeds() throws Exception {
        given(loginAuthenticationService.authenticate(eq("2024305999"), eq("secret")))
                .willReturn(new AuthenticatedUserSnapshot(
                        1001L,
                        "2024305999",
                        "Test User",
                        "student",
                        Set.of("student"),
                        Set.of("application.view.self"),
                        List.of()
                ));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"2024305999\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String accessToken = com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.accessToken");
        String refreshToken = com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.refreshToken");
        Claims accessClaims = parseClaims(accessToken);
        Claims refreshClaims = parseClaims(refreshToken);

        assertThat(accessClaims.get("sid", String.class)).isNotBlank();
        assertThat(refreshClaims.get("sid", String.class)).isEqualTo(accessClaims.get("sid", String.class));
        ArgumentCaptor<LoginSessionCreateCommand> commandCaptor = ArgumentCaptor.forClass(LoginSessionCreateCommand.class);
        then(loginSessionService).should().createLoginSession(commandCaptor.capture());
        LoginSessionCreateCommand command = commandCaptor.getValue();
        assertThat(command.userId()).isEqualTo(1001L);
        assertThat(command.sessionNo()).isEqualTo(accessClaims.get("sid", String.class));
        assertThat(command.accessTokenId()).isEqualTo(accessClaims.getId());
        assertThat(command.refreshTokenId()).isEqualTo(refreshClaims.getId());
        assertThat(command.clientIp()).isEqualTo("127.0.0.1");
        assertThat(command.refreshTokenExpiresAt()).isNotNull();
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
    void shouldReturn4011WhenRefreshTokenIsExpired() throws Exception {
        String refreshToken = createExpiredRefreshToken();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4011"));
    }

    @Test
    void shouldReturn4012WhenRefreshSessionIsInvalid() throws Exception {
        String refreshToken = createRefreshToken();
        org.mockito.BDDMockito.willThrow(new AuthenticationFailedException("refresh token 会话不存在或已失效"))
                .given(refreshSessionService)
                .validateRefreshSession(any(RefreshSessionValidationCommand.class));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"))
                .andExpect(jsonPath("$.message").value("refresh token 会话不存在或已失效"));
    }

    @Test
    void shouldIssueNewTokenPairWhenRefreshTokenIsValid() throws Exception {
        String refreshToken = createRefreshToken();
        given(refreshTokenCurrentUserLoader.load(any())).willReturn(new AuthenticatedUserSnapshot(
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

        String responseBody = result.getResponse().getContentAsString();
        Claims accessClaims = parseClaims(com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.accessToken"));
        Claims refreshClaims = parseClaims(com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.refreshToken"));
        assertThat(accessClaims.get("sid", String.class)).isEqualTo("session-no-123");
        assertThat(refreshClaims.get("sid", String.class)).isEqualTo("session-no-123");
        then(refreshSessionService).should().validateRefreshSession(
                new RefreshSessionValidationCommand(1001L, "session-no-123", "refresh-jti-456")
        );
        ArgumentCaptor<RefreshSessionContinueCommand> continueCaptor = ArgumentCaptor.forClass(RefreshSessionContinueCommand.class);
        then(refreshSessionService).should(times(1)).continueRefreshSession(continueCaptor.capture());
        RefreshSessionContinueCommand continueCommand = continueCaptor.getValue();
        assertThat(continueCommand.sessionNo()).isEqualTo("session-no-123");
        assertThat(continueCommand.oldRefreshTokenId()).isEqualTo("refresh-jti-456");
        assertThat(continueCommand.newAccessTokenId()).isEqualTo(accessClaims.getId());
        assertThat(continueCommand.newRefreshTokenId()).isEqualTo(refreshClaims.getId());
        assertThat(continueCommand.refreshTokenExpiresAt()).isNotNull();
    }

    @Test
    void shouldReturn401WhenLogoutWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"));
    }

    @Test
    void shouldReturn401WhenLogoutWithInvalidToken() throws Exception {
        String invalidToken = "invalid-token-string";

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"));
    }

    @Test
    void shouldLogoutSuccessfullyWithValidToken() throws Exception {
        String accessToken = createAccessTokenWithJti();

        given(userAuthorizationContextLoader.load(any())).willReturn(
                new edu.whut.eval.domain.auth.model.UserAuthorizationContext(
                        1001L,
                        "2024305999",
                        "Test User",
                        "student",
                        Set.of("student"),
                        Set.of("evaluation:apply:create"),
                        List.of()
                )
        );

        given(logoutService.logoutByAccessTokenId(eq("test-jti-123")))
                .willReturn(true);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }


    @Test
    void shouldReturn401WhenLogoutSessionDoesNotExistOrIsInvalid() throws Exception {
        String accessToken = createAccessTokenWithJti();
        given(userAuthorizationContextLoader.load(any())).willReturn(
                new edu.whut.eval.domain.auth.model.UserAuthorizationContext(
                        1001L,
                        "2024305999",
                        "Test User",
                        "student",
                        Set.of("student"),
                        Set.of("evaluation:apply:create"),
                        List.of()
                )
        );
        given(logoutService.logoutByAccessTokenId(eq("test-jti-123")))
                .willReturn(false);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4012"));
    }

    private String createAccessToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .id("refresh-jti-456")
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

    private String createAccessTokenWithJti() {
        Instant now = Instant.now();
        return Jwts.builder()
                .id("test-jti-123")
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
                .claim("sid", "session-no-123")
                .claim("roles", List.of("student", "class-monitor"))
                .claim("authorities", List.of("system:security:probe"))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String createRefreshToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .id("refresh-jti-456")
                .subject("1001")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("token_type", "refresh")
                .claim("uid", 1001L)
                .claim("uno", "2024305999")
                .claim("identity", "student")
                .claim("sid", "session-no-123")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String createExpiredRefreshToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .id("expired-refresh-jti")
                .subject("1001")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now.minusSeconds(7200)))
                .expiration(Date.from(now.minusSeconds(120)))
                .claim("token_type", "refresh")
                .claim("uid", 1001L)
                .claim("uno", "2024305999")
                .claim("identity", "student")
                .claim("sid", "expired-session-no")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
