package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentDetail;
import edu.whut.eval.domain.iam.model.IamRoleAssignmentPageItem;
import edu.whut.eval.domain.iam.query.RoleAssignmentPageQuery;
import edu.whut.eval.domain.iam.repository.RoleAssignmentAdminRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.mapper.IamRoleMapper;
import edu.whut.eval.infra.persistence.mapper.IamUserRoleAssignmentMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusRoleAssignmentAdminRepository;
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
@ContextConfiguration(classes = MybatisPlusRoleAssignmentAdminRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusRoleAssignmentAdminRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleAssignmentAdminRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user_role_assignment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_role");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute(
                "CREATE TABLE iam_user (" +
                        "id BIGINT PRIMARY KEY, " +
                        "user_no VARCHAR(64) NOT NULL, " +
                        "user_name VARCHAR(64) NOT NULL, " +
                        "email VARCHAR(128) NULL, " +
                        "phone VARCHAR(32) NULL, " +
                        "password_hash VARCHAR(255) NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NULL, " +
                        "updated_at TIMESTAMP NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE iam_role (" +
                        "id BIGINT PRIMARY KEY, " +
                        "role_code VARCHAR(64) NOT NULL, " +
                        "role_name VARCHAR(64) NOT NULL, " +
                        "status VARCHAR(32) NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE org_unit (" +
                        "id BIGINT PRIMARY KEY, " +
                        "parent_id BIGINT NULL, " +
                        "unit_type VARCHAR(32) NOT NULL, " +
                        "unit_code VARCHAR(64) NOT NULL, " +
                        "unit_name VARCHAR(128) NOT NULL, " +
                        "path VARCHAR(255) NOT NULL, " +
                        "status VARCHAR(32) NOT NULL)"
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
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, email, phone, password_hash, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                1010L, "2024305001", "王老师", "w@example.com", "13800000000", "hash", "ACTIVE",
                java.sql.Timestamp.valueOf("2026-05-01 00:00:00"), java.sql.Timestamp.valueOf("2026-05-18 00:00:00"));
        jdbcTemplate.update("INSERT INTO iam_role (id, role_code, role_name, status) VALUES (?,?,?,?)", 21L, "COUNSELOR", "辅导员", "ACTIVE");
        jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE");
    }

    @Test
    void shouldPersistAssignedByAndLoadDetail() {
        IamRoleAssignmentDetail created = repository.create(
                1010L,
                "COUNSELOR",
                "辅导员",
                2002L,
                "计算机与人工智能学院",
                "2026-05-18T00:00:00",
                "2027-07-01T00:00:00",
                "MANUAL",
                9001L,
                "ACTIVE"
        );

        Long assignedBy = jdbcTemplate.queryForObject(
                "SELECT assigned_by FROM iam_user_role_assignment WHERE id = ?",
                Long.class,
                created.assignmentId()
        );

        assertThat(assignedBy).isEqualTo(9001L);
        assertThat(repository.findDetailById(created.assignmentId())).isPresent();
        assertThat(repository.findDetailById(created.assignmentId()).get().roleCode()).isEqualTo("COUNSELOR");
    }

    @Test
    void shouldIgnoreFutureAndExpiredAssignmentsWhenCheckingDuplicate() {
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70021L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2099-01-01 00:00:00"), null, "ACTIVE", 9001L
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70022L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2020-01-01 00:00:00"), java.sql.Timestamp.valueOf("2021-01-01 00:00:00"), "ACTIVE", 9001L
        );

        assertThat(repository.existsActiveAssignment(1010L, "COUNSELOR", 2002L, null)).isFalse();

        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70023L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2024-01-01 00:00:00"), null, "ACTIVE", 9001L
        );

        assertThat(repository.existsActiveAssignment(1010L, "COUNSELOR", 2002L, null)).isTrue();
    }

    @Test
    void shouldPageRoleAssignmentsWithDisplayFieldsAndDerivedExpiredStatus() {
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70021L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2024-01-01 00:00:00"), null, "ACTIVE", 9001L
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70022L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2024-01-01 00:00:00"), java.sql.Timestamp.valueOf("2024-02-01 00:00:00"), "ACTIVE", 9001L
        );

        PageResult<IamRoleAssignmentPageItem> result = repository.pageAssignments(new RoleAssignmentPageQuery(
                1,
                20,
                1010L,
                "COUNSELOR",
                "EXPIRED",
                2002L
        ));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(item -> {
            assertThat(item.assignmentId()).isEqualTo(70022L);
            assertThat(item.userNo()).isEqualTo("2024305001");
            assertThat(item.userName()).isEqualTo("王老师");
            assertThat(item.status()).isEqualTo("EXPIRED");
        });
    }

    @Test
    void shouldDisplayFutureEffectiveAssignmentAsInactive() {
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70021L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2099-01-01 00:00:00"), null, "ACTIVE", 9001L
        );

        PageResult<IamRoleAssignmentPageItem> result = repository.pageAssignments(new RoleAssignmentPageQuery(
                1,
                20,
                1010L,
                "COUNSELOR",
                null,
                2002L
        ));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).singleElement().satisfies(item -> {
            assertThat(item.assignmentId()).isEqualTo(70021L);
            assertThat(item.status()).isEqualTo("INACTIVE");
        });
    }

    @Test
    void shouldRevokeAssignmentExplicitly() {
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (id, user_id, role_id, org_unit_id, source_type, effective_from, effective_to, status, assigned_by, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                70021L, 1010L, 21L, 2002L, "MANUAL", java.sql.Timestamp.valueOf("2024-01-01 00:00:00"), null, "ACTIVE", 9001L
        );

        IamRoleAssignmentDetail result = repository.revoke(70021L);

        assertThat(result.assignmentId()).isEqualTo(70021L);
        assertThat(result.status()).isEqualTo("INACTIVE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM iam_user_role_assignment WHERE id = ?",
                String.class,
                70021L
        )).isEqualTo("INACTIVE");
    }

    @Configuration
    @MapperScan(basePackageClasses = {IamUserRoleAssignmentMapper.class, IamRoleMapper.class, IamUserMapper.class, OrgUnitMapper.class})
    @Import({MybatisPlusConfig.class, MybatisPlusRoleAssignmentAdminRepository.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:role_assignment_admin_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
