package edu.whut.eval.app.finalrecord;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.query.FinalRecordAccessContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.FinalRecordQueryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusFinalRecordQueryRepository;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusFinalRecordQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusFinalRecordQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinalRecordQueryRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_component_score");
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("CREATE TABLE iam_user (id BIGINT PRIMARY KEY, user_no VARCHAR(64), user_name VARCHAR(128), status VARCHAR(32))");
        jdbcTemplate.execute("CREATE TABLE org_unit (id BIGINT PRIMARY KEY, unit_name VARCHAR(128), path VARCHAR(512))");
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
        jdbcTemplate.execute("""
                CREATE TABLE final_component_score (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  final_record_id BIGINT NOT NULL,
                  category_code VARCHAR(64) NOT NULL,
                  item_code VARCHAR(64) NOT NULL,
                  score_value DECIMAL(10,2) NOT NULL,
                  display_text VARCHAR(1000) NULL,
                  source_type VARCHAR(32) NOT NULL,
                  source_ref_id VARCHAR(64) NULL,
                  created_at DATETIME NOT NULL)
                """);
        insertOrgUnit(2002L, "计算机与人工智能学院", "/WHUT/CS");
        insertStudent(1001L, "2024305001", "张三", 2010L, "计科一班", "/WHUT/CS/CS2022/CS2201");
        insertStudent(1002L, "2024305002", "李四", 2011L, "计科二班", "/WHUT/CS/CS2022/CS2202");
        insertFinalRecord(41001L, 1001L, "2025-2026", "SUBMITTED", "2026-07-07 12:00:00");
        insertFinalRecord(41002L, 1002L, "2025-2026", "SUBMITTED", "2026-07-07 13:00:00");
        insertComponent(41001L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2.00", "论文已审核通过", "21013");
    }

    @Test
    void shouldNormalizePageQueryAndRejectDraftStatus() {
        FinalRecordPageQuery query = new FinalRecordPageQuery("2025-2026", null, "  ", null, 0, 200);

        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(100);
        assertThat(query.getKeyword()).isNull();
        assertThatThrownBy(() -> new FinalRecordPageQuery("2025-2026", "DRAFT", null, null, 1, 20))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new FinalRecordPageQuery(" ", null, null, null, 1, 20))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldPageAdminRecordsWithinWholeRecordScope() {
        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithOrgSubtree(2002L),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
                .containsExactly(41002L, 41001L);
    }

    @Test
    void shouldHideDraftRecordsFromAdminListAndDetail() {
        insertFinalRecord(41003L, 1001L, "2025-2026", "DRAFT", "2026-07-07 14:00:00");

        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithOrgSubtree(2002L),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
                .doesNotContain(41003L);
        assertThat(repository.findAdminFinalRecordDetail(41003L)).isEmpty();
    }

    @Test
    void shouldUsePrimaryMembershipOnlyForFinalRecordScopeAndListRows() {
        jdbcTemplate.update("INSERT INTO org_membership (user_id, org_unit_id, membership_type, is_primary, status) VALUES (?, ?, 'STUDENT', 0, 'ACTIVE')",
                1001L, 2011L);

        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithOrgSubtree(2002L),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
                .containsExactly(41002L, 41001L);
    }

    @Test
    void shouldKeepNoMembershipRecordsVisibleToAllScopeWithNullOrgFields() {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                1003L, "2024305003", "王五");
        insertFinalRecord(41003L, 1003L, "2025-2026", "SUBMITTED", "2026-07-07 14:00:00");

        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithAllScope(),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(3L);
        assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
                .contains(41003L);
        assertThat(repository.findAdminFinalRecordDetail(41003L))
                .get()
                .extracting(FinalRecordQueryRow::getOrgUnitId, FinalRecordQueryRow::getOrgPath)
                .containsExactly(null, null);
    }

    @Test
    void shouldReturnEmptyPageForUnsupportedScopeOnlyCaller() {
        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithCategoryOnlyScope(),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isZero();
        assertThat(page.records()).isEmpty();
    }

    @Test
    void shouldFindStudentFinalRecordAndOrderedComponents() {
        assertThat(repository.findStudentFinalRecord(1001L, "2025-2026"))
                .map(FinalRecordQueryRow::getFinalRecordId)
                .contains(41001L);

        List<FinalComponentScoreRow> components = repository.listStudentFinalRecordComponents(41001L);

        assertThat(components).hasSize(1);
        assertThat(components.get(0).getItemName()).isNull();
        assertThat(components.get(0).getDisplayText()).isEqualTo("论文已审核通过");
    }

    @Test
    void shouldFindAdminDetailUnscopedForAccessValidatorContext() {
        assertThat(repository.findAdminFinalRecordDetail(41001L))
                .map(FinalRecordQueryRow::getOrgPath)
                .contains("/WHUT/CS/CS2022/CS2201");
        assertThat(repository.listAdminFinalRecordComponents(41001L)).hasSize(1);
    }

    private FinalRecordAccessContext accessContextWithAllScope() {
        return new FinalRecordAccessContext(
                1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), Set.of("score.view.assigned"),
                List.of(new IamScopeRule(3L, "score.view.assigned", "ALL", null, null, null, null, 100, "ACTIVE")),
                "score.view.assigned"
        );
    }

    private FinalRecordAccessContext accessContextWithOrgSubtree(Long orgUnitId) {
        return new FinalRecordAccessContext(
                1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), Set.of("score.view.assigned"),
                List.of(new IamScopeRule(1L, "score.view.assigned", "ORG_SUBTREE", orgUnitId, null, null, null, 80, "ACTIVE")),
                "score.view.assigned"
        );
    }

    private FinalRecordAccessContext accessContextWithCategoryOnlyScope() {
        return new FinalRecordAccessContext(
                1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), Set.of("score.view.assigned"),
                List.of(new IamScopeRule(2L, "score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null, null, 80, "ACTIVE")),
                "score.view.assigned"
        );
    }

    private void insertStudent(Long userId, String userNo, String userName, Long orgUnitId, String orgName, String orgPath) {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (?, ?, ?, 'ACTIVE')", userId, userNo, userName);
        insertOrgUnit(orgUnitId, orgName, orgPath);
        jdbcTemplate.update("INSERT INTO org_membership (user_id, org_unit_id, membership_type, is_primary, status) VALUES (?, ?, 'STUDENT', 1, 'ACTIVE')", userId, orgUnitId);
    }

    private void insertOrgUnit(Long orgUnitId, String orgName, String orgPath) {
        jdbcTemplate.update("INSERT INTO org_unit (id, unit_name, path) VALUES (?, ?, ?)", orgUnitId, orgName, orgPath);
    }

    private void insertFinalRecord(Long id, Long studentUserId, String academicYear, String status, String submittedAt) {
        jdbcTemplate.update("""
                INSERT INTO final_record (id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0.80, 2.00, 0.60, 1.20, 4.60, ?, NULL, NULL, 1, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """, id, studentUserId, academicYear, status, submittedAt);
    }

    private void insertComponent(Long recordId, String categoryCode, String itemCode, String score, String displayText, String sourceRefId) {
        jdbcTemplate.update("""
                INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at)
                VALUES (?, ?, ?, ?, ?, 'APPLICATION', ?, CURRENT_TIMESTAMP())
                """, recordId, categoryCode, itemCode, score, displayText, sourceRefId);
    }

    @Configuration
    @MapperScan(basePackageClasses = FinalRecordQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            MybatisPlusFinalRecordQueryRepository.class
    })
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:final_record_query_repository_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
