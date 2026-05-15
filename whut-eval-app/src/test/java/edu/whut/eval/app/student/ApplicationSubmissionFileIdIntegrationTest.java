package edu.whut.eval.app.student;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.UpdateApplicationDraftCommand;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.domain.application.model.ApplicationSubmission;
import edu.whut.eval.domain.application.repository.ApplicationSubmissionRepository;
import edu.whut.eval.domain.application.service.ActiveSubmissionPolicy;
import edu.whut.eval.domain.application.service.ApplicationSubmissionWindowPolicy;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ApplicationAttachmentMapper;
import edu.whut.eval.infra.persistence.mapper.ApplicationSubmissionMapper;
import edu.whut.eval.infra.persistence.mapper.FileAssetMapper;
import edu.whut.eval.infra.persistence.mapper.PublicAttachmentEntryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisApplicationAttachmentResolver;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ApplicationSubmissionFileIdIntegrationTest.TestConfig.class)
class ApplicationSubmissionFileIdIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationSubmissionCommandApplicationService applicationSubmissionCommandApplicationService;

    @Autowired
    private ApplicationSubmissionRepository applicationSubmissionRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS public_attachment_entry");
        jdbcTemplate.execute("DROP TABLE IF EXISTS file_asset");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_attachment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_submission");
        jdbcTemplate.execute(
                "CREATE TABLE file_asset (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "file_id VARCHAR(64) NOT NULL, " +
                        "storage_key VARCHAR(512) NOT NULL, " +
                        "bucket VARCHAR(128) NOT NULL, " +
                        "original_filename VARCHAR(255) NOT NULL, " +
                        "content_type VARCHAR(128) NOT NULL, " +
                        "size BIGINT NOT NULL, " +
                        "sha256 CHAR(64) NULL, " +
                        "uploader_user_id BIGINT NOT NULL, " +
                        "uploader_type VARCHAR(32) NOT NULL, " +
                        "upload_channel VARCHAR(32) NOT NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL, " +
                        "updated_at TIMESTAMP NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE public_attachment_entry (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "file_id VARCHAR(64) NOT NULL, " +
                        "display_name VARCHAR(255) NOT NULL, " +
                        "description VARCHAR(1000) NULL, " +
                        "category_code VARCHAR(64) NULL, " +
                        "scope_type VARCHAR(32) NOT NULL, " +
                        "scope_value VARCHAR(128) NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "published_by BIGINT NULL, " +
                        "published_at TIMESTAMP NULL, " +
                        "sort_no INT NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL, " +
                        "updated_at TIMESTAMP NOT NULL)"
        );
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
    }

    @Test
    void shouldCreateDraftByResolvingAttachmentFileIds() {
        insertFileAsset("file-self", "uploads/self.pdf", 1001L, "ACTIVE");
        insertFileAsset("file-public", "uploads/public.pdf", 9001L, "ACTIVE");
        insertPublicEntry("file-public", "ALL", "PUBLISHED");

        ApplicationSubmissionView result = applicationSubmissionCommandApplicationService.createDraft(
                new CreateApplicationDraftCommand(
                        10L,
                        "competition",
                        "item-1",
                        "2025-2026",
                        "1",
                        "申请标题",
                        "申请说明",
                        List.of("file-self", "file-public")
                )
        );

        assertThat(result.getAttachmentCount()).isEqualTo(2);
        ApplicationSubmission saved = applicationSubmissionRepository.findById(result.getApplicationId()).orElseThrow();
        assertThat(saved.getEvidenceAttachments()).extracting("fileId")
                .containsExactly("file-self", "file-public");
        assertThat(saved.getEvidenceAttachments()).extracting("storageKey")
                .containsExactly("uploads/self.pdf", "uploads/public.pdf");
    }

    @Test
    void shouldUpdateDraftByReplacingResolvedAttachmentFileIds() {
        insertFileAsset("file-initial", "uploads/initial.pdf", 1001L, "ACTIVE");
        insertFileAsset("file-updated", "uploads/updated.pdf", 1001L, "ACTIVE");

        ApplicationSubmissionView created = applicationSubmissionCommandApplicationService.createDraft(
                new CreateApplicationDraftCommand(
                        10L,
                        "competition",
                        "item-1",
                        "2025-2026",
                        "1",
                        "申请标题",
                        "申请说明",
                        List.of("file-initial")
                )
        );

        ApplicationSubmissionView updated = applicationSubmissionCommandApplicationService.updateDraft(
                new UpdateApplicationDraftCommand(
                        created.getApplicationId(),
                        "新标题",
                        "新说明",
                        List.of("file-updated"),
                        created.getVersion()
                )
        );

        assertThat(updated.getAttachmentCount()).isEqualTo(1);
        ApplicationSubmission reloaded = applicationSubmissionRepository.findById(created.getApplicationId()).orElseThrow();
        assertThat(reloaded.getEvidenceAttachments()).hasSize(1);
        assertThat(reloaded.getEvidenceAttachments().get(0).getFileId()).isEqualTo("file-updated");
        assertThat(reloaded.getEvidenceAttachments().get(0).getStorageKey()).isEqualTo("uploads/updated.pdf");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM application_attachment WHERE application_id = ?", Long.class, created.getApplicationId()))
                .isEqualTo(1L);
    }

    private void insertFileAsset(String fileId, String storageKey, Long uploaderUserId, String status) {
        jdbcTemplate.update(
                "INSERT INTO file_asset (file_id, storage_key, bucket, original_filename, content_type, size, sha256, uploader_user_id, uploader_type, upload_channel, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                fileId, storageKey, "bucket-a", fileId + ".pdf", "application/pdf", 128L, null,
                uploaderUserId, "USER", "SELF_UPLOAD", status
        );
    }

    private void insertPublicEntry(String fileId, String scopeType, String status) {
        jdbcTemplate.update(
                "INSERT INTO public_attachment_entry (file_id, display_name, description, category_code, scope_type, scope_value, status, published_by, published_at, sort_no, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                fileId, fileId + "-display", null, "policy", scopeType, null, status, 9001L, 0
        );
    }

    @Configuration
    @MapperScan(basePackageClasses = {
            ApplicationSubmissionMapper.class,
            ApplicationAttachmentMapper.class,
            FileAssetMapper.class,
            PublicAttachmentEntryMapper.class
    })
    @Import({
            MybatisPlusConfig.class,
            MybatisPlusApplicationSubmissionRepository.class,
            MybatisApplicationAttachmentResolver.class,
            ApplicationSubmissionCommandApplicationService.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:application_submission_file_id_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        UserAuthorizationContextAssembler userAuthorizationContextAssembler() {
            return () -> Optional.of(new UserAuthorizationContext(
                    1001L,
                    "2024305999",
                    "Test User",
                    "student",
                    Set.of("student"),
                    Set.of("application.create", "application.update"),
                    List.of()
            ));
        }

        @Bean
        ApplicationSubmissionWindowPolicy applicationSubmissionWindowPolicy() {
            return (orgUnitId, categoryCode, itemCode, academicYear, term) -> true;
        }

        @Bean
        ActiveSubmissionPolicy activeSubmissionPolicy() {
            return (applicantUserId, itemCode, academicYear, term, excludedApplicationId) -> false;
        }
    }
}
