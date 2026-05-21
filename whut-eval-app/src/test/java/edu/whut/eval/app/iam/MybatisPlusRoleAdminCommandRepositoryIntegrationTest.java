package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.iam.model.IamRole;
import edu.whut.eval.domain.iam.repository.RoleAdminCommandRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamPermissionMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamRolePermissionMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAdminCommandRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusRoleAdminCommandRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusRoleAdminCommandRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleAdminCommandRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_role_permission");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_permission");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_role");
        jdbcTemplate.execute(
                "CREATE TABLE iam_role (" +
                        "id BIGINT PRIMARY KEY, " +
                        "role_code VARCHAR(64) NOT NULL UNIQUE, " +
                        "role_name VARCHAR(128) NOT NULL, " +
                        "role_scope VARCHAR(32) NOT NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE iam_permission (" +
                        "id BIGINT PRIMARY KEY, " +
                        "permission_code VARCHAR(128) NOT NULL, " +
                        "permission_name VARCHAR(128) NOT NULL, " +
                        "permission_group VARCHAR(64) NOT NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE iam_role_permission (" +
                        "id BIGINT PRIMARY KEY, " +
                        "role_id BIGINT NOT NULL, " +
                        "permission_id BIGINT NOT NULL, " +
                        "created_at TIMESTAMP NULL)"
        );
        jdbcTemplate.update("INSERT INTO iam_role (id, role_code, role_name, role_scope, status, created_at) VALUES (?,?,?,?,?,?)",
                21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", java.sql.Timestamp.valueOf("2026-05-20 10:00:00"));
        jdbcTemplate.update("INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at) VALUES (?,?,?,?,?,?)",
                5010L, "permission.manage", "权限管理", "manage", "ACTIVE", java.sql.Timestamp.valueOf("2026-05-20 10:00:00"));
        jdbcTemplate.update("INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at) VALUES (?,?,?,?,?,?)",
                5011L, "role.manage", "角色管理", "manage", "ACTIVE", java.sql.Timestamp.valueOf("2026-05-20 10:00:00"));
        jdbcTemplate.update("INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at) VALUES (?,?,?,?,?,?)",
                5012L, "user.manage", "用户管理", "manage", "ACTIVE", java.sql.Timestamp.valueOf("2026-05-20 10:00:00"));
        jdbcTemplate.update("INSERT INTO iam_role_permission (id, role_id, permission_id, created_at) VALUES (?,?,?,?)",
                7001L, 21L, 5010L, java.sql.Timestamp.valueOf("2026-05-20 10:00:00"));
        jdbcTemplate.update("INSERT INTO iam_role_permission (id, role_id, permission_id, created_at) VALUES (?,?,?,?)",
                7002L, 21L, 5012L, java.sql.Timestamp.valueOf("2026-05-20 10:00:00"));
    }

    @Test
    void shouldCreateRoleWithRoleScope() {
        IamRole result = repository.create("COLLEGE_ADMIN", "学院管理员", "ORG_SUBTREE", "ACTIVE");

        assertThat(result.roleId()).isNotNull();
        assertThat(result.roleCode()).isEqualTo("COLLEGE_ADMIN");
        assertThat(jdbcTemplate.queryForObject("SELECT role_scope FROM iam_role WHERE role_code = ?", String.class, "COLLEGE_ADMIN"))
                .isEqualTo("ORG_SUBTREE");
    }

    @Test
    void shouldTranslateDuplicateRoleCodeToConflictException() {
        assertThatThrownBy(() -> repository.create("COUNSELOR", "重复辅导员", "ORG_UNIT", "ACTIVE"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("roleCode 已存在");
    }

    @Test
    void shouldUpdateRoleWithExpectedSnapshot() {
        IamRole existing = new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00");
        boolean updated = repository.update(existing, "学院辅导员", "ORG_UNIT", "DISABLED");

        assertThat(updated).isTrue();
        assertThat(repository.findById(21L)).isPresent();
        assertThat(repository.findById(21L).get()).satisfies(item -> {
            assertThat(item.roleName()).isEqualTo("学院辅导员");
            assertThat(item.roleScope()).isEqualTo("ORG_UNIT");
            assertThat(item.status()).isEqualTo("DISABLED");
        });
    }

    @Test
    void shouldReturnFalseWhenExpectedSnapshotDoesNotMatchStatus() {
        IamRole existing = new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "DISABLED", "2026-05-20T10:00:00");
        boolean updated = repository.update(existing, "学院辅导员", "ORG_UNIT", "DISABLED");

        assertThat(updated).isFalse();
    }

    @Test
    void shouldReturnFalseWhenExpectedSnapshotDoesNotMatchRoleFields() {
        jdbcTemplate.update("UPDATE iam_role SET role_name = ? WHERE id = ?", "学院辅导员", 21L);

        IamRole existing = new IamRole(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE", "2026-05-20T10:00:00");
        boolean updated = repository.update(existing, "学生辅导员", "ORG_UNIT", "DISABLED");

        assertThat(updated).isFalse();
    }

    @Test
    void shouldReplaceRolePermissions() {
        repository.replacePermissions(21L, java.util.List.of("permission.manage", "role.manage"));

        assertThat(jdbcTemplate.queryForList(
                "SELECT p.permission_code FROM iam_role_permission rp " +
                        "JOIN iam_permission p ON p.id = rp.permission_id " +
                        "WHERE rp.role_id = ? ORDER BY p.permission_code",
                String.class,
                21L
        )).containsExactly("permission.manage", "role.manage");
    }

    @Configuration
    @Import(MybatisPlusConfig.class)
    @MapperScan(basePackageClasses = {IamRoleMapper.class, IamRolePermissionMapper.class, IamPermissionMapper.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:role_admin_repo;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                            MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setPlugins(mybatisPlusInterceptor);
            factoryBean.setConfiguration(new MybatisConfiguration());
            return factoryBean.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        RoleAdminCommandRepository roleAdminCommandRepository(IamRoleMapper iamRoleMapper,
                                                              IamRolePermissionMapper iamRolePermissionMapper,
                                                              IamPermissionMapper iamPermissionMapper) {
            return new MybatisPlusRoleAdminCommandRepository(iamRoleMapper, iamRolePermissionMapper, iamPermissionMapper);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
