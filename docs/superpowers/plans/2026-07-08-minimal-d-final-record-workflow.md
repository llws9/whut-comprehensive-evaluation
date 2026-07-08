# Minimal D Final Record Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Minimal D backend final-record loop: safe D initialization, student final-record read/submit, admin scoped list/detail, and admin confirmation.

**Architecture:** Add a final-record slice parallel to the existing B/C application slices: domain aggregate and repositories in `whut-eval-domain`, orchestration and view DTOs in `whut-eval-application`, MyBatis persistence in `whut-eval-infra`, and HTTP controllers in `whut-eval-interfaces`. Final-record admin authorization is whole-record organization scoped through a dedicated `FinalRecordResourceContext`, `canAccessFinalRecord(...)`, and `FinalRecordScopePredicateBuilder`, so score category/item rules cannot expose aggregate final records.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Security method annotations, Spring transactions, MyBatis Plus mapper style, H2 MySQL-mode integration tests, JUnit 5, AssertJ, Mockito.

---

## Source Spec

- Primary spec: `docs/superpowers/specs/2026-07-07-minimal-d-final-record-workflow-design.md`
- Target-state reference: `docs/team-delivery/group-d-score-finalization-import-export.md`
- Target-state sample SQL, not runtime safe-init: `docs/team-delivery/group-d-score-finalization-import-export.sql`
- Existing patterns to mirror:
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ReviewApplicationCommandApplicationService.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusReviewApplicationQueryRepository.java`
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/review/ReviewApplicationController.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultResourceScopeAccessEvaluatorTest.java`

## File Structure

### SQL and Permission Seed

- Create `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
  - Creates `final_record` and `final_component_score` with `CREATE TABLE IF NOT EXISTS`.
  - Adds idempotent `score.confirm.assigned` seed rows.
  - Does not reseed `final.view.self` or `final.submit.self`: they are existing A-group permissions (`iam_permission` 5013/5012) with existing SELF scope rows (`iam_scope_rule` 8004/8005) in `docs/team-delivery/group-a-identity-user-admin.sql`.
  - Fails loudly on reserved-id collisions instead of silently binding roles to the wrong permission.
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
  - Adds D safe-init structure, rerun, seed reuse, and reserved-id collision tests.
- Modify `whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java`
  - Adds `SCORE_CONFIRM_ASSIGNED` if absent.
  - Verifies existing `FINAL_SUBMIT_SELF` and `FINAL_VIEW_SELF` constants remain mapped to `final.submit.self` and `final.view.self`.
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/iam/GroupAIdentitySqlSeedConsistencyTest.java`
  - Ensures `SCORE_CONFIRM_ASSIGNED` is intentionally backed by the D safe-init file, not by A-group identity SQL.
  - Ensures `FINAL_SUBMIT_SELF` and `FINAL_VIEW_SELF` are intentionally backed by existing A-group identity SQL, not duplicated by the D safe-init file.

### Domain

- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/model/FinalRecordStatus.java`
  - Enum values: `DRAFT`, `SUBMITTED`, `CONFIRMED`.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/model/FinalComponentScore.java`
  - Immutable component row frozen from approved application facts.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/model/FinalRecord.java`
  - Immutable aggregate with `submit(long expectedVersion)` and `confirm(long expectedVersion, String confirmComment)`.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/FinalRecordPageQuery.java`
  - Admin list query object with `academicYear`, `status`, `keyword`, `orgUnitId`, `pageNo`, and bounded `pageSize`.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/FinalRecordAccessContext.java`
  - Context used by the repository to carry current user, authorities, scope rules, and permission code.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/repository/FinalRecordRepository.java`
  - Command repository for final-record creation, transition updates, components, and source aggregation reads.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service/FinalSubmissionWindowPolicy.java`
  - Extension point called before mutation.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service/NoopFinalSubmissionWindowPolicy.java`
  - Minimal D no-op implementation.
- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service/FinalRecordScopePredicateBuilder.java`
  - Builds an `ApplicationScopePredicate` using only `ALL`, `ORG_UNIT`, and `ORG_SUBTREE`.
- Create `whut-eval-common/src/main/java/edu/whut/eval/common/exception/SubmitWindowClosedException.java`
  - Extends `ConflictException`.

### Application Layer

- Create `whut-eval-application/src/main/java/edu/whut/eval/application/auth/model/FinalRecordResourceContext.java`
  - Whole-record resource context for single-record access decisions.
- Modify `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/ResourceScopeAccessEvaluator.java`
  - Adds `canAccessFinalRecord(...)`.
- Modify `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultResourceScopeAccessEvaluator.java`
  - Implements final-record-only scope matching for `ALL`, `ORG_UNIT`, and `ORG_SUBTREE`.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/command/SubmitFinalRecordCommand.java`
  - Fields: `String academicYear`, `Long expectedVersion`.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/command/ConfirmFinalRecordCommand.java`
  - Fields: `Long recordId`, `String comment`, `Long expectedVersion`.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalRecordView.java`
  - Admin/internal record view. Includes `confirmComment`.
  - Fields: `finalRecordId`, `studentUserId`, `academicYear`, `status`, `moralTotal`, `intellectualTotal`, `physicalTotal`, `laborTotal`, `grandTotal`, `submittedAt`, `confirmedAt`, `confirmComment`, `version`.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalComponentScoreView.java`
  - Fields mirror `FinalComponentScoreRow`.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalComponentScoreListView.java`
  - Fields: `List<FinalComponentScoreView> components`; no paging metadata.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalRecordStudentView.java`
  - Student-facing record view. Must not include `confirmComment`.
  - Fields: `finalRecordId`, `studentUserId`, `academicYear`, `status`, `moralTotal`, `intellectualTotal`, `physicalTotal`, `laborTotal`, `grandTotal`, `submittedAt`, `confirmedAt`, `version`.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/AdminFinalRecordListItemView.java`
  - Fields: `finalRecordId`, `studentUserId`, `studentUserNo`, `studentUserName`, `orgUnitId`, `orgUnitName`, `academicYear`, `status`, `moralTotal`, `intellectualTotal`, `physicalTotal`, `laborTotal`, `grandTotal`, `submittedAt`, `confirmedAt`, `version`.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/AdminFinalRecordDetailView.java`
  - Contains `record`, `student`, and `components`; `record.confirmComment` is visible only on this admin detail path.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/ConfirmFinalRecordResultView.java`
  - Fields: `finalRecordId`, `status`, `confirmComment`, `confirmedAt`, `version`.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalRecordQueryRow.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalComponentScoreRow.java`
  - Includes nullable `itemName` for API-contract stability. Minimal D returns JSON `null` because item-name enrichment belongs to E-group platform definitions; `displayText` carries the frozen approved-fact display text.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java`
  - Query repository for student lookup and admin list/detail. It belongs in the application layer because, like the existing `ReviewApplicationQueryRepository`, it returns application query rows.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordAccessValidator.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordQueryApplicationService.java`
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordCommandApplicationService.java`

### Infrastructure

- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/FinalRecordDO.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/FinalComponentScoreDO.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordMapper.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalComponentScoreMapper.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordAggregationMapper.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordRepository.java`
- Create `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java`

### Interfaces

- Create `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentFinalRecordController.java`
- Create `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/request/SubmitFinalRecordRequest.java`
- Create `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java`
- Create `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/request/ConfirmFinalRecordRequest.java`

### Tests

- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordStateMachineTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordScopePredicateBuilderTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordRepositoryIntegrationTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordCommandApplicationServiceTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordCommandApplicationServiceIntegrationTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/StudentFinalRecordControllerWebMvcTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java`
- Create `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java`

---

### Task 1: D Safe-Init SQL and Permission Seed

**Files:**
- Create: `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/GroupAIdentitySqlSeedConsistencyTest.java`

- [ ] **Step 1: Write failing D safe-init structure tests**

Add a D safe-init constant to `TeamDeliverySqlConsistencyTest`:

```java
private static final Path D_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-d-score-finalization-import-export.safe-init.sql");
```

Add these tests:

