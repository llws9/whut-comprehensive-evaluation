package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserMembershipAdminRepository;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusUserMembershipAdminRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusUserMembershipAdminRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMembershipAdminRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
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
        jdbcTemplate.update(
                "INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status, joined_at, left_at, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                70021L, 1010L, 2002L, "IMPORT", 1, "ACTIVE",
                java.sql.Timestamp.valueOf("2024-01-01 00:00:00"), null, java.sql.Timestamp.valueOf("2024-01-01 00:00:00")
        );
        jdbcTemplate.update(
                "INSERT INTO iam_user (id, user_no, user_name, email, phone, password_hash, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                1010L, "2024305001", "王老师", "w@example.com", "13800000000", "hash", "ACTIVE",
                Timestamp.valueOf("2024-01-01 00:00:00"), Timestamp.valueOf("2024-01-01 00:00:00")
        );
        jdbcTemplate.update(
                "INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status, joined_at, left_at, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                70022L, 1010L, 2009L, "SYNC", 0, "ACTIVE",
                java.sql.Timestamp.valueOf("2024-02-01 00:00:00"), null, java.sql.Timestamp.valueOf("2024-02-01 00:00:00")
        );
    }

    @Test
    void shouldCreatePrimaryMembershipForUser() {
        repository.createPrimaryMembership(1010L, 2011L, "2026-06-03T10:00:00");

        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT user_id, org_unit_id, membership_type, is_primary, status, joined_at, left_at, created_at " +
                        "FROM org_membership WHERE user_id = ? AND org_unit_id = ? AND status = 'ACTIVE'",
                1010L,
                2011L
        );

        assertThat(created.get("user_id")).isEqualTo(1010L);
        assertThat(created.get("org_unit_id")).isEqualTo(2011L);
        assertThat(created.get("membership_type")).isEqualTo("MANUAL");
        assertThat(created.get("is_primary")).isIn(true, 1);
        assertThat(created.get("status")).isEqualTo("ACTIVE");
        assertThat(created.get("joined_at")).isEqualTo(Timestamp.valueOf("2026-06-03 10:00:00"));
        assertThat(created.get("created_at")).isEqualTo(Timestamp.valueOf("2026-06-03 10:00:00"));
        assertThat(created.get("left_at")).isNull();
    }

    @Test
    void shouldReplaceMembershipSetByPreservingTypeAndMarkingRemovedInactive() {
        repository.replaceMemberships(
                1010L,
                List.of(
                        new OrgMembership(70021L, 1010L, 2002L, "IMPORT", false, "ACTIVE", "2024-01-01T00:00:00", null),
                        new OrgMembership(null, 1010L, 2010L, "MANUAL", true, "ACTIVE", "2026-05-19T10:00:00", null)
                ),
                List.of(
                        new OrgMembership(70022L, 1010L, 2009L, "SYNC", false, "INACTIVE", "2024-02-01T00:00:00", "2026-05-19T10:00:00")
                )
        );

        Map<String, Object> kept = jdbcTemplate.queryForMap(
                "SELECT membership_type, is_primary, status, joined_at, left_at FROM org_membership WHERE id = ?",
                70021L
        );
        Map<String, Object> removed = jdbcTemplate.queryForMap(
                "SELECT membership_type, is_primary, status, joined_at, left_at FROM org_membership WHERE id = ?",
                70022L
        );
        Map<String, Object> inserted = jdbcTemplate.queryForMap(
                "SELECT membership_type, is_primary, status, joined_at, left_at FROM org_membership WHERE user_id = ? AND org_unit_id = ? AND status = 'ACTIVE'",
                1010L,
                2010L
        );

        assertThat(kept.get("membership_type")).isEqualTo("IMPORT");
        assertThat(kept.get("is_primary")).isIn(false, 0);
        assertThat(kept.get("status")).isEqualTo("ACTIVE");
        assertThat(kept.get("left_at")).isNull();

        assertThat(removed.get("membership_type")).isEqualTo("SYNC");
        assertThat(removed.get("status")).isEqualTo("INACTIVE");
        assertThat(removed.get("left_at")).isNotNull();

        assertThat(inserted.get("membership_type")).isEqualTo("MANUAL");
        assertThat(inserted.get("is_primary")).isIn(true, 1);
        assertThat(inserted.get("status")).isEqualTo("ACTIVE");
        assertThat(inserted.get("left_at")).isNull();
    }

    @Test
    void shouldBlockSecondTransactionWhenLockingSameUser() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(testConfigTransactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        AtomicBoolean secondLockAcquired = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                repository.lockUserForMembershipReplace(1010L);
                firstLockAcquired.countDown();
                try {
                    assertThat(releaseFirstTransaction.await(2, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));

            assertThat(firstLockAcquired.await(1, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                repository.lockUserForMembershipReplace(1010L);
                secondLockAcquired.set(true);
            }));

            Thread.sleep(200);
            assertThat(secondLockAcquired).isFalse();

            releaseFirstTransaction.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(secondLockAcquired).isTrue();
    }

    @Autowired
    private DataSourceTransactionManager testConfigTransactionManager;

    @Configuration
    @MapperScan(basePackageClasses = {OrgMembershipMapper.class, IamUserMapper.class})
    @Import({MybatisPlusConfig.class, MybatisPlusUserMembershipAdminRepository.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:user_membership_admin_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
