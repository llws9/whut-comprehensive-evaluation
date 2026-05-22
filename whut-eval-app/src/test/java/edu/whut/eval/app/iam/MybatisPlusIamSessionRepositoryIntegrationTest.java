package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.iam.model.IamSession;
import edu.whut.eval.domain.iam.model.IamSessionStatus;
import edu.whut.eval.domain.iam.repository.IamSessionRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamSessionMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamSessionRepository;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusIamSessionRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusIamSessionRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IamSessionRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_session");
        jdbcTemplate.execute(
                "CREATE TABLE iam_session (" +
                        "id BIGINT PRIMARY KEY, " +
                        "user_id BIGINT NOT NULL, " +
                        "token_id VARCHAR(128) NOT NULL UNIQUE, " +
                        "login_ip VARCHAR(64) NOT NULL, " +
                        "user_agent VARCHAR(255) NOT NULL, " +
                        "expired_at TIMESTAMP NOT NULL, " +
                        "revoked_at TIMESTAMP NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL)"
        );
    }

    @Test
    void shouldSaveAndFindBySessionId() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 12, 0, 0);
        IamSession saved = repository.save(new IamSession(
                null,
                1001L,
                "sid-1001",
                "127.0.0.1",
                "JUnit",
                now.plusHours(8),
                null,
                IamSessionStatus.ACTIVE,
                now
        ));

        assertThat(saved.id()).isNotNull();
        assertThat(repository.findBySessionId("sid-1001")).hasValueSatisfying(found -> {
            assertThat(found.userId()).isEqualTo(1001L);
            assertThat(found.sessionId()).isEqualTo("sid-1001");
            assertThat(found.loginIp()).isEqualTo("127.0.0.1");
            assertThat(found.userAgent()).isEqualTo("JUnit");
            assertThat(found.expiredAt()).isEqualTo(now.plusHours(8));
            assertThat(found.status()).isEqualTo(IamSessionStatus.ACTIVE);
        });
    }

    @Test
    void shouldRevokeExistingSessionBySessionId() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 12, 0, 0);
        jdbcTemplate.update(
                "INSERT INTO iam_session (id, user_id, token_id, login_ip, user_agent, expired_at, revoked_at, status, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                2001L,
                1001L,
                "sid-revoke",
                "127.0.0.1",
                "JUnit",
                Timestamp.valueOf(now.plusHours(8)),
                null,
                "ACTIVE",
                Timestamp.valueOf(now.minusMinutes(3))
        );

        repository.revoke("sid-revoke", now);

        assertThat(repository.findBySessionId("sid-revoke")).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(IamSessionStatus.REVOKED);
            assertThat(found.revokedAt()).isEqualTo(now);
        });
    }

    @Test
    void shouldOnlyRevokeCurrentSession() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 12, 0, 0);
        jdbcTemplate.update(
                "INSERT INTO iam_session (id, user_id, token_id, login_ip, user_agent, expired_at, revoked_at, status, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                3001L,
                1001L,
                "sid-current",
                "127.0.0.1",
                "JUnit",
                Timestamp.valueOf(now.plusHours(8)),
                null,
                "ACTIVE",
                Timestamp.valueOf(now.minusMinutes(5))
        );
        jdbcTemplate.update(
                "INSERT INTO iam_session (id, user_id, token_id, login_ip, user_agent, expired_at, revoked_at, status, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                3002L,
                1001L,
                "sid-other",
                "127.0.0.1",
                "JUnit",
                Timestamp.valueOf(now.plusHours(8)),
                null,
                "ACTIVE",
                Timestamp.valueOf(now.minusMinutes(5))
        );

        repository.revoke("sid-current", now);

        assertThat(repository.findBySessionId("sid-current")).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(IamSessionStatus.REVOKED);
            assertThat(found.revokedAt()).isEqualTo(now);
        });
        assertThat(repository.findBySessionId("sid-other")).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(IamSessionStatus.ACTIVE);
            assertThat(found.revokedAt()).isNull();
        });
    }

    @Test
    void shouldExtendExpirationBySessionId() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 12, 0, 0);
        jdbcTemplate.update(
                "INSERT INTO iam_session (id, user_id, token_id, login_ip, user_agent, expired_at, revoked_at, status, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                2002L,
                1002L,
                "sid-extend",
                "127.0.0.1",
                "JUnit",
                Timestamp.valueOf(now.plusHours(1)),
                null,
                "ACTIVE",
                Timestamp.valueOf(now.minusMinutes(3))
        );

        repository.extendExpiration("sid-extend", now.plusHours(12));

        assertThat(repository.findBySessionId("sid-extend")).hasValueSatisfying(found ->
                assertThat(found.expiredAt()).isEqualTo(now.plusHours(12)));
    }

    @Configuration
    @MapperScan(basePackageClasses = IamSessionMapper.class)
    @Import({MybatisPlusConfig.class, MybatisPlusIamSessionRepository.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:iam_session_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
