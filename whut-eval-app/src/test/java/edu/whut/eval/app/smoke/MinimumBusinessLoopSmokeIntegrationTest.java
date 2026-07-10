package edu.whut.eval.app.smoke;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import edu.whut.eval.app.security.AuthController;
import edu.whut.eval.app.security.AuthTokenResponseAssembler;
import edu.whut.eval.application.application.command.ApproveReviewCommand;
import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.SubmitApplicationCommand;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.application.service.ApplicationAttachmentResolver;
import edu.whut.eval.application.application.service.ApplicationOrgMembershipValidator;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationAccessValidator;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
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
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.application.finalrecord.command.ConfirmFinalRecordCommand;
import edu.whut.eval.application.finalrecord.command.SubmitFinalRecordCommand;
import edu.whut.eval.application.finalrecord.query.ConfirmFinalRecordResultView;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.application.finalrecord.service.FinalRecordAccessValidator;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.common.exception.AuthenticationFailedException;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.service.ApplicationSubmissionWindowPolicy;
import edu.whut.eval.domain.application.service.ActiveSubmissionPolicy;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.domain.config.RuleEngineService;
import edu.whut.eval.domain.config.StudentContext;
import edu.whut.eval.domain.config.StudentEvaluationSummary;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import edu.whut.eval.domain.finalrecord.service.FinalSubmissionWindowPolicy;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.iam.repository.RoleAssignmentQueryRepository;
import edu.whut.eval.infra.cache.UserCacheGateway;
import edu.whut.eval.infra.config.MybatisPlusConfig;
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
import edu.whut.eval.infra.security.context.RepositoryUserAuthorizationContextLoader;
import edu.whut.eval.infra.security.config.JwtProperties;
import edu.whut.eval.infra.security.config.SecurityProperties;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtTokenIssuer;
import edu.whut.eval.infra.security.jwt.RefreshTokenClaimsMapper;
import edu.whut.eval.infra.security.password.Sha256PasswordHashVerifier;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MinimumBusinessLoopSmokeIntegrationTest.TestConfig.class)
class MinimumBusinessLoopSmokeIntegrationTest {

    private static final String PASSWORD = "ChangeMe123!";
    private static final String ACADEMIC_YEAR = "2025-2026";
    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();
    private static final Path TEAM_DELIVERY = ROOT.resolve("docs/team-delivery");
    private static final Path GROUP_A_SQL = TEAM_DELIVERY.resolve("group-a-identity-user-admin.sql");
    private static final Path GROUP_B_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-b-student-application.safe-init.sql");
    private static final Path GROUP_C_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-c-review-workflow.safe-init.sql");
    private static final Path GROUP_D_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-d-score-finalization-import-export.safe-init.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthController authController;

    @Autowired
    private SwitchingAuthorizationContextAssembler authorizationContextAssembler;

    @Autowired
    private ApplicationSubmissionCommandApplicationService applicationService;

    @Autowired
    private ReviewApplicationCommandApplicationService reviewService;

