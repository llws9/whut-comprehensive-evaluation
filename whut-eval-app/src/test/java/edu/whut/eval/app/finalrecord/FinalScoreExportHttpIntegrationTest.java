package edu.whut.eval.app.finalrecord;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.model.AccessSessionValidationCommand;
import edu.whut.eval.application.auth.service.AccessSessionService;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.application.auth.service.UserAuthorizationContextLoader;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportApplicationService;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContextLoadRequest;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.finalrecord.exporting.PoiFinalScoreExportWorkbookWriter;
import edu.whut.eval.infra.persistence.mapper.FinalRecordQueryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusFinalRecordQueryRepository;
import edu.whut.eval.infra.security.config.JwtConfigurationValidator;
import edu.whut.eval.infra.security.config.SecurityConfiguration;
import edu.whut.eval.infra.security.context.SecurityContextCurrentUserProvider;
import edu.whut.eval.infra.security.context.SecurityContextUserAuthorizationContextAssembler;
import edu.whut.eval.infra.security.jwt.JwtAuthenticationFilter;
import edu.whut.eval.infra.security.jwt.JwtClaimsParser;
import edu.whut.eval.infra.security.jwt.JwtClaimsToCurrentUserMapper;
import edu.whut.eval.infra.security.jwt.JwtTokenResolver;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.web.RestAccessDeniedHandler;
import edu.whut.eval.infra.security.web.RestAuthenticationEntryPoint;
import edu.whut.eval.interfaces.admin.AdminFinalScoreExportController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = FinalScoreExportHttpIntegrationTest.TestApplication.class)
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
class FinalScoreExportHttpIntegrationTest {

    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";
    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private AccessSessionService accessSessionService;

    @BeforeEach
    void setUp() {
        resetSchema();
        seedData();
        given(userAuthorizationContextLoader.load(any(UserAuthorizationContextLoadRequest.class))).willAnswer(invocation -> {
            UserAuthorizationContextLoadRequest request = invocation.getArgument(0);
            return new UserAuthorizationContext(
                    request.getUserId(),
                    request.getUserNo(),
                    request.getUserName(),
                    request.getIdentity(),
                    request.getRoles(),
                    Set.of(AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED),
                    List.of(new IamScopeRule(
                            8023L,
                            AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED,
                            "ORG_SUBTREE",
                            2002L,
                            null,
                            null,
                            "{\"scoreRole\":\"counselor\"}",
                            80,
                            "ACTIVE"
                    ))
            );
        });
    }

