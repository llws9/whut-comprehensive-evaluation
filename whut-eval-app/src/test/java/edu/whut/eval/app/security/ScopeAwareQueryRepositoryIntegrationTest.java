package edu.whut.eval.app.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.application.auth.model.ScoreResourceContext;
import edu.whut.eval.application.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.DefaultScoreScopePredicateBuilder;
import edu.whut.eval.application.auth.service.DefaultScopePredicateBuilder;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.ScoreScopePredicateBuilder;
import edu.whut.eval.application.auth.service.ScopePredicateBuilder;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ExampleApplicationScopeQueryMapper;
import edu.whut.eval.infra.persistence.mapper.ExampleScoreScopeQueryMapper;
import edu.whut.eval.infra.persistence.repository.ExampleApplicationScopeQueryRepository;
import edu.whut.eval.infra.persistence.repository.ExampleScoreScopeQueryRepository;
import edu.whut.eval.infra.security.sql.ScoreScopeSqlTranslator;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ScopeAwareQueryRepositoryIntegrationTest.TestConfig.class)
class ScopeAwareQueryRepositoryIntegrationTest {

    private static final String APPLICATION_PERMISSION = "application.review";
    private static final String SCORE_PERMISSION = "score.view.assigned";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ExampleApplicationScopeQueryRepository applicationRepository;

    @Autowired
    private ExampleScoreScopeQueryRepository scoreRepository;

    @Autowired
    private ResourceScopeAccessEvaluator resourceScopeAccessEvaluator;

    @BeforeEach
    void setUpSchemaAndData() {
        recreateTables();
        insertApplicationRows();
        insertScoreRows();
    }