```java
@Test
void shouldProvideNonDestructiveDGroupSafeInitSql() throws Exception {
    String sql = Files.readString(D_GROUP_SAFE_INIT_SQL);

    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `final_record`");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `final_component_score`");
    assertThat(sql).doesNotContain("DROP TABLE");
    assertThat(sql).doesNotContain("ENGINE=");
    assertThat(sql).doesNotContain("CHARSET");
    assertThat(sql).doesNotContain("COLLATE");
    assertThat(sql).doesNotContain("COMMENT=");
    assertThat(extractCreateTableBlock(sql, "final_record"))
            .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
            .contains("`student_user_id` BIGINT NOT NULL")
            .contains("`academic_year` VARCHAR(32) NOT NULL")
            .contains("`status` VARCHAR(32) NOT NULL")
            .contains("`moral_total` DECIMAL(10,2) NOT NULL")
            .contains("`intellectual_total` DECIMAL(10,2) NOT NULL")
            .contains("`physical_total` DECIMAL(10,2) NOT NULL")
            .contains("`labor_total` DECIMAL(10,2) NOT NULL")
            .contains("`grand_total` DECIMAL(10,2) NOT NULL")
            .contains("`submitted_at` DATETIME DEFAULT NULL")
            .contains("`confirmed_at` DATETIME DEFAULT NULL")
            .contains("`confirm_comment` VARCHAR(1000) DEFAULT NULL")
            .contains("`version` BIGINT NOT NULL")
            .contains("`created_at` DATETIME NOT NULL")
            .contains("`updated_at` DATETIME NOT NULL")
            .contains("UNIQUE KEY `uk_final_record_student_year` (`student_user_id`, `academic_year`)")
            .contains("KEY `idx_final_record_student_user_id` (`student_user_id`)")
            .contains("KEY `idx_final_record_academic_year` (`academic_year`)")
            .contains("KEY `idx_final_record_status` (`status`)");
    assertThat(extractCreateTableBlock(sql, "final_component_score"))
            .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
            .contains("`final_record_id` BIGINT NOT NULL")
            .contains("`category_code` VARCHAR(64) NOT NULL")
            .contains("`item_code` VARCHAR(64) NOT NULL")
            .contains("`score_value` DECIMAL(10,2) NOT NULL")
            .contains("`display_text` VARCHAR(1000) DEFAULT NULL")
            .contains("`source_type` VARCHAR(32) NOT NULL")
            .contains("`source_ref_id` VARCHAR(64) DEFAULT NULL")
            .contains("`created_at` DATETIME NOT NULL")
            .contains("KEY `idx_final_component_score_record_id` (`final_record_id`)")
            .contains("KEY `idx_final_component_score_category_code` (`category_code`)")
            .contains("KEY `idx_final_component_score_item_code` (`item_code`)");
}

@Test
void shouldRerunDGroupSafeInitSqlWithoutOverwritingRuntimeFinalRecords() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:d_group_safe_init;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
        executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));
        connection.createStatement().executeUpdate("""
                INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total,
                    labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
                VALUES (1001, '2025-2026', 'SUBMITTED', 0.80, 5.00, 0.60, 1.20, 7.60,
                    '2026-07-07 12:00:00', NULL, NULL, 1, '2026-07-07 12:00:00', '2026-07-07 12:00:00')
                """);
        long recordId = singleLong(connection, "SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'");
        connection.createStatement().executeUpdate("""
                INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at)
                VALUES (%d, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', 2.00, 'runtime component', 'APPLICATION', '21013', '2026-07-07 12:00:00')
                """.formatted(recordId));

        executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));

        assertThat(countRows(connection, "final_record", "id = " + recordId)).isEqualTo(1);
        assertThat(singleString(connection, "SELECT status FROM final_record WHERE id = " + recordId)).isEqualTo("SUBMITTED");
        assertThat(singleString(connection, "SELECT display_text FROM final_component_score WHERE final_record_id = " + recordId))
                .isEqualTo("runtime component");
    }
}
```

- [ ] **Step 2: Write failing D permission seed tests**

Add tests that run the D safe-init together with minimal IAM tables. Use explicit inserts for `iam_role`, `iam_user_role_assignment`, and existing scope rows only when needed by foreign keys. The assertions must prove three branches:

```java
@Test
void shouldSeedScoreConfirmPermissionUsingExistingNaturalKeyWhenIdDiffers() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:d_confirm_permission_existing_code;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
        createMinimalIamTables(connection);
        connection.createStatement().executeUpdate("""
                INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status)
                VALUES (9099, 'score.confirm.assigned', '已有确认权限', 'score', 'ACTIVE')
                """);

        executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));

        assertThat(singleLong(connection, "SELECT id FROM iam_permission WHERE permission_code = 'score.confirm.assigned'"))
                .isEqualTo(9099L);
        assertThat(countRows(connection, "iam_role_permission", "role_id = 4003 AND permission_id = 9099")).isEqualTo(1);
        assertThat(countRows(connection, "iam_role_permission", "role_id = 4004 AND permission_id = 9099")).isEqualTo(1);
        assertThat(countRows(connection, "iam_role_permission", "permission_id = 5023")).isEqualTo(0);
    }
}

@Test
void shouldRerunDPermissionSeedWithoutDuplicateBindings() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:d_confirm_permission_rerun;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
        createMinimalIamTables(connection);

        executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));
        executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));

        long permissionId = singleLong(connection, "SELECT id FROM iam_permission WHERE permission_code = 'score.confirm.assigned'");
        assertThat(permissionId).isEqualTo(5023L);
        assertThat(countRows(connection, "iam_permission", "permission_code = 'score.confirm.assigned'")).isEqualTo(1);
        assertThat(countRows(connection, "iam_role_permission", "role_id = 4003 AND permission_id = " + permissionId)).isEqualTo(1);
        assertThat(countRows(connection, "iam_role_permission", "role_id = 4004 AND permission_id = " + permissionId)).isEqualTo(1);
        assertThat(countRows(connection, "iam_scope_rule", "assignment_id = 7010 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002")).isEqualTo(1);
        assertThat(countRows(connection, "iam_scope_rule", "assignment_id = 7011 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002")).isEqualTo(1);
    }
}

@Test
void shouldFailDPermissionSeedWhenReservedIdsAreOccupiedByUnrelatedRows() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:d_confirm_permission_collision;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
        createMinimalIamTables(connection);
        connection.createStatement().executeUpdate("""
                INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status)
                VALUES (5023, 'unrelated.permission', '占用固定编号', 'test', 'ACTIVE')
                """);

        assertThatThrownBy(() -> executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL)))
                .hasMessageContaining("5023");
    }
}
```

Add a helper method in `TeamDeliverySqlConsistencyTest`:

```java
private static void createMinimalIamTables(Connection connection) throws Exception {
    executeStatements(connection, """
            CREATE TABLE iam_permission (
              id BIGINT NOT NULL,
              permission_code VARCHAR(128) NOT NULL,
              permission_name VARCHAR(128) NOT NULL,
              permission_group VARCHAR(64) NOT NULL,
              status VARCHAR(32) NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_iam_permission_code (permission_code)
            );
            CREATE TABLE iam_role_permission (
              id BIGINT NOT NULL,
              role_id BIGINT NOT NULL,
              permission_id BIGINT NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_iam_role_permission_role_permission (role_id, permission_id)
            );
            CREATE TABLE iam_scope_rule (
              id BIGINT NOT NULL,
              assignment_id BIGINT NOT NULL,
              permission_code VARCHAR(128) NOT NULL,
              scope_type VARCHAR(64) NOT NULL,
              org_unit_id BIGINT DEFAULT NULL,
              category_code VARCHAR(64) DEFAULT NULL,
              item_code VARCHAR(64) DEFAULT NULL,
              expression_json VARCHAR(2000) DEFAULT NULL,
              priority INT NOT NULL,
              status VARCHAR(32) NOT NULL,
              PRIMARY KEY (id),
              UNIQUE KEY uk_iam_scope_rule_natural (assignment_id, permission_code, scope_type, org_unit_id)
            );
            """);
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -Dtest=TeamDeliverySqlConsistencyTest#shouldProvideNonDestructiveDGroupSafeInitSql,TeamDeliverySqlConsistencyTest#shouldRerunDGroupSafeInitSqlWithoutOverwritingRuntimeFinalRecords,TeamDeliverySqlConsistencyTest#shouldSeedScoreConfirmPermissionUsingExistingNaturalKeyWhenIdDiffers,TeamDeliverySqlConsistencyTest#shouldRerunDPermissionSeedWithoutDuplicateBindings,TeamDeliverySqlConsistencyTest#shouldFailDPermissionSeedWhenReservedIdsAreOccupiedByUnrelatedRows -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the D safe-init file and seed logic do not exist.

- [ ] **Step 4: Add the permission constant and seed alignment assertion**

Add to `AuthorizationPermissionCodes` after `SCORE_EXPORT_ASSIGNED` only if it is not already present:

```java
public static final String SCORE_CONFIRM_ASSIGNED = "score.confirm.assigned";
```

Also keep these existing final-record self-permission constants unchanged:

```java
public static final String FINAL_SUBMIT_SELF = "final.submit.self";
public static final String FINAL_VIEW_SELF = "final.view.self";
```

Add to `GroupAIdentitySqlSeedConsistencyTest` focused assertions for ownership boundaries:

```java
@Test
void shouldKeepScoreConfirmAssignedOutOfAGroupIdentitySeed() throws Exception {
    String groupASql = Files.readString(TEAM_DELIVERY.resolve("group-a-identity-user-admin.sql"));

    assertThat(AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED).isEqualTo("score.confirm.assigned");
    assertThat(groupASql).doesNotContain("score.confirm.assigned");
}