    @Test
    void shouldExportScopedFinalScoresThroughHttpAsXlsx() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/exports/final-scores")
                        .param("academicYear", "2025-2026")
                        .param("classes", "CS2201")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        endsWith("final-scores-2025-2026.xlsx\"")))
                .andReturn();

        then(accessSessionService).should().validateAccessSession(
                new AccessSessionValidationCommand(1010L, "session-export-1010", "access-jti-export")
        );
        assertWorkbook(result.getResponse().getContentAsByteArray());
    }

    private void assertWorkbook(byte[] content) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheet("final-scores");
            assertThat(sheet).isNotNull();
            assertThat(headerValues(sheet.getRow(0))).containsExactly(
                    "最终成绩ID",
                    "学年",
                    "学号",
                    "姓名",
                    "年级编码",
                    "年级",
                    "班级编码",
                    "班级",
                    "状态",
                    "德育总分",
                    "智育总分",
                    "体育总分",
                    "劳育总分",
                    "总分",
                    "提交时间",
                    "确认时间"
            );

            Row firstDataRow = sheet.getRow(1);
            assertThat(firstDataRow.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("41001");
            assertThat(firstDataRow.getCell(1).getStringCellValue()).isEqualTo("2025-2026");
            assertThat(firstDataRow.getCell(2).getStringCellValue()).isEqualTo("2024305001");
            assertThat(firstDataRow.getCell(3).getStringCellValue()).isEqualTo("张三");
            assertThat(firstDataRow.getCell(6).getStringCellValue()).isEqualTo("CS2201");
            assertThat(firstDataRow.getCell(8).getStringCellValue()).isEqualTo("SUBMITTED");
            assertThat(firstDataRow.getCell(9).getNumericCellValue()).isEqualTo(0.80);
            assertThat(firstDataRow.getCell(10).getNumericCellValue()).isEqualTo(2.00);
            assertThat(firstDataRow.getCell(11).getNumericCellValue()).isEqualTo(0.60);
            assertThat(firstDataRow.getCell(12).getNumericCellValue()).isEqualTo(1.20);
            assertThat(firstDataRow.getCell(13).getNumericCellValue()).isEqualTo(4.60);
            assertThat(sheet.getRow(2)).isNull();
        }
    }

    private List<String> headerValues(Row row) {
        return java.util.stream.IntStream.range(0, 16)
                .mapToObj(index -> row.getCell(index).getStringCellValue())
                .toList();
    }

    private String token() {
        Instant now = Instant.now();
        return Jwts.builder()
                .id("access-jti-export")
                .subject("1010")
                .issuer("whut-eval")
                .audience().add("whut-eval-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .claim("uid", 1010L)
                .claim("uno", "T1010")
                .claim("uname", "Counselor")
                .claim("identity", "teacher")
                .claim("sid", "session-export-1010")
                .claim("roles", List.of("counselor"))
                .claim("authorities", List.of(AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED))
                .claim("token_type", "access")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private void resetSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("CREATE TABLE iam_user (id BIGINT PRIMARY KEY, user_no VARCHAR(64), user_name VARCHAR(128), status VARCHAR(32))");
        jdbcTemplate.execute("""
                CREATE TABLE org_unit (
                  id BIGINT PRIMARY KEY,
                  parent_id BIGINT NULL,
                  unit_type VARCHAR(32) NOT NULL,
                  unit_code VARCHAR(64) NOT NULL,
                  unit_name VARCHAR(128) NOT NULL,
                  path VARCHAR(512) NOT NULL,
                  status VARCHAR(32) NOT NULL)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE org_membership (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  org_unit_id BIGINT NOT NULL,
                  membership_type VARCHAR(32) NOT NULL,
                  is_primary TINYINT(1) NOT NULL DEFAULT 0,
                  status VARCHAR(32) NOT NULL)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE final_record (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  student_user_id BIGINT NOT NULL,
                  academic_year VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  moral_total DECIMAL(10,2) NOT NULL,
                  intellectual_total DECIMAL(10,2) NOT NULL,
                  physical_total DECIMAL(10,2) NOT NULL,
                  labor_total DECIMAL(10,2) NOT NULL,
                  grand_total DECIMAL(10,2) NOT NULL,
                  submitted_at DATETIME NULL,
                  confirmed_at DATETIME NULL,
                  confirm_comment VARCHAR(1000) NULL,
                  version BIGINT NOT NULL,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL)
                """);
    }

    private void seedData() {
        insertOrgUnit(2001L, null, "SCHOOL", "WHUT", "武汉理工大学", "/WHUT", "ACTIVE");
        insertOrgUnit(2002L, 2001L, "COLLEGE", "CS", "计算机与人工智能学院", "/WHUT/CS", "ACTIVE");
        insertOrgUnit(2005L, 2002L, "GRADE", "CS2022", "2022级计算机", "/WHUT/CS/CS2022", "ACTIVE");
        insertOrgUnit(2010L, 2005L, "CLASS", "CS2201", "计科一班", "/WHUT/CS/CS2022/CS2201", "ACTIVE");
        insertOrgUnit(2011L, 2005L, "CLASS", "CS2202", "计科二班", "/WHUT/CS/CS2022/CS2202", "ACTIVE");
        insertStudent(1001L, "2024305001", "张三", 2010L);
        insertStudent(1002L, "2024305002", "李四", 2011L);
        insertStudent(1003L, "2024305003", "王五", 2010L);
        insertFinalRecord(41001L, 1001L, "2025-2026", "SUBMITTED");
        insertFinalRecord(41002L, 1002L, "2025-2026", "SUBMITTED");
        insertFinalRecord(41003L, 1003L, "2024-2025", "SUBMITTED");
    }

    private void insertOrgUnit(Long orgUnitId,
                               Long parentId,
                               String unitType,
                               String unitCode,
                               String orgName,
                               String orgPath,
                               String status) {
        jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                orgUnitId, parentId, unitType, unitCode, orgName, orgPath, status);
    }

    private void insertStudent(Long userId, String userNo, String userName, Long orgUnitId) {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                userId, userNo, userName);
        jdbcTemplate.update("INSERT INTO org_membership (user_id, org_unit_id, membership_type, is_primary, status) VALUES (?, ?, 'STUDENT', 1, 'ACTIVE')",
                userId, orgUnitId);
    }

    private void insertFinalRecord(Long id, Long studentUserId, String academicYear, String status) {
        jdbcTemplate.update("""
                INSERT INTO final_record (id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0.80, 2.00, 0.60, 1.20, 4.60, '2026-07-07 12:00:00', NULL, NULL, 1, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """, id, studentUserId, academicYear, status);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    @MapperScan(basePackageClasses = FinalRecordQueryMapper.class)
    @Import({
            AdminFinalScoreExportController.class,
            GlobalExceptionHandler.class,
            FinalScoreExportApplicationService.class,
            PoiFinalScoreExportWorkbookWriter.class,
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            MybatisPlusFinalRecordQueryRepository.class,
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
    static class TestApplication {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:final_score_export_http_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
