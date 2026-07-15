package edu.whut.eval.app.student;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.application.query.LectureCandidatePageQuery;
import edu.whut.eval.domain.application.query.LectureCandidateRecord;
import edu.whut.eval.domain.application.repository.LectureCandidateQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.LectureCandidateQueryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisLectureCandidateQueryRepository;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisLectureCandidateQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisLectureCandidateQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LectureCandidateQueryRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_component_score");
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
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
                  updated_at DATETIME NOT NULL,
                  UNIQUE KEY uk_final_record_student_year (student_user_id, academic_year))
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
    }

    @Test
    void shouldPageCurrentStudentsImportedLectureComponentsByAcademicYearAndKeyword() {
        Long currentRecordId = insertRecord(1001L, "2025-2026");
        Long oldYearRecordId = insertRecord(1001L, "2024-2025");
        Long otherStudentRecordId = insertRecord(2002L, "2025-2026");
        insertComponent(currentRecordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "1.25",
                "学院学术讲座 讲座签到", "IMPORT", "LECTURE-20252026-20260518143000-ABCDEF123456");
        insertComponent(currentRecordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "1.00",
                "学院就业讲座 讲座签到", "IMPORT", "LECTURE-20252026-20260518153000-ABCDEF123457");
        insertComponent(currentRecordId, "INTELLECTUAL", "INTELLECTUAL_MENTOR", "5.00",
                "导师评价", "IMPORT", "MENTOR-1");
        insertComponent(currentRecordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "2.00",
                "学生申报讲座", "APPLICATION", "app-1");
        insertComponent(oldYearRecordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "1.00",
                "学院学术讲座 讲座签到", "IMPORT", "LECTURE-20242025-20240518143000-ABCDEF123456");
        insertComponent(otherStudentRecordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "1.00",
                "学院学术讲座 讲座签到", "IMPORT", "LECTURE-20252026-20260518143000-ABCDEF123456");

        PageResult<LectureCandidateRecord> page = repository.pageStudentLectureCandidates(
                1001L,
                new LectureCandidatePageQuery("2025-2026", "学术", 1, 10)
        );

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).hasSize(1);
        LectureCandidateRecord record = page.records().getFirst();
        assertThat(record.lectureId()).isNotNull();
        assertThat(record.title()).isEqualTo("学院学术讲座 讲座签到");
        assertThat(record.academicYear()).isEqualTo("2025-2026");
        assertThat(record.maxScore()).isEqualByComparingTo("1.25");
        assertThat(record.sourceRefId()).isEqualTo("LECTURE-20252026-20260518143000-ABCDEF123456");
    }

    @Test
    void shouldReturnEmptyPageWhenStudentHasNoImportedLectureComponents() {
        Long recordId = insertRecord(1001L, "2025-2026");
        insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_MENTOR", "5.00",
                "导师评价", "IMPORT", "MENTOR-1");

        PageResult<LectureCandidateRecord> page = repository.pageStudentLectureCandidates(
                1001L,
                new LectureCandidatePageQuery("2025-2026", null, 1, 10)
        );

        assertThat(page.total()).isZero();
        assertThat(page.records()).isEmpty();
    }

    @Test
    void shouldApplyPaginationToImportedLectureComponents() {
        Long recordId = insertRecord(1001L, "2025-2026");
        insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "1.00",
                "讲座A", "IMPORT", "LECTURE-20252026-20260518143000-AAAAAA111111");
        insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "2.00",
                "讲座B", "IMPORT", "LECTURE-20252026-20260518153000-BBBBBB222222");

        PageResult<LectureCandidateRecord> page = repository.pageStudentLectureCandidates(
                1001L,
                new LectureCandidatePageQuery("2025-2026", null, 2, 1)
        );

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().getFirst().title()).isEqualTo("讲座B");
    }

    private Long insertRecord(Long studentUserId, String academicYear) {
        jdbcTemplate.update("""
                INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total,
                                          grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                VALUES (?, ?, 'DRAFT', 0, 0, 0, 0, 0, NULL, NULL, NULL, 0, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """, studentUserId, academicYear);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM final_record", Long.class);
    }

    private void insertComponent(Long finalRecordId, String categoryCode, String itemCode, String scoreValue,
                                 String displayText, String sourceType, String sourceRefId) {
        jdbcTemplate.update("""
                INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP())
                """, finalRecordId, categoryCode, itemCode, new BigDecimal(scoreValue), displayText, sourceType, sourceRefId);
    }

    @Configuration
    @MapperScan(basePackageClasses = LectureCandidateQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisLectureCandidateQueryRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            org.springframework.jdbc.datasource.DriverManagerDataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:lecture_candidate_query_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