@Test
void shouldKeepFinalSelfPermissionsOwnedByAGroupIdentitySeed() throws Exception {
    String groupASql = Files.readString(TEAM_DELIVERY.resolve("group-a-identity-user-admin.sql"));
    Path dGroupSafeInitSql = TEAM_DELIVERY.resolve("group-d-score-finalization-import-export.safe-init.sql");
    String dSafeInitSql = Files.exists(dGroupSafeInitSql) ? Files.readString(dGroupSafeInitSql) : "";

    assertThat(AuthorizationPermissionCodes.FINAL_SUBMIT_SELF).isEqualTo("final.submit.self");
    assertThat(AuthorizationPermissionCodes.FINAL_VIEW_SELF).isEqualTo("final.view.self");
    assertThat(groupASql).contains("final.submit.self").contains("final.view.self");
    assertThat(dSafeInitSql).doesNotContain("final.submit.self").doesNotContain("final.view.self");
}
```

- [ ] **Step 5: Add D safe-init SQL**

Create `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql` with table creation first, then permission collision checks, then idempotent inserts. Use H2/MySQL-compatible statements only.

The table DDL must be:

```sql
CREATE TABLE IF NOT EXISTS `final_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_user_id` BIGINT NOT NULL,
  `academic_year` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `moral_total` DECIMAL(10,2) NOT NULL,
  `intellectual_total` DECIMAL(10,2) NOT NULL,
  `physical_total` DECIMAL(10,2) NOT NULL,
  `labor_total` DECIMAL(10,2) NOT NULL,
  `grand_total` DECIMAL(10,2) NOT NULL,
  `submitted_at` DATETIME DEFAULT NULL,
  `confirmed_at` DATETIME DEFAULT NULL,
  `confirm_comment` VARCHAR(1000) DEFAULT NULL,
  `version` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_final_record_student_year` (`student_user_id`, `academic_year`),
  KEY `idx_final_record_student_user_id` (`student_user_id`),
  KEY `idx_final_record_academic_year` (`academic_year`),
  KEY `idx_final_record_status` (`status`)
);

CREATE TABLE IF NOT EXISTS `final_component_score` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `final_record_id` BIGINT NOT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `item_code` VARCHAR(64) NOT NULL,
  `score_value` DECIMAL(10,2) NOT NULL,
  `display_text` VARCHAR(1000) DEFAULT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `source_ref_id` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_final_component_score_record_id` (`final_record_id`),
  KEY `idx_final_component_score_category_code` (`category_code`),
  KEY `idx_final_component_score_item_code` (`item_code`)
);
```

For permission seed logic, implement this exact behavior:

```sql
-- D safe-init owns only the new admin confirmation permission.
-- Student final-record self permissions are already owned by A-group identity SQL:
--   5012 final.submit.self, 5013 final.view.self, 8004/8005 SELF scope rules.
-- Do not duplicate final.submit.self or final.view.self here.

-- Reserved id collision checks. These SELECTs deliberately fail with division by zero in H2/MySQL mode when a reserved id is occupied by an unrelated natural key.
SELECT CASE WHEN EXISTS (SELECT 1 FROM iam_permission WHERE id = 5023 AND permission_code <> 'score.confirm.assigned') THEN 1 / 0 ELSE 1 END;
SELECT CASE WHEN EXISTS (SELECT 1 FROM iam_role_permission WHERE id = 6048 AND NOT EXISTS (
  SELECT 1 FROM iam_permission p WHERE p.permission_code = 'score.confirm.assigned' AND p.id = iam_role_permission.permission_id AND iam_role_permission.role_id = 4003
)) THEN 1 / 0 ELSE 1 END;
SELECT CASE WHEN EXISTS (SELECT 1 FROM iam_role_permission WHERE id = 6049 AND NOT EXISTS (
  SELECT 1 FROM iam_permission p WHERE p.permission_code = 'score.confirm.assigned' AND p.id = iam_role_permission.permission_id AND iam_role_permission.role_id = 4004
)) THEN 1 / 0 ELSE 1 END;
SELECT CASE WHEN EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = 8019 AND NOT (assignment_id = 7010 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002)) THEN 1 / 0 ELSE 1 END;
SELECT CASE WHEN EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = 8020 AND NOT (assignment_id = 7011 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002)) THEN 1 / 0 ELSE 1 END;

INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status)
SELECT 5023, 'score.confirm.assigned', '确认授权范围最终成绩', 'score', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM iam_permission WHERE permission_code = 'score.confirm.assigned');

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT 6048, 4003, p.id
FROM iam_permission p
WHERE p.permission_code = 'score.confirm.assigned'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = 4003 AND rp.permission_id = p.id
  );

INSERT INTO iam_role_permission (id, role_id, permission_id)
SELECT 6049, 4004, p.id
FROM iam_permission p
WHERE p.permission_code = 'score.confirm.assigned'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = 4004 AND rp.permission_id = p.id
  );

INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status)
SELECT 8019, 7010, 'score.confirm.assigned', 'ORG_SUBTREE', 2002, NULL, NULL, '{"scoreRole":"counselor"}', 80, 'ACTIVE'
WHERE NOT EXISTS (
  SELECT 1 FROM iam_scope_rule
  WHERE assignment_id = 7010 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002
);

INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status)
SELECT 8020, 7011, 'score.confirm.assigned', 'ORG_SUBTREE', 2002, NULL, NULL, '{"scoreRole":"college_reviewer"}', 70, 'ACTIVE'
WHERE NOT EXISTS (
  SELECT 1 FROM iam_scope_rule
  WHERE assignment_id = 7011 AND permission_code = 'score.confirm.assigned' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002
);
```

- [ ] **Step 6: Run tests to verify they pass**

Run:

```bash
mvn -pl whut-eval-app -Dtest=TeamDeliverySqlConsistencyTest,GroupAIdentitySqlSeedConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java whut-eval-app/src/test/java/edu/whut/eval/app/iam/GroupAIdentitySqlSeedConsistencyTest.java
git commit -m "feat: add minimal d safe init seed"
```

---

### Task 2: Final Record Aggregate and Window Policy

**Files:**
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/model/FinalRecordStatus.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/model/FinalComponentScore.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/model/FinalRecord.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service/FinalSubmissionWindowPolicy.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service/NoopFinalSubmissionWindowPolicy.java`
- Create: `whut-eval-common/src/main/java/edu/whut/eval/common/exception/SubmitWindowClosedException.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordStateMachineTest.java`

- [ ] **Step 1: Write failing state-machine tests**

Create `FinalRecordStateMachineTest.java` with tests that assert:

```java
@Test
void shouldSubmitDraftAndIncrementVersion() {
    FinalRecord draft = draftRecord();

    FinalRecord submitted = draft.submit(0L);

    assertThat(submitted.getStatus()).isEqualTo(FinalRecordStatus.SUBMITTED);
    assertThat(submitted.getVersion()).isEqualTo(1L);
    assertThat(submitted.getSubmittedAt()).isNotNull();
    assertThat(submitted.getCreatedAt()).isEqualTo(draft.getCreatedAt());
    assertThat(submitted.getUpdatedAt()).isAfterOrEqualTo(draft.getUpdatedAt());
    assertThat(draft.getStatus()).isEqualTo(FinalRecordStatus.DRAFT);
    assertThat(draft.getVersion()).isEqualTo(0L);
    assertThat(draft.getSubmittedAt()).isNull();
}

@Test
void shouldConfirmSubmittedRecordWithOptionalComment() {
    FinalRecord submitted = draftRecord().submit(0L);

    FinalRecord confirmed = submitted.confirm(1L, "辅导员已复核，无异议");

    assertThat(confirmed.getStatus()).isEqualTo(FinalRecordStatus.CONFIRMED);
    assertThat(confirmed.getVersion()).isEqualTo(2L);
    assertThat(confirmed.getConfirmedAt()).isNotNull();
    assertThat(confirmed.getConfirmComment()).isEqualTo("辅导员已复核，无异议");
    assertThat(submitted.getStatus()).isEqualTo(FinalRecordStatus.SUBMITTED);
    assertThat(submitted.getVersion()).isEqualTo(1L);
    assertThat(submitted.getConfirmedAt()).isNull();
}

@Test
void shouldRejectInvalidTransitionsAndVersionMismatches() {
    FinalRecord draft = draftRecord();
    FinalRecord submitted = draft.submit(0L);
    FinalRecord confirmed = submitted.confirm(1L, null);

    assertThatThrownBy(() -> draft.confirm(0L, null)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> submitted.submit(1L)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> confirmed.confirm(2L, null)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> draft.submit(9L)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> submitted.confirm(9L, null)).isInstanceOf(ConflictException.class);
}
```

Add `draftRecord()` with known totals:

```java
private FinalRecord draftRecord() {
    Instant now = Instant.parse("2026-07-07T12:00:00Z");
    return FinalRecord.createDraft(
            null,
            1001L,
            "2025-2026",
            new BigDecimal("0.80"),
            new BigDecimal("5.00"),
            new BigDecimal("0.60"),
            new BigDecimal("1.20"),
            new BigDecimal("7.60"),
            now
    );
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=FinalRecordStateMachineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the final-record domain classes do not exist.

- [ ] **Step 3: Implement the aggregate and policy classes**

Create `FinalRecordStatus`:

```java
package edu.whut.eval.domain.finalrecord.model;

public enum FinalRecordStatus {
    DRAFT,
    SUBMITTED,
    CONFIRMED
}
```

Create `FinalComponentScore` as an immutable value object with fields:

```java
Long id;
Long finalRecordId;
String categoryCode;
String itemCode;
BigDecimal scoreValue;
String displayText;
String sourceType;
String sourceRefId;
Instant createdAt;
```

Create `FinalRecord` with:

```java
public static FinalRecord createDraft(Long id,
                                      Long studentUserId,
                                      String academicYear,
                                      BigDecimal moralTotal,
                                      BigDecimal intellectualTotal,
                                      BigDecimal physicalTotal,
                                      BigDecimal laborTotal,
                                      BigDecimal grandTotal,
                                      Instant now)
