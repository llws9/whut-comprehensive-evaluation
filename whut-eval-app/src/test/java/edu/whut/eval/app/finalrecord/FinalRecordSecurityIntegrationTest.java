package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportApplicationService;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportFile;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordDetailView;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordListItemView;
import edu.whut.eval.application.finalrecord.query.ConfirmFinalRecordResultView;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.query.FinalRecordView;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentView;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import edu.whut.eval.domain.shared.PageResult;
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
import edu.whut.eval.interfaces.admin.AdminFinalScoreExportController;
import edu.whut.eval.interfaces.admin.AdminFinalRecordController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.student.StudentFinalRecordController;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {StudentFinalRecordController.class, AdminFinalRecordController.class, AdminFinalScoreExportController.class})
@ContextConfiguration(classes = FinalRecordSecurityIntegrationTest.TestApplication.class)
@Import({
        StudentFinalRecordController.class,
        AdminFinalRecordController.class,
        AdminFinalScoreExportController.class,
        SecurityConfiguration.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        JwtAuthenticationFilter.class,
        JwtTokenResolver.class,
        JwtClaimsParser.class,
        JwtClaimsToCurrentUserMapper.class,
        SecurityContextCurrentUserProvider.class,
        SecurityContextUserAuthorizationContextAssembler.class,
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
class FinalRecordSecurityIntegrationTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";
    private final Map<Long, Set<String>> authoritiesByUserId = new HashMap<>();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinalRecordQueryApplicationService queryApplicationService;

    @MockBean
    private FinalRecordCommandApplicationService commandApplicationService;

    @MockBean
    private FinalScoreExportApplicationService exportApplicationService;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private AccessSessionService accessSessionService;

    @BeforeEach
    void setUp() {
        given(userAuthorizationContextLoader.load(any(UserAuthorizationContextLoadRequest.class))).willAnswer(invocation -> {
            UserAuthorizationContextLoadRequest request = invocation.getArgument(0);
            return new UserAuthorizationContext(request.getUserId(), request.getUserNo(), request.getUserName(),
                    request.getIdentity(), request.getRoles(), authoritiesByUserId.getOrDefault(request.getUserId(), Set.of()), List.of());
        });
        given(queryApplicationService.getStudentFinalRecord("2025-2026")).willReturn(studentView());
        given(queryApplicationService.pageAdminFinalRecords(any())).willReturn(new PageResult<>(0, List.of()));
        given(queryApplicationService.pageUnsubmittedStudents(any()))
                .willReturn(new PageResult<>(1, List.of(unsubmittedStudent())));
        given(queryApplicationService.getAdminFinalRecordDetail(41001L)).willReturn(adminDetail());
        given(commandApplicationService.confirm(any())).willReturn(new ConfirmFinalRecordResultView(
                41001L, FinalRecordStatus.CONFIRMED, "ok", Instant.now(), 2L));
        given(exportApplicationService.export(any())).willReturn(new FinalScoreExportFile(
                "final-scores-2025-2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "xlsx".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    void shouldReturn401WhenAnonymousReadsStudentFinalRecord() throws Exception {
        mockMvc.perform(get("/api/student/final-records/2025-2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenStudentLacksFinalViewSelf() throws Exception {
        mockMvc.perform(get("/api/student/final-records/2025-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1001L, "application.view.self")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStudentWithFinalViewSelf() throws Exception {
        mockMvc.perform(get("/api/student/final-records/2025-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1001L, "final.view.self")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAdminListWithScoreViewAssigned() throws Exception {
        mockMvc.perform(get("/api/admin/final-records")
                        .param("academicYear", "2025-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.view.assigned")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAdminUnsubmittedListWithScoreViewAssigned() throws Exception {
        mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                        .param("academicYear", "2025-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.view.assigned")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("UNSUBMITTED"));
    }

    @Test
    void shouldRejectAdminUnsubmittedListWithoutScoreViewAssigned() throws Exception {
        mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                        .param("academicYear", "2025-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.confirm.assigned")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-4030"));
    }

    @Test
    void shouldReturn401WhenAnonymousReadsAdminUnsubmittedList() throws Exception {
        mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldMapUnsubmittedListServiceAccessDeniedTo403() throws Exception {
        willThrow(new AccessDeniedAppException("当前用户无未提交最终成绩名单查询权限"))
                .given(queryApplicationService).pageUnsubmittedStudents(any());

        mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                        .param("academicYear", "2025-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.view.assigned")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-4030"))
                .andExpect(jsonPath("$.message").value("当前用户无未提交最终成绩名单查询权限"));
    }

    @Test
    void shouldRejectAdminDetailWithoutScoreViewAssigned() throws Exception {
        mockMvc.perform(get("/api/admin/final-records/41001")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.confirm.assigned")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminConfirmWithScoreConfirmAssigned() throws Exception {
        mockMvc.perform(post("/api/admin/final-records/41001/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.confirm.assigned"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"ok\",\"expectedVersion\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn401WhenAnonymousExportsFinalScores() throws Exception {
        mockMvc.perform(get("/api/admin/exports/final-scores")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WhenAdminLacksScoreExportAssigned() throws Exception {
        mockMvc.perform(get("/api/admin/exports/final-scores")
                        .param("academicYear", "2025-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.view.assigned")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminExportWithScoreExportAssigned() throws Exception {
        mockMvc.perform(get("/api/admin/exports/final-scores")
                        .param("academicYear", "2025-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.export.assigned")))
                .andExpect(status().isOk());
    }

    private String tokenWithAuthorities(Long userId, String... authorities) {
        authoritiesByUserId.put(userId, Set.copyOf(Arrays.asList(authorities)));
        Instant now = Instant.now();
        return Jwts.builder()
                .id("access-jti-" + UUID.randomUUID())
                .subject(String.valueOf(userId))
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("uid", userId)
                .claim("uno", "U" + userId)
                .claim("uname", "User")
                .claim("identity", "TEST")
                .claim("sid", "session-no-123")
                .claim("roles", List.of("TEST"))
                .claim("authorities", List.of(authorities))
                .claim("token_type", "access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private FinalRecordStudentView studentView() {
        return new FinalRecordStudentView(41001L, 1001L, "2025-2026", FinalRecordStatus.SUBMITTED,
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                Instant.now(), null, 1L);
    }

    private AdminFinalRecordDetailView adminDetail() {
        return new AdminFinalRecordDetailView(
                new FinalRecordView(41001L, 1001L, "2025-2026", FinalRecordStatus.SUBMITTED,
                        BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                        Instant.now(), null, null, 1L),
                studentView(),
                List.of()
        );
    }

    private UnsubmittedStudentView unsubmittedStudent() {
        return new UnsubmittedStudentView(1002L, "S1002", "Student B", "2022", "计科二班",
                "UNSUBMITTED", Instant.parse("2026-07-07T12:34:56Z").toString());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    static class TestApplication {
    }
}
