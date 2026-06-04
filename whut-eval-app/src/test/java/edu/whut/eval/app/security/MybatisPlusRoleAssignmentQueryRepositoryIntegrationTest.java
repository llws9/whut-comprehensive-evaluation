package edu.whut.eval.app.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.repository.RoleAssignmentQueryRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAssignmentQueryRepository;
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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusRoleAssignmentQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusRoleAssignmentQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleAssignmentQueryRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user_role_assignment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_role");
        jdbcTemplate.execute(
                "CREATE TABLE iam_role (" +
                        "id BIGINT PRIMARY KEY, " +
                        "role_code VARCHAR(64) NOT NULL, " +
                        "role_name VARCHAR(64) NOT NULL, " +
                        "role_scope VARCHAR(32) NOT NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE iam_user_role_assignment (" +
                        "id BIGINT PRIMARY KEY, " +
                        "user_id BIGINT NOT NULL, " +
                        "role_id BIGINT NOT NULL, " +
                        "org_unit_id BIGINT NOT NULL, " +
                        "source_type VARCHAR(32) NOT NULL, " +
                        "effective_from TIMESTAMP NOT NULL, " +
                        "effective_to TIMESTAMP NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "assigned_by BIGINT NULL, " +
                        "created_at TIMESTAMP NOT NULL)"
        );
        jdbcTemplate.update("INSERT INTO iam_role (id, role_code, role_name, role_scope, status, created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE");
    }

    @Test
    void shouldFilterRuntimeActiveAssignmentsByCurrentEffectiveTimeWindow() {
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70021L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2024-01-01 00:00:00"), null, "ACTIVE", 9001L
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70022L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2099-01-01 00:00:00"), null, "ACTIVE", 9001L
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70023L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2024-01-01 00:00:00"), java.sql.Timestamp.valueOf("2024-02-01 00:00:00"), "ACTIVE", 9001L
        );

        List<IamRoleAssignment> result = repository.findActiveAssignmentsByUserId(1010L);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.assignmentId()).isEqualTo(70021L);
            assertThat(item.roleCode()).isEqualTo("COUNSELOR");
            assertThat(item.status()).isEqualTo("ACTIVE");
        });
    }

    @Configuration
    @MapperScan(basePackageClasses = {IamUserRoleAssignmentMapper.class, IamRoleMapper.class})
    @Import({MybatisPlusConfig.class, MybatisPlusRoleAssignmentQueryRepository.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:role_assignment_query_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