```

`createDraft(...)` must set `status = DRAFT`, `version = 0L`, `submittedAt = null`, `confirmedAt = null`, `confirmComment = null`, and both `createdAt` / `updatedAt` to `now`.

and transition methods:

```java
public FinalRecord submit(long expectedVersion) {
    assertVersion(expectedVersion);
    if (status != FinalRecordStatus.DRAFT) {
        throw new ConflictException("最终成绩只能从草稿状态提交");
    }
    Instant now = Instant.now();
    return new FinalRecord(id, studentUserId, academicYear, FinalRecordStatus.SUBMITTED,
            moralTotal, intellectualTotal, physicalTotal, laborTotal, grandTotal,
            now, confirmedAt, confirmComment, version + 1, createdAt, now);
}

public FinalRecord confirm(long expectedVersion, String confirmComment) {
    assertVersion(expectedVersion);
    if (status != FinalRecordStatus.SUBMITTED) {
        throw new ConflictException("只能确认已提交的最终成绩");
    }
    Instant now = Instant.now();
    return new FinalRecord(id, studentUserId, academicYear, FinalRecordStatus.CONFIRMED,
            moralTotal, intellectualTotal, physicalTotal, laborTotal, grandTotal,
            submittedAt, now, confirmComment, version + 1, createdAt, now);
}
```

Create `FinalSubmissionWindowPolicy`:

```java
package edu.whut.eval.domain.finalrecord.service;

import java.time.Instant;

public interface FinalSubmissionWindowPolicy {
    void assertSubmitAllowed(long studentUserId, String academicYear, Instant now);
}
```

Create `NoopFinalSubmissionWindowPolicy`:

```java
package edu.whut.eval.domain.finalrecord.service;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NoopFinalSubmissionWindowPolicy implements FinalSubmissionWindowPolicy {
    @Override
    public void assertSubmitAllowed(long studentUserId, String academicYear, Instant now) {
        // Minimal D only installs the extension point; platform-window enforcement is deferred.
    }
}
```

Create `SubmitWindowClosedException`:

```java
package edu.whut.eval.common.exception;

