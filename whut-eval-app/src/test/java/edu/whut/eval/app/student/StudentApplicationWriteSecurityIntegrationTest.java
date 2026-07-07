package edu.whut.eval.app.student;

import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.SubmitApplicationCommand;
import edu.whut.eval.application.application.command.UpdateApplicationDraftCommand;
import edu.whut.eval.application.application.command.WithdrawApplicationCommand;
import edu.whut.eval.application.application.query.ApplicationAttachmentView;
import edu.whut.eval.application.application.query.ApplicationSubmissionDetailView;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.application.application.service.ApplicationSubmissionDetailApplicationService;
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
import edu.whut.eval.interfaces.student.StudentApplicationSubmissionController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudentApplicationSubmissionController.class)
@ContextConfiguration(classes = StudentApplicationWriteSecurityIntegrationTest.TestApplication.class)
@Import({
        StudentApplicationSubmissionController.class,
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
class StudentApplicationWriteSecurityIntegrationTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApplicationSubmissionCommandApplicationService applicationSubmissionCommandApplicationService;

    @MockBean
    private ApplicationSubmissionDetailApplicationService applicationSubmissionDetailApplicationService;

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
        ApplicationSubmissionView writeView = new ApplicationSubmissionView(
                1L,
                ApplicationSubmissionStatus.DRAFT,
                "申请标题",
                "申请说明",
                1,
                0L
        );
        given(applicationSubmissionCommandApplicationService.createDraft(any(CreateApplicationDraftCommand.class))).willReturn(writeView);
        given(applicationSubmissionCommandApplicationService.submit(any(SubmitApplicationCommand.class))).willReturn(writeView);
        given(applicationSubmissionCommandApplicationService.updateDraft(any(UpdateApplicationDraftCommand.class))).willReturn(writeView);
        given(applicationSubmissionCommandApplicationService.withdraw(any(WithdrawApplicationCommand.class))).willReturn(writeView);
        given(applicationSubmissionDetailApplicationService.getOwnedDetail(1L)).willReturn(detailView());
    }

    @Test
    void shouldRejectAnonymousCreateDraft() throws Exception {
        mockMvc.perform(post("/api/student/applications/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(validCreateDraftJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnonymousDetailRead() throws Exception {
        mockMvc.perform(get("/api/student/applications/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowStudentWithSubmitAuthorityToCreateDraft() throws Exception {
        mockMvc.perform(post("/api/student/applications/drafts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1001L, "application.submit"))
                        .contentType(APPLICATION_JSON)
                        .content(validCreateDraftJson()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowStudentWithSubmitAuthorityToSubmit() throws Exception {
        mockMvc.perform(post("/api/student/applications/1/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1001L, "application.submit"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"optionCode\":\"OPTION_A\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectStudentWithoutSubmitAuthorityOnSubmit() throws Exception {
        mockMvc.perform(post("/api/student/applications/1/submit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1002L, "application.update"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"optionCode\":\"OPTION_A\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectStudentWithoutUpdateAuthorityOnWithdraw() throws Exception {
        mockMvc.perform(post("/api/student/applications/1/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1003L, "application.submit"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"x\",\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectStudentWithoutUpdateAuthorityOnUpdateDraft() throws Exception {
        mockMvc.perform(put("/api/student/applications/1/draft")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1004L, "application.submit"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"新标题\",\"description\":\"新说明\",\"attachmentFileIds\":[\"file-1\"],\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStudentWithViewSelfAuthorityToReadDetail() throws Exception {
        mockMvc.perform(get("/api/student/applications/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1005L, "application.view.self")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectStudentWithoutViewSelfAuthorityOnDetail() throws Exception {
        mockMvc.perform(get("/api/student/applications/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1006L, "application.submit")))
                .andExpect(status().isForbidden());
    }

    private String validCreateDraftJson() {
        return """
                {
                  "orgUnitId": 2010,
                  "categoryCode": "INTELLECTUAL",
                  "itemCode": "INTELLECTUAL_PAPER",
                  "academicYear": "2025-2026",
                  "term": "上学期",
                  "title": "申请标题",
                  "description": "申请说明",
                  "attachmentFileIds": ["file-1"]
                }
                """;
    }

    private Set<String> authoritiesFor(Long userId) {
        return switch (userId.intValue()) {
            case 1001 -> Set.of("application.submit");
            case 1002 -> Set.of("application.update");
            case 1003, 1004, 1006 -> Set.of("application.submit");
            case 1005 -> Set.of("application.view.self");
            default -> Set.of();
        };
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
                .claim("uno", "2024305999")
                .claim("uname", "Test Student")
                .claim("identity", "student")
                .claim("sid", "session-no-123")
                .claim("roles", List.of("student"))
                .claim("authorities", Arrays.asList(authorities))
                .claim("token_type", "access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private ApplicationSubmissionDetailView detailView() {
        ApplicationSubmissionDetailView view = new ApplicationSubmissionDetailView();
        view.setApplicationId(1L);
        view.setOrgUnitId(2010L);
        view.setCategoryCode("INTELLECTUAL");
        view.setItemCode("INTELLECTUAL_PAPER");
        view.setAcademicYear("2025-2026");
        view.setTerm("上学期");
        view.setTitle("申请标题");
        view.setDescription("申请说明");
        view.setStatus(ApplicationSubmissionStatus.DRAFT);
        view.setVersion(0L);
        view.setEvidenceCount(1);
        view.setAttachments(List.of(new ApplicationAttachmentView("file-1", "a.pdf", "application/pdf", 128L, 0)));
        return view;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    static class TestApplication {
    }
}
