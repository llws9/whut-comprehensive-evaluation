package edu.whut.eval.app.infra;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.domain.org.model.OrgUnit;
import edu.whut.eval.domain.org.repository.OrgQueryRepository;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.OrgMembershipMapper;
import edu.whut.eval.infra.persistence.mapper.OrgUnitMapper;
import edu.whut.eval.infra.persistence.repository.MybatisPlusOrgQueryRepository;
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
@ContextConfiguration(classes = MybatisPlusOrgQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusOrgQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrgQueryRepository repository;

    @BeforeEach
    void setUpSchemaAndData() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("CREATE TABLE org_unit (" +
                "id BIGINT PRIMARY KEY, " +
                "parent_id BIGINT NULL, " +
                "unit_type VARCHAR(32) NOT NULL, " +
                "unit_code VARCHAR(64) NOT NULL, " +
                "unit_name VARCHAR(128) NOT NULL, " +
                "path VARCHAR(255) NOT NULL, " +
                "status VARCHAR(32) NOT NULL)");
        jdbcTemplate.execute("CREATE TABLE org_membership (" +
                "id BIGINT PRIMARY KEY, " +
                "user_id BIGINT NOT NULL, " +
                "org_unit_id BIGINT NOT NULL, " +
                "membership_type VARCHAR(32) NOT NULL, " +
                "is_primary TINYINT(1) NOT NULL DEFAULT 0, " +
                "status VARCHAR(32) NOT NULL, " +
                "joined_at TIMESTAMP NOT NULL, " +
                "left_at TIMESTAMP NULL, " +
                "created_at TIMESTAMP NOT NULL)");
    }

    @Test
    void shouldDropRequestedDisabledRootWholeBranchWhenIncludeDisabledFalse() {
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2001L, null, "SCHOOL", "WHUT", "武汉理工大学", "/WHUT", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2002L, 2001L, "COLLEGE", "CS", "计算机与人工智能学院", "/WHUT/CS", "DISABLED"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2005L, 2002L, "GRADE", "CS2022", "计算机 2022 级", "/WHUT/CS/CS2022", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2010L, 2005L, "CLASS", "CS2201", "计算机 2201 班", "/WHUT/CS/CS2022/CS2201", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2006L, 2002L, "GRADE", "CS2023", "计算机 2023 级", "/WHUT/CS/CS2023", "DISABLED"
        );

        assertThat(repository.findDescendants(2002L, false)).isEmpty();
    }

    @Test
    void shouldPruneDisabledNodeAndAllDescendantsWhenIncludeDisabledFalse() {
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2001L, null, "SCHOOL", "WHUT", "武汉理工大学", "/WHUT", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2002L, 2001L, "COLLEGE", "CS", "计算机与人工智能学院", "/WHUT/CS", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2005L, 2002L, "GRADE", "CS2022", "计算机 2022 级", "/WHUT/CS/CS2022", "DISABLED"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2010L, 2005L, "CLASS", "CS2201", "计算机 2201 班", "/WHUT/CS/CS2022/CS2201", "ACTIVE"
        );

        assertThat(repository.findDescendants(2002L, false))
                .extracting(unit -> unit.id() + ":" + unit.status())
                .containsExactly("2002:ACTIVE");
    }

    @Test
    void shouldNotLeakSiblingBranchWhenRootPathIsSharedPrefix() {
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2001L, null, "SCHOOL", "WHUT", "武汉理工大学", "/WHUT", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2002L, 2001L, "COLLEGE", "CS", "计算机与人工智能学院", "/WHUT/CS", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2005L, 2002L, "GRADE", "CS2022", "计算机 2022 级", "/WHUT/CS/CS2022", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2102L, 2001L, "COLLEGE", "CS2", "计算数据学院", "/WHUT/CS2", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2105L, 2102L, "GRADE", "CS22022", "计算数据 2022 级", "/WHUT/CS2022", "ACTIVE"
        );

        assertThat(repository.findDescendants(2002L, false))
                .extracting(unit -> unit.id())
                .containsExactly(2002L, 2005L);
    }

    @Test
    void shouldFindDescendantsWhenRootPathEndsWithTrailingSlash() {
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2001L, null, "SCHOOL", "WHUT", "武汉理工大学", "/WHUT", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2002L, 2001L, "COLLEGE", "CS", "计算机与人工智能学院", "/WHUT/CS/", "ACTIVE"
        );
        jdbcTemplate.update(
                "INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (?,?,?,?,?,?,?)",
                2005L, 2002L, "GRADE", "CS2022", "计算机 2022 级", "/WHUT/CS/CS2022", "ACTIVE"
        );

        assertThat(repository.findDescendants(2002L, false))
                .extracting(OrgUnit::id)
                .containsExactly(2002L, 2005L);
    }

    @Configuration
    @MapperScan(basePackageClasses = {OrgUnitMapper.class, OrgMembershipMapper.class})
    @Import(MybatisPlusConfig.class)
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:org_query_repo_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        MybatisPlusOrgQueryRepository mybatisPlusOrgQueryRepository(OrgUnitMapper orgUnitMapper,
                                                                    OrgMembershipMapper orgMembershipMapper) {
            return new MybatisPlusOrgQueryRepository(orgUnitMapper, orgMembershipMapper);
        }
    }
}