public class SubmitWindowClosedException extends ConflictException {
    public SubmitWindowClosedException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl whut-eval-app -Dtest=FinalRecordStateMachineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/model whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service whut-eval-common/src/main/java/edu/whut/eval/common/exception/SubmitWindowClosedException.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordStateMachineTest.java
git commit -m "feat: add final record aggregate"
```

---

### Task 3: Whole-Record Scope Evaluation

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/model/FinalRecordResourceContext.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/ResourceScopeAccessEvaluator.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultResourceScopeAccessEvaluator.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service/FinalRecordScopePredicateBuilder.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordScopePredicateBuilderTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultResourceScopeAccessEvaluatorTest.java`

- [ ] **Step 1: Write failing final-record scope tests**

Add tests proving supported and unsupported scope behavior:

```java
@Test
void shouldAllowFinalRecordOnlyForAllOrgUnitAndOrgSubtreeScopes() {
    UserAuthorizationContext admin = authorizationContextWithScopes(
            "score.view.assigned",
            List.of(
                    scope("score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null),
                    scope("score.view.assigned", "ITEM", null, "INTELLECTUAL", "INTELLECTUAL_PAPER"),
                    scope("score.view.assigned", "ORG_SUBTREE", 2002L, null, null)
            )
    );

    ScopeAccessDecision decision = evaluator.canAccessFinalRecord(
            admin,
            "score.view.assigned",
            new FinalRecordResourceContext(41001L, 1001L, 2010L, "/2001/2002/2010/", "2025-2026")
    );

    assertThat(decision.isAllowed()).isTrue();
    assertThat(decision.getMatchedScopeType()).isEqualTo("ORG_SUBTREE");
}

@Test
void shouldDenyFinalRecordForCategoryItemAndCustomExpressionOnlyScopes() {
    UserAuthorizationContext admin = authorizationContextWithScopes(
            "score.view.assigned",
            List.of(
                    scope("score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null),
                    scope("score.view.assigned", "ITEM", null, "INTELLECTUAL", "INTELLECTUAL_PAPER"),
                    scope("score.view.assigned", "CUSTOM_EXPRESSION", 2010L, null, null)
            )
    );

    ScopeAccessDecision decision = evaluator.canAccessFinalRecord(
            admin,
            "score.view.assigned",
            new FinalRecordResourceContext(41001L, 1001L, 2010L, "/2001/2002/2010/", "2025-2026")
    );

    assertThat(decision.isAllowed()).isFalse();
}

@Test
void shouldExposeOnlyFinalRecordSafeFieldsInResourceContext() {
    FinalRecordResourceContext context = new FinalRecordResourceContext(41001L, 1001L, 2010L, "/2001/2002/2010/", "2025-2026");

    assertThat(context.getOwnerUserId()).isEqualTo(1001L);
    assertThat(context.getCategoryCode()).isNull();
    assertThat(context.getItemCode()).isNull();
    assertThat(context.getFieldValue("finalRecordId")).isEqualTo(41001L);
    assertThat(context.getFieldValue("studentUserId")).isEqualTo(1001L);
    assertThat(context.getFieldValue("ownerUserId")).isEqualTo(1001L);
    assertThat(context.getFieldValue("unknown")).isNull();
}
```

Create `FinalRecordScopePredicateBuilderTest`:

```java
@Test
void shouldBuildAllowAllPredicateForAllScope() {
    FinalRecordScopePredicateBuilder builder = new FinalRecordScopePredicateBuilder();
    AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("score.view.assigned", List.of(
            new AuthorizationScope("score.view.assigned", "ALL", null, null, null, null, 100)
    ));

    ApplicationScopePredicate predicate = builder.buildForFinalRecord(context(), scopeSet);

    assertThat(predicate.isGranted()).isTrue();
    assertThat(predicate.isEmptyResult()).isFalse();
    assertThat(predicate.getClauses()).isEmpty();
}

@Test
void shouldBuildPredicateFromOnlyWholeRecordScopes() {
    FinalRecordScopePredicateBuilder builder = new FinalRecordScopePredicateBuilder();
    UserAuthorizationContext admin = context();
    AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("score.view.assigned", List.of(
            new AuthorizationScope("score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null, null, 90),
            new AuthorizationScope("score.view.assigned", "ORG_UNIT", 2010L, null, null, null, 80),
            new AuthorizationScope("score.view.assigned", "ORG_SUBTREE", 2002L, null, null, "{\"scoreRole\":\"counselor\"}", 70)
    ));

    ApplicationScopePredicate predicate = builder.buildForFinalRecord(admin, scopeSet);

    assertThat(predicate.isGranted()).isTrue();
    assertThat(predicate.isEmptyResult()).isFalse();
    assertThat(predicate.getClauses()).hasSize(2);
    assertThat(predicate.getClauses()).extracting(ApplicationScopeClause::getOrgUnitId).contains(2010L);
    assertThat(predicate.getClauses()).extracting(ApplicationScopeClause::getOrgSubtreeRootId).contains(2002L);
}

@Test
void shouldReturnEmptyResultPredicateForUnsupportedScopesOnly() {
    FinalRecordScopePredicateBuilder builder = new FinalRecordScopePredicateBuilder();
    AuthorizationScopeSet scopeSet = AuthorizationScopeSet.granted("score.view.assigned", List.of(
            new AuthorizationScope("score.view.assigned", "CATEGORY", null, "INTELLECTUAL", null, null, 90),
            new AuthorizationScope("score.view.assigned", "ITEM", null, "INTELLECTUAL", "INTELLECTUAL_PAPER", null, 80)
    ));

    ApplicationScopePredicate predicate = builder.buildForFinalRecord(context(), scopeSet);

    assertThat(predicate.isGranted()).isTrue();
    assertThat(predicate.isEmptyResult()).isTrue();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -Dtest=DefaultResourceScopeAccessEvaluatorTest,FinalRecordScopePredicateBuilderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because final-record scope classes and evaluator method do not exist.

- [ ] **Step 3: Implement final-record resource context and evaluator**

Create `FinalRecordResourceContext` implementing `ScopeResourceContext`. The getters must return:

```java
getOwnerUserId() -> studentUserId
getOrgUnitId() -> orgUnitId
getOrgPath() -> orgPath
getCategoryCode() -> null
getItemCode() -> null
getFieldValue("finalRecordId") -> finalRecordId
getFieldValue("studentUserId") -> studentUserId
getFieldValue("ownerUserId") -> studentUserId
getFieldValue("orgUnitId") -> orgUnitId
getFieldValue("orgPath") -> orgPath
getFieldValue("academicYear") -> academicYear
unknown field -> null
```

Add to `ResourceScopeAccessEvaluator`:

```java
ScopeAccessDecision canAccessFinalRecord(UserAuthorizationContext authorizationContext,
                                         String permissionCode,
                                         FinalRecordResourceContext resourceContext);
```

In `DefaultResourceScopeAccessEvaluator`, implement `canAccessFinalRecord(...)` through a dedicated matcher that:

```java
if (!scopeSet.isGranted()) deny;
if (scopeSet.allowsAll()) allow;
for each scope:
  ALL -> allow
  ORG_UNIT -> match non-null resource orgUnitId exactly
  ORG_SUBTREE -> match non-null resource orgPath containing "/" + scope.orgUnitId + "/"
  every other scope -> false
```

Do not call `matchesCategoryAndItem(...)` for final records. Do not evaluate `CUSTOM_EXPRESSION` for final records.

- [ ] **Step 4: Implement final-record scope predicate builder**

Create `FinalRecordScopePredicateBuilder` under `edu.whut.eval.domain.finalrecord.service`:

```java
@Service
public class FinalRecordScopePredicateBuilder {
    public ApplicationScopePredicate buildForFinalRecord(UserAuthorizationContext authorizationContext,
                                                        AuthorizationScopeSet scopeSet) {
        if (!scopeSet.isGranted()) {
            return ApplicationScopePredicate.denied(scopeSet.getPermissionCode());
        }
        if (scopeSet.allowsAll()) {
            return ApplicationScopePredicate.allowAll(scopeSet.getPermissionCode());
        }
        List<ApplicationScopeClause> clauses = new ArrayList<>();
        for (AuthorizationScope scope : scopeSet.getScopes()) {
            String scopeType = normalize(scope.getScopeType());
            if ("ORG_UNIT".equals(scopeType)) {
                clauses.add(new ApplicationScopeClause(scopeType, null, scope.getOrgUnitId(), null, null, null, null, null, null));
            } else if ("ORG_SUBTREE".equals(scopeType)) {
                clauses.add(new ApplicationScopeClause(scopeType, null, null, scope.getOrgUnitId(), null, null, null, null, null));
            }
        }
        if (clauses.isEmpty()) {
            return ApplicationScopePredicate.restricted(scopeSet.getPermissionCode(), List.of());
        }
        return ApplicationScopePredicate.restricted(scopeSet.getPermissionCode(), clauses);
    }
}
```

Unsupported scope types are not authorization failure for list predicates. When the user has the permission but only unsupported scopes (`CATEGORY`, `ITEM`, `CUSTOM_EXPRESSION`, etc.), `ApplicationScopePredicate.restricted(permissionCode, List.of())` is the granted empty-result predicate (`isGranted() == true`, `isEmptyResult() == true`) so admin list returns `200` with an empty `PageResult`. Detail/confirm still use `canAccessFinalRecord(...)` and return `403` when no whole-record scope matches.

Use the actual `ApplicationScopeClause` constructor signature from `whut-eval-domain/src/main/java/edu/whut/eval/domain/auth/model/ApplicationScopeClause.java`; the important field mapping is `orgUnitId` for `ORG_UNIT` and `orgSubtreeRootId` for `ORG_SUBTREE`.

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
mvn -pl whut-eval-app -Dtest=DefaultResourceScopeAccessEvaluatorTest,FinalRecordScopePredicateBuilderTest,ScopeSqlTranslatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, including existing score/application translator tests.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/auth/model/FinalRecordResourceContext.java whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/ResourceScopeAccessEvaluator.java whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/DefaultResourceScopeAccessEvaluator.java whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service/FinalRecordScopePredicateBuilder.java whut-eval-app/src/test/java/edu/whut/eval/app/security/DefaultResourceScopeAccessEvaluatorTest.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordScopePredicateBuilderTest.java
git commit -m "feat: add final record scope evaluation"
```

---

### Task 4: Persistence Model, Aggregation, and Command Repository

**Files:**
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/repository/FinalRecordRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/FinalRecordDO.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/FinalComponentScoreDO.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalComponentScoreMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordAggregationMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordRepository.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing repository integration tests**

Create tests proving:

- only `APPROVED` submissions are read as source rows;
- an approved submission with no `application_fact` row fails aggregation with `ConflictException`;
- zero approved submissions for the student/year fails aggregation with `ConflictException`;
- `application_fact.score_value IS NULL` fails with `ConflictException`;
- unsupported `category_code` fails with `ConflictException`;
- successful aggregation partitions totals and inserts ordered components;
- defensive delete-before-insert leaves no duplicate components;
- a duplicate `(student_user_id, academic_year)` create maps to `ConflictException`.

Use seed rows based on the existing B safe-init schema:

```java
@Test
void shouldAggregateApprovedFactsIntoSubmittedRecord() {
    long sportsId = insertApplication(1001L, 2010L, "SPORTS", "SPORTS_COMPETITION", "2025-2026", "APPROVED");
    insertFact(sportsId, "0.60", "体育竞赛已审核通过");
    long paperId = insertApplication(1001L, 2010L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2025-2026", "APPROVED");
    insertFact(paperId, "2.00", "论文已审核通过");
    long laborId = insertApplication(1001L, 2010L, "LABOR", "LABOR_SERVICE", "2025-2026", "APPROVED");
    insertFact(laborId, "1.20", "劳动实践已审核通过");
    long draftId = insertApplication(1001L, 2010L, "MORAL", "MORAL_HONOR", "2025-2026", "DRAFT");
    insertFact(draftId, "9.99", "草稿不得进入最终成绩");

    AggregatedFinalRecordSnapshot snapshot = repository.aggregateApprovedFacts(1001L, "2025-2026");

    assertThat(snapshot.components()).extracting(FinalComponentScore::getCategoryCode, FinalComponentScore::getItemCode)
            .containsExactly(
                    tuple("INTELLECTUAL", "INTELLECTUAL_PAPER"),
                    tuple("LABOR", "LABOR_SERVICE"),
                    tuple("SPORTS", "SPORTS_COMPETITION")
            );
    assertThat(snapshot.intellectualTotal()).isEqualByComparingTo("2.00");
    assertThat(snapshot.laborTotal()).isEqualByComparingTo("1.20");
    assertThat(snapshot.moralTotal()).isEqualByComparingTo("0.00");
    assertThat(snapshot.physicalTotal()).isEqualByComparingTo("0.60");
    assertThat(snapshot.grandTotal()).isEqualByComparingTo("3.80");
}
```

Add tests for failure branches:

```java
@Test
void shouldFailAggregationWhenNoApprovedSubmissionsExist() {
    insertApplication(1001L, 2010L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2025-2026", "DRAFT");

    assertThatThrownBy(() -> repository.aggregateApprovedFacts(1001L, "2025-2026"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("已审核申请");
}

@Test
void shouldFailAggregationWhenApprovedSubmissionHasNoFactRows() {
    insertApplication(1001L, 2010L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2025-2026", "APPROVED");

    assertThatThrownBy(() -> repository.aggregateApprovedFacts(1001L, "2025-2026"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("approved snapshot");
}

@Test
void shouldFailAggregationWhenApprovedFactScoreIsNull() {
    long applicationId = insertApplication(1001L, 2010L, "INTELLECTUAL", "INTELLECTUAL_PAPER", "2025-2026", "APPROVED");
    insertFact(applicationId, null, "分值缺失");

    assertThatThrownBy(() -> repository.aggregateApprovedFacts(1001L, "2025-2026"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("score");
}

@Test
void shouldFailAggregationWhenCategoryDoesNotBelongToFinalTotalPartition() {
    long applicationId = insertApplication(1001L, 2010L, "UNKNOWN", "UNKNOWN_ITEM", "2025-2026", "APPROVED");
    insertFact(applicationId, "1.00", "未知分类");

    assertThatThrownBy(() -> repository.aggregateApprovedFacts(1001L, "2025-2026"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("category");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusFinalRecordRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because repository classes do not exist.

- [ ] **Step 3: Implement command repository contracts**

Create `FinalRecordRepository` with methods:

```java
Optional<FinalRecord> findByStudentAndAcademicYear(long studentUserId, String academicYear);
Optional<FinalRecord> findById(long finalRecordId);
AggregatedFinalRecordSnapshot aggregateApprovedFacts(long studentUserId, String academicYear);
FinalRecord insertDraft(FinalRecord record);
void deleteDraft(long finalRecordId);
void deleteComponents(long finalRecordId);
void batchInsertComponents(long finalRecordId, List<FinalComponentScore> components);
FinalRecord updateTransition(FinalRecord record);
List<FinalComponentScore> listComponents(long finalRecordId);
```

Create an `AggregatedFinalRecordSnapshot` record in the same repository package:

```java
public record AggregatedFinalRecordSnapshot(
        BigDecimal moralTotal,
        BigDecimal intellectualTotal,
        BigDecimal physicalTotal,
        BigDecimal laborTotal,
        BigDecimal grandTotal,
        List<FinalComponentScore> components
) {
}
```

- [ ] **Step 4: Implement mappers and repository**

Use `application_submission.status = 'APPROVED'` and load approved ids first:

```java
List<Long> approvedIds = aggregationMapper.selectApprovedApplicationIds(studentUserId, academicYear);
if (approvedIds.isEmpty()) {
    throw new ConflictException("没有可汇总的已审核申请");
}
List<ApprovedApplicationFactRow> facts = aggregationMapper.selectApprovedFacts(approvedIds);
Set<Long> factApplicationIds = facts.stream().map(ApprovedApplicationFactRow::applicationId).collect(Collectors.toSet());
if (!factApplicationIds.containsAll(approvedIds)) {
    throw new ConflictException("approved snapshot incomplete");
}
```

Define `FinalRecordAggregationMapper` and `ApprovedApplicationFactRow` explicitly:

```java
List<Long> selectApprovedApplicationIds(@Param("studentUserId") long studentUserId,
                                        @Param("academicYear") String academicYear);

List<ApprovedApplicationFactRow> selectApprovedFacts(@Param("applicationIds") List<Long> applicationIds);

record ApprovedApplicationFactRow(
        long applicationId,
        String categoryCode,
        String itemCode,
        BigDecimal scoreValue,
        String displayText,
        String sourceType,
        String sourceRefId
) {
}
```

Validate:

```java
if (row.scoreValue() == null) throw new ConflictException("approved fact score missing");
if (!Set.of("MORAL", "INTELLECTUAL", "SPORTS", "LABOR").contains(row.categoryCode())) {
    throw new ConflictException("unsupported final record category: " + row.categoryCode());
}
```

Category-to-total mapping is fixed as:

| `categoryCode` | Header total field | Database column |
|---|---|---|
| `MORAL` | `moralTotal` | `moral_total` |
| `INTELLECTUAL` | `intellectualTotal` | `intellectual_total` |
| `SPORTS` | `physicalTotal` | `physical_total` |
| `LABOR` | `laborTotal` | `labor_total` |

`SPORTS -> physical_total` is intentional because the frozen D table uses the physical-education column name while B/C application facts use the category code `SPORTS`.

Map physical total from `SPORTS`:

```java
case "SPORTS" -> physicalTotal = physicalTotal.add(row.scoreValue());
```

After all components are partitioned, compute `grandTotal` as `moralTotal.add(intellectualTotal).add(physicalTotal).add(laborTotal)` and assert it equals the sum of all component `scoreValue` values. If the two totals differ, throw `ConflictException("final record total mismatch")` before inserting any row.

Use batch insert for components and order reads by:

```sql
ORDER BY category_code ASC, item_code ASC, id ASC
```

Catch duplicate-key or zero-update transition failures and rethrow `ConflictException`.

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusFinalRecordRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/repository whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/FinalRecordDO.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/FinalComponentScoreDO.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordMapper.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalComponentScoreMapper.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordAggregationMapper.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordRepository.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordRepositoryIntegrationTest.java
git commit -m "feat: add final record command repository"
```

---

### Task 5: Query Repository and Admin Scoped List/Detail

**Files:**
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/FinalRecordPageQuery.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/FinalRecordAccessContext.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalRecordQueryRow.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalComponentScoreRow.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing query repository tests**

Cover:

- student lookup returns only current student's record;
- absent student header returns `Optional.empty()`;
- components are stable ordered;
- admin list uses whole-record scope and `PageResult<T>` shape;
- unsupported-scope-only list returns empty page;
- admin detail loads unscoped by id for existence and resource-context construction through `findAdminFinalRecordDetail(recordId)`, then scoped access is enforced by the application-layer access validator;
- status filter accepts only `SUBMITTED` and `CONFIRMED`;
- `DRAFT` status in query throws `ValidationException`;
- blank keyword is ignored;
- keyword matches `student_user.user_no` or `student_user.user_name`;
- `pageSize` defaults to `20` and caps at `100`.

Example assertions:

```java
@Test
void shouldPageAdminRecordsWithinWholeRecordScope() {
    insertFinalRecord(41001L, 1001L, "2025-2026", "SUBMITTED", "2026-07-07 12:00:00");
    insertFinalRecord(41002L, 1002L, "2025-2026", "SUBMITTED", "2026-07-07 13:00:00");

    PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
            accessContextWithOrgSubtree(2002L),
            new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(2L);
    assertThat(page.records()).extracting(FinalRecordQueryRow::getFinalRecordId)
            .containsExactly(41002L, 41001L);
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusFinalRecordQueryRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because query repository classes do not exist.

- [ ] **Step 3: Implement query objects**

`FinalRecordPageQuery` must normalize:

```java
academicYear blank -> ValidationException
pageNo = pageNo <= 0 ? 1 : pageNo
pageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100)
keyword = null when blank
status allowed: null, SUBMITTED, CONFIRMED
DRAFT or unknown status -> ValidationException
offset = (pageNo - 1) * pageSize
```

`FinalRecordAccessContext` mirrors the exact field structure of `ApplicationAccessContext` and `ScoreAccessContext`:

```java
Long userId;
String userNo;
String userName;
String identity;
Set<String> roles;
Set<String> authorities;
List<IamScopeRule> scopeRules;
String permissionCode;
```

The constructor must defensively copy `roles`, `authorities`, and `scopeRules` the same way the existing access contexts do. `permissionCode` is the final-record permission being evaluated, e.g. `SCORE_VIEW_ASSIGNED` for admin list/detail and `SCORE_CONFIRM_ASSIGNED` for confirm access checks.

`FinalRecordQueryRepository` must expose exactly these methods; downstream tasks and tests must not introduce alternate names:

```java
Optional<FinalRecordQueryRow> findStudentFinalRecord(long studentUserId, String academicYear);
List<FinalComponentScoreRow> listStudentFinalRecordComponents(long finalRecordId);
PageResult<FinalRecordQueryRow> pageAdminFinalRecords(FinalRecordAccessContext accessContext, FinalRecordPageQuery query);
Optional<FinalRecordQueryRow> findAdminFinalRecordDetail(long finalRecordId);
List<FinalComponentScoreRow> listAdminFinalRecordComponents(long finalRecordId);
```

`FinalComponentScoreRow` must include:

```java
Long id;
Long finalRecordId;
String categoryCode;
String itemCode;
String itemName; // nullable; Minimal D returns null until E-group definitions provide names
BigDecimal scoreValue;
String displayText;
String sourceType;
String sourceRefId;
Instant createdAt;
```

`FinalComponentScoreView` mirrors these fields. Mapping from row to view must set `itemName` directly from the row; the Minimal D SQL must select `NULL AS item_name` and must not invent a name from `itemCode`.

- [ ] **Step 4: Implement query mapper and repository**

`MybatisPlusFinalRecordQueryRepository` must:

- build a `UserAuthorizationContext` from `FinalRecordAccessContext`;
- evaluate `AuthorizationScopeSet` using `AuthorizationScopeEvaluator`;
- build final-record predicate using `FinalRecordScopePredicateBuilder`;
- translate it with existing `ApplicationScopeSqlTranslator`;
- join `final_record` to current student identity and org binding tables used by A-group identity;
- use column aliases expected by `FinalRecordQueryRow`:
  - `final_record_id`
  - `student_user_id`
  - `student_user_no`
  - `student_user_name`
  - `org_unit_id`
  - `org_unit_name`
  - `org_path`
  - totals, timestamps, status, version.

The SQL provider must combine filters as:

```sql
WHERE final_record.academic_year = #{query.academicYear}
  AND (<scope predicate>)
  AND optional status
  AND optional orgUnitId as an extra AND condition after scope filtering; if the requested orgUnitId is outside the caller's authorized scope, the page naturally returns empty rather than throwing
  AND optional keyword on user_no/name
