package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.UserAdminCommandRepository;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.infra.cache.UserCacheGateway;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserAdminCommandRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusUserAdminCommandRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusUserAdminCommandRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAdminCommandRepository repository;

    @Autowired
    private TestUserCacheGateway userCacheGateway;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute(
                "CREATE TABLE iam_user (" +
                        "id BIGINT PRIMARY KEY, " +
                        "user_no VARCHAR(64) NOT NULL UNIQUE, " +
                        "user_name VARCHAR(64) NOT NULL, " +
                        "email VARCHAR(128) NULL, " +
                        "phone VARCHAR(32) NULL, " +
                        "password_hash VARCHAR(255) NULL, " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL, " +
                        "updated_at TIMESTAMP NOT NULL)"
        );
        userCacheGateway.clear();
        jdbcTemplate.update(
                "INSERT INTO iam_user (id, user_no, user_name, email, phone, password_hash, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                1010L,
                "2024305001",
                "王老师",
                "wang@example.com",
                "13800000000",
                "hash",
                "ACTIVE",
                Timestamp.valueOf("2024-01-01 00:00:00"),
                Timestamp.valueOf("2024-01-01 00:00:00")
        );
    }

    @Test
    void shouldCreateUserAndWarmCache() {
        IamUser created = repository.create(
                "2024305002",
                "李老师",
                "li@example.com",
                "13900000000",
                "hashed-password",
                "ACTIVE"
        );

        assertThat(created.id()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_name FROM iam_user WHERE id = ?",
                String.class,
                created.id()
        )).isEqualTo("李老师");
        assertThat(userCacheGateway.getByUserNo("2024305002")).isPresent();
    }

    @Test
    void shouldTranslateDuplicateUserNoToConflictException() {
        assertThatThrownBy(() -> repository.create(
                "2024305001",
                "重复王老师",
                "dup@example.com",
                "13900000001",
                "hashed-password",
                "ACTIVE"
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("userNo 已存在");
    }

    @Test
    void shouldUpdateStatusByExpectedCurrentStatus() {
        boolean updated = repository.updateStatus(1010L, "ACTIVE", "LOCKED");

        assertThat(updated).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM iam_user WHERE id = ?",
                String.class,
                1010L
        )).isEqualTo("LOCKED");
        assertThat(userCacheGateway.getByUserNo("2024305001")).hasValueSatisfying(user ->
                assertThat(user.status()).isEqualTo("LOCKED"));
    }

    @Test
    void shouldReturnFalseWhenStatusAlreadyChanged() {
        boolean updated = repository.updateStatus(1010L, "DISABLED", "LOCKED");

        assertThat(updated).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM iam_user WHERE id = ?",
                String.class,
                1010L
        )).isEqualTo("ACTIVE");
    }

    @Configuration
    @MapperScan(basePackageClasses = IamUserMapper.class)
    @Import({MybatisPlusConfig.class, MybatisPlusUserAdminCommandRepository.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:user_admin_command_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        TestUserCacheGateway userCacheGateway() {
            return new TestUserCacheGateway();
        }
    }

    static class TestUserCacheGateway implements UserCacheGateway {
        private java.util.Map<String, IamUser> cache = new java.util.HashMap<>();

        @Override
        public Optional<IamUser> getByUserNo(String userNo) {
            return Optional.ofNullable(cache.get(userNo));
        }

        @Override
        public void put(IamUser user) {
            cache.put(user.userNo(), user);
        }

        @Override
        public void evictByUserNo(String userNo) {
            cache.remove(userNo);
        }

        void clear() {
            cache = new java.util.HashMap<>();
        }
    }
}
