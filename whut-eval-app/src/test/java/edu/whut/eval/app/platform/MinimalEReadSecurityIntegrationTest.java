package edu.whut.eval.app.platform;

import edu.whut.eval.application.auth.model.AccessSessionValidationCommand;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.application.file.query.FileMetadataResponse;
import edu.whut.eval.application.file.query.PublicAttachmentResponse;
import edu.whut.eval.application.file.service.FileQueryApplicationService;
import edu.whut.eval.application.file.service.PublicAttachmentCommandApplicationService;
import edu.whut.eval.application.platform.query.EvaluationItemResponse;
import edu.whut.eval.application.platform.query.PlatformMenuStatus;
import edu.whut.eval.application.platform.service.PlatformReadApplicationService;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityConfiguration;
import edu.whut.eval.infra.security.context.SecurityContextCurrentUserProvider;
import edu.whut.eval.infra.security.context.SecurityContextUserAuthorizationContextAssembler;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationFilter;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtClaimsToCurrentUserMapper;
import edu.whut.eval.infra.security.jwt.JwtTokenResolver;
import edu.whut.eval.infra.security.web.RestAccessDeniedHandler;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import edu.whut.eval.interfaces.file.FileQueryController;
import edu.whut.eval.interfaces.platform.PlatformReadController;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        PlatformReadController.class,
        FileQueryController.class
})
@ContextConfiguration(classes = MinimalEReadSecurityIntegrationTest.TestApplication.class)
@Import({
        PlatformReadController.class,
        FileQueryController.class,
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
class MinimalEReadSecurityIntegrationTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformReadApplicationService platformReadApplicationService;

    @MockBean
    private FileQueryApplicationService fileQueryApplicationService;

    @MockBean
    private PublicAttachmentCommandApplicationService publicAttachmentCommandApplicationService;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private AccessSessionService accessSessionService;

    @BeforeEach
    void setUpAuthenticatedStudent() {
        given(userAuthorizationContextLoader.load(any(UserAuthorizationContextLoadRequest.class))).willReturn(
                new UserAuthorizationContext(
                        1001L,
                        "2024305999",
                        "Test Student",
                        "student",
                        Set.of("student"),
                        Set.of("application.view.self"),
                        List.of()
                )
        );
    }

    @Test
    void shouldReturn401WhenAnonymousReadsPlatformStatus() throws Exception {
        mockMvc.perform(get("/api/platform/menu/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4010"));
    }

    @Test
    void shouldAllowAuthenticatedNonAdminToReadPlatformStatus() throws Exception {
        given(platformReadApplicationService.getMenuStatus())
                .willReturn(new PlatformMenuStatus(true, false, "NACOS"));

        mockMvc.perform(get("/api/platform/menu/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createValidToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.source").value("NACOS"));

        then(accessSessionService).should().validateAccessSession(
                new AccessSessionValidationCommand(1001L, "session-no-123", "access-jti-123")
        );
    }

    @Test
    void shouldAllowAuthenticatedNonAdminToReadEvaluationItems() throws Exception {
        given(platformReadApplicationService.listEvaluationItems(null))
                .willReturn(List.of(new EvaluationItemResponse(
                        "INTELLECTUAL",
                        "智育",
                        "INTELLECTUAL_PAPER",
                        "论文发表",
                        "学术论文发表加分",
                        new BigDecimal("6.00"),
                        null,
                        "STUDENT_APPLY",
                        true,
                        20,
                        "intellectual-paper"
                )));

        mockMvc.perform(get("/api/platform/evaluation-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createValidToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].itemCode").value("INTELLECTUAL_PAPER"));
    }

    @Test
    void shouldAllowAuthenticatedNonAdminToReadFileMetadata() throws Exception {
        given(fileQueryApplicationService.getMetadata("file-own"))
                .willReturn(new FileMetadataResponse(
                        "file-own",
                        "award.pdf",
                        "application/pdf",
                        128L,
                        "ACTIVE",
                        "SELF_UPLOAD",
                        true,
                        true,
                        LocalDateTime.parse("2026-07-06T10:00:00")
                ));

        mockMvc.perform(get("/api/files/file-own")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createValidToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value("file-own"));
    }

    @Test
    void shouldAllowAuthenticatedNonAdminToReadPublicAttachments() throws Exception {
        given(fileQueryApplicationService.listPublicAttachments(null))
                .willReturn(List.of(new PublicAttachmentResponse(
                        14001L,
                        "FILE-0008",
                        "综测申请模板",
                        "学生申请材料填写模板",
                        "INTELLECTUAL",
                        "综测申请模板.pdf",
                        "application/pdf",
                        142000L,
                        LocalDateTime.parse("2026-05-11T09:00:00"),
                        10
                )));

        mockMvc.perform(get("/api/files/public-attachments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createValidToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].entryId").value(14001));
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
                .claim("uname", "Test Student")
                .claim("identity", "student")
                .claim("sid", "session-no-123")
                .claim("roles", List.of("student"))
                .claim("authorities", List.of("application.view.self"))
                .claim("token_type", "access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