ORDER BY submitted_at DESC, final_record_id DESC
LIMIT #{limit} OFFSET #{offset}
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
mvn -pl whut-eval-app -Dtest=MybatisPlusFinalRecordQueryRepositoryIntegrationTest,ScopeSqlTranslatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalRecordQueryRow.java whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalComponentScoreRow.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java
git commit -m "feat: add final record scoped queries"
```

---

### Task 6: Application Services for Student Submit/Read and Admin Confirm

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/command/SubmitFinalRecordCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/command/ConfirmFinalRecordCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalRecordView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalComponentScoreView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalComponentScoreListView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/FinalRecordStudentView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/AdminFinalRecordListItemView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/AdminFinalRecordDetailView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/ConfirmFinalRecordResultView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordAccessValidator.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordQueryApplicationService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordCommandApplicationService.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordCommandApplicationServiceTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordCommandApplicationServiceIntegrationTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java`

- [ ] **Step 1: Write failing command-service tests**

Cover:

```java
@Test
void shouldInvokeSubmitWindowPolicyBeforeMutation() {
    SubmitFinalRecordCommand command = new SubmitFinalRecordCommand("2025-2026", 0L);
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext(1001L));
    given(repository.findByStudentAndAcademicYear(1001L, "2025-2026")).willReturn(Optional.empty());
    given(repository.aggregateApprovedFacts(1001L, "2025-2026")).willReturn(snapshot());

    service.submit(command);

    InOrder inOrder = inOrder(windowPolicy, repository);
    inOrder.verify(windowPolicy).assertSubmitAllowed(eq(1001L), eq("2025-2026"), any(Instant.class));
    inOrder.verify(repository).findByStudentAndAcademicYear(1001L, "2025-2026");
}

@Test
void shouldRejectSubmitWhenExpectedVersionMissingOrNotZeroForCreatePath() {
    assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand(" ", 0L)))
            .isInstanceOf(ValidationException.class);

    assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand("2025-2026", null)))
            .isInstanceOf(ValidationException.class);

    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext(1001L));
    given(repository.findByStudentAndAcademicYear(1001L, "2025-2026")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand("2025-2026", 1L)))
            .isInstanceOf(ConflictException.class);
}

@Test
void shouldRejectResubmitExistingSubmittedOrConfirmedRecord() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext(1001L));
    given(repository.findByStudentAndAcademicYear(1001L, "2025-2026"))
            .willReturn(Optional.of(existingSubmittedRecord()));

    assertThatThrownBy(() -> service.submit(new SubmitFinalRecordCommand("2025-2026", 1L)))
            .isInstanceOf(ConflictException.class);
}

@Test
void shouldCleanupStaleDraftBeforeRebuildingSubmission() {
    FinalRecord staleDraft = existingDraftRecord(41000L);
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(studentContext(1001L));
    given(repository.findByStudentAndAcademicYear(1001L, "2025-2026"))
            .willReturn(Optional.of(staleDraft));
    given(repository.aggregateApprovedFacts(1001L, "2025-2026")).willReturn(snapshot());

    service.submit(new SubmitFinalRecordCommand("2025-2026", 0L));

    InOrder inOrder = inOrder(repository);
    inOrder.verify(repository).deleteComponents(41000L);
    inOrder.verify(repository).deleteDraft(41000L);
    inOrder.verify(repository).aggregateApprovedFacts(1001L, "2025-2026");
}

@Test
void shouldConfirmSubmittedRecordAfterWholeRecordScopeCheck() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext());
    given(repository.findById(41001L)).willReturn(Optional.of(existingSubmittedRecord()));
    given(queryRepository.findAdminFinalRecordDetail(41001L)).willReturn(Optional.of(rowForStudentInScope()));
    given(accessEvaluator.canAccessFinalRecord(eq(adminContext()), eq("score.confirm.assigned"), any(FinalRecordResourceContext.class)))
            .willReturn(ScopeAccessDecision.allow("ORG_SUBTREE", "matched"));

    ConfirmFinalRecordResultView result = service.confirm(new ConfirmFinalRecordCommand(41001L, "辅导员已复核，无异议", 1L));

    assertThat(result.status()).isEqualTo(FinalRecordStatus.CONFIRMED);
    assertThat(result.confirmComment()).isEqualTo("辅导员已复核，无异议");
}

@Test
void shouldReturnNotFoundWhenConfirmTargetDoesNotExist() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext());
    given(repository.findById(41001L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirm(new ConfirmFinalRecordCommand(41001L, null, 1L)))
            .isInstanceOf(ResourceNotFoundException.class);
}

@Test
void shouldFailConfirmWhenProjectionIsIncomplete() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContext());
    given(repository.findById(41001L)).willReturn(Optional.of(existingSubmittedRecord()));
    given(queryRepository.findAdminFinalRecordDetail(41001L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirm(new ConfirmFinalRecordCommand(41001L, null, 1L)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("projection");
}
```

