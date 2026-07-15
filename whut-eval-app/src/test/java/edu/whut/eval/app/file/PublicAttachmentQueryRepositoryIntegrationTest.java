package edu.whut.eval.app.file;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.application.file.query.FileAssetDescriptor;
import edu.whut.eval.application.file.query.PublicAttachmentDescriptor;
import edu.whut.eval.application.file.service.FileQueryRepository;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultScopePredicateBuilder;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.FileAssetMapper;
import edu.whut.eval.infra.persistence.mapper.PublicAttachmentEntryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisFileQueryRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PublicAttachmentQueryRepositoryIntegrationTest.TestConfig.class)
class PublicAttachmentQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FileQueryRepository fileQueryRepository;

    @Autowired
    private PublicAttachmentEntryMapper publicAttachmentEntryMapper;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_attachment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_submission");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS public_attachment_entry");
        jdbcTemplate.execute("DROP TABLE IF EXISTS file_asset");
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
                "CREATE TABLE org_unit (" +
                        "id BIGINT PRIMARY KEY, " +
                        "path VARCHAR(255) NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE application_submission (" +
                        "application_id BIGINT PRIMARY KEY, " +
                        "applicant_user_id BIGINT NOT NULL, " +
                        "org_unit_id BIGINT NOT NULL, " +
                        "category_code VARCHAR(64) NOT NULL, " +
                        "item_code VARCHAR(64) NOT NULL, " +
                        "status VARCHAR(32) NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE application_attachment (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "application_id BIGINT NOT NULL, " +
                        "file_id VARCHAR(64) NOT NULL)"
        );
    }

    @Test
    void shouldFindActiveFileByFileId() {
        insertFileAsset("file-active", "uploads/active.pdf", 1001L, "ACTIVE");
        insertFileAsset("file-inactive", "uploads/inactive.pdf", 1001L, "ARCHIVED");

        Optional<FileAssetDescriptor> activeFile = fileQueryRepository.findActiveFileByFileId("file-active");

        assertThat(activeFile).isPresent();
        assertThat(activeFile.orElseThrow().getStorageKey()).isEqualTo("uploads/active.pdf");
        assertThat(fileQueryRepository.findActiveFileByFileId("file-inactive")).isEmpty();
    }

    @Test
    void shouldDetectPublishedAllPublicAttachment() {
        insertFileAsset("file-public", "uploads/public.pdf", 9001L, "ACTIVE");
        insertPublicEntry(1L, "file-public", "INTELLECTUAL", "ALL", "PUBLISHED", 10, "2026-05-11 09:00:00");

        assertThat(fileQueryRepository.existsPublishedAllPublicAttachment("file-public")).isTrue();
        assertThat(fileQueryRepository.existsPublishedAllPublicAttachment("missing")).isFalse();
    }

    @Test
    void shouldListOnlyPublishedAllAttachmentsBackedByActiveFilesWithStableOrder() {
        insertFileAsset("file-first", "uploads/first.pdf", 9001L, "ACTIVE");
        insertFileAsset("file-second", "uploads/second.pdf", 9001L, "ACTIVE");
        insertFileAsset("file-role", "uploads/role.pdf", 9001L, "ACTIVE");
        insertFileAsset("file-org", "uploads/org.pdf", 9001L, "ACTIVE");
        insertFileAsset("file-draft", "uploads/draft.pdf", 9001L, "ACTIVE");
        insertFileAsset("file-offline", "uploads/offline.pdf", 9001L, "ACTIVE");
        insertFileAsset("file-inactive", "uploads/inactive.pdf", 9001L, "ARCHIVED");
        insertPublicEntry(14002L, "file-second", "INTELLECTUAL", "ALL", "PUBLISHED", 10, "2026-05-11 09:10:00");
        insertPublicEntry(14001L, "file-first", "INTELLECTUAL", "ALL", "PUBLISHED", 10, "2026-05-11 09:10:00");
        insertPublicEntry(14003L, "file-role", "INTELLECTUAL", "ROLE", "PUBLISHED", 20, "2026-05-11 09:20:00");
        insertPublicEntry(14004L, "file-org", "INTELLECTUAL", "ORG_UNIT", "PUBLISHED", 30, "2026-05-11 09:30:00");
        insertPublicEntry(14005L, "file-draft", "INTELLECTUAL", "ALL", "DRAFT", 40, "2026-05-11 09:40:00");
        insertPublicEntry(14006L, "file-offline", "INTELLECTUAL", "ALL", "OFFLINE", 50, "2026-05-11 09:50:00");
        insertPublicEntry(14007L, "file-inactive", "INTELLECTUAL", "ALL", "PUBLISHED", 60, "2026-05-11 10:00:00");
        insertFileAsset("file-moral", "uploads/moral.pdf", 9001L, "ACTIVE");
        insertPublicEntry(14008L, "file-moral", "MORAL", "ALL", "PUBLISHED", 1, "2026-05-11 11:00:00");

        List<PublicAttachmentDescriptor> responses = fileQueryRepository.listPublishedAllPublicAttachments("INTELLECTUAL");

        assertThat(responses).extracting(PublicAttachmentDescriptor::getEntryId)
                .containsExactly(14001L, 14002L);
        assertThat(responses.getFirst().getOriginalFilename()).isEqualTo("file-first.pdf");
    }

    @Test
    void shouldDetectActiveFileBoundToApplicationOwnedByCurrentUser() {
        insertFileAsset("file-bound", "uploads/bound.pdf", 9001L, "ACTIVE");
        jdbcTemplate.update("INSERT INTO org_unit (id, path) VALUES (2010, '/WHUT/CS/CS2021/CS2101')");
        jdbcTemplate.update("INSERT INTO application_submission (application_id, applicant_user_id, org_unit_id, category_code, item_code, status) VALUES (21013, 1001, 2010, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', 'SUBMITTED')");
        jdbcTemplate.update("INSERT INTO application_submission (application_id, applicant_user_id, org_unit_id, category_code, item_code, status) VALUES (21014, 1002, 2010, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', 'SUBMITTED')");
        jdbcTemplate.update("INSERT INTO application_attachment (application_id, file_id) VALUES (21013, 'file-bound')");

        assertThat(fileQueryRepository.existsVisibleApplicationBinding("file-bound", 1001L)).isTrue();
        assertThat(fileQueryRepository.existsVisibleApplicationBinding("file-bound", 1002L)).isFalse();
    }

    @Test
    void shouldInsertAndOfflinePublicAttachmentEntry() {
        insertFileAsset("file-new", "uploads/new.pdf", 9001L, "ACTIVE");

        PublicAttachmentEntryMapper.PublishPublicAttachmentSqlRecord record =
                new PublicAttachmentEntryMapper.PublishPublicAttachmentSqlRecord(
                        "file-new",
                        "新模板",
                        "填写说明",
                        "INTELLECTUAL",
                        "ALL",
                        null,
                        1012L,
                        java.time.LocalDateTime.parse("2026-07-15T10:00:00"),
                        15
                );

        int inserted = publicAttachmentEntryMapper.insertPublished(record);
        Long entryId = record.id();

        assertThat(inserted).isEqualTo(1);
        assertThat(entryId).isNotNull();
        assertThat(publicAttachmentEntryMapper.countActivePublishedByFileId("file-new")).isEqualTo(1);
        assertThat(publicAttachmentEntryMapper.selectById(entryId).orElseThrow().getStatus()).isEqualTo("PUBLISHED");

        int updated = publicAttachmentEntryMapper.offlineById(
                entryId,
                "资料过期",
                java.time.LocalDateTime.parse("2026-07-15T11:00:00")
        );

        assertThat(updated).isEqualTo(1);
        assertThat(publicAttachmentEntryMapper.selectById(entryId).orElseThrow().getStatus()).isEqualTo("OFFLINE");
        assertThat(publicAttachmentEntryMapper.countActivePublishedByFileId("file-new")).isZero();

        int repeatedUpdate = publicAttachmentEntryMapper.offlineById(
                entryId,
                "重复下架",
                java.time.LocalDateTime.parse("2026-07-15T12:00:00")
        );

        assertThat(repeatedUpdate).isZero();
    }

    private void insertFileAsset(String fileId, String storageKey, Long uploaderUserId, String status) {
        jdbcTemplate.update(
                "INSERT INTO file_asset (file_id, storage_key, bucket, original_filename, content_type, size, sha256, uploader_user_id, uploader_type, upload_channel, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                fileId, storageKey, "bucket-a", fileId + ".pdf", "application/pdf", 128L, null,
                uploaderUserId, "USER", "SELF_UPLOAD", status
        );
    }

    private void insertPublicEntry(Long id, String fileId, String categoryCode, String scopeType, String status,
                                   int sortNo, String publishedAt) {
        jdbcTemplate.update(
                "INSERT INTO public_attachment_entry (id, file_id, display_name, description, category_code, scope_type, scope_value, status, published_by, published_at, sort_no, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
                id, fileId, fileId + "-display", fileId + "-description", categoryCode, scopeType, null, status,
                9001L, publishedAt, sortNo
        );
    }

    @Configuration
    @MapperScan(basePackageClasses = {FileAssetMapper.class, PublicAttachmentEntryMapper.class})
    @Import({
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            DefaultScopePredicateBuilder.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            MybatisFileQueryRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:public_attachment_query_repository_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
    }
}
