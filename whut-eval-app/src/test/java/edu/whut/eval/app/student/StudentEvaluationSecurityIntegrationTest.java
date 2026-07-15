package edu.whut.eval.app.student;

import edu.whut.eval.application.application.query.StudentEvaluationItemView;
import edu.whut.eval.application.application.query.StudentEvaluationPointsView;
import edu.whut.eval.application.application.service.StudentEvaluationApplicationService;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
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
import edu.whut.eval.interfaces.student.StudentEvaluationController;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudentEvaluationController.class)
@ContextConfiguration(classes = StudentEvaluationSecurityIntegrationTest.TestApplication.class)
@Import({
        StudentEvaluationController.class,
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
class StudentEvaluationSecurityIntegrationTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentEvaluationApplicationService studentEvaluationApplicationService;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private AccessSessionService accessSessionService;

    @BeforeEach
    void setUpSecurityContext() {
        given(userAuthorizationContextLoader.load(any(UserAuthorizationContextLoadRequest.class))).willAnswer(invocation -> {
            UserAuthorizationContextLoadRequest request = invocation.getArgument(0);
            return new UserAuthorizationContext(
                    request.getUserId(),
                    request.getUserNo(),
                    request.getUserName(),
                    request.getIdentity(),
                    request.getRoles(),
                    authoritiesFor(request.getUserId()),
                    List.of()
            );
        });
        given(studentEvaluationApplicationService.listItems("INTELLECTUAL")).willReturn(List.of(
                new StudentEvaluationItemView(
                        "INTELLECTUAL_PAPER",
                        "论文发表",
                        "INTELLECTUAL",
                        "智育",
                        "学术论文发表加分",
                        new BigDecimal("36.00"),
                        "STUDENT_APPLY",
                        true,
                        List.of()
                )
        ));
        given(studentEvaluationApplicationService.calculatePoints("INTELLECTUAL_PAPER", "PAPER_I_1"))
                .willReturn(new StudentEvaluationPointsView(
                        "INTELLECTUAL_PAPER",
                        "PAPER_I_1",
                        new BigDecimal("36.00"),
                        "I类第一档"
                ));
    }

    @Test
    void shouldRejectAnonymousEvaluationItems() throws Exception {
        mockMvc.perform(get("/api/student/evaluation/items")
                        .param("categoryCode", "INTELLECTUAL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowStudentWithViewSelfAuthorityToListEvaluationItems() throws Exception {
        mockMvc.perform(get("/api/student/evaluation/items")
                        .param("categoryCode", "INTELLECTUAL")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1001L, "application.view.self")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectStudentWithoutViewSelfAuthorityOnCalculatePoints() throws Exception {
        mockMvc.perform(post("/api/student/evaluation/calculate-points")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1002L, "application.submit"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"itemCode\":\"INTELLECTUAL_PAPER\",\"optionCode\":\"PAPER_I_1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStudentWithViewSelfAuthorityToCalculatePoints() throws Exception {
        mockMvc.perform(post("/api/student/evaluation/calculate-points")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1001L, "application.view.self"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"itemCode\":\"INTELLECTUAL_PAPER\",\"optionCode\":\"PAPER_I_1\"}"))
                .andExpect(status().isOk());
    }

    private Set<String> authoritiesFor(Long userId) {
        return switch (userId.intValue()) {
            case 1001 -> Set.of("application.view.self");
            case 1002 -> Set.of("application.submit");
            default -> Set.of();
        };
    }

    private String tokenWithAuthorities(Long userId, String... authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .subject(String.valueOf(userId))
                .id("access-jti-" + UUID.randomUUID())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("uid", userId)
                .claim("uno", "2024305999")
                .claim("uname", "张三")
                .claim("identity", "student")
                .claim("sid", "session-no-123")
                .claim("roles", List.of("STUDENT"))
                .claim("authorities", List.of(authorities))
                .claim("token_type", "access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    static class TestApplication {
    }
}
