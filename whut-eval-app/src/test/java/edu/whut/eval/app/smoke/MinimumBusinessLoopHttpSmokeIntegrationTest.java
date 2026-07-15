package edu.whut.eval.app.smoke;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jayway.jsonpath.JsonPath;
import edu.whut.eval.app.security.AuthController;
import edu.whut.eval.app.security.AuthTokenResponseAssembler;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.application.service.ApplicationAttachmentResolver;
import edu.whut.eval.application.application.service.ApplicationOrgMembershipValidator;
import edu.whut.eval.application.application.service.ApplicationOverviewQueryApplicationService;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.application.application.service.ApplicationSubmissionDetailApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationAccessValidator;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultLoginAuthenticationService;
import edu.whut.eval.application.auth.service.DefaultResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.DefaultScopePredicateBuilder;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.application.auth.service.LoginSessionService;
import edu.whut.eval.application.auth.service.LogoutService;
import edu.whut.eval.application.auth.service.RefreshSessionService;
import edu.whut.eval.application.auth.service.RefreshTokenCurrentUserLoader;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.application.finalrecord.service.FinalRecordAccessValidator;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.domain.application.service.ActiveSubmissionPolicy;
import edu.whut.eval.domain.application.service.ApplicationSubmissionWindowPolicy;
import edu.whut.eval.domain.config.RuleEngineService;
import edu.whut.eval.domain.config.StudentContext;
import edu.whut.eval.domain.config.StudentEvaluationSummary;
import edu.whut.eval.domain.finalrecord.service.FinalSubmissionWindowPolicy;
import edu.whut.eval.infra.cache.UserCacheGateway;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.repository.MybatisApplicationAttachmentResolver;
import edu.whut.eval.infra.persistence.repository.MybatisApplicationOverviewQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusApplicationReviewLogRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusApplicationSubmissionRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusFinalRecordQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusFinalRecordRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamSessionRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamUserQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusOrgUnitLookupRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusReviewApplicationQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAssignmentQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserAuthorityQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserScopeRuleQueryRepository;
import edu.whut.eval.infra.persistence.repository.RepositoryBackedActiveSubmissionPolicy;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityConfiguration;
import edu.whut.eval.infra.security.context.RepositoryUserAuthorizationContextLoader;
import edu.whut.eval.infra.security.context.SecurityContextCurrentUserProvider;
import edu.whut.eval.infra.security.context.SecurityContextUserAuthorizationContextAssembler;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationFilter;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtClaimsToCurrentUserMapper;
import edu.whut.eval.infra.security.jwt.JwtTokenIssuer;
import edu.whut.eval.infra.security.jwt.JwtTokenResolver;
import edu.whut.eval.infra.security.jwt.RefreshTokenClaimsMapper;
import edu.whut.eval.infra.security.password.Sha256PasswordHashVerifier;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.web.RestAccessDeniedHandler;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import edu.whut.eval.interfaces.admin.AdminFinalRecordController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.review.ReviewApplicationController;
import edu.whut.eval.interfaces.student.StudentApplicationSubmissionController;
import edu.whut.eval.interfaces.student.StudentFinalRecordController;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MinimumBusinessLoopHttpSmokeIntegrationTest.TestConfig.class)
@AutoConfigureMockMvc
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
class MinimumBusinessLoopHttpSmokeIntegrationTest {

