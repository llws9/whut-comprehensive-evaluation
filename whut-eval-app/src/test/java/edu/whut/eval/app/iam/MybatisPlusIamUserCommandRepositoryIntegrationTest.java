package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.domain.iam.repository.IamUserCommandRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamUserMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusIamUserCommandRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusIamUserCommandRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusIamUserCommandRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IamUserCommandRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute(
                "CREATE TABLE iam_user (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "user_no VARCHAR(64) NOT NULL, " +
                        "user_name VARCHAR(64) NOT NULL, " +
                        "email VARCHAR(128), " +
                        "phone VARCHAR(32), " +
                        "password_hash VARCHAR(255), " +
                        "status VARCHAR(32) NOT NULL, " +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        );

        jdbcTemplate.update(
                "INSERT INTO iam_user (id, user_no, user_name, email, phone, password_hash, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                1010L, "2024305001", "王老师", "w@example.com", "13800000000", "oldhash", "ACTIVE",
                Timestamp.valueOf("2024-01-01 00:00:00"), Timestamp.valueOf("2024-01-01 00:00:00")
        );
    }

    @Test
    void shouldUpdateExistingUserByUserNo() {
        boolean updated = repository.updateForImportByUserNo(
                "2024305001", "新姓名", "newhash", "new@example.com", "13811112222"
        );

        assertThat(updated).isTrue();
        String name = jdbcTemplate.queryForObject("SELECT user_name FROM iam_user WHERE user_no = '2024305001'", String.class);
        String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM iam_user WHERE user_no = '2024305001'", String.class);
        assertThat(name).isEqualTo("新姓名");
        assertThat(passwordHash).isEqualTo("newhash");
    }

    @Test
    void shouldCreateUserWithCreateUserMethod() {
        IamUser created = repository.createUser("2024305002", "李老师", "hash2", "l@example.com", "13800001111");

        assertThat(created.id()).isNotNull();
        assertThat(created.userNo()).isEqualTo("2024305002");
    }

    @Configuration
    @MapperScan(basePackageClasses = {IamUserMapper.class})
    @Import({MybatisPlusConfig.class, MybatisPlusIamUserCommandRepository.class})
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:iam_user_command_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
    }
}
