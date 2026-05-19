package edu.whut.eval.app.iam;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.domain.iam.repository.ScopeRuleAdminRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.IamScopeRuleMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusScopeRuleAdminRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusScopeRuleAdminRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusScopeRuleAdminRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScopeRuleAdminRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_scope_rule");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("CREATE TABLE iam_scope_rule (" +
                "id BIGINT PRIMARY KEY, " +
                "assignment_id BIGINT NOT NULL, " +
                "permission_code VARCHAR(128) NOT NULL, " +
                "scope_type VARCHAR(32) NOT NULL, " +
                "org_unit_id BIGINT NULL, " +
                "category_code VARCHAR(64) NULL, " +
                "item_code VARCHAR(64) NULL, " +
                "expression_json VARCHAR(2000) NULL, " +
                "priority INT NOT NULL, " +
                "status VARCHAR(32) NOT NULL, " +
                "created_at TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE org_unit (" +
                "id BIGINT PRIMARY KEY, " +
                "parent_id BIGINT NULL, " +
                "unit_type VARCHAR(32) NOT NULL, " +
                "unit_code VARCHAR(64) NOT NULL, " +
                "unit_name VARCHAR(128) NOT NULL, " +
                "path VARCHAR(255) NOT NULL, " +
                "status VARCHAR(32) NOT NULL)");
        jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2002L, 1L, "COLLEGE", "CS", "计算机与人工智能学院", "/1/2002/", "ACTIVE");
    }

    @Test
    void shouldPersistExpressionJsonAndLoadItBack() {
        repository.create(
                70021L,
                "manage.review.view",
                "CUSTOM_EXPRESSION",
                2002L,
                "计算机与人工智能学院",
                null,
                null,
                Map.of("studentId", "2024305001"),
                80,
                "ACTIVE"
        );

        String expressionJson = jdbcTemplate.queryForObject(
                "SELECT expression_json FROM iam_scope_rule WHERE assignment_id = ?",
                String.class,
                70021L
        );

        assertThat(expressionJson).contains("studentId");
        assertThat(repository.findByAssignmentId(70021L)).hasSize(1);
        assertThat(repository.findByAssignmentId(70021L).getFirst().expressionJson()).containsEntry("studentId", "2024305001");
    }

    @Test
    void shouldDetectSemanticDuplicateOnlyForActiveRule() {
        jdbcTemplate.update(
                "INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                81001L, 70021L, "manage.review.view", "CATEGORY", null, "MORAL", null, null, 90, "ACTIVE"
        );
        assertThat(repository.existsSemanticDuplicate(70021L, "manage.review.view", "CATEGORY", null, "MORAL", null, null)).isTrue();

        jdbcTemplate.update("UPDATE iam_scope_rule SET status = 'INACTIVE' WHERE id = 81001");
        assertThat(repository.existsSemanticDuplicate(70021L, "manage.review.view", "CATEGORY", null, "MORAL", null, null)).isFalse();
    }

    @Configuration
    @MapperScan(basePackageClasses = IamScopeRuleMapper.class)
    @Import(MybatisPlusConfig.class)
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:scope_rule_admin_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MybatisPlusScopeRuleAdminRepository mybatisPlusScopeRuleAdminRepository(IamScopeRuleMapper iamScopeRuleMapper,
                                                                                ObjectMapper objectMapper) {
            return new MybatisPlusScopeRuleAdminRepository(iamScopeRuleMapper, objectMapper);
        }
    }
}
