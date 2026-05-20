package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.org.model.OrgMembership;
import edu.whut.eval.domain.org.repository.UserMembershipAdminRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
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

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

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
                "INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status, joined_at, left_at, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                70022L, 1010L, 2009L, "SYNC", 0, "ACTIVE",
                java.sql.Timestamp.valueOf("2024-02-01 00:00:00"), null, java.sql.Timestamp.valueOf("2024-02-01 00:00:00")
        );
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

    @Configuration
    @MapperScan(basePackageClasses = OrgMembershipMapper.class)
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
