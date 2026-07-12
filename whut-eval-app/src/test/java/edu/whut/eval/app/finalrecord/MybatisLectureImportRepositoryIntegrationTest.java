package edu.whut.eval.app.finalrecord;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import edu.whut.eval.application.finalrecord.importing.LectureImportRepository;
import edu.whut.eval.application.finalrecord.importing.LectureImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.LectureImportedComponent;
import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.LectureImportMapper;
import edu.whut.eval.infra.persistence.repository.MybatisLectureImportRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisLectureImportRepositoryIntegrationTest.TestConfig.class)
class MybatisLectureImportRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LectureImportRepository repository;

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
                  id BIGINT PRIMARY KEY,
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
                  score_value DECIMAL(10,3) NOT NULL,
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
    }

    @Test
    void shouldFindActiveStudentTargetWithoutIdentityAndResolveSmallestPrimaryMembershipId() {
        insertStudent(1004L, "S1004", "ACTIVE");
        insertMembership(9002L, 1004L, 2010L, true, "ACTIVE");
        insertMembership(9001L, 1004L, 3010L, true, "ACTIVE");

        Optional<LectureImportStudentTarget> target = repository.findTarget("S1004", "2025-2026");

        assertThat(target).isPresent();
        assertThat(target.get().orgUnitId()).isEqualTo(3010L);
    }

    @Test
    void shouldFindOnlyActiveOrgPathForScopeRoot() {
        insertOrgUnit(4001L, "临时学院", "/WHUT/TEMP", "INACTIVE");

        assertThat(repository.findActiveOrgPath(2002L)).contains("/WHUT/CS");
        assertThat(repository.findActiveOrgPath(4001L)).isEmpty();
        assertThat(repository.findActiveOrgPath(9999L)).isEmpty();
    }

    @Test
    void shouldDetectExistingLectureBatchByAcademicYearAndSourceRefId() {
        Long recordId = insertDraftRecord(1001L, "2025-2026");
        insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "1.00", "IMPORT", "LECTURE-20252026-20260518143000-ABCDEF123456");

        assertThat(repository.lectureBatchExists("2025-2026", "LECTURE-20252026-20260518143000-ABCDEF123456")).isTrue();
        assertThat(repository.lectureBatchExists("2026-2027", "LECTURE-20252026-20260518143000-ABCDEF123456")).isFalse();
    }

    @Test
    void shouldInsertLectureComponentsWithoutOverwritingPreviousLectureBatches() {
        repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-BATCH0000001", List.of(
                component(2L, 1001L, "S1001", "1.25", "讲座A")
        ));
        repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-BATCH0000002", List.of(
                component(3L, 1001L, "S1001", "2.00", "讲座B")
        ));

        Long recordId = jdbcTemplate.queryForObject("SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'", Long.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score WHERE final_record_id = ?", Long.class, recordId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForList("SELECT source_ref_id FROM final_component_score WHERE final_record_id = ? ORDER BY id", String.class, recordId))
                .containsExactly(
                        "LECTURE-20252026-20260518143000-BATCH0000001",
                        "LECTURE-20252026-20260518143000-BATCH0000002"
                );
        assertThat(jdbcTemplate.queryForObject("SELECT intellectual_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("3.25");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(2L);
    }

    @Test
    void shouldRecalculateTotalsAfterEachSuccessfulLectureRowAndIncrementVersion() {
        Long recordId = insertDraftRecord(1001L, "2025-2026");
        insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_APPLICATION", "2.00", "APPLICATION", "app-1");

        repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-EXISTING0001", List.of(
                component(2L, 1001L, "S1001", "1.25", "讲座A"),
                component(3L, 1001L, "S1001", "2.00", "讲座B")
        ));

        assertThat(jdbcTemplate.queryForObject("SELECT intellectual_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("5.25");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(2L);
    }

    @Test
    void shouldRoundTotalsHalfUpAndIncrementVersionPerSuccessfulLectureRow() {
        Long recordId = insertDraftRecord(1001L, "2025-2026");
        insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_APPLICATION", "1.235", "APPLICATION", "app-1");

        repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-ROUND0000001", List.of(
                component(2L, 1001L, "S1001", "0.001", "讲座A"),
                component(3L, 1001L, "S1001", "0.004", "讲座B")
        ));

        assertThat(jdbcTemplate.queryForObject("SELECT intellectual_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("1.24");
        assertThat(jdbcTemplate.queryForObject("SELECT grand_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
                .isEqualByComparingTo("1.24");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(2L);
    }

    @Test
    void shouldKeepEarlierSuccessfulDraftRowsWhenLaterRowIsLocked() {
        Long draftRecordId = insertDraftRecord(1001L, "2025-2026");
        insertFinalRecord(1004L, "2025-2026", "SUBMITTED");
        insertStudent(1004L, "S1004", "ACTIVE");
        insertMembership(8004L, 1004L, 2010L, true, "ACTIVE");

        List<LectureImportFailedRow> failures = repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-MIXED0000001", List.of(
                component(2L, 1001L, "S1001", "1.00", "讲座A"),
                component(3L, 1004L, "S1004", "2.00", "讲座B")
        ));

        assertThat(failures).extracting("rowNo").containsExactly(3L);
        assertThat(failures).extracting("code").containsExactly("FINAL_RECORD_LOCKED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score WHERE final_record_id = ?", Long.class, draftRecordId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT intellectual_total FROM final_record WHERE id = ?", BigDecimal.class, draftRecordId))
                .isEqualByComparingTo("1.00");
    }

    @Test
    void shouldRollbackAllInsertedComponentsWhenTotalsUpdateFails() {
        Long draftRecordId = insertDraftRecord(1001L, "2025-2026");
        insertComponent(draftRecordId, "UNKNOWN", "UNKNOWN_ITEM", "3.00", "APPLICATION", "bad-category");

        assertThatThrownBy(() -> repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-ROLLBACK00001", List.of(
                component(2L, 1001L, "S1001", "1.00", "讲座A")
        )))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("unsupported final record category");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM final_component_score
                WHERE source_ref_id = 'LECTURE-20252026-20260518143000-ROLLBACK00001'
                """, Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT intellectual_total FROM final_record WHERE id = ?", BigDecimal.class, draftRecordId))
                .isEqualByComparingTo("0.00");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, draftRecordId)).isZero();
    }

    @Test
    void shouldReturnFinalRecordLockedFailureAndLeaveNoComponentForSubmittedRecord() {
        insertFinalRecord(1001L, "2025-2026", "SUBMITTED");

        List<LectureImportFailedRow> failures = repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-LOCKED000001", List.of(
                component(2L, 1001L, "S1001", "1.00", "讲座")
        ));

        assertThat(failures).extracting("code").containsExactly("FINAL_RECORD_LOCKED");
        assertThat(failures.get(0).rawValue())
                .containsEntry("studentNo", "S1001")
                .containsEntry("scoreValue", "1.00")
                .containsEntry("displayText", "讲座");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score", Long.class)).isZero();
    }

    @Test
    void shouldReturnFinalRecordLockedFailureAndLeaveNoComponentForConfirmedRecord() {
        insertFinalRecord(1001L, "2025-2026", "CONFIRMED");

        List<LectureImportFailedRow> failures = repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-LOCKED000002", List.of(
                component(2L, 1001L, "S1001", "1.00", "讲座")
        ));

        assertThat(failures).extracting("code").containsExactly("FINAL_RECORD_LOCKED");
        assertThat(failures.get(0).rawValue())
                .containsEntry("studentNo", "S1001")
                .containsEntry("scoreValue", "1.00")
                .containsEntry("displayText", "讲座");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score", Long.class)).isZero();
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

    private static LectureImportedComponent component(Long rowNo, Long studentUserId, String studentNo, String scoreValue, String displayText) {
        return new LectureImportedComponent(
                rowNo,
                studentUserId,
                studentNo,
                scoreValue,
                new BigDecimal(scoreValue),
                displayText,
                displayText
        );
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = LectureImportMapper.class)
    @Import({
            MybatisPlusConfig.class,
            MybatisLectureImportRepository.class
    })
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:lecture_import_repository_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
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
