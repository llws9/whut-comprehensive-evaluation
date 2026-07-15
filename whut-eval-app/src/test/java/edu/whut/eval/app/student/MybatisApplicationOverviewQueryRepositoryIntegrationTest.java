package edu.whut.eval.app.student;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.application.query.ApplicationOverviewSummary;
import edu.whut.eval.domain.application.repository.ApplicationOverviewQueryRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ApplicationOverviewQueryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisApplicationOverviewQueryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisApplicationOverviewQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisApplicationOverviewQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationOverviewQueryRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_submission");
        jdbcTemplate.execute(
                "CREATE TABLE application_submission (" +
                        "application_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "applicant_user_id BIGINT NOT NULL, " +
                        "org_unit_id BIGINT NOT NULL, " +
                        "category_code VARCHAR(64) NOT NULL, " +
                        "item_code VARCHAR(64) NOT NULL, " +
                        "academic_year VARCHAR(32) NOT NULL, " +
                        "term VARCHAR(32) NOT NULL, " +
                        "title VARCHAR(255) NOT NULL, " +
                        "description VARCHAR(1000) NOT NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "submitted_at TIMESTAMP NULL, " +
                        "created_at TIMESTAMP NOT NULL, " +
                        "updated_at TIMESTAMP NOT NULL, " +
                        "version BIGINT NOT NULL)"
        );
    }

    @Test
    void shouldCountCurrentStudentsApplicationsAndIgnoreDeletedAndOtherUsers() {
        insertSubmission(1001L, "DRAFT", "2024-2025");
        insertSubmission(1001L, "DRAFT", "2025-2026");
        insertSubmission(1001L, "SUBMITTED", "2025-2026");
        insertSubmission(1001L, "RETURNED", "2023-2024");
        insertSubmission(1001L, "APPROVED", "2024-2025");
        insertSubmission(1001L, "REJECTED", "2022-2023");
        insertSubmission(1001L, "WITHDRAWN", "2026-2027");
        insertSubmission(1001L, "DELETED", "2027-2028");
        insertSubmission(2002L, "DRAFT", "2029-2030");

        ApplicationOverviewSummary overview = repository.getStudentOverview(1001L);

        assertThat(overview.draftCount()).isEqualTo(2);
        assertThat(overview.submittedCount()).isEqualTo(1);
        assertThat(overview.returnedCount()).isEqualTo(1);
        assertThat(overview.approvedCount()).isEqualTo(1);
        assertThat(overview.rejectedCount()).isEqualTo(1);
        assertThat(overview.latestAcademicYear()).isEqualTo("2026-2027");
    }

    @Test
    void shouldReturnZeroOverviewForStudentWithoutApplications() {
        insertSubmission(2002L, "DRAFT", "2029-2030");

        ApplicationOverviewSummary overview = repository.getStudentOverview(1001L);

        assertThat(overview.draftCount()).isZero();
        assertThat(overview.submittedCount()).isZero();
        assertThat(overview.returnedCount()).isZero();
        assertThat(overview.approvedCount()).isZero();
        assertThat(overview.rejectedCount()).isZero();
        assertThat(overview.latestAcademicYear()).isNull();
    }

    private void insertSubmission(Long applicantUserId, String status, String academicYear) {
        jdbcTemplate.update(
                "INSERT INTO application_submission (applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), ?)",
                applicantUserId, 2010L, "INTELLECTUAL", "item-1", academicYear, "1", "申请标题", "申请说明", status, 0L
        );
    }

    @Configuration
    @MapperScan(basePackageClasses = ApplicationOverviewQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisApplicationOverviewQueryRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:application_overview_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
    }
}
