package edu.whut.eval.app.finalrecord;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.finalrecord.model.FinalComponentScore;
import edu.whut.eval.domain.finalrecord.model.FinalRecord;
import edu.whut.eval.domain.finalrecord.repository.AggregatedFinalRecordSnapshot;
import edu.whut.eval.domain.finalrecord.repository.FinalRecordRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.FinalComponentScoreMapper;
import edu.whut.eval.infra.persistence.mapper.FinalRecordAggregationMapper;
import edu.whut.eval.infra.persistence.mapper.FinalRecordMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusFinalRecordRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusFinalRecordRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusFinalRecordRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinalRecordRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_component_score");
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_fact");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_submission");
        jdbcTemplate.execute("""
                CREATE TABLE application_submission (
                  application_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  applicant_user_id BIGINT NOT NULL,
                  org_unit_id BIGINT NOT NULL,
                  category_code VARCHAR(64) NOT NULL,
                  item_code VARCHAR(64) NOT NULL,
                  academic_year VARCHAR(32) NOT NULL,
                  term VARCHAR(32) NOT NULL,
                  title VARCHAR(255) NOT NULL,
                  description VARCHAR(1000) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  submitted_at TIMESTAMP NULL,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL,
                  version BIGINT NOT NULL)
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
                  updated_at DATETIME NOT NULL)
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
    void shouldAggregateApprovedFactsIntoSubmittedRecord() {
        long sportsId = insertApplication(1001L, 2010L, "SPORTS", "SPORTS_COMPETITION", "2025-2026", "APPROVED");
        insertFact(sportsId, "0.60", "体育竞赛已审核通过");
        long paperId = insertApplication(1001L, 2010L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2025-2026", "APPROVED");
        insertFact(paperId, "2.00", "论文已审核通过");
        long laborId = insertApplication(1001L, 2010L, "LABOR", "LABOR_SERVICE", "2025-2026", "APPROVED");
        insertFact(laborId, "1.20", "劳动实践已审核通过");
        long draftId = insertApplication(1001L, 2010L, "MORAL", "MORAL_HONOR", "2025-2026", "DRAFT");
        insertFact(draftId, "9.99", "草稿不得进入最终成绩");

        AggregatedFinalRecordSnapshot snapshot = repository.aggregateApprovedFacts(1001L, "2025-2026");

        assertThat(snapshot.components()).extracting(FinalComponentScore::getCategoryCode, FinalComponentScore::getItemCode)
                .containsExactly(
                        tuple("INTELLECTUAL", "INTELLECTUAL_PAPER"),
                        tuple("LABOR", "LABOR_SERVICE"),
                        tuple("SPORTS", "SPORTS_COMPETITION")
                );
        assertThat(snapshot.intellectualTotal()).isEqualByComparingTo("2.00");
        assertThat(snapshot.laborTotal()).isEqualByComparingTo("1.20");
        assertThat(snapshot.moralTotal()).isEqualByComparingTo("0.00");
        assertThat(snapshot.physicalTotal()).isEqualByComparingTo("0.60");
        assertThat(snapshot.grandTotal()).isEqualByComparingTo("3.80");
    }

    @Test
    void shouldFailAggregationWhenNoApprovedSubmissionsExist() {
        insertApplication(1001L, 2010L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2025-2026", "DRAFT");

        assertThatThrownBy(() -> repository.aggregateApprovedFacts(1001L, "2025-2026"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已审核申请");
    }

    @Test
    void shouldFailAggregationWhenApprovedSubmissionHasNoFactRows() {
        insertApplication(1001L, 2010L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2025-2026", "APPROVED");

        assertThatThrownBy(() -> repository.aggregateApprovedFacts(1001L, "2025-2026"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("approved snapshot");
    }

    @Test
    void shouldFailAggregationWhenApprovedFactScoreIsNull() {
        long applicationId = insertApplication(1001L, 2010L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2025-2026", "APPROVED");
        insertFact(applicationId, null, "分值缺失");

        assertThatThrownBy(() -> repository.aggregateApprovedFacts(1001L, "2025-2026"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("score");
    }

    @Test
    void shouldFailAggregationWhenCategoryDoesNotBelongToFinalTotalPartition() {
        long applicationId = insertApplication(1001L, 2010L, "UNKNOWN", "UNKNOWN_ITEM", "2025-2026", "APPROVED");
        insertFact(applicationId, "1.00", "未知分类");

        assertThatThrownBy(() -> repository.aggregateApprovedFacts(1001L, "2025-2026"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("category");
    }

    @Test
    void shouldPersistTransitionAndReplaceComponentsDefensively() {
        FinalRecord inserted = repository.insertDraft(draftRecord());
        repository.batchInsertComponents(inserted.getId(), List.of(component("LABOR", "LABOR_SERVICE", "1.20", "21013")));
        repository.deleteComponents(inserted.getId());
        repository.batchInsertComponents(inserted.getId(), List.of(component("INTELLECTUAL", "INTELLECTUAL_PAPER", "2.00", "21014")));

        FinalRecord submitted = repository.updateTransition(inserted.submit(0L));

        assertThat(submitted.getStatus().name()).isEqualTo("SUBMITTED");
        assertThat(repository.listComponents(inserted.getId())).extracting(FinalComponentScore::getItemCode)
                .containsExactly("INTELLECTUAL_PAPER");
    }

    @Test
    void shouldMapDuplicateStudentYearCreateToConflictException() {
        repository.insertDraft(draftRecord());

        assertThatThrownBy(() -> repository.insertDraft(draftRecord()))
                .isInstanceOf(ConflictException.class);
    }

    private FinalRecord draftRecord() {
        Instant now = Instant.parse("2026-07-07T12:00:00Z");
        return FinalRecord.createDraft(null, 1001L, "2025-2026",
                new BigDecimal("0.80"), new BigDecimal("5.00"), new BigDecimal("0.60"),
                new BigDecimal("1.20"), new BigDecimal("7.60"), now);
    }

    private FinalComponentScore component(String categoryCode, String itemCode, String score, String sourceRefId) {
        return new FinalComponentScore(null, null, categoryCode, itemCode, new BigDecimal(score),
                "component", "APPLICATION", sourceRefId, Instant.parse("2026-07-07T12:00:00Z"));
    }

    private long insertApplication(Long applicantUserId, Long orgUnitId, String categoryCode, String itemCode, String academicYear, String status) {
        jdbcTemplate.update("""
                INSERT INTO application_submission (applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, '1', 'title', 'desc', ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), 0)
                """, applicantUserId, orgUnitId, categoryCode, itemCode, academicYear, status);
        return jdbcTemplate.queryForObject("SELECT MAX(application_id) FROM application_submission", Long.class);
    }

    private void insertFact(long applicationId, String scoreValue, String displayText) {
        jdbcTemplate.update("""
                INSERT INTO application_fact (application_id, score_value, display_text, evidence_count, extra_json, created_at, updated_at)
                VALUES (?, ?, ?, 1, '{}', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """, applicationId, scoreValue == null ? null : new BigDecimal(scoreValue), displayText);
    }

    @Configuration
    @MapperScan(basePackageClasses = {FinalRecordMapper.class, FinalComponentScoreMapper.class, FinalRecordAggregationMapper.class})
    @Import({
            MybatisPlusConfig.class,
            MybatisPlusFinalRecordRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:final_record_repository_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
    }
}