Add transaction-boundary integration coverage in `FinalRecordCommandApplicationServiceIntegrationTest`. This is a Spring service integration test, separate from the Mockito-focused `FinalRecordCommandApplicationServiceTest`. Force a failure after `insertDraft(...)` and before `updateTransition(...)`, for example by making `batchInsertComponents(...)` throw `DataIntegrityViolationException`, then assert the transaction rolls back the intermediate DRAFT row and component writes:

```java
assertThat(countRows(connection, "final_record", "student_user_id = 1001")).isZero();
assertThat(countRows(connection, "final_component_score", "source_ref_id = '21013'")).isZero();
```

Name the test `shouldRollbackDraftAndComponentsWhenSubmitFailsBeforeTransition`. This test is required because read paths intentionally hide DRAFT rows; a leaked durable DRAFT would otherwise block resubmission through the `(student_user_id, academic_year)` unique key while remaining invisible to the student UI.

Add a second transaction-boundary test named `shouldRollbackDraftAndComponentsWhenSubmitTransitionFails`. Let component insertion succeed, force `updateTransition(...)` to fail with an optimistic-lock / zero-update `ConflictException`, then assert both `final_record` and `final_component_score` are rolled back. This covers the failure branch after component insertion and before the final `SUBMITTED` transition commits.

- [ ] **Step 2: Write failing query-service tests**

Cover:

- student header `404` on absent record;
- student header `404` when a defensive fixture contains a `DRAFT` record, because Minimal D does not expose draft final records externally;
- student components `404` when header absent;
- admin list with unsupported-scope-only permission returns empty `PageResult`;
- admin detail out of scope returns `AccessDeniedAppException`, not `ResourceNotFoundException`;
- caller with no permission returns `AccessDeniedAppException`.

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -Dtest=FinalRecordCommandApplicationServiceTest,FinalRecordCommandApplicationServiceIntegrationTest,FinalRecordQueryApplicationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because application services do not exist.

- [ ] **Step 4: Implement command service**

`submit(...)` must:

```java
if (command.expectedVersion() == null) {
    throw new ValidationException("expectedVersion 不能为空");
}
if (command.academicYear() == null || command.academicYear().isBlank()) {
    throw new ValidationException("academicYear 不能为空");
}
UserAuthorizationContext student = userAuthorizationContextAssembler.requiredAuthorizationContext();
if (!student.hasAuthority(AuthorizationPermissionCodes.FINAL_SUBMIT_SELF)) {
    throw new AccessDeniedAppException("当前用户无最终成绩提交权限");
}
windowPolicy.assertSubmitAllowed(student.getUserId(), command.academicYear(), Instant.now());
Optional<FinalRecord> existing = repository.findByStudentAndAcademicYear(student.getUserId(), command.academicYear());
if (existing.isPresent()) {
    FinalRecord existingRecord = existing.get();
    if (existingRecord.getStatus() == FinalRecordStatus.DRAFT) {
        repository.deleteComponents(existingRecord.getId());
        repository.deleteDraft(existingRecord.getId());
    } else {
        throw new ConflictException("最终成绩已存在，不能重复汇总");
    }
}
if (command.expectedVersion() != 0L) {
    throw new ConflictException("首次提交 expectedVersion 必须为 0");
}
AggregatedFinalRecordSnapshot snapshot = repository.aggregateApprovedFacts(student.getUserId(), command.academicYear());
FinalRecord draft = FinalRecord.createDraft(null, student.getUserId(), command.academicYear(), snapshot totals, Instant.now());
FinalRecord inserted = repository.insertDraft(draft);
repository.deleteComponents(inserted.getId());
repository.batchInsertComponents(inserted.getId(), snapshot.components());
FinalRecord submitted = repository.updateTransition(inserted.submit(0L));
return toFinalRecordStudentView(submitted);
```

Annotate submit with `@Transactional`. The stale-DRAFT cleanup, fresh draft creation, component insertion, and `SUBMITTED` transition must commit or roll back as one unit. Do not create records in read methods.

`confirm(...)` must:

```java
expectedVersion null -> ValidationException
comment length > 1000 -> ValidationException
permission must be SCORE_CONFIRM_ASSIGNED
repository.findById(recordId) unscoped for existence
repository.findById(recordId) empty -> ResourceNotFoundException
queryRepository.findAdminFinalRecordDetail(recordId) for resource context
queryRepository.findAdminFinalRecordDetail(recordId) empty after findById succeeded -> ConflictException("final record projection incomplete")
accessValidator.requireAccess(admin, row, SCORE_CONFIRM_ASSIGNED)
record.confirm(expectedVersion, normalizedComment)
repository.updateTransition(confirmed)
return ConfirmFinalRecordResultView
```

Annotate confirm with `@Transactional`.

- [ ] **Step 5: Implement query service and access validator**

Student query:

```java
require FINAL_VIEW_SELF authority
find by current user + academicYear
absent -> ResourceNotFoundException
DRAFT -> ResourceNotFoundException; durable DRAFT rows are treated as stale internal data and are recoverable only through submit's stale-DRAFT cleanup path
components endpoint called when header record does not exist -> ResourceNotFoundException
```

Admin query:

```java
list: require SCORE_VIEW_ASSIGNED authority, build FinalRecordAccessContext, return PageResult<AdminFinalRecordListItemView>
detail: require SCORE_VIEW_ASSIGNED authority, load unscoped detail, absent -> ResourceNotFoundException, scope denied -> AccessDeniedAppException
```

`FinalRecordAccessValidator.requireAccess(...)` must build:

```java
new FinalRecordResourceContext(
    row.getFinalRecordId(),
    row.getStudentUserId(),
    row.getOrgUnitId(),
    row.getOrgPath(),
    row.getAcademicYear()
)
```

and call `canAccessFinalRecord(...)` using the passed permission code.

- [ ] **Step 6: Run tests to verify they pass**

Run:

```bash
mvn -pl whut-eval-app -Dtest=FinalRecordCommandApplicationServiceTest,FinalRecordCommandApplicationServiceIntegrationTest,FinalRecordQueryApplicationServiceTest,MybatisPlusFinalRecordRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordCommandApplicationServiceTest.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordCommandApplicationServiceIntegrationTest.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java
git commit -m "feat: add final record application services"
```

---

### Task 7: Student and Admin HTTP Controllers

