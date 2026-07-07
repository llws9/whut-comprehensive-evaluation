package edu.whut.eval.app.review;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.application.query.ReviewApplicationQueryRow;
import edu.whut.eval.application.application.repository.ReviewApplicationQueryRepository;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultScopePredicateBuilder;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ReviewApplicationPageQuery;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ReviewApplicationQueryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusReviewApplicationQueryRepository;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ReviewApplicationQueryRepositoryIntegrationTest.TestConfig.class)
class ReviewApplicationQueryRepositoryIntegrationTest {

    private static final String APPLICATION_PERMISSION = "application.review";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewApplicationQueryRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        recreateTables();
        insertRows();
    }

    @Test
    void shouldFilterReviewListByScopeStatusAcademicYearAndKeyword() {
        PageResult<ReviewApplicationQueryRow> result = repository.pageReviewApplications(
                accessContext(),
                new ReviewApplicationPageQuery(1, 20, "2025-2026", "INTELLECTUAL", null, "SUBMITTED", "20210001", null)
        );

        assertThat(result.total()).isEqualTo(1);
        ReviewApplicationQueryRow row = result.records().get(0);
        assertThat(row.getApplicationId()).isEqualTo(21013L);
        assertThat(row.getApplicantUserName()).isEqualTo("张三");
        assertThat(row.getApplicantUserNo()).isEqualTo("20210001");
        assertThat(row.getOrgUnitName()).isEqualTo("计算机 2101 班");
        assertThat(row.getTitle()).isEqualTo("期刊论文录用申请");
        assertThat(row.getStatus()).isEqualTo("SUBMITTED");
        assertThat(row.getOrgPath()).isEqualTo("/1/2002/2010/");
    }

    @Test
    void shouldLoadReviewDetailResourceWithOrgPathAttachmentsAndScoringSnapshot() {
        ReviewApplicationQueryRow detail = repository.findReviewApplicationDetail(accessContext(), 21013L).orElseThrow();

        assertThat(detail.getApplicationId()).isEqualTo(21013L);
        assertThat(detail.getOrgPath()).isEqualTo("/1/2002/2010/");
        assertThat(detail.getOptionCode()).isEqualTo("PAPER_CORE_FIRST_AUTHOR");
        assertThat(detail.getAppliedPoints()).isEqualByComparingTo("2.00");
        assertThat(detail.getAttachments()).hasSize(1);
        assertThat(detail.getAttachments().get(0).getFileId()).isEqualTo("file-1");
        assertThat(detail.getAttachments().get(0).getStorageKey()).isEqualTo("storage/private/a.pdf");
    }

    @Test
    void shouldRejectUnsupportedReviewListStatus() {
        assertThatThrownBy(() -> new ReviewApplicationPageQuery(1, 20, null, null, null, "DRAFT", null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 SUBMITTED、APPROVED、RETURNED 或 REJECTED");
    }

    @Test
    void shouldRejectTooLargeReviewPageSize() {
        assertThatThrownBy(() -> new ReviewApplicationPageQuery(1, 101, null, null, null, "SUBMITTED", null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("pageSize 不能超过 100");
    }

    private void recreateTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_attachment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_fact");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_submission");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("CREATE TABLE iam_user (id BIGINT PRIMARY KEY, user_no VARCHAR(64), user_name VARCHAR(128), status VARCHAR(32))");
        jdbcTemplate.execute("CREATE TABLE org_unit (id BIGINT PRIMARY KEY, unit_name VARCHAR(128), path VARCHAR(255), status VARCHAR(32))");
        jdbcTemplate.execute("""
                CREATE TABLE application_submission (
                    application_id BIGINT PRIMARY KEY,
                    applicant_user_id BIGINT NOT NULL,
                    org_unit_id BIGINT NOT NULL,
                    category_code VARCHAR(64) NOT NULL,
                    item_code VARCHAR(64) NOT NULL,
                    academic_year VARCHAR(32) NOT NULL,
                    term VARCHAR(32) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    description VARCHAR(1000) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    submitted_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    version BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE application_attachment (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    application_id BIGINT NOT NULL,
                    file_id VARCHAR(128) NOT NULL,
                    storage_key VARCHAR(512) NOT NULL,
                    original_filename VARCHAR(255) NOT NULL,
                    content_type VARCHAR(128) NOT NULL,
                    size BIGINT NOT NULL,
                    uploaded_by BIGINT NOT NULL,
                    sort_no INT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE application_fact (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    application_id BIGINT NOT NULL,
                    score_value DECIMAL(10,2) NULL,
                    display_text VARCHAR(1000) NULL,
                    evidence_count INT NOT NULL,
                    extra_json VARCHAR(2000) NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL
                )
                """);
    }

    private void insertRows() {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1001, '20210001', '张三', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1002, '20210002', '李四', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO org_unit (id, unit_name, path, status) VALUES (2010, '计算机 2101 班', '/1/2002/2010/', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO org_unit (id, unit_name, path, status) VALUES (3000, '外部班级', '/1/3000/', 'ACTIVE')");
        jdbcTemplate.update("""
                INSERT INTO application_submission (application_id, applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version)
                VALUES (21013, 1001, 2010, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '2025-2026', '上学期', '期刊论文录用申请', '申请说明', 'SUBMITTED', '2026-07-06 10:00:00', '2026-07-06 09:00:00', '2026-07-06 10:00:00', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO application_submission (application_id, applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version)
                VALUES (21014, 1002, 3000, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '2025-2026', '上学期', '不可见申请', '申请说明', 'SUBMITTED', '2026-07-06 10:00:00', '2026-07-06 09:00:00', '2026-07-06 10:00:00', 1)
                """);
        jdbcTemplate.update("INSERT INTO application_attachment (application_id, file_id, storage_key, original_filename, content_type, size, uploaded_by, sort_no) VALUES (21013, 'file-1', 'storage/private/a.pdf', 'a.pdf', 'application/pdf', 128, 1001, 0)");
        jdbcTemplate.update("""
                INSERT INTO application_fact (application_id, score_value, display_text, evidence_count, extra_json, created_at, updated_at)
                VALUES (21013, 2.00, NULL, 1, '{"optionCode":"PAPER_CORE_FIRST_AUTHOR","maxPoints":"6.00","exceedsMaxPoints":false}', '2026-07-06 10:00:00', '2026-07-06 10:00:00')
                """);
    }

    private ApplicationAccessContext accessContext() {
        return new ApplicationAccessContext(
                1010L,
                "reviewer-1",
                "Reviewer",
                "COUNSELOR",
                Set.of("COUNSELOR"),
                Set.of(APPLICATION_PERMISSION),
                List.of(new IamScopeRule(1L, APPLICATION_PERMISSION, "ORG_SUBTREE", 2002L, null, null, null, 10, "ACTIVE")),
                APPLICATION_PERMISSION
        );
    }

    @Configuration
    @MapperScan(basePackageClasses = ReviewApplicationQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            DefaultScopePredicateBuilder.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            MybatisPlusReviewApplicationQueryRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:review_application_query_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
    }
}
