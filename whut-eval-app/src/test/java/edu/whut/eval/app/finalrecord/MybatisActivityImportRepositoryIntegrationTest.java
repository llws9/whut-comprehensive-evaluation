package edu.whut.eval.app.finalrecord;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.application.finalrecord.importing.ActivityImportItemDefinition;
import edu.whut.eval.application.finalrecord.importing.ActivityImportRepository;
import edu.whut.eval.application.finalrecord.importing.ActivityImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.ActivityImportedComponent;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.ActivityImportMapper;
import edu.whut.eval.infra.persistence.repository.MybatisActivityImportRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisActivityImportRepositoryIntegrationTest.TestConfig.class)
class MybatisActivityImportRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ActivityImportRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_component_score");
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS evaluation_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("CREATE TABLE iam_user (id BIGINT PRIMARY KEY, user_no VARCHAR(64), user_name VARCHAR(128), status VARCHAR(32))");
        jdbcTemplate.execute("CREATE TABLE org_unit (id BIGINT PRIMARY KEY, unit_name VARCHAR(128), path VARCHAR(512), status VARCHAR(32))");
        jdbcTemplate.execute("""
                CREATE TABLE org_membership (
                  id BIGINT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  org_unit_id BIGINT NOT NULL,
                  membership_type VARCHAR(32) NOT NULL,
                  is_primary TINYINT(1) NOT NULL DEFAULT 0,
                  status VARCHAR(32) NOT NULL)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE evaluation_item (
                  id BIGINT PRIMARY KEY,
                  category_code VARCHAR(64) NOT NULL,
                  item_code VARCHAR(64) NOT NULL,
                  cap_rule_json VARCHAR(1000) NULL,
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
        insertStudent(1001L, "S1001", "ACTIVE");
        insertStudent(1002L, "S1002", "DISABLED");
        insertStudent(1003L, "S1003", "ACTIVE");
        insertMembership(8001L, 1001L, 2010L, true, "ACTIVE");
        insertMembership(8002L, 1002L, 2010L, true, "ACTIVE");
        insertMembership(8003L, 1003L, 2010L, true, "DISABLED");
        insertEvaluationItem(12006L, "SPORTS", "SPORTS_COMPETITION", "{\"maxPoints\":1.5,\"allowOverflow\":false}", "ACTIVE");
    }

    @Test
    void shouldFindOnlyActiveSportsItemAndParseCapRule() {
        insertEvaluationItem(12007L, "SPORTS", "SPORTS_INACTIVE", "{\"maxPoints\":2.0,\"allowOverflow\":false}", "INACTIVE");
        insertEvaluationItem(12008L, "MORAL", "MORAL_VOLUNTEER", "{\"maxPoints\":2.0,\"allowOverflow\":false}", "ACTIVE");

        Optional<ActivityImportItemDefinition> item = repository.findActiveSportsItem("SPORTS_COMPETITION");

        assertThat(item).isPresent();
        assertThat(item.get().itemCode()).isEqualTo("SPORTS_COMPETITION");
        assertThat(item.get().categoryCode()).isEqualTo("SPORTS");
        assertThat(item.get().maxPoints()).isEqualByComparingTo("1.5");
        assertThat(item.get().allowOverflow()).isFalse();
        assertThat(repository.findActiveSportsItem("SPORTS_INACTIVE")).isEmpty();
        assertThat(repository.findActiveSportsItem("MORAL_VOLUNTEER")).isEmpty();
    }

    @Test
    void shouldTreatInvalidCapMetadataAsMissingItem() {
        insertEvaluationItem(12101L, "SPORTS", "SPORTS_NULL_CAP", null, "ACTIVE");
        insertEvaluationItem(12102L, "SPORTS", "SPORTS_BAD_JSON", "{bad", "ACTIVE");
        insertEvaluationItem(12103L, "SPORTS", "SPORTS_ARRAY_JSON", "[]", "ACTIVE");
        insertEvaluationItem(12104L, "SPORTS", "SPORTS_MISSING_FIELD", "{\"maxPoints\":1.0}", "ACTIVE");
        insertEvaluationItem(12105L, "SPORTS", "SPORTS_WRONG_TYPE", "{\"maxPoints\":\"1.0\",\"allowOverflow\":false}", "ACTIVE");
        insertEvaluationItem(12106L, "SPORTS", "SPORTS_NEGATIVE", "{\"maxPoints\":-0.01,\"allowOverflow\":false}", "ACTIVE");
        insertEvaluationItem(12107L, "SPORTS", "SPORTS_TOO_LARGE", "{\"maxPoints\":100000000.00,\"allowOverflow\":false}", "ACTIVE");

        assertThat(repository.findActiveSportsItem("SPORTS_NULL_CAP")).isEmpty();
        assertThat(repository.findActiveSportsItem("SPORTS_BAD_JSON")).isEmpty();
        assertThat(repository.findActiveSportsItem("SPORTS_ARRAY_JSON")).isEmpty();
        assertThat(repository.findActiveSportsItem("SPORTS_MISSING_FIELD")).isEmpty();
        assertThat(repository.findActiveSportsItem("SPORTS_WRONG_TYPE")).isEmpty();
        assertThat(repository.findActiveSportsItem("SPORTS_NEGATIVE")).isEmpty();
        assertThat(repository.findActiveSportsItem("SPORTS_TOO_LARGE")).isEmpty();
    }

    @Test
    void shouldFindActiveStudentTargetWithoutOrgPathAndResolveSmallestPrimaryMembershipId() {
        insertStudent(1004L, "S1004", "ACTIVE");
        insertMembership(9002L, 1004L, 2010L, true, "ACTIVE");
        insertMembership(9001L, 1004L, 3010L, true, "ACTIVE");

        Optional<ActivityImportStudentTarget> target = repository.findTarget("S1004", "2025-2026");

        assertThat(target).isPresent();
        assertThat(target.get().studentUserId()).isEqualTo(1004L);
        assertThat(target.get().studentNo()).isEqualTo("S1004");
        assertThat(target.get().orgUnitId()).isEqualTo(3010L);
        assertThat(repository.findTarget("S1002", "2025-2026")).isEmpty();
        assertThat(repository.findTarget("S1003", "2025-2026")).isEmpty();
    }

    @Test
    void shouldFindOnlyActiveOrgPathForScopeRoot() {
        insertOrgUnit(4001L, "临时学院", "/WHUT/TEMP", "INACTIVE");

        assertThat(repository.findActiveOrgPath(2002L)).contains("/WHUT/CS");
        assertThat(repository.findActiveOrgPath(4001L)).isEmpty();
        assertThat(repository.findActiveOrgPath(9999L)).isEmpty();
    }

    @Test
    void shouldDetectExistingActivityBatchByYearCategoryItemSourceTypeAndSourceRefId() {
        Long recordId = insertDraftRecord(1001L, "2025-2026");
        insertComponent(recordId, "SPORTS", "SPORTS_COMPETITION", "1.00", "IMPORT", "ACTIVITY-20252026-20260518143000-ABCDEF123456");
        insertComponent(recordId, "SPORTS", "SPORTS_OTHER", "1.00", "IMPORT", "ACTIVITY-20252026-20260518143000-OTHERITEM01");
        insertComponent(recordId, "SPORTS", "SPORTS_COMPETITION", "1.00", "APPLICATION", "ACTIVITY-20252026-20260518143000-APPONLY001");

        assertThat(repository.activityBatchExists("2025-2026", "SPORTS", "SPORTS_COMPETITION", "ACTIVITY-20252026-20260518143000-ABCDEF123456")).isTrue();
        assertThat(repository.activityBatchExists("2026-2027", "SPORTS", "SPORTS_COMPETITION", "ACTIVITY-20252026-20260518143000-ABCDEF123456")).isFalse();
        assertThat(repository.activityBatchExists("2025-2026", "INTELLECTUAL", "SPORTS_COMPETITION", "ACTIVITY-20252026-20260518143000-ABCDEF123456")).isFalse();
        assertThat(repository.activityBatchExists("2025-2026", "SPORTS", "SPORTS_OTHER", "ACTIVITY-20252026-20260518143000-ABCDEF123456")).isFalse();
        assertThat(repository.activityBatchExists("2025-2026", "SPORTS", "SPORTS_COMPETITION", "ACTIVITY-20252026-20260518143000-APPONLY001")).isFalse();
    }

    @Test
    void shouldCreateDraftAndInsertSportsActivityIntoPhysicalTotal() {
        repository.insertActivityComponents("2025-2026", List.of(
                component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.25", "校运会")
        ));

        Long recordId = jdbcTemplate.queryForObject("SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'", Long.class);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM final_record WHERE id = ?", String.class, recordId)).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject("SELECT category_code FROM final_component_score WHERE final_record_id = ?", String.class, recordId)).isEqualTo("SPORTS");
        assertThat(jdbcTemplate.queryForObject("SELECT item_code FROM final_component_score WHERE final_record_id = ?", String.class, recordId)).isEqualTo("SPORTS_COMPETITION");
        assertThat(jdbcTemplate.queryForObject("SELECT source_type FROM final_component_score WHERE final_record_id = ?", String.class, recordId)).isEqualTo("IMPORT");
        assertThat(jdbcTemplate.queryForObject("SELECT physical_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("1.25");
        assertThat(jdbcTemplate.queryForObject("SELECT grand_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("1.25");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(1L);
    }

    @Test
    void shouldPreserveExistingCategoryTotalsWhenAddingSportsActivity() {
        Long recordId = insertDraftRecord(1001L, "2025-2026");
        insertComponent(recordId, "MORAL", "MORAL_VOLUNTEER", "1.00", "APPLICATION", "app-moral");
        insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "2.00", "IMPORT", "lecture-1");
        insertComponent(recordId, "LABOR", "LABOR_SERVICE", "3.00", "IMPORT", "labor-1");

        repository.insertActivityComponents("2025-2026", List.of(
                component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "0.50", "校运会")
        ));

        assertThat(jdbcTemplate.queryForObject("SELECT moral_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("1.00");
        assertThat(jdbcTemplate.queryForObject("SELECT intellectual_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("2.00");
        assertThat(jdbcTemplate.queryForObject("SELECT physical_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("0.50");
        assertThat(jdbcTemplate.queryForObject("SELECT labor_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("3.00");
        assertThat(jdbcTemplate.queryForObject("SELECT grand_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("6.50");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(1L);
    }

    @Test
    void shouldAppendDifferentActivityBatchesForSameStudentAndItem() {
        repository.insertActivityComponents("2025-2026", List.of(
                component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.00", "活动A", "ACTIVITY-20252026-20260518143000-BATCH000001")
        ));
        repository.insertActivityComponents("2025-2026", List.of(
                component(3L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "2.00", "活动B", "ACTIVITY-20252026-20260518143000-BATCH000002")
        ));

        Long recordId = jdbcTemplate.queryForObject("SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'", Long.class);
        assertThat(jdbcTemplate.queryForList("SELECT source_ref_id FROM final_component_score WHERE final_record_id = ? ORDER BY id", String.class, recordId))
                .containsExactly(
                        "ACTIVITY-20252026-20260518143000-BATCH000001",
                        "ACTIVITY-20252026-20260518143000-BATCH000002"
                );
        assertThat(jdbcTemplate.queryForObject("SELECT physical_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("3.00");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(2L);
    }

    @Test
    void shouldReturnLockedFailureWithActivityRawValuesAndLeaveNoComponent() {
        insertFinalRecord(1001L, "2025-2026", "CONFIRMED");

        List<ActivityImportFailedRow> failures = repository.insertActivityComponents("2025-2026", List.of(
                component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.00", "校运会")
        ));

        assertThat(failures).extracting("rowNo").containsExactly(2L);
        assertThat(failures).extracting("code").containsExactly("FINAL_RECORD_LOCKED");
        assertThat(failures.get(0).rawValue())
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        "studentNo", "S1001",
                        "displayText", "校运会"
                ));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score", Long.class)).isZero();
    }

    @Test
    void shouldRollbackInsertedActivityComponentsWhenTotalsUpdateFails() {
        Long recordId = insertDraftRecord(1001L, "2025-2026");
        insertComponent(recordId, "UNKNOWN", "UNKNOWN_ITEM", "3.00", "APPLICATION", "bad-category");

        assertThatThrownBy(() -> repository.insertActivityComponents("2025-2026", List.of(
                component(2L, 1001L, "S1001", "SPORTS_COMPETITION", "SPORTS", "1.00", "校运会", "ACTIVITY-20252026-20260518143000-ROLLBACK0001")
        )))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("unsupported final record category")
                .hasMessageNotContaining("UNKNOWN");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM final_component_score
                WHERE source_ref_id = 'ACTIVITY-20252026-20260518143000-ROLLBACK0001'
                """, Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT physical_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("0.00");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isZero();
    }

    private void insertOrgUnit(Long id, String unitName, String path, String status) {
        jdbcTemplate.update("INSERT INTO org_unit (id, unit_name, path, status) VALUES (?, ?, ?, ?)", id, unitName, path, status);
    }

    private void insertStudent(Long id, String userNo, String status) {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (?, ?, ?, ?)", id, userNo, userNo, status);
    }

    private void insertMembership(Long id, Long userId, Long orgUnitId, boolean primary, String status) {
        jdbcTemplate.update("""
                INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status)
                VALUES (?, ?, ?, 'STUDENT', ?, ?)
                """, id, userId, orgUnitId, primary ? 1 : 0, status);
    }

    private void insertEvaluationItem(Long id, String categoryCode, String itemCode, String capRuleJson, String status) {
        jdbcTemplate.update("""
                INSERT INTO evaluation_item (id, category_code, item_code, cap_rule_json, status)
                VALUES (?, ?, ?, ?, ?)
                """, id, categoryCode, itemCode, capRuleJson, status);
    }

    private Long insertDraftRecord(Long studentUserId, String academicYear) {
        return insertFinalRecord(studentUserId, academicYear, "DRAFT");
    }

    private Long insertFinalRecord(Long studentUserId, String academicYear, String status) {
        jdbcTemplate.update("""
                INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total,
                                          grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                VALUES (?, ?, ?, 0, 0, 0, 0, 0, NULL, NULL, NULL, 0, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())
                """, studentUserId, academicYear, status);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM final_record", Long.class);
    }

    private void insertComponent(Long finalRecordId, String categoryCode, String itemCode,
                                 String scoreValue, String sourceType, String sourceRefId) {
        jdbcTemplate.update("""
                INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at)
                VALUES (?, ?, ?, ?, 'component', ?, ?, CURRENT_TIMESTAMP())
                """, finalRecordId, categoryCode, itemCode, new BigDecimal(scoreValue), sourceType, sourceRefId);
    }

    private static ActivityImportedComponent component(Long rowNo,
                                                       Long studentUserId,
                                                       String studentNo,
                                                       String itemCode,
                                                       String categoryCode,
                                                       String scoreValue,
                                                       String displayText) {
        return component(rowNo, studentUserId, studentNo, itemCode, categoryCode, scoreValue, displayText,
                "ACTIVITY-20252026-20260518143000-BATCH000001");
    }

    private static ActivityImportedComponent component(Long rowNo,
                                                       Long studentUserId,
                                                       String studentNo,
                                                       String itemCode,
                                                       String categoryCode,
                                                       String scoreValue,
                                                       String displayText,
                                                       String activityBatchId) {
        return new ActivityImportedComponent(
                rowNo,
                studentUserId,
                studentNo,
                itemCode,
                categoryCode,
                new BigDecimal(scoreValue),
                displayText,
                displayText,
                activityBatchId
        );
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = ActivityImportMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisActivityImportRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:activity_import_repository_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
