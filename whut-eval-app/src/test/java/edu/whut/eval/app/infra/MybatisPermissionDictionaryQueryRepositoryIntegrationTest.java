package edu.whut.eval.app.infra;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.iam.repository.PermissionDictionaryQueryRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.AdminPermissionDictionaryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPermissionDictionaryQueryRepository;
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
@ContextConfiguration(classes = MybatisPermissionDictionaryQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisPermissionDictionaryQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PermissionDictionaryQueryRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_permission");
        jdbcTemplate.execute("CREATE TABLE iam_permission (" +
                "id BIGINT PRIMARY KEY, " +
                "permission_code VARCHAR(128) NOT NULL, " +
                "permission_name VARCHAR(128) NOT NULL, " +
                "permission_group VARCHAR(64) NOT NULL, " +
                "status VARCHAR(32) NOT NULL)");
        jdbcTemplate.update(
                "INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status) VALUES (?,?,?,?,?)",
                5010L, "permission.manage", "权限管理", "manage", "ACTIVE"
        );
    }

    @Test
    void shouldDeriveStableDescriptionFromPermissionName() {
        assertThat(repository.findPermissions("permission", "manage", "ACTIVE"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.permissionCode()).isEqualTo("permission.manage");
                    assertThat(item.description()).isEqualTo("权限管理");
                });
    }

    @Configuration
    @MapperScan(basePackageClasses = AdminPermissionDictionaryMapper.class)
    @Import(MybatisPlusConfig.class)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:permission_dictionary_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        MybatisPermissionDictionaryQueryRepository mybatisPermissionDictionaryQueryRepository(
                AdminPermissionDictionaryMapper adminPermissionDictionaryMapper) {
            return new MybatisPermissionDictionaryQueryRepository(adminPermissionDictionaryMapper);
        }
    }
}