    private static final String PASSWORD = "ChangeMe123!";
    private static final String ACADEMIC_YEAR = "2025-2026";
    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();
    private static final Path TEAM_DELIVERY = ROOT.resolve("docs/team-delivery");
    private static final Path GROUP_A_SQL = TEAM_DELIVERY.resolve("group-a-identity-user-admin.sql");
    private static final Path GROUP_E_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-e-platform-governance-attachment-ai.safe-init.sql");
    private static final Path GROUP_B_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-b-student-application.safe-init.sql");
    private static final Path GROUP_C_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-c-review-workflow.safe-init.sql");
    private static final Path GROUP_D_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-d-score-finalization-import-export.safe-init.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("DROP ALL OBJECTS");
        executeStatements(toH2CompatibleGroupASql(Files.readString(GROUP_A_SQL)));
        executeStatements(Files.readString(GROUP_E_SAFE_INIT_SQL));
        executeStatements(Files.readString(GROUP_B_SAFE_INIT_SQL));
        executeStatements(Files.readString(GROUP_C_SAFE_INIT_SQL));
        executeStatements(Files.readString(GROUP_D_SAFE_INIT_SQL));
    }

    @Test
    void shouldRunMinimumBusinessLoopThroughHttpControllersAndSecurityFilter() throws Exception {
        String studentToken = stage("login student 2022010101", () -> login("2022010101"));
        String counselorToken = stage("login counselor T20260001", () -> login("T20260001"));

        MvcResult draftResult = stage("create student draft with seeded attachment", () ->
                mockMvc.perform(post("/api/student/applications/drafts")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "orgUnitId": 2010,
                                          "categoryCode": "INTELLECTUAL",
                                          "itemCode": "INTELLECTUAL_PAPER",
                                          "academicYear": "2025-2026",
                                          "term": "上学期",
                                          "title": "最小 HTTP 闭环论文申请",
                                          "description": "通过 HTTP controller 和安全过滤器验证最小业务闭环",
                                          "attachmentFileIds": ["FILE-0001"]
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("OK"))
                        .andExpect(jsonPath("$.data.status").value("DRAFT"))
                        .andExpect(jsonPath("$.data.attachmentCount").value(1))
                        .andReturn()
        );
        Long applicationId = readLong(draftResult, "$.data.applicationId");
        Long draftVersion = readLong(draftResult, "$.data.version");

        MvcResult submittedResult = stage("submit student application", () ->
                mockMvc.perform(post("/api/student/applications/{applicationId}/submit", applicationId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "expectedVersion": %d,
                                          "appliedPoints": 2.00,
                                          "optionCode": "PAPER_CORE_FIRST_AUTHOR"
                                        }
                                        """.formatted(draftVersion)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                        .andExpect(jsonPath("$.data.appliedPoints").value(2.00))
                        .andReturn()
        );
        Long submittedVersion = readLong(submittedResult, "$.data.version");

        stage("approve application as counselor", () ->
                mockMvc.perform(post("/api/review/applications/{applicationId}/approve", applicationId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + counselorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "expectedVersion": %d,
                                          "comment": "通过"
                                        }
                                        """.formatted(submittedVersion)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("APPROVED"))
                        .andExpect(jsonPath("$.data.reviewLogId").isNumber())
                        .andReturn()
        );

        MvcResult finalRecordResult = stage("submit final record as student", () ->
                mockMvc.perform(post("/api/student/final-records/submit")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "academicYear": "2025-2026",
                                          "expectedVersion": 0
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                        .andExpect(jsonPath("$.data.grandTotal").value(2.00))
                        .andReturn()
        );
        Long finalRecordId = readLong(finalRecordResult, "$.data.finalRecordId");
        Long finalRecordVersion = readLong(finalRecordResult, "$.data.version");

        stage("confirm final record as counselor", () ->
                mockMvc.perform(post("/api/admin/final-records/{recordId}/confirm", finalRecordId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + counselorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "expectedVersion": %d,
                                          "comment": "确认无误"
                                        }
                                        """.formatted(finalRecordVersion)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                        .andExpect(jsonPath("$.data.confirmComment").value("确认无误"))
                        .andReturn()
        );

        stage("reject student token from admin final record reads", () ->
                mockMvc.perform(get("/api/admin/final-records")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + studentToken)
                                .param("academicYear", ACADEMIC_YEAR))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("AUTH-4030"))
                        .andReturn()
        );

        stage("seed out-of-scope confirmed final record", () -> {
            seedOutOfScopeConfirmedFinalRecord();
            return null;
        });

        stage("list confirmed final records as counselor", () ->
                mockMvc.perform(get("/api/admin/final-records")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + counselorToken)
                                .param("academicYear", ACADEMIC_YEAR)
                                .param("status", "CONFIRMED")
                                .param("pageNo", "1")
                                .param("pageSize", "20"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("OK"))
                        .andExpect(jsonPath("$.data.total").value(1))
                        .andExpect(jsonPath("$.data.records[0].finalRecordId").value(finalRecordId.intValue()))
                        .andExpect(jsonPath("$.data.records[0].studentUserNo").value("2022010101"))
                        .andExpect(jsonPath("$.data.records[0].orgUnitId").value(2010))
                        .andExpect(jsonPath("$.data.records[0].status").value("CONFIRMED"))
                        .andExpect(jsonPath("$.data.records[0].grandTotal").value(2.00))
                        .andExpect(jsonPath("$.data.records[0].confirmedAt").isNotEmpty())
                        .andReturn()
        );

        stage("reject out-of-scope final record detail as counselor", () ->
                mockMvc.perform(get("/api/admin/final-records/{recordId}", 90001)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + counselorToken))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("AUTH-4030"))
                        .andReturn()
        );

        stage("get confirmed final record detail as counselor", () ->
                mockMvc.perform(get("/api/admin/final-records/{recordId}", finalRecordId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + counselorToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("OK"))
                        .andExpect(jsonPath("$.data.record.finalRecordId").value(finalRecordId.intValue()))
                        .andExpect(jsonPath("$.data.record.status").value("CONFIRMED"))
                        .andExpect(jsonPath("$.data.record.confirmComment").value("确认无误"))
                        .andExpect(jsonPath("$.data.student.studentUserId").value(1001))
                        .andExpect(jsonPath("$.data.components[0].sourceRefId").value(String.valueOf(applicationId)))
                        .andExpect(jsonPath("$.data.components[0].scoreValue").value(2.00))
                        .andReturn()
        );

        stage("verify persisted business loop state", () -> {
            assertThat(applicationStatus(applicationId)).isEqualTo("APPROVED");
            assertThat(countRows("application_fact", "application_id = " + applicationId)).isEqualTo(1);
            assertThat(reviewLogCount(applicationId)).isEqualTo(1);
            assertThat(finalRecordStatus(finalRecordId)).isEqualTo("CONFIRMED");
            assertThat(componentSourceRef(finalRecordId)).isEqualTo(String.valueOf(applicationId));
            assertThat(finalRecordGrandTotal(finalRecordId)).isEqualByComparingTo("2.00");
            return null;
        });
    }

    @Test
    void shouldReportStageNameWhenHttpSmokeStageFails() {
        IllegalStateException rootCause = new IllegalStateException("root failure");

        AssertionError failure = assertThrows(AssertionError.class, () ->
                stage("diagnostic probe", () -> {
                    throw rootCause;
                }));

        assertThat(failure)
                .hasMessage("HTTP smoke stage failed: diagnostic probe")
                .hasCause(rootCause);
    }

    private <T> T stage(String stageName, StageAction<T> action) throws Exception {
        try {
            return action.execute();
        } catch (AssertionError | Exception failure) {
            throw new AssertionError("HTTP smoke stage failed: " + stageName, failure);
        }
    }

    @FunctionalInterface
    private interface StageAction<T> {
        T execute() throws Exception;
    }

    private String login(String credential) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credential":"%s","password":"%s"}
                                """.formatted(credential, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }

    private Long readLong(MvcResult result, String path) throws java.io.UnsupportedEncodingException {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private String applicationStatus(Long applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM application_submission WHERE application_id = ?",
                String.class,
                applicationId
        );
    }

    private String finalRecordStatus(Long finalRecordId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM final_record WHERE id = ?",
                String.class,
                finalRecordId
        );
    }

    private String componentSourceRef(Long finalRecordId) {
        return jdbcTemplate.queryForObject(
                "SELECT source_ref_id FROM final_component_score WHERE final_record_id = ?",
                String.class,
                finalRecordId
        );
    }

    private int reviewLogCount(Long applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_review_log WHERE application_id = ? AND action = 'APPROVE'",
                Integer.class,
                applicationId
        );
    }

    private BigDecimal finalRecordGrandTotal(Long finalRecordId) {
        return jdbcTemplate.queryForObject(
                "SELECT grand_total FROM final_record WHERE id = ?",
                BigDecimal.class,
                finalRecordId
        );
    }

    private int countRows(String tableName, String condition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE " + condition, Integer.class);
    }

    private void seedOutOfScopeConfirmedFinalRecord() {
        jdbcTemplate.update("""
                INSERT INTO final_record
                    (id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total,
                     labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                VALUES
                    (90001, 1007, ?, 'CONFIRMED', 0.00, 3.00, 0.00,
                     0.00, 3.00, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), '范围外确认记录', 1, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """, ACADEMIC_YEAR);
        jdbcTemplate.update("""
                INSERT INTO final_component_score
                    (id, final_record_id, category_code, item_code, score_value, display_text,
                     source_type, source_ref_id, created_at)
                VALUES
                    (90001, 90001, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', 3.00, '范围外论文已确认',
                     'APPLICATION_FACT', '90001', CURRENT_TIMESTAMP())
                """);
    }

    private void executeStatements(String sql) {
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    private static String toH2CompatibleGroupASql(String sql) {
        String normalized = sql
                .replaceAll("(?m)^--.*$", "")
                .replace("`", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织树'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组织归属'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色模板'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限字典'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关系'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色分配'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据范围规则'", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录会话'", "")
                .replace("JSON DEFAULT NULL", "VARCHAR(2048) DEFAULT NULL")
                .replace("TINYINT(1)", "BOOLEAN")
                .replace("SET NAMES utf8mb4;", "");
        normalized = normalized.lines()
                .filter(line -> !line.trim().startsWith("CONSTRAINT fk_"))
                .reduce("", (left, right) -> left + right + System.lineSeparator());
        return normalized.replaceAll(",\\s*\\)", ")");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableTransactionManagement
    @MapperScan(basePackages = "edu.whut.eval.infra.persistence.mapper")
    @Import({
            MybatisPlusConfig.class,
            AuthController.class,
            StudentApplicationSubmissionController.class,
            ReviewApplicationController.class,
            StudentFinalRecordController.class,
            AdminFinalRecordController.class,
            GlobalExceptionHandler.class,
            SecurityConfiguration.class,
            RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class,
            JwtAuthenticationFilter.class,
            JwtTokenResolver.class,
            JwtClaimsParser.class,
            JwtClaimsToCurrentUserMapper.class,
            JwtTokenIssuer.class,
            RefreshTokenClaimsMapper.class,
            SecurityContextCurrentUserProvider.class,
            SecurityContextUserAuthorizationContextAssembler.class,
            RepositoryUserAuthorizationContextLoader.class,
            JwtConfigurationValidator.class,
            MybatisPlusIamUserQueryRepository.class,
            MybatisPlusRoleAssignmentQueryRepository.class,
            MybatisPlusUserAuthorityQueryRepository.class,
            MybatisPlusUserScopeRuleQueryRepository.class,
            MybatisPlusIamSessionRepository.class,
            MybatisPlusOrgUnitLookupRepository.class,
            MybatisPlusApplicationSubmissionRepository.class,
            MybatisPlusApplicationReviewLogRepository.class,
            MybatisPlusReviewApplicationQueryRepository.class,
            MybatisPlusFinalRecordRepository.class,
            MybatisPlusFinalRecordQueryRepository.class,
            MybatisApplicationAttachmentResolver.class,
            MybatisApplicationOverviewQueryRepository.class,
            RepositoryBackedActiveSubmissionPolicy.class,
            DefaultAuthorizationScopeEvaluator.class,
            DefaultScopePredicateBuilder.class,
            DefaultResourceScopeAccessEvaluator.class,
            ReviewApplicationAccessValidator.class,
            FinalRecordAccessValidator.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            DefaultLoginAuthenticationService.class,
            AccessSessionService.class,
            LoginSessionService.class,
            Sha256PasswordHashVerifier.class,
            AuthTokenResponseAssembler.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:minimum_business_loop_http_smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor interceptor) throws Exception {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factoryBean.setConfiguration(configuration);
            factoryBean.setPlugins(interceptor);
            return factoryBean.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        UserCacheGateway userCacheGateway() {
            return new UserCacheGateway() {
                @Override
                public Optional<edu.whut.eval.domain.iam.model.IamUser> getByUserNo(String userNo) {
                    return Optional.empty();
                }

                @Override
                public void put(edu.whut.eval.domain.iam.model.IamUser user) {
                }

                @Override
                public void evictByUserNo(String userNo) {
                }
            };
        }

        @Bean
        ApplicationSubmissionWindowPolicy applicationSubmissionWindowPolicy() {
            return (orgUnitId, categoryCode, itemCode, academicYear, term) -> true;
        }

        @Bean
        ApplicationOrgMembershipValidator applicationOrgMembershipValidator(JdbcTemplate jdbcTemplate) {
            return (userId, orgUnitId) -> jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM org_membership
                    WHERE user_id = ? AND org_unit_id = ? AND status = 'ACTIVE'
                    """, Integer.class, userId, orgUnitId) > 0;
        }

        @Bean
        RuleEngineService ruleEngineService() {
            return new RuleEngineService() {
                @Override
                public BigDecimal calculatePoints(String itemCode, String optionCode, StudentContext context) {
                    return null;
                }

                @Override
                public BigDecimal calculateMaxPoints(String itemCode, StudentContext context) {
                    return new BigDecimal("6.00");
                }

                @Override
                public boolean allowsCustomPoints(String itemCode, String optionCode) {
                    return true;
                }

                @Override
                public boolean requiresOption(String itemCode) {
                    return true;
                }

                @Override
                public boolean evaluateEligibility(String categoryCode, StudentEvaluationSummary summary) {
                    return true;
                }
            };
        }

        @Bean
        FinalSubmissionWindowPolicy finalSubmissionWindowPolicy() {
            return (studentUserId, academicYear, now) -> {
            };
        }

        @Bean
        ApplicationSubmissionCommandApplicationService applicationSubmissionCommandApplicationService(
                UserAuthorizationContextAssembler assembler,
                edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository repository,
                ApplicationSubmissionWindowPolicy windowPolicy,
                ActiveSubmissionPolicy activeSubmissionPolicy,
                ApplicationAttachmentResolver attachmentResolver,
                RuleEngineService ruleEngineService,
                ApplicationOrgMembershipValidator membershipValidator) {
            return new ApplicationSubmissionCommandApplicationService(
                    assembler,
                    repository,
                    windowPolicy,
                    activeSubmissionPolicy,
                    attachmentResolver,
                    ruleEngineService,
                    membershipValidator
            );
        }

        @Bean
        ApplicationSubmissionDetailApplicationService applicationSubmissionDetailApplicationService(
                UserAuthorizationContextAssembler assembler,
                edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository repository) {
            return new ApplicationSubmissionDetailApplicationService(assembler, repository);
        }

        @Bean
        ApplicationOverviewQueryApplicationService applicationOverviewQueryApplicationService(
                UserAuthorizationContextAssembler assembler,
                edu.whut.eval.domain.application.repository.ApplicationOverviewQueryRepository repository) {
            return new ApplicationOverviewQueryApplicationService(assembler, repository);
        }

        @Bean
        ReviewApplicationCommandApplicationService reviewApplicationCommandApplicationService(
                UserAuthorizationContextAssembler assembler,
                ReviewApplicationQueryRepository queryRepository,
                ReviewApplicationAccessValidator accessValidator,
                edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository applicationRepository,
                edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository reviewLogRepository) {
            return new ReviewApplicationCommandApplicationService(
                    assembler,
                    queryRepository,
                    accessValidator,
                    applicationRepository,
                    reviewLogRepository
            );
        }

        @Bean
        ReviewApplicationQueryApplicationService reviewApplicationQueryApplicationService(
                UserAuthorizationContextAssembler assembler,
                ReviewApplicationQueryRepository queryRepository,
                ReviewApplicationAccessValidator accessValidator,
                edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository reviewLogRepository,
                edu.whut.eval.application.file.service.FileQueryApplicationService fileQueryApplicationService) {
            return new ReviewApplicationQueryApplicationService(
                    assembler,
                    queryRepository,
                    accessValidator,
                    reviewLogRepository,
                    fileQueryApplicationService
            );
        }

        @Bean
        FinalRecordQueryApplicationService finalRecordQueryApplicationService(
                UserAuthorizationContextAssembler assembler,
                FinalRecordQueryRepository queryRepository,
                FinalRecordAccessValidator accessValidator) {
            return new FinalRecordQueryApplicationService(assembler, queryRepository, accessValidator);
        }

        @Bean
        FinalRecordCommandApplicationService finalRecordCommandApplicationService(
                UserAuthorizationContextAssembler assembler,
                edu.whut.eval.domain.finalrecord.repository.FinalRecordRepository repository,
                FinalRecordQueryRepository queryRepository,
                FinalSubmissionWindowPolicy windowPolicy,
                FinalRecordAccessValidator accessValidator) {
            return new FinalRecordCommandApplicationService(
                    assembler,
                    repository,
                    queryRepository,
                    windowPolicy,
                    accessValidator
            );
        }

        @Bean
        RefreshTokenCurrentUserLoader refreshTokenCurrentUserLoader() {
            return Mockito.mock(RefreshTokenCurrentUserLoader.class);
        }

        @Bean
        RefreshSessionService refreshSessionService() {
            return Mockito.mock(RefreshSessionService.class);
        }

        @Bean
        LogoutService logoutService() {
            return Mockito.mock(LogoutService.class);
        }

        @Bean
        edu.whut.eval.application.file.service.FileQueryApplicationService fileQueryApplicationService() {
            return Mockito.mock(edu.whut.eval.application.file.service.FileQueryApplicationService.class);
        }
    }
}