    @Test
    void shouldQueryAccessibleApplicationsAndMatchResourceEvaluator() {
        UserAuthorizationContext context = createApplicationContext();

        List<ApplicationResourceContext> visibleResources = applicationRepository.listAccessibleApplications(
                context,
                APPLICATION_PERMISSION
        );

        Set<Long> visibleIds = visibleResources.stream()
                .map(ApplicationResourceContext::getApplicationId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> evaluatedIds = allApplicationResources().stream()
                .filter(resource -> resourceScopeAccessEvaluator.canAccessApplication(context, APPLICATION_PERMISSION, resource).isAllowed())
                .map(ApplicationResourceContext::getApplicationId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(visibleIds).containsExactly(9001L, 9003L);
        assertThat(visibleIds).containsExactlyElementsOf(evaluatedIds);
    }

    @Test
    void shouldQueryAccessibleScoresAndMatchResourceEvaluator() {
        UserAuthorizationContext context = createScoreContext();

        List<ScoreResourceContext> visibleResources = scoreRepository.listAccessibleScores(
                context,
                SCORE_PERMISSION
        );

        Set<Long> visibleIds = visibleResources.stream()
                .map(ScoreResourceContext::getScoreId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> evaluatedIds = allScoreResources().stream()
                .filter(resource -> resourceScopeAccessEvaluator.canAccessScore(context, SCORE_PERMISSION, resource).isAllowed())
                .map(ScoreResourceContext::getScoreId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(visibleIds).containsExactly(8001L, 8002L, 8003L);
        assertThat(visibleIds).containsExactlyElementsOf(evaluatedIds);
    }

    /**
     * 每次测试都重建示例表，避免跨测试共享脏数据。
     */
    private void recreateTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS example_application_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS example_score_record");
        jdbcTemplate.execute(
                "CREATE TABLE example_application_record (" +
                        "application_id BIGINT PRIMARY KEY, " +
                        "applicant_user_id BIGINT NOT NULL, " +
                        "org_unit_id BIGINT NOT NULL, " +
                        "org_path VARCHAR(255) NOT NULL, " +
                        "category_code VARCHAR(64), " +
                        "item_code VARCHAR(64))"
        );
        jdbcTemplate.execute(
                "CREATE TABLE example_score_record (" +
                        "score_id BIGINT PRIMARY KEY, " +
                        "student_user_id BIGINT NOT NULL, " +
                        "org_unit_id BIGINT NOT NULL, " +
                        "org_path VARCHAR(255) NOT NULL, " +
                        "category_code VARCHAR(64), " +
                        "item_code VARCHAR(64), " +
                        "academic_year VARCHAR(32))"
        );
    }

    /**
     * 申请示例数据覆盖 SELF、ORG_UNIT_ITEM 与 CUSTOM_EXPRESSION 三种命中路径。
     */
    private void insertApplicationRows() {
        jdbcTemplate.update(
                "INSERT INTO example_application_record (application_id, applicant_user_id, org_unit_id, org_path, category_code, item_code) VALUES (?, ?, ?, ?, ?, ?)",
                9001L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE"
        );
        jdbcTemplate.update(
                "INSERT INTO example_application_record (application_id, applicant_user_id, org_unit_id, org_path, category_code, item_code) VALUES (?, ?, ?, ?, ?, ?)",
                9002L, 2002L, 3001L, "/1/3001/", "PRACTICE", "VOLUNTEER_SERVICE"
        );
        jdbcTemplate.update(
                "INSERT INTO example_application_record (application_id, applicant_user_id, org_unit_id, org_path, category_code, item_code) VALUES (?, ?, ?, ?, ?, ?)",
                9003L, 1001L, 4001L, "/1/4001/", "INTELLECTUAL", "INNOVATION_PROJECT"
        );
        jdbcTemplate.update(
                "INSERT INTO example_application_record (application_id, applicant_user_id, org_unit_id, org_path, category_code, item_code) VALUES (?, ?, ?, ?, ?, ?)",
                9004L, 3003L, 5001L, "/1/5001/", "MORAL", "OTHER"
        );
    }

    /**
     * 成绩示例数据覆盖 SELF、ORG_SUBTREE 与 CUSTOM_EXPRESSION 三种命中路径。
     */
    private void insertScoreRows() {
        jdbcTemplate.update(
                "INSERT INTO example_score_record (score_id, student_user_id, org_unit_id, org_path, category_code, item_code, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                8001L, 1001L, 3002L, "/1/3001/3002/", "ACADEMIC", "LECTURE", "2024-2025"
        );
        jdbcTemplate.update(
                "INSERT INTO example_score_record (score_id, student_user_id, org_unit_id, org_path, category_code, item_code, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                8002L, 2002L, 3003L, "/1/3001/3003/", "ACADEMIC", "COMPETITION", "2025-2026"
        );
        jdbcTemplate.update(
                "INSERT INTO example_score_record (score_id, student_user_id, org_unit_id, org_path, category_code, item_code, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                8003L, 1001L, 4001L, "/1/4001/", "MORAL", "CLASS_ACTIVITY", "2025-2026"
        );
        jdbcTemplate.update(
                "INSERT INTO example_score_record (score_id, student_user_id, org_unit_id, org_path, category_code, item_code, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                8004L, 3003L, 5001L, "/1/5001/", "MORAL", "OTHER", "2025-2026"
        );
    }

    private List<ApplicationResourceContext> allApplicationResources() {
        return List.of(
                new ApplicationResourceContext(9001L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE"),
                new ApplicationResourceContext(9002L, 2002L, 3001L, "/1/3001/", "PRACTICE", "VOLUNTEER_SERVICE"),
                new ApplicationResourceContext(9003L, 1001L, 4001L, "/1/4001/", "INTELLECTUAL", "INNOVATION_PROJECT"),
                new ApplicationResourceContext(9004L, 3003L, 5001L, "/1/5001/", "MORAL", "OTHER")
        );
    }

    private List<ScoreResourceContext> allScoreResources() {
        return List.of(
                new ScoreResourceContext(8001L, 1001L, 3002L, "/1/3001/3002/", "ACADEMIC", "LECTURE", "2024-2025"),
                new ScoreResourceContext(8002L, 2002L, 3003L, "/1/3001/3003/", "ACADEMIC", "COMPETITION", "2025-2026"),
                new ScoreResourceContext(8003L, 1001L, 4001L, "/1/4001/", "MORAL", "CLASS_ACTIVITY", "2025-2026"),
                new ScoreResourceContext(8004L, 3003L, 5001L, "/1/5001/", "MORAL", "OTHER", "2025-2026")
        );
    }

    private UserAuthorizationContext createApplicationContext() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(APPLICATION_PERMISSION),
                List.of(
                        new IamScopeRule(1L, APPLICATION_PERMISSION, "SELF", null, null, null, null, 10, "ACTIVE"),
                        new IamScopeRule(2L, APPLICATION_PERMISSION, "ORG_UNIT_ITEM", 3001L, "INTELLECTUAL", "ACADEMIC_LECTURE", null, 20, "ACTIVE"),
                        new IamScopeRule(
                                3L,
                                APPLICATION_PERMISSION,
                                "CUSTOM_EXPRESSION",
                                4001L,
                                null,
                                null,
                                "{\"allOf\":[{\"field\":\"ownerUserId\",\"operator\":\"EQ\",\"valueFrom\":\"currentUser.userId\"}]}",
                                30,
                                "ACTIVE"
                        )
                )
        );
    }

    private UserAuthorizationContext createScoreContext() {
        return new UserAuthorizationContext(
                1001L,
                "2024305999",
                "Test User",
                "student",
                Set.of("student"),
                Set.of(SCORE_PERMISSION),
                List.of(
                        new IamScopeRule(11L, SCORE_PERMISSION, "SELF", null, null, null, null, 10, "ACTIVE"),
                        new IamScopeRule(12L, SCORE_PERMISSION, "ORG_SUBTREE", 3001L, "ACADEMIC", null, null, 20, "ACTIVE"),
                        new IamScopeRule(
                                13L,
                                SCORE_PERMISSION,
                                "CUSTOM_EXPRESSION",
                                null,
                                null,
                                null,
                                "{\"allOf\":[{\"field\":\"academicYear\",\"operator\":\"EQ\",\"value\":\"2025-2026\"},{\"field\":\"studentUserId\",\"operator\":\"EQ\",\"valueFrom\":\"currentUser.userId\"}]}",
                                30,
                                "ACTIVE"
                        )
                )
        );
    }

    @Configuration
    @MapperScan(basePackageClasses = ExampleApplicationScopeQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            DefaultScopePredicateBuilder.class,
            DefaultScoreScopePredicateBuilder.class,
            DefaultResourceScopeAccessEvaluator.class,
            JsonScopeRuleExpressionInterpreter.class,
            edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator.class,
            ScoreScopeSqlTranslator.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:scope_query_repository_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        ExampleApplicationScopeQueryRepository exampleApplicationScopeQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                                                                      ScopePredicateBuilder scopePredicateBuilder,
                                                                                      edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator applicationScopeSqlTranslator,
                                                                                      ExampleApplicationScopeQueryMapper exampleApplicationScopeQueryMapper) {
            return new ExampleApplicationScopeQueryRepository(
                    authorizationScopeEvaluator,
                    scopePredicateBuilder,
                    applicationScopeSqlTranslator,
                    exampleApplicationScopeQueryMapper
            );
        }

        @Bean
        ExampleScoreScopeQueryRepository exampleScoreScopeQueryRepository(AuthorizationScopeEvaluator authorizationScopeEvaluator,
                                                                          ScoreScopePredicateBuilder scoreScopePredicateBuilder,
                                                                          ScoreScopeSqlTranslator scoreScopeSqlTranslator,
                                                                          ExampleScoreScopeQueryMapper exampleScoreScopeQueryMapper) {
            return new ExampleScoreScopeQueryRepository(
                    authorizationScopeEvaluator,
                    scoreScopePredicateBuilder,
                    scoreScopeSqlTranslator,
                    exampleScoreScopeQueryMapper
            );
        }
    }
}
