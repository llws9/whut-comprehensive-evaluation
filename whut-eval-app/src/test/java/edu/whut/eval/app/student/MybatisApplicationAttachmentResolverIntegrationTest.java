package edu.whut.eval.app.student;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.application.application.service.ApplicationAttachmentResolver;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.application.model.AttachmentRef;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.FileAssetMapper;
import edu.whut.eval.infra.persistence.mapper.PublicAttachmentEntryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisApplicationAttachmentResolver;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisApplicationAttachmentResolverIntegrationTest.TestConfig.class)
class MybatisApplicationAttachmentResolverIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationAttachmentResolver applicationAttachmentResolver;

    @BeforeEach
    void setUpSchema() {
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
    }

    @Test
    void shouldResolveOwnActiveFile() {
        insertFileAsset("file-own", "uploads/self.pdf", 1001L, "ACTIVE");

        List<AttachmentRef> attachments = applicationAttachmentResolver.resolveForBinding(List.of("file-own"), 1001L);

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getFileId()).isEqualTo("file-own");
        assertThat(attachments.get(0).getStorageKey()).isEqualTo("uploads/self.pdf");
        assertThat(attachments.get(0).getUploadedBy()).isEqualTo(1001L);
    }

    @Test
    void shouldResolvePublishedAllPublicFileForOtherUser() {
        insertFileAsset("file-public", "uploads/public.pdf", 9001L, "ACTIVE");
        insertPublicEntry("file-public", "ALL", "PUBLISHED");

        List<AttachmentRef> attachments = applicationAttachmentResolver.resolveForBinding(List.of("file-public"), 1001L);

        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getFileId()).isEqualTo("file-public");
        assertThat(attachments.get(0).getUploadedBy()).isEqualTo(9001L);
    }

    @Test
    void shouldRejectWhenPublicEntryScopeIsNotAll() {
        insertFileAsset("file-scoped", "uploads/scoped.pdf", 9001L, "ACTIVE");
        insertPublicEntry("file-scoped", "ORG_UNIT", "PUBLISHED");

        assertThatThrownBy(() -> applicationAttachmentResolver.resolveForBinding(List.of("file-scoped"), 1001L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("当前用户无权使用指定附件");
    }

    @Test
    void shouldRejectWhenFileIsInactive() {
        insertFileAsset("file-deleted", "uploads/deleted.pdf", 1001L, "DELETED");

        assertThatThrownBy(() -> applicationAttachmentResolver.resolveForBinding(List.of("file-deleted"), 1001L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("附件不存在或已失效");
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
    @MapperScan(basePackageClasses = {FileAssetMapper.class, PublicAttachmentEntryMapper.class})
    @Import({
            MybatisPlusConfig.class,
            MybatisApplicationAttachmentResolver.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:application_attachment_resolver_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
