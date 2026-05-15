package edu.whut.eval.app.file;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.application.file.query.StoredFileDescriptor;
import edu.whut.eval.application.file.service.FileAssetRegistry;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.FileAssetWriteMapper;
import edu.whut.eval.infra.persistence.repository.MybatisFileAssetRegistry;
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
@ContextConfiguration(classes = MybatisFileAssetRegistryIntegrationTest.TestConfig.class)
class MybatisFileAssetRegistryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FileAssetRegistry fileAssetRegistry;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS file_asset");
        jdbcTemplate.execute(
                "CREATE TABLE file_asset (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "file_id VARCHAR(64) NOT NULL UNIQUE, " +
                        "storage_key VARCHAR(512) NOT NULL UNIQUE, " +
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
    }

    @Test
    void shouldInsertFileAssetAndReturnDescriptorWithFileId() {
        StoredFileDescriptor result = fileAssetRegistry.registerUploadedFile(
                new StoredFileDescriptor(
                        "whut-eval-dev",
                        "uploads/dev/profile/uuid-avatar.png",
                        "https://cdn.whut.example.com/uploads/dev/profile/uuid-avatar.png",
                        "avatar.png",
                        "image/png",
                        5L
                ),
                1001L,
                "student"
        );

        assertThat(result.getFileId()).startsWith("file_");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM file_asset", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT uploader_user_id FROM file_asset WHERE file_id = ?", Long.class, result.getFileId()))
                .isEqualTo(1001L);
        assertThat(jdbcTemplate.queryForObject("SELECT uploader_type FROM file_asset WHERE file_id = ?", String.class, result.getFileId()))
                .isEqualTo("USER");
        assertThat(jdbcTemplate.queryForObject("SELECT upload_channel FROM file_asset WHERE file_id = ?", String.class, result.getFileId()))
                .isEqualTo("SELF_UPLOAD");
        assertThat(jdbcTemplate.queryForObject("SELECT storage_key FROM file_asset WHERE file_id = ?", String.class, result.getFileId()))
                .isEqualTo("uploads/dev/profile/uuid-avatar.png");
    }

    @Configuration
    @MapperScan(basePackageClasses = FileAssetWriteMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisFileAssetRegistry.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:file_asset_registry_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