**Files:**
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentFinalRecordController.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/request/SubmitFinalRecordRequest.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/request/ConfirmFinalRecordRequest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/StudentFinalRecordControllerWebMvcTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java`

- [ ] **Step 1: Write failing WebMvc and annotation tests**

Student WebMvc expected shapes:

```java
mockMvc.perform(get("/api/student/final-records/2025-2026"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.finalRecordId").value(41001))
        .andExpect(jsonPath("$.data.academicYear").value("2025-2026"))
        .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.data.confirmComment").doesNotExist());

mockMvc.perform(get("/api/student/final-records/2025-2026/components"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.components[0].categoryCode").value("INTELLECTUAL"))
        .andExpect(jsonPath("$.data.components[0].itemName").value(nullValue()));

mockMvc.perform(post("/api/student/final-records/submit")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"academicYear":"2025-2026","expectedVersion":0}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(1));
```

Admin WebMvc expected shapes:

```java
mockMvc.perform(get("/api/admin/final-records")
        .param("academicYear", "2025-2026")
        .param("status", "SUBMITTED")
        .param("pageNo", "1")
        .param("pageSize", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].finalRecordId").value(41001))
        .andExpect(jsonPath("$.data.pageNo").doesNotExist())
        .andExpect(jsonPath("$.data.pageSize").doesNotExist());

mockMvc.perform(get("/api/admin/final-records/41001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.record.confirmComment").isEmpty())
        .andExpect(jsonPath("$.data.student.studentUserId").value(1001))
        .andExpect(jsonPath("$.data.components").isArray());

mockMvc.perform(post("/api/admin/final-records/41001/confirm")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"comment":"辅导员已复核，无异议","expectedVersion":1}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.confirmComment").value("辅导员已复核，无异议"));
```

Annotation tests must assert exact `@PreAuthorize` strings:

```java
hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_VIEW_SELF)
hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_SUBMIT_SELF)
hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)
hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_CONFIRM_ASSIGNED)
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -Dtest=StudentFinalRecordControllerWebMvcTest,AdminFinalRecordControllerWebMvcTest,FinalRecordControllerSecurityAnnotationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because controllers do not exist.

- [ ] **Step 3: Implement student controller**

Create:

```java
@RestController
@RequestMapping("/api/student/final-records")
@Validated
public class StudentFinalRecordController {
    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_VIEW_SELF)")
    @GetMapping("/{academicYear}")
    public ApiResponse<FinalRecordStudentView> getFinalRecord(@PathVariable String academicYear) { ... }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_VIEW_SELF)")
    @GetMapping("/{academicYear}/components")
    public ApiResponse<FinalComponentScoreListView> listComponents(@PathVariable String academicYear) { ... }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).FINAL_SUBMIT_SELF)")
    @PostMapping("/submit")
    public ApiResponse<FinalRecordStudentView> submit(@Valid @RequestBody SubmitFinalRecordRequest request) { ... }
}
```

`SubmitFinalRecordRequest` fields:

```java
@NotBlank
private String academicYear;
@NotNull
private Long expectedVersion;
```

- [ ] **Step 4: Implement admin controller**

Create:

```java
@RestController
@RequestMapping("/api/admin/final-records")
@Validated
public class AdminFinalRecordController {
    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")
    @GetMapping
    public ApiResponse<PageResult<AdminFinalRecordListItemView>> pageFinalRecords(...) { ... }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")
    @GetMapping("/{recordId}")
    public ApiResponse<AdminFinalRecordDetailView> getFinalRecord(@PathVariable Long recordId) { ... }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_CONFIRM_ASSIGNED)")
    @PostMapping("/{recordId}/confirm")
    public ApiResponse<ConfirmFinalRecordResultView> confirm(@PathVariable Long recordId,
                                                             @Valid @RequestBody ConfirmFinalRecordRequest request) { ... }
}
```

`ConfirmFinalRecordRequest` fields:

```java
@Size(max = 1000)
private String comment;
@NotNull
private Long expectedVersion;
```

- [ ] **Step 5: Run tests to verify they pass**

Run:

```bash
mvn -pl whut-eval-app -Dtest=StudentFinalRecordControllerWebMvcTest,AdminFinalRecordControllerWebMvcTest,FinalRecordControllerSecurityAnnotationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentFinalRecordController.java whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/request/SubmitFinalRecordRequest.java whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/request/ConfirmFinalRecordRequest.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/StudentFinalRecordControllerWebMvcTest.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java
git commit -m "feat: add final record controllers"
```

---

### Task 8: Security Integration and End-to-End Regression

**Files:**
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java`
- Modify existing tests only if security configuration wiring needs additional controller imports.

- [ ] **Step 1: Write focused security integration tests**

Cover:

- unauthenticated student final-record read returns `401`;
- authenticated user without `AuthorizationPermissionCodes.FINAL_VIEW_SELF` (`final.view.self`) returns `403`;
- student with `AuthorizationPermissionCodes.FINAL_VIEW_SELF` (`final.view.self`) can read their own record;
- admin with `score.view.assigned` and only unsupported category/item scopes gets `200` empty page for list;
- same unsupported-scope-only admin gets `403` for detail;
- caller with no `score.view.assigned` gets `403` for list and detail;
- admin with `AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED` (`score.confirm.assigned`) and whole-record org scope can confirm;
- admin with `AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED` (`score.confirm.assigned`) but unsupported category/item scopes gets `403` for confirm.

Use the same `MockMvc` + security setup style as `ReviewApplicationSecurityIntegrationTest`.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -Dtest=FinalRecordSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL until the controller/service/security wiring is complete.

- [ ] **Step 3: Fix Spring wiring only where tests prove it is needed**

Concrete acceptable fixes:

- add missing constructor dependencies to test config;
- add mapper scanning for final-record mappers;
- add final-record safe-init SQL execution in final-record integration test setup;
- add controller import to WebMvc slice tests.

Do not weaken `@PreAuthorize` annotations to make tests pass.

- [ ] **Step 4: Run focused Minimal D suite**

Run:

```bash
mvn -pl whut-eval-app -Dtest=TeamDeliverySqlConsistencyTest,GroupAIdentitySqlSeedConsistencyTest,FinalRecordStateMachineTest,FinalRecordScopePredicateBuilderTest,DefaultResourceScopeAccessEvaluatorTest,MybatisPlusFinalRecordRepositoryIntegrationTest,MybatisPlusFinalRecordQueryRepositoryIntegrationTest,FinalRecordCommandApplicationServiceTest,FinalRecordCommandApplicationServiceIntegrationTest,FinalRecordQueryApplicationServiceTest,StudentFinalRecordControllerWebMvcTest,AdminFinalRecordControllerWebMvcTest,FinalRecordControllerSecurityAnnotationTest,FinalRecordSecurityIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 5: Run regression suites for dependent B/C/Auth behavior**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ApplicationSubmissionCommandApplicationServiceTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest,ReviewApplicationCommandApplicationServiceTest,ReviewApplicationQueryRepositoryIntegrationTest,ReviewApplicationSecurityIntegrationTest,ScopeAwareQueryRepositoryIntegrationTest,ScopeSqlTranslatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

- [ ] **Step 6: Run full verification**

Run:

```bash
mvn test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java
git commit -m "test: verify final record security flow"
```

---

## Execution Order and Ownership

Run tasks in order. Task 1 must land first because every integration test needs `final_record`, `final_component_score`, and `score.confirm.assigned`. Task 3 can run after Task 2 and before persistence because application services need the final-record access API. Tasks 4 and 5 can be assigned to separate workers only if their write sets remain disjoint: Task 4 owns command mappers/repository and Task 5 owns query mappers/repository. Task 6 must integrate Tasks 2-5. Task 7 and Task 8 close the HTTP/security surface.

Recommended subagent split:

- Worker A: Task 1 only.
- Worker B: Task 2 and Task 3.
- Worker C: Task 4.
- Worker D: Task 5.
- Main session or a fresh integration worker: Task 6, then Task 7, then Task 8.

Before executing in any worker, check:

```bash
git status --short
```

Expected: only intentional spec/plan changes are present, or unrelated user changes are clearly left untouched.

## Self-Review

Spec coverage check:

- Safe-init SQL and idempotent D permission seed are covered by Task 1.
- Final record status transitions, version checks, timestamps, and confirm comments are covered by Task 2.
- Whole-record authorization, unsupported-scope-only semantics, and `FinalRecordResourceContext` are covered by Task 3.
- Approved-fact aggregation, null score, missing fact, category partition, transactional component writes, and duplicate create conflicts are covered by Task 4 and Task 6.
- Student reads, admin list/detail, `PageResult<T>` shape, status/keyword/org filters, stable sorting, and unscoped existence semantics are covered by Task 5 and Task 6.
- Controller contracts, request validation, exact permission annotations, and security filter behavior are covered by Task 7 and Task 8.
- Out-of-scope D import/export/unsubmitted-list/platform-window enforcement remain excluded; Task 2 only adds the no-op window-policy extension point required by the spec.

Placeholder scan:

- No task leaves a class unnamed.
- No task asks for generic validation without concrete failure conditions.
- No response DTO shape is left implicit.
- No plan step asks the implementer to add tests without naming the test file, behavior, command, and expected result.

Type consistency check:

- Interface request field is `comment`; domain/database field is `confirmComment` / `confirm_comment`.
- Student response omits `confirmComment`; admin detail and confirm response include it.
- Admin list returns `PageResult<T>` with only `total` and `records`.
- First submit uses absent-record `expectedVersion = 0`, commits as `SUBMITTED`, and returns `version = 1`.
- Admin list rejects `DRAFT` as a filter value because Minimal D never commits externally visible draft records.
