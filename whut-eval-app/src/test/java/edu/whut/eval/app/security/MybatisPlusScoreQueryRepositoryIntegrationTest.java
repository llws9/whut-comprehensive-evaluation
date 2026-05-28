package edu.whut.eval.app.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.model.ScoreResourceContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.DefaultScoreScopePredicateBuilder;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.score.model.ScoreRecord;
import edu.whut.eval.domain.score.query.ScoreAccessContext;
import edu.whut.eval.domain.score.query.ScorePageQuery;
import edu.whut.eval.domain.score.repository.ScoreQueryRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ScoreQueryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusScoreQueryRepository;
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
@ContextConfiguration(classes = MybatisPlusScoreQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusScoreQueryRepositoryIntegrationTest {

    private static final String SCORE_PERMISSION = "score.view.assigned";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScoreQueryRepository scoreQueryRepository;

    @Autowired
    private ResourceScopeAccessEvaluator resourceScopeAccessEvaluator;

    @BeforeEach
    void setUpSchemaAndData() {
        recreateTables();
        insertScoreRows();
    }

    @Test
    void shouldPageAccessibleScoresAndMatchResourceEvaluator() {
        ScoreAccessContext accessContext = createAccessContext();

        PageResult<ScoreRecord> result = scoreQueryRepository.pageAccessibleScores(
                accessContext,
                new ScorePageQuery(1, 10, null, null, null, null, null, null)
        );

        Set<Long> visibleIds = result.records().stream()
                .map(ScoreRecord::scoreId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> evaluatedIds = allScoreResources().stream()
                .filter(resource -> resourceScopeAccessEvaluator.canAccessScore(
                        createAuthorizationContext(),
                        SCORE_PERMISSION,
                        resource
                ).isAllowed())
                .map(ScoreResourceContext::getScoreId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(result.total()).isEqualTo(3);
        assertThat(visibleIds).containsExactly(8001L, 8002L, 8003L);
        assertThat(visibleIds).containsExactlyElementsOf(evaluatedIds);
    }

    @Test
    void shouldApplyBusinessFiltersAfterScope() {
        ScoreAccessContext accessContext = createAccessContext();

        PageResult<ScoreRecord> result = scoreQueryRepository.pageAccessibleScores(
                accessContext,
                new ScorePageQuery(1, 10, null, null, null, null, null, "2025-2026")
        );

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.records()).extracting(ScoreRecord::scoreId).containsExactly(8002L, 8003L);
    }

    @Test
    void shouldKeepTotalWhenPagingAccessibleScores() {
        ScoreAccessContext accessContext = createAccessContext();

        PageResult<ScoreRecord> result = scoreQueryRepository.pageAccessibleScores(
                accessContext,
                new ScorePageQuery(1, 1, null, null, null, null, null, null)
        );

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.records()).extracting(ScoreRecord::scoreId).containsExactly(8001L);
    }

    /**
     * 每次测试都重建正式成绩表，确保分页和总数断言不受上一个测试污染。
     */
    private void recreateTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS score_record");
        jdbcTemplate.execute(
                "CREATE TABLE score_record (" +
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
     * 这里故意保留“范围命中”和“不命中”的混合数据，用于验证 SQL translator 真正收窄后的列表结果。
     */
    private void insertScoreRows() {
        jdbcTemplate.update(
                "INSERT INTO score_record (score_id, student_user_id, org_unit_id, org_path, category_code, item_code, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                8001L, 1001L, 3002L, "/1/3001/3002/", "ACADEMIC", "LECTURE", "2024-2025"
        );
        jdbcTemplate.update(
                "INSERT INTO score_record (score_id, student_user_id, org_unit_id, org_path, category_code, item_code, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                8002L, 2002L, 3003L, "/1/3001/3003/", "ACADEMIC", "COMPETITION", "2025-2026"
        );
        jdbcTemplate.update(
                "INSERT INTO score_record (score_id, student_user_id, org_unit_id, org_path, category_code, item_code, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                8003L, 1001L, 4001L, "/1/4001/", "MORAL", "CLASS_ACTIVITY", "2025-2026"
        );
        jdbcTemplate.update(
                "INSERT INTO score_record (score_id, student_user_id, org_unit_id, org_path, category_code, item_code, academic_year) VALUES (?, ?, ?, ?, ?, ?, ?)",
                8004L, 3003L, 5001L, "/1/5001/", "MORAL", "OTHER", "2025-2026"
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

    private ScoreAccessContext createAccessContext() {
        return new ScoreAccessContext(
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
                ),
                SCORE_PERMISSION
        );
    }

    /**
     * 单条资源判定仍使用现有 application 层上下文，借此验证正式 Repository 与单条校验器语义一致。
     */
    private UserAuthorizationContext createAuthorizationContext() {
        ScoreAccessContext accessContext = createAccessContext();
        return new UserAuthorizationContext(
                accessContext.getUserId(),
                accessContext.getUserNo(),
                accessContext.getUserName(),
                accessContext.getIdentity(),
                accessContext.getRoles(),
                accessContext.getAuthorities(),
                accessContext.getScopeRules()
        );
    }

    @Configuration
    @MapperScan(basePackageClasses = ScoreQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            DefaultScoreScopePredicateBuilder.class,
            DefaultResourceScopeAccessEvaluator.class,
            JsonScopeRuleExpressionInterpreter.class,
            ScoreScopeSqlTranslator.class,
            MybatisPlusScoreQueryRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:formal_score_query_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
    }
}