    @Autowired
    private FinalRecordCommandApplicationService finalRecordService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        jdbcTemplate.execute("DROP ALL OBJECTS");
        executeStatements(toH2CompatibleGroupASql(Files.readString(GROUP_A_SQL)));
        executeStatements(Files.readString(GROUP_B_SAFE_INIT_SQL));
        executeStatements(Files.readString(GROUP_C_SAFE_INIT_SQL));
        executeStatements(Files.readString(GROUP_D_SAFE_INIT_SQL));
        authorizationContextAssembler.clear();
    }

    @Test
    void shouldRunMinimumDemoBusinessLoopFromLoginToFinalRecordConfirmation() throws Exception {
        assertLoginSucceeds("2022010101");
        assertLoginSucceeds("T20260001");

        authorizationContextAssembler.useUserNo("2022010101");
        ApplicationSubmissionView draft = applicationService.createDraft(new CreateApplicationDraftCommand(
                2010L,
                "INTELLECTUAL",
                "INTELLECTUAL_PAPER",
                ACADEMIC_YEAR,
                "上学期",
                "最小演示闭环论文申请",
                "用于验证登录、申请、审核、最终成绩汇总与确认的 smoke 数据",
                List.of("FILE-SMOKE-001")
        ));
        ApplicationSubmissionView submitted = applicationService.submit(new SubmitApplicationCommand(
                draft.getApplicationId(),
                draft.getVersion(),
                new BigDecimal("2.00"),
                "PAPER_CORE_FIRST_AUTHOR"
        ));

        authorizationContextAssembler.useUserNo("T20260001");
        ReviewActionResultView approved = reviewService.approve(new ApproveReviewCommand(
                submitted.getApplicationId(),
                submitted.getVersion(),
                "通过"
        ));

        authorizationContextAssembler.useUserNo("2022010101");
        FinalRecordStudentView finalRecord = finalRecordService.submit(new SubmitFinalRecordCommand(ACADEMIC_YEAR, 0L));

        authorizationContextAssembler.useUserNo("T20260001");
        ConfirmFinalRecordResultView confirmed = finalRecordService.confirm(new ConfirmFinalRecordCommand(
                finalRecord.finalRecordId(),
                "确认无误",
                finalRecord.version()
        ));

        assertThat(approved.status()).isEqualTo(ApplicationSubmissionStatus.APPROVED);
        assertThat(finalRecord.status()).isEqualTo(FinalRecordStatus.SUBMITTED);
        assertThat(confirmed.status()).isEqualTo(FinalRecordStatus.CONFIRMED);
        assertThat(confirmed.confirmComment()).isEqualTo("确认无误");
        assertThat(applicationStatus(submitted.getApplicationId())).isEqualTo("APPROVED");
        assertThat(countRows("application_fact", "application_id = " + submitted.getApplicationId())).isEqualTo(1);
        assertThat(finalRecordStatus(finalRecord.finalRecordId())).isEqualTo("CONFIRMED");
        assertThat(componentSourceRef(finalRecord.finalRecordId())).isEqualTo(String.valueOf(submitted.getApplicationId()));
        assertThat(reviewLogCount(submitted.getApplicationId())).isEqualTo(1);
        assertThat(finalRecordGrandTotal(finalRecord.finalRecordId())).isEqualByComparingTo("2.00");
    }

    private void assertLoginSucceeds(String credential) throws Exception {
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

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackages = "edu.whut.eval.infra.persistence.mapper")
    @Import({
            MybatisPlusConfig.class,
            MybatisPlusIamUserQueryRepository.class,
            MybatisPlusRoleAssignmentQueryRepository.class,
            MybatisPlusUserAuthorityQueryRepository.class,
            MybatisPlusUserScopeRuleQueryRepository.class,
            RepositoryUserAuthorizationContextLoader.class,
            MybatisPlusIamSessionRepository.class,
            MybatisPlusOrgUnitLookupRepository.class,
            MybatisPlusApplicationSubmissionRepository.class,
            MybatisPlusApplicationReviewLogRepository.class,
            MybatisPlusReviewApplicationQueryRepository.class,
            MybatisPlusFinalRecordRepository.class,
            MybatisPlusFinalRecordQueryRepository.class,
            RepositoryBackedActiveSubmissionPolicy.class,
            DefaultAuthorizationScopeEvaluator.class,
            DefaultScopePredicateBuilder.class,
            DefaultResourceScopeAccessEvaluator.class,
            ReviewApplicationAccessValidator.class,
            FinalRecordAccessValidator.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            DefaultLoginAuthenticationService.class,
            LoginSessionService.class,
            Sha256PasswordHashVerifier.class,
            JwtTokenIssuer.class,
            JwtClaimsParser.class,
            RefreshTokenClaimsMapper.class,
            AuthTokenResponseAssembler.class,
            AuthController.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:minimum_business_loop_smoke;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SecurityProperties securityProperties() {
            SecurityProperties properties = new SecurityProperties();
            JwtProperties jwt = properties.getJwt();
            jwt.setEnabled(true);
            jwt.setAlgorithm("HS256");
            jwt.setIssuer("whut-eval");
            jwt.setAudience("whut-eval-api");
            jwt.setSecret("test-jwt-secret-should-be-long-enough-1234567890");
            jwt.setAccessTokenTtlSeconds(7200);
            jwt.setRefreshTokenTtlSeconds(604800);
            return properties;
        }

        @Bean
        UserCacheGateway userCacheGateway() {
            return new UserCacheGateway() {
                @Override
                public Optional<IamUser> getByUserNo(String userNo) {
                    return Optional.empty();
                }

                @Override
                public void put(IamUser user) {
                }

                @Override
                public void evictByUserNo(String userNo) {
                }
            };
        }

        @Bean
        SwitchingAuthorizationContextAssembler switchingAuthorizationContextAssembler(
                IamUserQueryRepository userQueryRepository,
                RoleAssignmentQueryRepository roleAssignmentQueryRepository,
                UserAuthorizationContextLoader authorizationContextLoader) {
            return new SwitchingAuthorizationContextAssembler(
                    userQueryRepository,
                    roleAssignmentQueryRepository,
                    authorizationContextLoader
            );
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
        ApplicationAttachmentResolver applicationAttachmentResolver() {
            return (attachmentFileIds, currentUserId) -> attachmentFileIds.stream()
                    .map(fileId -> new AttachmentRef(
                            fileId,
                            "smoke/" + fileId + ".pdf",
                            fileId + ".pdf",
                            "application/pdf",
                            1024L,
                            currentUserId
                    ))
                    .toList();
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
    }

    static class SwitchingAuthorizationContextAssembler implements UserAuthorizationContextAssembler {
        private final IamUserQueryRepository userQueryRepository;
        private final RoleAssignmentQueryRepository roleAssignmentQueryRepository;
        private final UserAuthorizationContextLoader authorizationContextLoader;
        private String currentUserNo;

        SwitchingAuthorizationContextAssembler(IamUserQueryRepository userQueryRepository,
                                               RoleAssignmentQueryRepository roleAssignmentQueryRepository,
                                               UserAuthorizationContextLoader authorizationContextLoader) {
            this.userQueryRepository = userQueryRepository;
            this.roleAssignmentQueryRepository = roleAssignmentQueryRepository;
            this.authorizationContextLoader = authorizationContextLoader;
        }

        void useUserNo(String userNo) {
            currentUserNo = userNo;
        }

        void clear() {
            currentUserNo = null;
        }

        @Override
        public Optional<UserAuthorizationContext> currentAuthorizationContext() {
            if (currentUserNo == null) {
                return Optional.empty();
            }
            IamUser user = userQueryRepository.findByUserNo(currentUserNo)
                    .orElseThrow(() -> new AuthenticationFailedException("测试用户不存在: " + currentUserNo));
            List<IamRoleAssignment> assignments = roleAssignmentQueryRepository.findActiveAssignmentsByUserId(user.id());
            Set<String> roles = assignments.stream()
                    .map(IamRoleAssignment::roleCode)
                    .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            return Optional.of(authorizationContextLoader.load(new UserAuthorizationContextLoadRequest(
                    user.id(),
                    user.userNo(),
                    user.userName(),
                    roles.stream().findFirst().orElse(null),
                    roles
            )));
        }
    }
}
