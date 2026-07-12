package edu.whut.eval.app.finalrecord;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportRepository;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportedComponent;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.MentorScoreImportMapper;
import edu.whut.eval.infra.persistence.repository.MybatisMentorScoreImportRepository;
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
import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisMentorScoreImportRepositoryIntegrationTest.TestConfig.class)
class MybatisMentorScoreImportRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MentorScoreImportRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_component_score");
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("CREATE TABLE iam_user (id BIGINT PRIMARY KEY, user_no VARCHAR(64), user_name VARCHAR(128), status VARCHAR(32))");
        jdbcTemplate.execute("CREATE TABLE org_unit (id BIGINT PRIMARY KEY, unit_name VARCHAR(128), path VARCHAR(512), status VARCHAR(32))");
        jdbcTemplate.execute("""
                CREATE TABLE org_membership (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  org_unit_id BIGINT NOT NULL,
                  membership_type VARCHAR(32) NOT NULL,
                  is_primary TINYINT(1) NOT NULL DEFAULT 0,
                  status VARCHAR(32) NOT NULL)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE final_record (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  student_user_id BIGINT NOT NULL,
                  academic_year VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  moral_total DECIMAL(10,2) NOT NULL,
                  intellectual_total DECIMAL(10,2) NOT NULL,
                  physical_total DECIMAL(10,2) NOT NULL,
                  labor_total DECIMAL(10,2) NOT NULL,
                  grand_total DECIMAL(10,2) NOT NULL,
                  submitted_at DATETIME NULL,
                  confirmed_at DATETIME NULL,
                  confirm_comment VARCHAR(1000) NULL,
                  version BIGINT NOT NULL,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  UNIQUE KEY uk_final_record_student_year (student_user_id, academic_year))
                """);
        jdbcTemplate.execute("""
                CREATE TABLE final_component_score (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  final_record_id BIGINT NOT NULL,
                  category_code VARCHAR(64) NOT NULL,
                  item_code VARCHAR(64) NOT NULL,
                  score_value DECIMAL(10,2) NOT NULL,
                  display_text VARCHAR(1000) NULL,
                  source_type VARCHAR(32) NOT NULL,
                  source_ref_id VARCHAR(64) NULL,
                  created_at DATETIME NOT NULL)
                """);
        insertOrgUnit(2002L, "计算机与人工智能学院", "/WHUT/CS", "ACTIVE");
        insertOrgUnit(2010L, "计科一班", "/WHUT/CS/CS2022/CS2201", "ACTIVE");
        insertOrgUnit(3010L, "机械一班", "/WHUT/ME/ME2022/ME2201", "ACTIVE");
        insertStudent(1001L, "S1001", "张三", "ACTIVE", 2010L, true, "ACTIVE");
        insertStudent(1002L, "S1002", "李四", "DISABLED", 2010L, true, "ACTIVE");
        insertStudent(1003L, "S1003", "王五", "ACTIVE", 2010L, true, "DISABLED");
    }

    @Test
    void shouldFindActiveStudentTargetWithoutIamUserIdentityColumn() {
        Optional<MentorScoreImportStudentTarget> target = repository.findTarget("S1001", "2025-2026");

        assertThat(target).isPresent();
        assertThat(target.get().studentUserId()).isEqualTo(1001L);
        assertThat(target.get().orgUnitId()).isEqualTo(2010L);
        assertThat(target.get().orgPath()).isEqualTo("/WHUT/CS/CS2022/CS2201");
        assertThat(target.get().finalRecordStatus()).isNull();
    }

    @Test
    void shouldExcludeInactiveUserAndInactivePrimaryMembership() {
        assertThat(repository.findTarget("S1002", "2025-2026")).isEmpty();
        assertThat(repository.findTarget("S1003", "2025-2026")).isEmpty();
    }

    @Test
    void shouldFindActiveOrgPathForScopeRoot() {
        assertThat(repository.findActiveOrgPath(2002L)).contains("/WHUT/CS");
        assertThat(repository.findActiveOrgPath(9999L)).isEmpty();
    }

    @Test
    void shouldInsertDraftRecordAndImportedComponent() {
        repository.upsertDraftComponent(component("MORAL", "MORAL_HONOR", "1.25", "mentor-001"), "D7-batch");

        Long recordId = jdbcTemplate.queryForObject("SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'", Long.class);
        assertThat(recordId).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM final_record WHERE id = ?", String.class, recordId)).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject("SELECT moral_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("1.25");
        assertThat(jdbcTemplate.queryForObject("SELECT grand_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("1.25");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT source_type FROM final_component_score WHERE final_record_id = ?", String.class, recordId))
                .isEqualTo("IMPORT");
    }

    @Test
    void shouldUpdateOnlyImportedComponentAndPreserveApplicationComponent() {
        Long recordId = insertDraftRecord(1001L, "2025-2026");
        insertComponent(recordId, "MORAL", "MORAL_HONOR", "3.00", "APPLICATION", "app-1");
        insertComponent(recordId, "MORAL", "MORAL_HONOR", "1.00", "IMPORT", "old-import");

        repository.upsertDraftComponent(component("MORAL", "MORAL_HONOR", "2.00", "new-import"), "D7-batch");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score WHERE final_record_id = ? AND source_type = 'APPLICATION'", Long.class, recordId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT score_value FROM final_component_score WHERE final_record_id = ? AND source_type = 'IMPORT'", BigDecimal.class, recordId))
                .isEqualByComparingTo("2.00");
        assertThat(jdbcTemplate.queryForObject("SELECT moral_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("5.00");
    }

    private void insertOrgUnit(Long id, String unitName, String path, String status) {
        jdbcTemplate.update("INSERT INTO org_unit (id, unit_name, path, status) VALUES (?, ?, ?, ?)", id, unitName, path, status);
    }

    private void insertStudent(Long id, String userNo, String userName, String userStatus,
                               Long orgUnitId, boolean primary, String membershipStatus) {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (?, ?, ?, ?)", id, userNo, userName, userStatus);
        jdbcTemplate.update("""
                INSERT INTO org_membership (user_id, org_unit_id, membership_type, is_primary, status)
                VALUES (?, ?, 'STUDENT', ?, ?)
                """, id, orgUnitId, primary ? 1 : 0, membershipStatus);
    }

    private Long insertDraftRecord(Long studentUserId, String academicYear) {
        jdbcTemplate.update("""
                INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total,
                                          grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                VALUES (?, ?, 'DRAFT', 0, 0, 0, 0, 0, NULL, NULL, NULL, 0, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """, studentUserId, academicYear);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM final_record", Long.class);
    }

    private void insertComponent(Long finalRecordId, String categoryCode, String itemCode,
                                 String scoreValue, String sourceType, String sourceRefId) {
        jdbcTemplate.update("""
                INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at)
                VALUES (?, ?, ?, ?, 'component', ?, ?, CURRENT_TIMESTAMP())
                """, finalRecordId, categoryCode, itemCode, new BigDecimal(scoreValue), sourceType, sourceRefId);
    }

    private static MentorScoreImportedComponent component(String categoryCode, String itemCode, String scoreValue, String sourceRefId) {
        return new MentorScoreImportedComponent(2L, 1001L, "2025-2026", categoryCode, itemCode,
                new BigDecimal(scoreValue), "导师评分", sourceRefId);
    }

    @Configuration
    @MapperScan(basePackageClasses = MentorScoreImportMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisMentorScoreImportRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:mentor_score_import_repository_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
