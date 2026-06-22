package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.query.UserPageQuery;
import edu.whut.eval.domain.iam.repository.IamUserQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.cache.UserCacheGateway;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.entity.IamUserDO;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamUserQueryRepository;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusIamUserQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusIamUserQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IamUserQueryRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user_role_assignment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_role");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute(
                "CREATE TABLE iam_user (" +
                        "id BIGINT PRIMARY KEY, " +
                        "user_no VARCHAR(64) NOT NULL, " +
                        "user_name VARCHAR(64) NOT NULL, " +
                        "email VARCHAR(128), " +
                        "phone VARCHAR(32), " +
                        "password_hash VARCHAR(255), " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL, " +
                        "updated_at TIMESTAMP NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE org_unit (" +
                        "id BIGINT PRIMARY KEY, " +
                        "unit_name VARCHAR(128) NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE org_membership (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "user_id BIGINT NOT NULL, " +
                        "org_unit_id BIGINT NOT NULL, " +
                        "membership_type VARCHAR(32) NOT NULL, " +
                        "is_primary TINYINT(1) NOT NULL DEFAULT 0, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "joined_at TIMESTAMP NOT NULL, " +
                        "left_at TIMESTAMP NULL, " +
                        "created_at TIMESTAMP NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE iam_role (" +
                        "id BIGINT PRIMARY KEY, " +
                        "role_code VARCHAR(64) NOT NULL)"
        );
        jdbcTemplate.execute(
                "CREATE TABLE iam_user_role_assignment (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "user_id BIGINT NOT NULL, " +
                        "role_id BIGINT NOT NULL, " +
                        "effective_from TIMESTAMP NOT NULL, " +
                        "effective_to TIMESTAMP NULL, " +
                        "status VARCHAR(32) NOT NULL)"
        );

        jdbcTemplate.update(
                "INSERT INTO iam_user (id, user_no, user_name, email, phone, password_hash, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                1010L, "2024305001", "王老师", "w@example.com", "13800000000", "hash", "ACTIVE",
                Timestamp.valueOf("2024-01-01 00:00:00"), Timestamp.valueOf("2024-01-01 00:00:00")
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user (id, user_no, user_name, email, phone, password_hash, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                1011L, "2024305002", "李老师", "l@example.com", "13800001111", "hash", "DISABLED",
                Timestamp.valueOf("2024-01-02 00:00:00"), Timestamp.valueOf("2024-01-02 00:00:00")
        );

        jdbcTemplate.update(
                "INSERT INTO org_unit (id, unit_name) VALUES (?, ?)",
                2002L, "计算机与人工智能学院"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, unit_name) VALUES (?, ?)",
                2009L, "计算机学院研工办"
        );
        jdbcTemplate.update(
                "INSERT INTO org_membership (user_id, org_unit_id, membership_type, is_primary, status, joined_at, left_at, created_at) VALUES (?,?,?,?,?,?,?,?)",
                1010L, 2002L, "IMPORT", 1, "ACTIVE",
                Timestamp.valueOf("2024-01-01 00:00:00"), null, Timestamp.valueOf("2024-01-01 00:00:00")
        );
        jdbcTemplate.update(
                "INSERT INTO org_membership (user_id, org_unit_id, membership_type, is_primary, status, joined_at, left_at, created_at) VALUES (?,?,?,?,?,?,?,?)",
                1011L, 2009L, "IMPORT", 1, "ACTIVE",
                Timestamp.valueOf("2024-01-02 00:00:00"), null, Timestamp.valueOf("2024-01-02 00:00:00")
        );
        jdbcTemplate.update("INSERT INTO iam_role (id, role_code) VALUES (?, ?)", 4003L, "COUNSELOR");
        jdbcTemplate.update("INSERT INTO iam_role (id, role_code) VALUES (?, ?)", 4006L, "PLATFORM_ADMIN");
        jdbcTemplate.update("INSERT INTO iam_role (id, role_code) VALUES (?, ?)", 4007L, "FUTURE_ROLE");
        jdbcTemplate.update("INSERT INTO iam_role (id, role_code) VALUES (?, ?)", 4008L, "EXPIRED_ROLE");
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (user_id, role_id, effective_from, effective_to, status) VALUES (?, ?, ?, ?, ?)",
                1010L, 4003L, Timestamp.valueOf("2024-01-01 00:00:00"), null, "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (user_id, role_id, effective_from, effective_to, status) VALUES (?, ?, ?, ?, ?)",
                1010L, 4007L, Timestamp.valueOf("2099-01-01 00:00:00"), null, "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (user_id, role_id, effective_from, effective_to, status) VALUES (?, ?, ?, ?, ?)",
                1010L, 4008L, Timestamp.valueOf("2024-01-01 00:00:00"), Timestamp.valueOf("2024-01-02 00:00:00"), "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user_role_assignment (user_id, role_id, effective_from, effective_to, status) VALUES (?, ?, ?, ?, ?)",
                1011L, 4006L, Timestamp.valueOf("2024-01-01 00:00:00"), null, "ACTIVE"
        );
    }

    @Test
    void shouldFilterByKeywordOnUserNoAndUserName() {
        UserPageQuery byNo = new UserPageQuery();
        byNo.setPageNo(1);
        byNo.setPageSize(20);
        byNo.setKeyword("2024305001");

        PageResult<IamUser> pageByNo = repository.pageUsers(byNo);

        assertThat(pageByNo.total()).isEqualTo(1L);
        assertThat(pageByNo.records()).extracting(IamUser::userNo).containsExactly("2024305001");

        UserPageQuery byName = new UserPageQuery();
        byName.setPageNo(1);
        byName.setPageSize(20);
        byName.setKeyword("李");

        PageResult<IamUser> pageByName = repository.pageUsers(byName);

        assertThat(pageByName.total()).isEqualTo(1L);
        assertThat(pageByName.records()).extracting(IamUser::userName).containsExactly("李老师");
    }

    @Test
    void shouldFilterByStatusAndOrgUnitId() {
        UserPageQuery query = new UserPageQuery();
        query.setPageNo(1);
        query.setPageSize(20);
        query.setStatus("ACTIVE");
        query.setOrgUnitId(2002L);

        PageResult<IamUser> page = repository.pageUsers(query);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).hasSize(1);
        assertThat(page.records().getFirst().id()).isEqualTo(1010L);
        assertThat(page.records().getFirst().createdAt()).isEqualTo("2024-01-01T00:00");
        assertThat(page.records()).allMatch(user -> "ACTIVE".equals(user.status()));
    }

    @Test
    void shouldLoadActiveOrgUnitNamesAndRoleCodesByUserIds() {
        assertThat(repository.findActiveOrgUnitNamesByUserIds(java.util.List.of(1010L)))
                .containsEntry(1010L, java.util.List.of("计算机与人工智能学院"));
        assertThat(repository.findActiveRoleCodesByUserIds(java.util.List.of(1010L)))
                .containsEntry(1010L, java.util.List.of("COUNSELOR"));
    }

    @Test
    void shouldExcludeFutureAndExpiredRoleAssignmentsFromActiveRoleCodes() {
        assertThat(repository.findActiveRoleCodesByUserIds(java.util.List.of(1010L)).get(1010L))
                .containsExactly("COUNSELOR");
    }

    @Configuration
    @MapperScan(basePackageClasses = {IamUserMapper.class, OrgMembershipMapper.class})
    @Import({MybatisPlusConfig.class, MybatisPlusIamUserQueryRepository.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:iam_user_query_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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

        @Bean
        UserCacheGateway userCacheGateway() {
            return new UserCacheGateway() {
                @Override
                public Optional<IamUser> getByUserNo(String userNo) {
                    return Optional.empty();
                }

                @Override
                public void put(IamUser user) {
                }

                @Override
                public void evictByUserNo(String userNo) {
                }
            };
        }
    }
}
