package edu.whut.eval.app.review;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.application.model.ApplicationReviewAction;
import edu.whut.eval.domain.application.model.ApplicationReviewLog;
import edu.whut.eval.domain.application.repository.ApplicationReviewLogRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ApplicationReviewLogMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusApplicationReviewLogRepository;
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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApplicationReviewLogRepositoryIntegrationTest.TestConfig.class)
class ApplicationReviewLogRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationReviewLogRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_review_log");
        jdbcTemplate.execute("""
                CREATE TABLE application_review_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    application_id BIGINT NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    reviewer_id BIGINT NOT NULL,
                    review_role VARCHAR(64) NOT NULL,
                    reason VARCHAR(1000) NULL,
                    reviewed_at DATETIME NOT NULL
                )
                """);
    }

    @Test
    void shouldAppendReviewLogAndReturnGeneratedId() {
        ApplicationReviewLog saved = repository.append(new ApplicationReviewLog(
                null,
                21013L,
                ApplicationReviewAction.APPROVE,
                1010L,
                "COUNSELOR",
                "材料完整",
                Instant.parse("2026-07-07T10:00:00Z")
        ));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAction()).isEqualTo(ApplicationReviewAction.APPROVE);
        assertThat(saved.getReviewedAt()).isEqualTo(Instant.parse("2026-07-07T10:00:00Z"));
        assertThat(jdbcTemplate.queryForObject("SELECT action FROM application_review_log WHERE id = ?", String.class, saved.getId()))
                .isEqualTo("APPROVE");
    }

    @Test
    void shouldListLogsByApplicationIdInStableOrder() {
        repository.append(new ApplicationReviewLog(null, 21013L, ApplicationReviewAction.RETURN, 1010L, "COUNSELOR", "补材料", Instant.parse("2026-07-07T10:00:00Z")));
        repository.append(new ApplicationReviewLog(null, 21013L, ApplicationReviewAction.APPROVE, 1011L, "COLLEGE_REVIEWER", "通过", Instant.parse("2026-07-07T11:00:00Z")));
        repository.append(new ApplicationReviewLog(null, 99999L, ApplicationReviewAction.REJECT, 1011L, "COLLEGE_REVIEWER", "其他申请", Instant.parse("2026-07-07T09:00:00Z")));

        List<ApplicationReviewLog> logs = repository.listByApplicationId(21013L);

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(ApplicationReviewLog::getAction)
                .containsExactly(ApplicationReviewAction.RETURN, ApplicationReviewAction.APPROVE);
    }

    @Configuration
    @MapperScan(basePackageClasses = ApplicationReviewLogMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisPlusApplicationReviewLogRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:application_review_log_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
