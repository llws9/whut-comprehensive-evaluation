package edu.whut.eval.app.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jayway.jsonpath.JsonPath;
import edu.whut.eval.application.auth.service.DefaultLoginAuthenticationService;
import edu.whut.eval.application.auth.service.LoginSessionService;
import edu.whut.eval.application.auth.service.LogoutService;
import edu.whut.eval.application.auth.service.RefreshSessionService;
import edu.whut.eval.application.auth.service.RefreshTokenCurrentUserLoader;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.infra.cache.UserCacheGateway;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamPermissionQueryMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamScopeRuleQueryMapper;
import edu.whut.eval.infra.persistence.mapper.IamSessionMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamSessionRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamUserQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAssignmentQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserAuthorityQueryRepository;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserScopeRuleQueryRepository;
import edu.whut.eval.infra.security.config.JwtProperties;
import edu.whut.eval.infra.security.config.SecurityProperties;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtTokenIssuer;
import edu.whut.eval.infra.security.jwt.RefreshTokenClaimsMapper;
import edu.whut.eval.infra.security.password.Sha256PasswordHashVerifier;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Claims;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ContextConfiguration(classes = AuthControllerSeedAccountLoginIntegrationTest.TestConfig.class)
@org.junit.jupiter.api.extension.ExtendWith(SpringExtension.class)
class AuthControllerSeedAccountLoginIntegrationTest {

    private static final String PASSWORD = "ChangeMe123!";
    private static final Path GROUP_A_SQL = Path.of(System.getProperty("user.dir"))
            .getParent()
            .resolve("docs/team-delivery/group-a-identity-user-admin.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthController authController;

    @Autowired
    private JwtClaimsParser jwtClaimsParser;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        jdbcTemplate.execute("DROP ALL OBJECTS");
        executeGroupASeed();
    }

    @Test
    void shouldLoginSeededStudentAccountThroughRealAuthenticationChain() throws Exception {
        Claims accessClaims = loginAndParseAccessClaims("2022010101");

        assertThat(accessClaims.get("uid", Number.class).longValue()).isEqualTo(1001L);
        assertThat(accessClaims.get("uno", String.class)).isEqualTo("2022010101");
        assertThat(accessClaims.get("identity", String.class)).isEqualTo("STUDENT");
        assertThat(accessClaims.get("roles", List.class)).contains("STUDENT");
        assertThat(accessClaims.get("authorities", List.class))
                .contains("application.submit", "application.update", "application.view.self", "score.view.self");
        assertThat(activeSessionCount(1001L)).isEqualTo(2);
    }

    @Test
    void shouldLoginSeededCounselorAccountThroughRealAuthenticationChain() throws Exception {
        Claims accessClaims = loginAndParseAccessClaims("T20260001");

        assertThat(accessClaims.get("uid", Number.class).longValue()).isEqualTo(1010L);
        assertThat(accessClaims.get("identity", String.class)).isEqualTo("COUNSELOR");
        assertThat(accessClaims.get("roles", List.class)).contains("COUNSELOR");
        assertThat(accessClaims.get("authorities", List.class))
                .contains("application.review", "review.task.assign", "score.export.assigned");
        assertThat(activeSessionCount(1010L)).isEqualTo(2);
    }

    @Test
    void shouldLoginSeededPlatformAdminAccountThroughRealAuthenticationChain() throws Exception {
        Claims accessClaims = loginAndParseAccessClaims("A20260001");

        assertThat(accessClaims.get("uid", Number.class).longValue()).isEqualTo(1012L);
        assertThat(accessClaims.get("identity", String.class)).isEqualTo("PLATFORM_ADMIN");
        assertThat(accessClaims.get("roles", List.class)).contains("PLATFORM_ADMIN");
        assertThat(accessClaims.get("authorities", List.class))
                .contains("user.manage", "role.manage", "permission.manage", "platform.switch.manage");
        assertThat(activeSessionCount(1012L)).isEqualTo(2);
    }

    @Test
    void shouldRejectSeededDisabledAccountEvenWithValidPasswordHash() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credential":"2022010109","password":"ChangeMe123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-4010"));
    }

    private Claims loginAndParseAccessClaims(String credential) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"credential":"%s","password":"%s"}
                                """.formatted(credential, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.accessTokenType").value("access"))
                .andExpect(jsonPath("$.data.refreshTokenType").value("refresh"))
                .andReturn();
        String responseBody = result.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.data.accessToken");
        String refreshToken = JsonPath.read(responseBody, "$.data.refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        Claims accessClaims = jwtClaimsParser.parse(accessToken, "seed-account-login-test");
        Claims refreshClaims = jwtClaimsParser.parse(refreshToken, "seed-account-login-test");
        assertThat(refreshClaims.get("sid", String.class)).isEqualTo(accessClaims.get("sid", String.class));
        return accessClaims;
    }

    private Integer activeSessionCount(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_session WHERE user_id = ? AND status = 'ACTIVE' AND session_no IS NOT NULL AND access_token_id IS NOT NULL AND refresh_token_id IS NOT NULL",
                Integer.class,
                userId
        );
    }

    private void executeGroupASeed() throws Exception {
        String sql = toH2CompatibleSql(Files.readString(GROUP_A_SQL));
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    private static String toH2CompatibleSql(String sql) {
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
    @MapperScan(basePackageClasses = {
            IamUserMapper.class,
            IamUserRoleAssignmentMapper.class,
            IamRoleMapper.class,
            IamPermissionQueryMapper.class,
            IamScopeRuleQueryMapper.class,
            IamSessionMapper.class
    })
    @Import({
            MybatisPlusConfig.class,
            MybatisPlusIamUserQueryRepository.class,
            MybatisPlusRoleAssignmentQueryRepository.class,
            MybatisPlusUserAuthorityQueryRepository.class,
            MybatisPlusUserScopeRuleQueryRepository.class,
            MybatisPlusIamSessionRepository.class,
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
            dataSource.setUrl("jdbc:h2:mem:seed_account_login_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
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
}
