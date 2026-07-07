package edu.whut.eval.app.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.application.model.ApplicationScoringSnapshot;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ApplicationAttachmentMapper;
import edu.whut.eval.infra.persistence.mapper.ApplicationFactMapper;
import edu.whut.eval.infra.persistence.mapper.ApplicationSubmissionMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusApplicationSubmissionRepository;
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
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusApplicationSubmissionRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusApplicationSubmissionRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationSubmissionRepository applicationSubmissionRepository;

    @Autowired
    private ApplicationSubmissionMapper applicationSubmissionMapper;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_fact");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_attachment");
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
        jdbcTemplate.execute(
                "CREATE TABLE application_attachment (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "application_id BIGINT NOT NULL, " +
                        "file_id VARCHAR(128) NOT NULL, " +
                        "storage_key VARCHAR(255) NOT NULL, " +
                        "original_filename VARCHAR(255) NOT NULL, " +
                        "content_type VARCHAR(128) NOT NULL, " +
                        "size BIGINT NOT NULL, " +
                        "uploaded_by BIGINT NOT NULL, " +
                        "sort_no INT NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE application_fact (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "application_id BIGINT NOT NULL, " +
                        "score_value DECIMAL(10,2) NULL, " +
                        "display_text VARCHAR(1000) NULL, " +
                        "evidence_count INT NOT NULL, " +
                        "extra_json VARCHAR(2000) NULL, " +
                        "created_at DATETIME NOT NULL, " +
                        "updated_at DATETIME NOT NULL, " +
                        "UNIQUE KEY uk_application_fact_application_id (application_id))"
        );
    }

    @Test
    void shouldSaveDraftAndReadItBackWithAttachments() {
        ApplicationSubmission saved = applicationSubmissionRepository.save(draft("file-1", "uploads/a.pdf"));

        assertThat(saved.getApplicationId()).isNotNull();
        assertThat(saved.getEvidenceAttachments()).hasSize(1);
        assertThat(saved.getEvidenceAttachments().get(0).getStorageKey()).isEqualTo("uploads/a.pdf");
        assertThat(applicationSubmissionRepository.findById(saved.getApplicationId()))
                .map(ApplicationSubmission::getTitle)
                .contains("申请标题");
    }

    @Test
    void shouldReplaceAttachmentsWhenUpdatingAggregate() {
        ApplicationSubmission saved = applicationSubmissionRepository.save(draft("file-1", "uploads/a.pdf"));
        ApplicationSubmission updated = saved.updateDraft(
                "新标题",
                "新说明",
                List.of(new AttachmentRef("file-2", "uploads/b.pdf", "b.pdf", "application/pdf", 20L, 1001L)),
                0L
        );

        ApplicationSubmission reloaded = applicationSubmissionRepository.save(updated);

        assertThat(reloaded.getVersion()).isEqualTo(1L);
        assertThat(reloaded.getEvidenceAttachments()).hasSize(1);
        assertThat(reloaded.getEvidenceAttachments().get(0).getFileId()).isEqualTo("file-2");
    }

    @Test
    void shouldCountApprovedAsActiveClaimAndIgnoreRejectedAndWithdrawn() {
        insertSubmission(1001L, "item-1", "2025-2026", "1", "APPROVED");
        assertThat(applicationSubmissionMapper.countActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).isEqualTo(1);

        jdbcTemplate.update("UPDATE application_submission SET status = 'REJECTED' WHERE applicant_user_id = 1001 AND item_code = 'item-1' AND academic_year = '2025-2026' AND term = '1'");
        assertThat(applicationSubmissionMapper.countActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).isZero();

        jdbcTemplate.update("UPDATE application_submission SET status = 'WITHDRAWN' WHERE applicant_user_id = 1001 AND item_code = 'item-1' AND academic_year = '2025-2026' AND term = '1'");
        assertThat(applicationSubmissionMapper.countActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).isZero();
    }

    @Test
    void shouldSaveAndReloadScoringSnapshot() {
        ApplicationSubmission submitted = draft("file-1", "uploads/a.pdf")
                .submit(0L, new ApplicationScoringSnapshot("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null));

        ApplicationSubmission saved = applicationSubmissionRepository.save(submitted);
        ApplicationSubmission reloaded = applicationSubmissionRepository.findById(saved.getApplicationId()).orElseThrow();

        assertThat(reloaded.getScoringSnapshot()).isNotNull();
        assertThat(reloaded.getScoringSnapshot().optionCode()).isEqualTo("OPTION_A");
        assertThat(reloaded.getScoringSnapshot().appliedPoints()).isEqualByComparingTo("2.00");
        assertThat(reloaded.getScoringSnapshot().evidenceCount()).isEqualTo(1);
    }

    @Test
    void shouldPreserveApplicationFactWhenApprovingSubmittedApplication() {
        ApplicationSubmission submitted = draft("file-1", "uploads/a.pdf")
                .submit(0L, new ApplicationScoringSnapshot("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null));
        ApplicationSubmission saved = applicationSubmissionRepository.save(submitted);

        ApplicationSubmission approved = applicationSubmissionRepository.save(saved.approve(saved.getVersion()));

        assertThat(approved.getStatus().name()).isEqualTo("APPROVED");
        assertThat(approved.getScoringSnapshot()).isNotNull();
        assertThat(approved.getScoringSnapshot().optionCode()).isEqualTo("OPTION_A");
        assertThat(approved.getScoringSnapshot().appliedPoints()).isEqualByComparingTo("2.00");
        assertThat(approved.getScoringSnapshot().maxPoints()).isEqualByComparingTo("6.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_fact WHERE application_id = ?",
                Integer.class,
                approved.getApplicationId()
        )).isEqualTo(1);
    }

    private ApplicationSubmission draft(String fileId, String storageKey) {
        return ApplicationSubmission.createDraft(
                1001L,
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of(new AttachmentRef(fileId, storageKey, "a.pdf", "application/pdf", 10L, 1001L))
        );
    }

    private void insertSubmission(Long applicantUserId, String itemCode, String academicYear, String term, String status) {
        jdbcTemplate.update(
                "INSERT INTO application_submission (applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), ?)",
                applicantUserId, 2010L, "INTELLECTUAL", itemCode, academicYear, term, "申请标题", "申请说明", status, 0L
        );
    }

    @Configuration
    @MapperScan(basePackageClasses = {ApplicationSubmissionMapper.class, ApplicationAttachmentMapper.class, ApplicationFactMapper.class})
    @Import({
            MybatisPlusConfig.class,
            MybatisPlusApplicationSubmissionRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:application_submission_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
