package edu.whut.eval.app.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.model.ApplicationResourceContext;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.application.auth.service.AuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.DefaultResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.DefaultScopePredicateBuilder;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.application.auth.service.ResourceScopeAccessEvaluator;
import edu.whut.eval.application.auth.service.ScopePredicateBuilder;
import edu.whut.eval.domain.application.model.ApplicationRecord;
import edu.whut.eval.domain.application.query.ApplicationAccessContext;
import edu.whut.eval.domain.application.query.ApplicationPageQuery;
import edu.whut.eval.domain.application.repository.ApplicationQueryRepository;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgUnitLookupRepository;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ApplicationQueryMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusApplicationQueryRepository;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusApplicationQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusApplicationQueryRepositoryIntegrationTest {

    private static final String APPLICATION_PERMISSION = "application.review";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationQueryRepository applicationQueryRepository;

    @Autowired
    private ResourceScopeAccessEvaluator resourceScopeAccessEvaluator;

    @BeforeEach
    void setUpSchemaAndData() {
        recreateTables();
        insertApplicationRows();
    }

    @Test
    void shouldPageAccessibleApplicationsAndMatchResourceEvaluator() {
        ApplicationAccessContext accessContext = createAccessContext();

        PageResult<ApplicationRecord> result = applicationQueryRepository.pageAccessibleApplications(
                accessContext,
                new ApplicationPageQuery(1, 10, null, null, null, null, null)
        );

        Set<Long> visibleIds = result.records().stream()
                .map(ApplicationRecord::applicationId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> evaluatedIds = allApplicationResources().stream()
                .filter(resource -> resourceScopeAccessEvaluator.canAccessApplication(
                        createAuthorizationContext(),
                        APPLICATION_PERMISSION,
                        resource
                ).isAllowed())
                .map(ApplicationResourceContext::getApplicationId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(result.total()).isEqualTo(2);
        assertThat(visibleIds).containsExactly(9001L, 9003L);
        assertThat(visibleIds).containsExactlyElementsOf(evaluatedIds);
    }

    @Test
    void shouldApplyBusinessFiltersAfterScope() {
        ApplicationAccessContext accessContext = createAccessContext();

        PageResult<ApplicationRecord> result = applicationQueryRepository.pageAccessibleApplications(
                accessContext,
                new ApplicationPageQuery(1, 10, null, null, 4001L, null, null)
        );

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.records()).extracting(ApplicationRecord::applicationId).containsExactly(9003L);
    }

    @Test
    void shouldKeepTotalWhenPagingAccessibleApplications() {
        ApplicationAccessContext accessContext = createAccessContext();

        PageResult<ApplicationRecord> result = applicationQueryRepository.pageAccessibleApplications(
                accessContext,
                new ApplicationPageQuery(1, 1, null, null, null, null, null)
        );

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.records()).extracting(ApplicationRecord::applicationId).containsExactly(9001L);
    }

    /**
     * 每次测试都重建正式申请表，确保分页和总数断言不受上一个测试污染。
     */
    private void recreateTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS application_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("CREATE TABLE org_unit (id BIGINT PRIMARY KEY, path VARCHAR(255) NOT NULL)");
        jdbcTemplate.execute(
                "CREATE TABLE application_record (" +
                        "application_id BIGINT PRIMARY KEY, " +
                        "applicant_user_id BIGINT NOT NULL, " +
                        "org_unit_id BIGINT NOT NULL, " +
                        "org_path VARCHAR(255) NOT NULL, " +
                        "category_code VARCHAR(64), " +
                        "item_code VARCHAR(64))"
        );
    }

    /**
     * 这里故意保留“范围命中”和“不命中”的混合数据，用于验证 SQL translator 真正收窄后的列表结果。
     */
    private void insertApplicationRows() {
        jdbcTemplate.update("INSERT INTO org_unit (id, path) VALUES (?, ?)", 3001L, "/1/3001");
        jdbcTemplate.update(
                "INSERT INTO application_record (application_id, applicant_user_id, org_unit_id, org_path, category_code, item_code) VALUES (?, ?, ?, ?, ?, ?)",
                9001L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE"
        );
        jdbcTemplate.update(
                "INSERT INTO application_record (application_id, applicant_user_id, org_unit_id, org_path, category_code, item_code) VALUES (?, ?, ?, ?, ?, ?)",
                9002L, 2002L, 3001L, "/1/3001/", "PRACTICE", "VOLUNTEER_SERVICE"
        );
        jdbcTemplate.update(
                "INSERT INTO application_record (application_id, applicant_user_id, org_unit_id, org_path, category_code, item_code) VALUES (?, ?, ?, ?, ?, ?)",
                9003L, 1001L, 4001L, "/1/4001/", "INTELLECTUAL", "INNOVATION_PROJECT"
        );
        jdbcTemplate.update(
                "INSERT INTO application_record (application_id, applicant_user_id, org_unit_id, org_path, category_code, item_code) VALUES (?, ?, ?, ?, ?, ?)",
                9004L, 3003L, 5001L, "/1/5001/", "MORAL", "OTHER"
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

    private ApplicationAccessContext createAccessContext() {
        return new ApplicationAccessContext(
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
                ),
                APPLICATION_PERMISSION
        );
    }

    /**
     * 单条资源判定仍使用现有 application 层上下文，借此验证正式 Repository 与单条校验器语义一致。
     */
    private UserAuthorizationContext createAuthorizationContext() {
        ApplicationAccessContext accessContext = createAccessContext();
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
    @MapperScan(basePackageClasses = ApplicationQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            DefaultScopePredicateBuilder.class,
            DefaultResourceScopeAccessEvaluator.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            MybatisPlusApplicationQueryRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:formal_application_query_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        OrgUnitLookupRepository orgUnitLookupRepository() {
            return new InMemoryOrgUnitLookupRepository();
        }
    }

    private static class InMemoryOrgUnitLookupRepository implements OrgUnitLookupRepository {

        private final Map<Long, OrgUnit> units = Map.of(
                3001L, new OrgUnit(3001L, 1L, "CLASS", "3001", "3001", "/1/3001", "ACTIVE"),
                4001L, new OrgUnit(4001L, 1L, "CLASS", "4001", "4001", "/1/4001", "ACTIVE")
        );

        @Override
        public Optional<OrgUnit> findById(Long id) {
            return Optional.ofNullable(units.get(id));
        }
    }
}
