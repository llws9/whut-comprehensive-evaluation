package edu.whut.eval.app.review;

import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationSummaryView;
import edu.whut.eval.application.application.query.ReviewApplicantView;
import edu.whut.eval.application.application.query.ReviewLogView;
import edu.whut.eval.application.application.query.ReviewScoringSnapshotView;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
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
import edu.whut.eval.interfaces.review.ReviewApplicationController;
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
import java.util.Arrays;
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

@WebMvcTest(controllers = ReviewApplicationController.class)
@ContextConfiguration(classes = ReviewApplicationSecurityIntegrationTest.TestApplication.class)
@Import({
        ReviewApplicationController.class,
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
class ReviewApplicationSecurityIntegrationTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService;

    @MockBean
    private ReviewApplicationCommandApplicationService reviewApplicationCommandApplicationService;

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
        given(reviewApplicationQueryApplicationService.getReviewDetail(21013L)).willReturn(detailView());
        given(reviewApplicationQueryApplicationService.listReviewLogs(21013L)).willReturn(List.of(
                new ReviewLogView(31000L, "RETURN", 1010L, null, "COUNSELOR", "补充材料",
                        Instant.parse("2026-07-07T11:00:00Z"))
        ));
        given(reviewApplicationCommandApplicationService.approve(any()))
                .willReturn(new ReviewActionResultView(21013L, ApplicationSubmissionStatus.APPROVED, 2L, 31001L, Instant.now()));
    }

    @Test
    void shouldRejectAnonymousReviewList() throws Exception {
        mockMvc.perform(get("/api/review/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUserWithoutApplicationReviewOnApprove() throws Exception {
        mockMvc.perform(post("/api/review/applications/21013/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1002L, "application.submit"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowReviewerWithApplicationReviewOnDetail() throws Exception {
        mockMvc.perform(get("/api/review/applications/21013")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "application.review")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowReviewerWithApplicationReviewOnLogs() throws Exception {
        mockMvc.perform(get("/api/review/applications/21013/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "application.review")))
                .andExpect(status().isOk());
    }

    private Set<String> authoritiesFor(Long userId) {
        if (userId == 1010L) {
            return Set.of("application.review");
        }
        return Set.of("application.submit");
    }

    private String tokenWithAuthorities(Long userId, String... authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id("access-jti-" + UUID.randomUUID())
                .subject(String.valueOf(userId))
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("uid", userId)
                .claim("uno", "A0010")
                .claim("uname", "Counselor")
                .claim("identity", "COUNSELOR")
                .claim("sid", "session-no-123")
                .claim("roles", List.of("COUNSELOR"))
                .claim("authorities", Arrays.asList(authorities))
                .claim("token_type", "access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private ReviewApplicationDetailView detailView() {
        return new ReviewApplicationDetailView(
                new ReviewApplicationSummaryView(
                        21013L,
                        "SUBMITTED",
                        "论文申请",
                        "申请说明",
                        "INTELLECTUAL",
                        "INTELLECTUAL_PAPER",
                        "2025-2026",
                        "上学期",
                        Instant.parse("2026-07-07T10:00:00Z"),
                        1L,
                        new ReviewScoringSnapshotView("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null)
                ),
                new ReviewApplicantView(1001L, "2024305999", "张三", 2010L, "计算机学院 1 班"),
                List.of(),
                List.of(),
                List.of("APPROVE", "RETURN", "REJECT")
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    static class TestApplication {
    }
}
