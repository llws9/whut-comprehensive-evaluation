package edu.whut.eval.app.preference;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.preference.model.UserPreference;
import edu.whut.eval.domain.preference.repository.UserPreferenceRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.UserPreferenceMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusUserPreferenceRepository;
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
@ContextConfiguration(classes = MybatisPlusUserPreferenceRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusUserPreferenceRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS user_preference");
        jdbcTemplate.execute(
                "CREATE TABLE user_preference (" +
                        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                        "user_id BIGINT NOT NULL UNIQUE, " +
                        "preferred_theme VARCHAR(32) NOT NULL, " +
                        "notifications_enabled BOOLEAN NOT NULL)"
        );
    }

    @Test
    void shouldSavePreferenceAndReadItBack() {
        UserPreference saved = userPreferenceRepository.save(new UserPreference(null, 1001L, "dark", true));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.userId()).isEqualTo(1001L);
        assertThat(saved.preferredTheme()).isEqualTo("dark");
        assertThat(saved.notificationsEnabled()).isTrue();
        assertThat(userPreferenceRepository.findByUserId(1001L))
                .map(UserPreference::preferredTheme)
                .contains("dark");
    }

    @Test
    void shouldReportWhetherPreferenceExistsByUserId() {
        jdbcTemplate.update(
                "INSERT INTO user_preference (user_id, preferred_theme, notifications_enabled) VALUES (?, ?, ?)",
                1001L, "light", false
        );

        assertThat(userPreferenceRepository.existsByUserId(1001L)).isTrue();
        assertThat(userPreferenceRepository.existsByUserId(2002L)).isFalse();
    }

    @Configuration
    @MapperScan(basePackageClasses = UserPreferenceMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisPlusUserPreferenceRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:user_preference_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
