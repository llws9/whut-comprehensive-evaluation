package edu.whut.eval.app.finalrecord;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.auth.service.DefaultAuthorizationScopeEvaluator;
import edu.whut.eval.application.auth.service.JsonScopeRuleExpressionInterpreter;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreRow;
import edu.whut.eval.application.finalrecord.query.FinalRecordQueryRow;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentRow;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.query.FinalRecordAccessContext;
import edu.whut.eval.domain.finalrecord.query.FinalRecordPageQuery;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.infra.config.MybatisPlusConfig;
import edu.whut.eval.infra.persistence.mapper.FinalRecordQueryMapper;
import edu.whut.eval.infra.persistence.mapper.FinalRecordQuerySqlProvider;
import edu.whut.eval.infra.persistence.repository.MybatisPlusFinalRecordQueryRepository;
import edu.whut.eval.infra.security.sql.ApplicationScopeSqlTranslator;
import edu.whut.eval.infra.security.sql.D11ScopeSqlShape;
import edu.whut.eval.infra.security.sql.SqlPredicateFragment;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisPlusFinalRecordQueryRepositoryIntegrationTest.TestConfig.class)
class MybatisPlusFinalRecordQueryRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinalRecordQueryRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_component_score");
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("""
                CREATE TABLE iam_user (
                  id BIGINT PRIMARY KEY,
                  user_no VARCHAR(64) NOT NULL,
                  user_name VARCHAR(128) NOT NULL,
                  status VARCHAR(32) NOT NULL)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE org_unit (
                  id BIGINT PRIMARY KEY,
                  parent_id BIGINT NULL,
                  unit_type VARCHAR(32) NOT NULL,
                  unit_code VARCHAR(64) NOT NULL,
                  unit_name VARCHAR(128) NOT NULL,
                  path VARCHAR(512) NOT NULL,
                  status VARCHAR(32) NOT NULL)
                """);
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
                  score_value DECIMAL(10,2) NOT NULL,
                  display_text VARCHAR(1000) NULL,
                  source_type VARCHAR(32) NOT NULL,
                  source_ref_id VARCHAR(64) NULL,
                  created_at DATETIME NOT NULL)
                """);
        insertLegacyOrgUnit(2002L, "计算机与人工智能学院", "/WHUT/CS", "COLLEGE");
        insertStudent(1001L, "2024305001", "张三", 2010L, "计科一班", "/WHUT/CS/CS2022/CS2201");
        insertStudent(1002L, "2024305002", "李四", 2011L, "计科二班", "/WHUT/CS/CS2022/CS2202");
        insertFinalRecord(41001L, 1001L, "2025-2026", "SUBMITTED", "2026-07-07 12:00:00");
        insertFinalRecord(41002L, 1002L, "2025-2026", "SUBMITTED", "2026-07-07 13:00:00");
        insertComponent(41001L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2.00", "论文已审核通过", "21013");
    }

    @Test
    void shouldNormalizePageQueryAndRejectDraftStatus() {
        FinalRecordPageQuery query = new FinalRecordPageQuery("2025-2026", null, "  ", null, 0, 200);

        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(100);
        assertThat(query.getKeyword()).isNull();
        assertThatThrownBy(() -> new FinalRecordPageQuery("2025-2026", "DRAFT", null, null, 1, 20))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new FinalRecordPageQuery(" ", null, null, null, 1, 20))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldPageAdminRecordsWithinWholeRecordScope() {
        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithOrgSubtree(2002L),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
                .containsExactly(41002L, 41001L);
    }

    @Test
    void shouldHideDraftRecordsFromAdminListAndDetail() {
        insertStudent(1003L, "2024305003", "王五", 2012L, "计科三班", "/WHUT/CS/CS2022/CS2203");
        insertFinalRecord(41003L, 1003L, "2025-2026", "DRAFT", "2026-07-07 14:00:00");

        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithOrgSubtree(2002L),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
                .doesNotContain(41003L);
        assertThat(repository.findAdminFinalRecordDetail(41003L)).isEmpty();
    }

    @Test
    void shouldUsePrimaryMembershipOnlyForFinalRecordScopeAndListRows() {
        jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (?, ?, ?, 'STUDENT', 0, 'ACTIVE')",
                3011L,
                1001L, 2011L);

        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithOrgSubtree(2002L),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
                .containsExactly(41002L, 41001L);
    }

    @Test
    void shouldKeepNoMembershipRecordsVisibleToAllScopeWithNullOrgFields() {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                1003L, "2024305003", "王五");
        insertFinalRecord(41003L, 1003L, "2025-2026", "SUBMITTED", "2026-07-07 14:00:00");

        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithAllScope(),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(3L);
        assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
                .contains(41003L);
        assertThat(repository.findAdminFinalRecordDetail(41003L))
                .get()
                .extracting(FinalRecordQueryRow::getOrgUnitId, FinalRecordQueryRow::getOrgPath)
                .containsExactly(null, null);
    }

    @Test
    void shouldReturnEmptyPageForUnsupportedScopeOnlyCaller() {
        PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
                accessContextWithCategoryOnlyScope(),
                new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
        );

        assertThat(page.total()).isZero();
        assertThat(page.records()).isEmpty();
    }

    @Test
    void shouldFindStudentFinalRecordAndOrderedComponents() {
        assertThat(repository.findStudentFinalRecord(1001L, "2025-2026"))
                .map(FinalRecordQueryRow::getFinalRecordId)
                .contains(41001L);

        List<FinalComponentScoreRow> components = repository.listStudentFinalRecordComponents(41001L);

        assertThat(components).hasSize(1);
        assertThat(components.get(0).getItemName()).isNull();
        assertThat(components.get(0).getDisplayText()).isEqualTo("论文已审核通过");
    }

    @Test
    void shouldFindAdminDetailUnscopedForAccessValidatorContext() {
        assertThat(repository.findAdminFinalRecordDetail(41001L))
                .map(FinalRecordQueryRow::getOrgPath)
                .contains("/WHUT/CS/CS2022/CS2201");
        assertThat(repository.listAdminFinalRecordComponents(41001L)).hasSize(1);
    }

    @Test
    void shouldIncludeCurrentRosterStudentsWithNoFinalRecord() {
        seedRoster();

        PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L, 1003L);
        assertThat(page.records()).allSatisfy(row -> assertThat(row.getLastUpdatedAt()).isNull());
    }

    @Test
    void shouldKeepDraftStudentsUnsubmittedAndExposeDraftUpdatedAt() {
        seedRoster();
        insertFinalRecord(11L, 1001L, "2025-2026", "DRAFT", "2026-07-12 10:15:30");

        PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );

        UnsubmittedStudentRow alice = findRow(page, 1001L);
        assertThat(page.total()).isEqualTo(3);
        assertThat(alice.getLastUpdatedAt()).isEqualTo(Instant.parse("2026-07-12T10:15:30Z"));
        assertThat(page.records()).filteredOn(row -> row.getStudentUserId().equals(1001L)).hasSize(1);
    }

    @Test
    void shouldExcludeSubmittedAndConfirmedStudents() {
        seedRoster();
        insertFinalRecord(21L, 1001L, "2025-2026", "SUBMITTED", "2026-07-12 10:15:30");
        insertFinalRecord(22L, 1002L, "2025-2026", "CONFIRMED", "2026-07-12 10:15:30");

        PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1003L);
        assertThat(findRow(page, 1003L).getLastUpdatedAt()).isNull();
    }

    @Test
    void shouldIsolateRecordsByAcademicYear() {
        seedRoster();
        insertFinalRecord(25L, 1001L, "2024-2025", "SUBMITTED", "2026-07-12 10:15:30");
        insertFinalRecord(26L, 1002L, "2024-2025", "CONFIRMED", "2026-07-12 10:15:30");
        insertFinalRecord(27L, 1003L, "2024-2025", "DRAFT", "2026-07-12 12:15:30");

        PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L, 1003L);
        assertThat(page.records()).allSatisfy(row -> assertThat(row.getLastUpdatedAt()).isNull());
    }

    @Test
    void shouldApplyOrgUnitAndOrgSubtreeScopeToVisibleClasses() {
        seedRoster();
        insertOrgUnit(2999L, null, "COLLEGE", "CS2", "相似学院", "/WHUT/CS2", "ACTIVE");
        insertOrgUnit(4999L, 2999L, "CLASS", "CS2X", "相似班", "/WHUT/CS2/CS2201", "ACTIVE");
        insertRosterStudent(1099L, "S099", "Similar", 5099L, 4999L);

        PageResult<UnsubmittedStudentRow> subtree = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );
        PageResult<UnsubmittedStudentRow> exactClass = repository.pageUnsubmittedStudents(
                accessContextWithOrgUnit(4001L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );

        assertThat(subtree.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L, 1003L);
        assertThat(exactClass.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L);
    }

    @Test
    void shouldApplyGradeAndClassesFiltersWithExactCaseSensitiveMatches() {
        seedRoster();

        assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", List.of("CS2201"), 1, 20)).records())
                .extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L);
        assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", "cs2022", List.of("cs2201"), 1, 20)).records())
                .isEmpty();
        assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", "计算机2022级", List.of("计算机2202班"), 1, 20)).records())
                .extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1003L);
        assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", "计算机", null, 1, 20)).records())
                .isEmpty();
        assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("2201"), 1, 20)).records())
                .isEmpty();
    }

    @Test
    void shouldDeduplicateByLowestVisibleMembershipAndPageDeterministically() {
        seedRoster();
        insertOrgUnit(4003L, 3001L, "CLASS", "CS2203", "计算机2203班", "/WHUT/CS/CS2022/CS2203", "ACTIVE");
        insertRosterStudent(1004L, "S004", "Dora", 5004L, 4003L);
        jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1004, 4002, 'STUDENT', 1, 'ACTIVE')");

        PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );
        PageResult<UnsubmittedStudentRow> firstPage = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 2)
        );
        PageResult<UnsubmittedStudentRow> secondPage = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 2, 2)
        );

        assertThat(page.total()).isEqualTo(4);
        assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L, 1003L, 1004L)
                .doesNotHaveDuplicates();
        assertThat(findRow(page, 1004L).getClassName()).isEqualTo("计算机2202班");
        assertThat(firstPage.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L);
        assertThat(secondPage.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1003L, 1004L);
    }

    @Test
    void shouldExcludeInactiveUsersMembershipsAndNonClassOrganizations() {
        seedRoster();
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1004, 'S004', 'InactiveUser', 'INACTIVE')");
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1005, 'S005', 'InactiveMembership', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1006, 'S006', 'NonPrimary', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1007, 'S007', 'TeacherMembership', 'ACTIVE')");
        insertOrgUnit(4014L, 3001L, "CLASS", "CS2299", "失效班级", "/WHUT/CS/CS2022/CS2299", "INACTIVE");
        insertOrgUnit(4015L, 3001L, "GRADE", "NOT_CLASS", "非班级组织", "/WHUT/CS/CS2022/NOT_CLASS", "ACTIVE");
        jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5004, 1004, 4001, 'STUDENT', 1, 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4001, 'STUDENT', 1, 'INACTIVE')");
        jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4001, 'STUDENT', 0, 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5007, 1007, 4001, 'TEACHER', 1, 'ACTIVE')");
        insertRosterStudent(1014L, "S014", "InactiveClass", 5014L, 4014L);
        insertRosterStudent(1015L, "S015", "NonClassOrg", 5015L, 4015L);

        PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(2002L),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L, 1003L);
    }

    @Test
    void shouldHandleAllUnsupportedAndDeniedScopes() {
        seedRoster();

        assertThat(repository.pageUnsubmittedStudents(accessContextWithAllScope(),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)).records())
                .extracting(UnsubmittedStudentRow::getStudentUserId)
                .containsExactly(1001L, 1002L, 1003L);
        assertThat(repository.pageUnsubmittedStudents(accessContextWithCategoryOnlyScope(),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)).records())
                .isEmpty();
        assertThat(repository.pageUnsubmittedStudents(accessContextWithoutScoreViewAssigned(),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)).records())
                .isEmpty();
    }

    @Test
    void shouldRejectUnsafeD11ScopeExpressionFragments() {
        FinalRecordQuerySqlProvider provider = new FinalRecordQuerySqlProvider();
        Map<String, Object> params = providerParams(new SqlPredicateFragment(
                "(__D11_CLASS_ALIAS__.id IN (#{scopeFragment.parameters.d11OrgUnit0})) OR 1 = 1",
                Map.of("d11OrgUnit0", 4001L)
        ));

        assertThatThrownBy(() -> provider.buildCountUnsubmittedStudents(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe D-11 scope expression: shape not whitelisted");
        assertThatThrownBy(() -> provider.buildSelectUnsubmittedStudents(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe D-11 scope expression: shape not whitelisted");
    }

    @Test
    void shouldRejectD11ScopeFragmentsWhenExpressionAndParameterMapDisagree() {
        FinalRecordQuerySqlProvider provider = new FinalRecordQuerySqlProvider();
        Map<String, Object> params = providerParams(new SqlPredicateFragment(
                "(" + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}") + ")",
                Map.of("d11Typo0", 4001L)
        ));

        assertThatThrownBy(() -> provider.buildCountUnsubmittedStudents(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe D-11 scope expression: parameter names mismatch");
        assertThatThrownBy(() -> provider.buildSelectUnsubmittedStudents(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe D-11 scope expression: parameter names mismatch");
    }

    @Test
    void shouldRejectD11ScopeFragmentsWithNonLongParameterValues() {
        FinalRecordQuerySqlProvider provider = new FinalRecordQuerySqlProvider();
        Map<String, Object> params = providerParams(new SqlPredicateFragment(
                "(" + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}") + ")",
                Map.of("d11OrgUnit0", "4001")
        ));

        assertThatThrownBy(() -> provider.buildCountUnsubmittedStudents(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe D-11 scope expression: non-Long parameter value");
        assertThatThrownBy(() -> provider.buildSelectUnsubmittedStudents(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsafe D-11 scope expression: non-Long parameter value");
    }

    @Test
    void shouldAcceptOnlyWhitelistedD11ScopeExpressionShapes() {
        FinalRecordQuerySqlProvider provider = new FinalRecordQuerySqlProvider();

        D11ScopeSqlShape.assertGeneratedFragmentsSelfValidateForD11();
        assertProviderAccepts(provider, SqlPredicateFragment.denyAll(), "1 = 0");
        assertProviderAccepts(provider, SqlPredicateFragment.alwaysTrue(), "1 = 1");
        assertProviderAccepts(provider, new SqlPredicateFragment(
                "(" + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}") + ")",
                Map.of("d11OrgUnit0", 4001L)
        ), "class_ou.id IN", "class_ou1.id IN");
        assertProviderAccepts(provider, new SqlPredicateFragment(
                "(" + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}, #{scopeFragment.parameters.d11OrgUnit1}") + ")",
                Map.of("d11OrgUnit0", 4001L, "d11OrgUnit1", 4002L)
        ), "class_ou.id IN", "class_ou1.id IN");
        assertProviderAccepts(provider, new SqlPredicateFragment(
                "(" + D11ScopeSqlShape.orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}") + ")",
                Map.of("d11Subtree0", 2002L)
        ), "LOCATE('%', root_ou.path) = 0");
        assertProviderAccepts(provider, new SqlPredicateFragment(
                "(" + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}")
                        + " OR " + D11ScopeSqlShape.orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}") + ")",
                Map.of("d11OrgUnit0", 4001L, "d11Subtree0", 2002L)
        ), " OR ");
        assertProviderAccepts(provider, new SqlPredicateFragment(
                "(" + D11ScopeSqlShape.orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}")
                        + " OR " + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}") + ")",
                Map.of("d11Subtree0", 2002L, "d11OrgUnit0", 4001L)
        ), " OR ");
    }

    private FinalRecordAccessContext accessContextWithAllScope() {
        return new FinalRecordAccessContext(
                1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), Set.of("score.view.assigned"),
                List.of(new IamScopeRule(3L, "score.view.assigned", "ALL", null, null, null, null, 100, "ACTIVE")),
                "score.view.assigned"
        );
    }

    private FinalRecordAccessContext accessContextWithOrgSubtree(Long orgUnitId) {
        return new FinalRecordAccessContext(
                1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), Set.of("score.view.assigned"),
                List.of(new IamScopeRule(1L, "score.view.assigned", "ORG_SUBTREE", orgUnitId, null, null, null, 80, "ACTIVE")),
                "score.view.assigned"
        );
    }

    private FinalRecordAccessContext accessContextWithOrgUnit(Long orgUnitId) {
        return new FinalRecordAccessContext(
                1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), Set.of("score.view.assigned"),
                List.of(new IamScopeRule(4L, "score.view.assigned", "ORG_UNIT", orgUnitId, null, null, null, 80, "ACTIVE")),
                "score.view.assigned"
        );
    }

    private FinalRecordAccessContext accessContextWithoutScoreViewAssigned() {
        return new FinalRecordAccessContext(
                1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), Set.of("other.permission"),
                List.of(new IamScopeRule(5L, "score.view.assigned", "ALL", null, null, null, null, 100, "ACTIVE")),
                "score.view.assigned"
        );
    }

    private FinalRecordAccessContext accessContextWithCategoryOnlyScope() {
        return new FinalRecordAccessContext(
                1010L, "T1010", "Counselor", "teacher", Set.of("counselor"), Set.of("score.view.assigned"),
                List.of(new IamScopeRule(2L, "score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null, null, 80, "ACTIVE")),
                "score.view.assigned"
        );
    }

    private void insertStudent(Long userId, String userNo, String userName, Long orgUnitId, String orgName, String orgPath) {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (?, ?, ?, 'ACTIVE')", userId, userNo, userName);
        insertLegacyOrgUnit(orgUnitId, orgName, orgPath, "CLASS");
        jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (?, ?, ?, 'STUDENT', 1, 'ACTIVE')",
                3000L + userId, userId, orgUnitId);
    }

    private void insertLegacyOrgUnit(Long orgUnitId, String orgName, String orgPath, String unitType) {
        jdbcTemplate.update("""
                MERGE INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status)
                KEY(id) VALUES (?, NULL, ?, ?, ?, ?, 'ACTIVE')
                """, orgUnitId, unitType, lastPathSegment(orgPath), orgName, orgPath);
    }

    private void insertFinalRecord(Long id, Long studentUserId, String academicYear, String status, String updatedAt) {
        jdbcTemplate.update("""
                INSERT INTO final_record (id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 0.80, 2.00, 0.60, 1.20, 4.60,
                        CASE WHEN ? = 'SUBMITTED' THEN CAST(? AS DATETIME) ELSE NULL END,
                        CASE WHEN ? = 'CONFIRMED' THEN CAST(? AS DATETIME) ELSE NULL END,
                        NULL, 1, CAST(? AS DATETIME), CAST(? AS DATETIME))
                """, id, studentUserId, academicYear, status,
                status, updatedAt, status, updatedAt, updatedAt, updatedAt);
    }

    private void insertComponent(Long recordId, String categoryCode, String itemCode, String score, String displayText, String sourceRefId) {
        jdbcTemplate.update("""
                INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at)
                VALUES (?, ?, ?, ?, ?, 'APPLICATION', ?, CURRENT_TIMESTAMP())
                """, recordId, categoryCode, itemCode, score, displayText, sourceRefId);
    }

    private void seedRoster() {
        jdbcTemplate.execute("DELETE FROM final_component_score");
        jdbcTemplate.execute("DELETE FROM final_record");
        jdbcTemplate.execute("DELETE FROM org_membership");
        jdbcTemplate.execute("DELETE FROM org_unit");
        jdbcTemplate.execute("DELETE FROM iam_user");
        insertOrgUnit(2002L, null, "COLLEGE", "CS", "计算机学院", "/WHUT/CS", "ACTIVE");
        insertOrgUnit(3001L, 2002L, "GRADE", "CS2022", "计算机2022级", "/WHUT/CS/CS2022", "ACTIVE");
        insertOrgUnit(4001L, 3001L, "CLASS", "CS2201", "计算机2201班", "/WHUT/CS/CS2022/CS2201", "ACTIVE");
        insertOrgUnit(4002L, 3001L, "CLASS", "CS2202", "计算机2202班", "/WHUT/CS/CS2022/CS2202", "ACTIVE");
        insertRosterStudent(1001L, "S001", "Alice", 5001L, 4001L);
        insertRosterStudent(1002L, "S002", "Bob", 5002L, 4001L);
        insertRosterStudent(1003L, "S003", "Cindy", 5003L, 4002L);
    }

    private void insertOrgUnit(Long id, Long parentId, String unitType, String unitCode, String unitName, String path, String status) {
        jdbcTemplate.update("""
                INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, parentId, unitType, unitCode, unitName, path, status);
    }

    private void insertRosterStudent(Long userId, String userNo, String userName, Long membershipId, Long orgUnitId) {
        jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (?, ?, ?, 'ACTIVE')",
                userId, userNo, userName);
        jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (?, ?, ?, 'STUDENT', 1, 'ACTIVE')",
                membershipId, userId, orgUnitId);
    }

    private UnsubmittedStudentRow findRow(PageResult<UnsubmittedStudentRow> page, Long studentUserId) {
        return page.records().stream()
                .filter(row -> studentUserId.equals(row.getStudentUserId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing unsubmitted student row: " + studentUserId));
    }

    private void assertProviderAccepts(FinalRecordQuerySqlProvider provider,
                                       SqlPredicateFragment fragment,
                                       String expectedSql) {
        assertProviderAccepts(provider, fragment, expectedSql, expectedSql);
    }

    private void assertProviderAccepts(FinalRecordQuerySqlProvider provider,
                                       SqlPredicateFragment fragment,
                                       String expectedCountSql,
                                       String expectedSelectSql) {
        Map<String, Object> params = providerParams(fragment);
        assertThat(provider.buildCountUnsubmittedStudents(params)).contains(expectedCountSql);
        assertThat(provider.buildSelectUnsubmittedStudents(params)).contains(expectedSelectSql);
    }

    private Map<String, Object> providerParams(SqlPredicateFragment fragment) {
        Map<String, Object> params = new HashMap<>();
        params.put("scopeFragment", fragment);
        params.put("query", new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20));
        return params;
    }

    private String lastPathSegment(String path) {
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    @Configuration
    @MapperScan(basePackageClasses = FinalRecordQueryMapper.class)
    @Import({
            MybatisPlusConfig.class,
            DefaultAuthorizationScopeEvaluator.class,
            JsonScopeRuleExpressionInterpreter.class,
            ApplicationScopeSqlTranslator.class,
            MybatisPlusFinalRecordQueryRepository.class
    })
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:final_record_query_repository_test;MODE=MySQL;TIME ZONE=UTC;DB_CLOSE_DELAY=-1");
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
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
