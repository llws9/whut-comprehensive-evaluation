# Minimal B Application Write Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the smallest B-group student application write loop needed for frontend integration after Minimal E.

**Architecture:** Keep the existing `ApplicationSubmission` aggregate and application service as the core write path. Add missing security, detail-read, scoring snapshot, and runtime-safe SQL pieces around the current model rather than replacing the module. Use E-owned file APIs only through `attachmentFileIds`; B never exposes storage internals.

**Tech Stack:** Java 21, Spring Boot, Spring Security method security, MyBatis annotations, MySQL/H2-compatible SQL tests, Maven, JUnit 5, MockMvc.

---

本 ExecPlan 是一份活文档。进度追踪、意外发现、决策日志和成果复盘必须随实现推进更新。

## 代码基线

- 分支：`main`
- 当前本地提交：`f1213cb docs(b): resolve spec review blockers`
- 远端状态：`main...origin/main [ahead 3]`
- Spec：`docs/superpowers/specs/2026-07-06-minimal-b-application-write-design.md`
- 提交策略：实现阶段按里程碑提交；本 proposal 计划不提交，等待用户 review。

## 目标与边界

完成后，学生端可以稳定执行：

1. 读取 Minimal E 平台与项目配置。
2. 上传或选择文件，拿到 `fileId`。
3. 创建申请草稿。
4. 更新草稿。
5. 提交申请并持久化提交时评分快照。
6. 撤回已提交申请。
7. 读取本人申请列表和详情以恢复编辑页。

不做 C 组审核、D 组最终成绩、E 组治理写接口、AI、公共附件发布或更复杂的 dashboard。
`orgUnitId` 在本轮继续由前端请求体传入，后端保存前只验证当前用户是该组织的有效成员；不修改 A 组 current-user 接口。

前置依赖：Minimal B 不在 B safe-init SQL 中创建 A 组 IAM/组织表。执行本计划前，目标库必须已经运行 A 组 schema/seed，至少包含 `org_membership`、`iam_user`、`iam_role`、`iam_permission`、`iam_role_permission`、`iam_user_role_assignment`、`iam_scope_rule` 和 `iam_session`。Task 3 的 `ApplicationOrgMembershipValidator` 依赖 `org_membership`；Task 6 的 seed 登录验证依赖完整 A 组权限数据。

## 文件结构与职责

- `docs/team-delivery/group-b-student-application.safe-init.sql`
  - 新增 B 组运行时安全初始化 SQL。
  - 非破坏性、可重跑、MySQL/H2 MySQL-mode 兼容。
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
  - 收敛学生状态机：`DRAFT/RETURNED` 可更新，`SUBMITTED` 可撤回。
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationScoringSnapshot.java`
  - 新增提交时评分快照值对象。
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
  - 增加 nullable scoring snapshot 字段，随提交持久化。
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/repository/ApplicationSubmissionRepository.java`
  - 保持 `findById/save`，由聚合包含快照后自然覆盖详情读取。
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/service/ActiveSubmissionPolicy.java`
  - 活跃申请语义保持接口不变，mapper 调整状态集合。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ApplicationSubmissionDetailView.java`
  - 新增本人申请详情响应模型，不含 storage internals。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ApplicationAttachmentView.java`
  - 新增详情附件响应模型。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionCommandApplicationService.java`
  - 调整 submit 评分语义、错误码、快照持久化、owner 403、`orgUnitId` 成员校验。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/CreateApplicationDraftCommand.java`
  - 已有创建草稿命令；本计划只复用。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/SubmitApplicationCommand.java`
  - 已有提交命令；`appliedPoints` 仅在自定义分值 option 时生效。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/UpdateApplicationDraftCommand.java`
  - 已有更新命令；本计划只复用。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/command/WithdrawApplicationCommand.java`
  - 已有撤回命令；本计划只复用。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ApplicationSubmissionView.java`
  - 已有写接口响应模型，已包含 `appliedPoints`、`maxPoints`、`exceedsMaxPoints`、`warningMessage`；本计划不新增字段。
- `whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java`
  - 已有 `APPLICATION_SUBMIT`、`APPLICATION_UPDATE`、`APPLICATION_VIEW_SELF` 常量；本计划只引用。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationOrgMembershipValidator.java`
  - 新增应用层端口，验证当前用户是否是目标组织有效成员。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisApplicationOrgMembershipValidator.java`
  - 基于 MyBatis mapper 查询实现组织成员校验。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/OrgMembershipMapper.java`
  - 增加按 user/org 判断 active membership 的查询。
- `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionDetailApplicationService.java`
  - 新增本人申请详情读取服务。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/ApplicationFactDO.java`
  - 新增 `application_fact` 持久化对象。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationFactMapper.java`
  - 新增按 application 写入/替换/读取评分快照 mapper。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationSubmissionMapper.java`
  - 调整 active-submission 状态集合包含 `APPROVED`。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/RepositoryBackedActiveSubmissionPolicy.java`
  - 将 mapper 的 count 结果转换成 boolean。
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationSubmissionRepository.java`
  - 保存和读取 `ApplicationScoringSnapshot`。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentApplicationSubmissionController.java`
  - 增加 `@PreAuthorize` 和 `GET /api/student/applications/{applicationId}`。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentQueryController.java`
  - 已有 `GET /api/student/query/applications` 本人申请列表端点；本计划只在 Task 7 回归验证中覆盖，不新增列表实现。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/request/UpdateApplicationDraftRequest.java`
  - 已有更新请求；本计划只复用。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/request/SubmitApplicationRequest.java`
  - 已有提交请求；`appliedPoints` 仅在自定义分值 option 时传入。
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/request/WithdrawApplicationRequest.java`
  - 已有撤回请求；本计划只复用。
- `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionCommandApplicationServiceTest.java`
  - 覆盖写服务 owner、重复、状态、评分规则。
- `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionStateMachineTest.java`
  - 覆盖领域状态机。
- `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionDetailApplicationServiceTest.java`
  - 覆盖本人详情读取。
- `whut-eval-app/src/test/java/edu/whut/eval/app/student/StudentApplicationSubmissionControllerWebMvcTest.java`
  - 覆盖 HTTP mapping 和响应 shape。
- `whut-eval-app/src/test/java/edu/whut/eval/app/query/StudentQueryControllerWebMvcTest.java`
  - 已有本人申请列表回归测试；Task 7 复跑以证明 list 合同仍然可用。
- `whut-eval-app/src/test/java/edu/whut/eval/app/student/StudentApplicationWriteSecurityIntegrationTest.java`
  - 覆盖启用 filter 的安全验证。
- `whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerSeedAccountLoginIntegrationTest.java`
  - 覆盖学生账号登录后有效权限。
- `whut-eval-app/src/test/java/edu/whut/eval/app/student/MybatisPlusApplicationSubmissionRepositoryIntegrationTest.java`
  - 覆盖 repository 持久化。
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
  - 覆盖 SQL 安全初始化。

## 任务

### Task 1: B safe-init SQL and mapper DDL alignment

**Files:**
- Create: `docs/team-delivery/group-b-student-application.safe-init.sql`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`

- [ ] **Step 1: Write failing SQL safety tests**

Add tests to `TeamDeliverySqlConsistencyTest`:

```java
private static final Path B_GROUP_SAFE_INIT_SQL = TEAM_DELIVERY.resolve("group-b-student-application.safe-init.sql");

@Test
void shouldProvideNonDestructiveBGroupSafeInitSql() throws Exception {
    String sql = Files.readString(B_GROUP_SAFE_INIT_SQL);

    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `application_submission`");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `application_attachment`");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `application_fact`");
    assertThat(sql).doesNotContain("DROP TABLE");
    assertThat(sql).doesNotContain("ENGINE=");
    assertThat(sql).doesNotContain("CHARSET");
    assertThat(sql).doesNotContain("COLLATE");
    assertThat(sql).doesNotContain("COMMENT=");
    assertThat(extractCreateTableBlock(sql, "application_submission"))
            .contains("`application_id` BIGINT NOT NULL AUTO_INCREMENT")
            .contains("KEY `idx_application_submission_active_claim` (`applicant_user_id`, `item_code`, `academic_year`, `term`, `status`)");
    assertThat(extractCreateTableBlock(sql, "application_attachment"))
            .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
            .contains("`storage_key` VARCHAR(512) NOT NULL")
            .contains("`uploaded_by` BIGINT NOT NULL")
            .contains("KEY `idx_application_attachment_application_id` (`application_id`)")
            .contains("KEY `idx_application_attachment_uploaded_by` (`uploaded_by`)")
            .doesNotContain("selected_source")
            .doesNotContain("snapshot_");
    assertThat(extractCreateTableBlock(sql, "application_fact"))
            .contains("`id` BIGINT NOT NULL AUTO_INCREMENT")
            .contains("`application_id` BIGINT NOT NULL")
            .contains("`score_value` DECIMAL(10,2) DEFAULT NULL")
            .contains("`display_text` VARCHAR(1000) DEFAULT NULL")
            .contains("`evidence_count` INT NOT NULL")
            .contains("`extra_json` VARCHAR(2000) DEFAULT NULL")
            .contains("UNIQUE KEY `uk_application_fact_application_id` (`application_id`)");
}

@Test
void shouldRerunBGroupSafeInitSqlWithoutOverwritingRuntimeApplicationRows() throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:b_group_safe_init;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")) {
        executeStatements(connection, Files.readString(B_GROUP_SAFE_INIT_SQL));

        connection.createStatement().executeUpdate("""
                INSERT INTO application_submission (applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version)
                VALUES (1001, 2010, 'INTELLECTUAL', 'INTELLECTUAL_PAPER', '2025-2026', '上学期', 'runtime title', 'runtime desc', 'DRAFT', NULL, '2026-07-06 10:00:00', '2026-07-06 10:00:00', 0)
                """);
        long applicationId = singleLong(connection, "SELECT application_id FROM application_submission WHERE title = 'runtime title'");
        connection.createStatement().executeUpdate("""
                INSERT INTO application_attachment (application_id, file_id, storage_key, original_filename, content_type, size, uploaded_by, sort_no)
                VALUES (%d, 'file_runtime_001', 'runtime/a.pdf', 'a.pdf', 'application/pdf', 128, 1001, 0)
                """.formatted(applicationId));
        connection.createStatement().executeUpdate("""
                INSERT INTO application_fact (application_id, score_value, display_text, evidence_count, extra_json, created_at, updated_at)
                VALUES (%d, 2.00, 'runtime warning', 1, '{"optionCode":"OPTION_A"}', '2026-07-06 10:00:00', '2026-07-06 10:00:00')
                """.formatted(applicationId));

        executeStatements(connection, Files.readString(B_GROUP_SAFE_INIT_SQL));

        assertThat(countRows(connection, "application_submission", "application_id = " + applicationId)).isEqualTo(1);
        assertThat(singleString(connection, "SELECT title FROM application_submission WHERE application_id = " + applicationId))
                .isEqualTo("runtime title");
        assertThat(countRows(connection, "application_attachment", "application_id = " + applicationId + " AND file_id = 'file_runtime_001'")).isEqualTo(1);
        assertThat(singleString(connection, "SELECT storage_key FROM application_attachment WHERE application_id = " + applicationId))
                .isEqualTo("runtime/a.pdf");
        assertThat(countRows(connection, "application_fact", "application_id = " + applicationId)).isEqualTo(1);
        assertThat(singleString(connection, "SELECT display_text FROM application_fact WHERE application_id = " + applicationId))
                .isEqualTo("runtime warning");
    }
}
```

`TeamDeliverySqlConsistencyTest` already contains helpers named `executeStatements`, `countRows`, `singleString`, `singleLong`, and `extractCreateTableBlock` from the Minimal E SQL safety work. If the file has drifted, add those helpers before the new B tests using the same implementations already present in that test class.

The H2 MySQL-mode test is a CI safety net for idempotence and mapper-compatible DDL. It does not replace a real MySQL deployment smoke test; production database creation still runs the same safe-init SQL in MySQL through DBeaver or deployment tooling.

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TeamDeliverySqlConsistencyTest test
```

Expected: fail because `group-b-student-application.safe-init.sql` does not exist.

- [ ] **Step 3: Create B safe-init SQL**

Create `docs/team-delivery/group-b-student-application.safe-init.sql`:

```sql
CREATE TABLE IF NOT EXISTS `application_submission` (
  `application_id` BIGINT NOT NULL AUTO_INCREMENT,
  `applicant_user_id` BIGINT NOT NULL,
  `org_unit_id` BIGINT NOT NULL,
  `category_code` VARCHAR(64) NOT NULL,
  `item_code` VARCHAR(64) NOT NULL,
  `academic_year` VARCHAR(32) NOT NULL,
  `term` VARCHAR(32) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` VARCHAR(1000) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `submitted_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `version` BIGINT NOT NULL,
  PRIMARY KEY (`application_id`),
  KEY `idx_application_submission_active_claim` (`applicant_user_id`, `item_code`, `academic_year`, `term`, `status`)
);

CREATE TABLE IF NOT EXISTS `application_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `file_id` VARCHAR(128) NOT NULL,
  `storage_key` VARCHAR(512) NOT NULL,
  `original_filename` VARCHAR(255) NOT NULL,
  `content_type` VARCHAR(128) NOT NULL,
  `size` BIGINT NOT NULL,
  `uploaded_by` BIGINT NOT NULL,
  `sort_no` INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_attachment_application_id` (`application_id`),
  KEY `idx_application_attachment_uploaded_by` (`uploaded_by`)
);

CREATE TABLE IF NOT EXISTS `application_fact` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `score_value` DECIMAL(10,2) DEFAULT NULL,
  `display_text` VARCHAR(1000) DEFAULT NULL,
  `evidence_count` INT NOT NULL,
  `extra_json` VARCHAR(2000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_application_fact_application_id` (`application_id`)
);
```

`application_fact.score_value` stores the applied points snapshot. `display_text` stores the warning message, `evidence_count` stores the number of bound attachments at submit time, and `extra_json` stores `optionCode`, `maxPoints`, and `exceedsMaxPoints`.

`application_attachment.storage_key` remains an internal database snapshot used by B persistence and must not appear in any B-facing response DTO.

- [ ] **Step 4: Re-run Task 1 verification**

Run the Task 1 command again.

Expected: `BUILD SUCCESS`, 0 failures, 0 errors, 0 skipped.

- [ ] **Step 5: Commit**

```bash
git add docs/team-delivery/group-b-student-application.safe-init.sql whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java
git commit -m "test(b): verify safe application table initialization"
```

### Task 2: Introduce scoring snapshot shape

**Files:**
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationScoringSnapshot.java`

- [ ] **Step 1: Add scoring snapshot model and constructor field**

Create `ApplicationScoringSnapshot`:

```java
package edu.whut.eval.domain.application.model;

import java.math.BigDecimal;

public record ApplicationScoringSnapshot(
        String optionCode,
        BigDecimal appliedPoints,
        BigDecimal maxPoints,
        int evidenceCount,
        boolean exceedsMaxPoints,
        String warningMessage
) {
}
```

Add `private final ApplicationScoringSnapshot scoringSnapshot;` to `ApplicationSubmission`.

Keep the existing public constructor signature as a compatibility overload and delegate it to a new full constructor:

```java
public ApplicationSubmission(Long applicationId,
                             Long applicantUserId,
                             Long orgUnitId,
                             String categoryCode,
                             String itemCode,
                             String academicYear,
                             String term,
                             String title,
                             String description,
                             List<AttachmentRef> evidenceAttachments,
                             ApplicationSubmissionStatus status,
                             Instant submittedAt,
                             Instant createdAt,
                             Instant updatedAt,
                             Long version) {
    this(applicationId, applicantUserId, orgUnitId, categoryCode, itemCode, academicYear, term, title, description,
            evidenceAttachments, status, submittedAt, createdAt, updatedAt, version, null);
}

public ApplicationSubmission(Long applicationId,
                             Long applicantUserId,
                             Long orgUnitId,
                             String categoryCode,
                             String itemCode,
                             String academicYear,
                             String term,
                             String title,
                             String description,
                             List<AttachmentRef> evidenceAttachments,
                             ApplicationSubmissionStatus status,
                             Instant submittedAt,
                             Instant createdAt,
                             Instant updatedAt,
                             Long version,
                             ApplicationScoringSnapshot scoringSnapshot) {
    this.applicationId = applicationId;
    this.applicantUserId = applicantUserId;
    this.orgUnitId = orgUnitId;
    this.categoryCode = categoryCode;
    this.itemCode = itemCode;
    this.academicYear = academicYear;
    this.term = term;
    this.title = title;
    this.description = description;
    this.evidenceAttachments = evidenceAttachments == null ? List.of() : List.copyOf(evidenceAttachments);
    this.status = status;
    this.submittedAt = submittedAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
    this.scoringSnapshot = scoringSnapshot;
}
```

Add:

```java
public ApplicationScoringSnapshot getScoringSnapshot() {
    return scoringSnapshot;
}
```

Update the internal constructor calls inside `ApplicationSubmission`:

- `createDraft(...)` passes `null`.
- `updateDraft(...)` preserves `scoringSnapshot`.
- `submit(...)` is completed in Task 4 and preserves or sets `scoringSnapshot`.
- `withdraw(...)` preserves `scoringSnapshot`.

External `ApplicationSubmission` constructor calls in repositories and existing tests can keep using the constructor compatibility overload until the Task 4 snapshot persistence work updates the call sites that need a real snapshot. This does not apply to the aggregate `submit(...)` method; Task 4 makes one-argument submit fail fast and moves real submissions to the snapshot-aware overload.

- [ ] **Step 2: Run compile check**

```bash
mvn -pl whut-eval-app -am -DskipTests compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationScoringSnapshot.java whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java
git commit -m "refactor(b): add application scoring snapshot model"
```

### Task 3: State machine, owner errors, and active-submission status semantics

**Files:**
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationOrgMembershipValidator.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisApplicationOrgMembershipValidator.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/OrgMembershipMapper.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationSubmissionMapper.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/RepositoryBackedActiveSubmissionPolicy.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionCommandApplicationService.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionStateMachineTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionCommandApplicationServiceTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/student/MybatisPlusApplicationSubmissionRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing state-machine tests**

Add tests:

```java
@Test
void shouldWithdrawSubmittedApplication() {
    ApplicationSubmission submitted = applicationWithStatus(ApplicationSubmissionStatus.SUBMITTED);

    ApplicationSubmission withdrawn = submitted.withdraw(0L);

    assertThat(withdrawn.getStatus()).isEqualTo(ApplicationSubmissionStatus.WITHDRAWN);
    assertThat(withdrawn.getVersion()).isEqualTo(1L);
}

@Test
void shouldRejectWithdrawFromDraftReturnedApprovedRejectedAndWithdrawn() {
    ApplicationSubmission draft = draft();
    assertThatThrownBy(() -> draft.withdraw(0L))
            .isInstanceOf(ValidationException.class)
            .hasMessage("当前申请状态不允许撤回");

    ApplicationSubmission withdrawn = applicationWithStatus(ApplicationSubmissionStatus.WITHDRAWN);
    assertThatThrownBy(() -> withdrawn.withdraw(0L))
            .isInstanceOf(ValidationException.class)
            .hasMessage("当前申请状态不允许撤回");

    ApplicationSubmission returned = applicationWithStatus(ApplicationSubmissionStatus.RETURNED);
    assertThatThrownBy(() -> returned.withdraw(0L))
            .isInstanceOf(ValidationException.class)
            .hasMessage("当前申请状态不允许撤回");

    ApplicationSubmission approved = applicationWithStatus(ApplicationSubmissionStatus.APPROVED);
    assertThatThrownBy(() -> approved.withdraw(0L))
            .isInstanceOf(ValidationException.class)
            .hasMessage("当前申请状态不允许撤回");

    ApplicationSubmission rejected = applicationWithStatus(ApplicationSubmissionStatus.REJECTED);
    assertThatThrownBy(() -> rejected.withdraw(0L))
            .isInstanceOf(ValidationException.class)
            .hasMessage("当前申请状态不允许撤回");
}
```

Use or add these helpers in `ApplicationSubmissionStateMachineTest`:

```java
private ApplicationSubmission draft() {
    return ApplicationSubmission.createDraft(
            1001L,
            2010L,
            "INTELLECTUAL",
            "INTELLECTUAL_PAPER",
            "2025-2026",
            "上学期",
            "申请标题",
            "申请说明",
            List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 128L, 1001L))
    );
}

private ApplicationSubmission applicationWithStatus(ApplicationSubmissionStatus status) {
    return new ApplicationSubmission(
            1L,
            1001L,
            2010L,
            "INTELLECTUAL",
            "INTELLECTUAL_PAPER",
            "2025-2026",
            "上学期",
            "申请标题",
            "申请说明",
            List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 128L, 1001L)),
            status,
            status == ApplicationSubmissionStatus.SUBMITTED ? Instant.parse("2026-07-06T10:00:00Z") : null,
            Instant.parse("2026-07-06T09:00:00Z"),
            Instant.parse("2026-07-06T09:00:00Z"),
            0L,
            null
    );
}

```

- [ ] **Step 2: Write failing service tests for owner and active status**

Add to `ApplicationSubmissionCommandApplicationServiceTest`:

```java
private final ApplicationOrgMembershipValidator applicationOrgMembershipValidator = mock(ApplicationOrgMembershipValidator.class);

@Test
void shouldReturnAccessDeniedWhenUpdatingAnotherUsersApplication() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
    given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(new ApplicationSubmission(
            1L, 2002L, 10L, "competition", "item-1", "2025-2026", "1",
            "申请标题", "申请说明", List.of(sampleAttachment()), ApplicationSubmissionStatus.DRAFT,
            null, Instant.now(), Instant.now(), 0L, null
    )));

    assertThatThrownBy(() -> applicationService.updateDraft(new UpdateApplicationDraftCommand(
            1L, "新标题", "新说明", List.of("file-1"), 0L
    ))).isInstanceOf(AccessDeniedAppException.class)
            .hasMessage("当前用户无权操作该申请");
}

@Test
void shouldRejectCreateDraftForOrgUnitOutsideCurrentUserMemberships() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
    given(applicationOrgMembershipValidator.isActiveMember(1001L, 9999L)).willReturn(false);

    assertThatThrownBy(() -> applicationService.createDraft(new CreateApplicationDraftCommand(
            9999L,
            "competition",
            "item-1",
            "2025-2026",
            "1",
            "申请标题",
            "申请说明",
            List.of("file-1")
    ))).isInstanceOf(AccessDeniedAppException.class)
            .hasMessage("当前用户不属于该组织");
}
```

Use or add these helpers in `ApplicationSubmissionCommandApplicationServiceTest`:

```java
private UserAuthorizationContext currentUser() {
    return new UserAuthorizationContext(
            1001L,
            "2024305999",
            "Test User",
            "student",
            Set.of("student"),
            Set.of("application.submit", "application.update"),
            List.of()
    );
}

private AttachmentRef sampleAttachment() {
    return new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 128L, 1001L);
}
```

Add a repository integration assertion in `MybatisPlusApplicationSubmissionRepositoryIntegrationTest`:

```java
@Test
void shouldCountApprovedAsActiveClaimAndIgnoreRejectedAndWithdrawn() {
    insertSubmission(1001L, "item-1", "2025-2026", "1", "APPROVED");
    assertThat(applicationSubmissionMapper.countActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).isEqualTo(1);

    jdbcTemplate.update("UPDATE application_submission SET status = 'REJECTED' WHERE applicant_user_id = 1001 AND item_code = 'item-1' AND academic_year = '2025-2026' AND term = '1'");
    assertThat(applicationSubmissionMapper.countActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).isZero();

    jdbcTemplate.update("UPDATE application_submission SET status = 'WITHDRAWN' WHERE applicant_user_id = 1001 AND item_code = 'item-1' AND academic_year = '2025-2026' AND term = '1'");
    assertThat(applicationSubmissionMapper.countActiveSubmission(1001L, "item-1", "2025-2026", "1", null)).isZero();
}
```

Add this helper in `MybatisPlusApplicationSubmissionRepositoryIntegrationTest`:

```java
private void insertSubmission(Long applicantUserId, String itemCode, String academicYear, String term, String status) {
    jdbcTemplate.update(
            "INSERT INTO application_submission (applicant_user_id, org_unit_id, category_code, item_code, academic_year, term, title, description, status, submitted_at, created_at, updated_at, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), ?)",
            applicantUserId, 2010L, "INTELLECTUAL", itemCode, academicYear, term, "申请标题", "申请说明", status, 0L
    );
}
```

- [ ] **Step 3: Run tests to verify failure**

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ApplicationSubmissionStateMachineTest,ApplicationSubmissionCommandApplicationServiceTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest test
```

Expected: fail because withdraw still uses `assertEditable()` and non-owner uses `ValidationException`.

- [ ] **Step 4: Implement state and owner changes**

In `ApplicationSubmission`:

Before editing, verify the current aggregate method is `withdraw(long expectedVersion)` and that the withdraw reason is carried only by `WithdrawApplicationCommand`, not by the domain aggregate. If the signature has drifted, adapt the plan to the current aggregate signature before editing tests.

```java
public ApplicationSubmission withdraw(long expectedVersion) {
    assertWithdrawable();
    assertExpectedVersion(expectedVersion);
    Instant now = Instant.now();
    return new ApplicationSubmission(
            applicationId,
            applicantUserId,
            orgUnitId,
            categoryCode,
            itemCode,
            academicYear,
            term,
            title,
            description,
            evidenceAttachments,
            ApplicationSubmissionStatus.WITHDRAWN,
            submittedAt,
            createdAt,
            now,
            version + 1,
            scoringSnapshot
    );
}

private void assertWithdrawable() {
    if (status != ApplicationSubmissionStatus.SUBMITTED) {
        throw new ValidationException("当前申请状态不允许撤回");
    }
}
```

In `ApplicationSubmissionCommandApplicationService.loadOwnedSubmission`:

```java
private ApplicationSubmission loadOwnedSubmission(Long applicationId, Long currentUserId) {
    ApplicationSubmission submission = applicationSubmissionRepository.findById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("申请不存在"));
    if (!currentUserId.equals(submission.getApplicantUserId())) {
        throw new AccessDeniedAppException("当前用户无权操作该申请");
    }
    return submission;
}
```

Create `ApplicationOrgMembershipValidator`:

```java
package edu.whut.eval.application.application.service;

public interface ApplicationOrgMembershipValidator {
    boolean isActiveMember(Long userId, Long orgUnitId);
}
```

Implement `MybatisApplicationOrgMembershipValidator`:

```java
@Repository
public class MybatisApplicationOrgMembershipValidator implements ApplicationOrgMembershipValidator {

    private final OrgMembershipMapper orgMembershipMapper;

    public MybatisApplicationOrgMembershipValidator(OrgMembershipMapper orgMembershipMapper) {
        this.orgMembershipMapper = orgMembershipMapper;
    }

    @Override
    public boolean isActiveMember(Long userId, Long orgUnitId) {
        return orgMembershipMapper.countActiveByUserIdAndOrgUnitId(userId, orgUnitId) > 0;
    }
}
```

Add to `OrgMembershipMapper`:

```java
@Select("SELECT COUNT(1) FROM org_membership WHERE user_id = #{userId} AND org_unit_id = #{orgUnitId} AND status = 'ACTIVE'")
int countActiveByUserIdAndOrgUnitId(@Param("userId") Long userId, @Param("orgUnitId") Long orgUnitId);
```

Inject `ApplicationOrgMembershipValidator` into `ApplicationSubmissionCommandApplicationService`:

```java
private final ApplicationOrgMembershipValidator applicationOrgMembershipValidator;

public ApplicationSubmissionCommandApplicationService(UserAuthorizationContextAssembler userAuthorizationContextAssembler,
                                                      ApplicationSubmissionRepository applicationSubmissionRepository,
                                                      ApplicationSubmissionWindowPolicy applicationSubmissionWindowPolicy,
                                                      ActiveSubmissionPolicy activeSubmissionPolicy,
                                                      ApplicationAttachmentResolver applicationAttachmentResolver,
                                                      RuleEngineService ruleEngineService,
                                                      ApplicationOrgMembershipValidator applicationOrgMembershipValidator) {
    this.userAuthorizationContextAssembler = userAuthorizationContextAssembler;
    this.applicationSubmissionRepository = applicationSubmissionRepository;
    this.applicationSubmissionWindowPolicy = applicationSubmissionWindowPolicy;
    this.activeSubmissionPolicy = activeSubmissionPolicy;
    this.applicationAttachmentResolver = applicationAttachmentResolver;
    this.ruleEngineService = ruleEngineService;
    this.applicationOrgMembershipValidator = applicationOrgMembershipValidator;
}
```

Update every test construction of `ApplicationSubmissionCommandApplicationService` to pass the existing `applicationOrgMembershipValidator` mock. In Spring integration tests importing this service, add a bean or mock for `ApplicationOrgMembershipValidator`.

For `createDraft`, check before active-submission and attachment resolution:

```java
if (!applicationOrgMembershipValidator.isActiveMember(authorizationContext.getUserId(), command.getOrgUnitId())) {
    throw new AccessDeniedAppException("当前用户不属于该组织");
}
```

For `updateDraft`, `submit`, and `withdraw`, load the owned submission first and validate the persisted submission org:

```java
if (!applicationOrgMembershipValidator.isActiveMember(authorizationContext.getUserId(), submission.getOrgUnitId())) {
    throw new AccessDeniedAppException("当前用户不属于该组织");
}
```

Replace `ApplicationSubmissionMapper.existsActiveSubmission` with an H2/MySQL-compatible count method:

```java
@Select("SELECT COUNT(1) FROM application_submission WHERE applicant_user_id = #{applicantUserId} AND item_code = #{itemCode} AND academic_year = #{academicYear} AND term = #{term} AND status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'APPROVED') AND (#{excludeApplicationId} IS NULL OR application_id <> #{excludeApplicationId})")
int countActiveSubmission(@Param("applicantUserId") Long applicantUserId,
                          @Param("itemCode") String itemCode,
                          @Param("academicYear") String academicYear,
                          @Param("term") String term,
                          @Param("excludeApplicationId") Long excludeApplicationId);
```

Run `rg -n "existsActiveSubmission|countActiveSubmission" whut-eval-domain whut-eval-application whut-eval-infra whut-eval-app whut-eval-interfaces`, update every reference, and delete the old `existsActiveSubmission(...)` method from `ApplicationSubmissionMapper`. The current baseline call site is `RepositoryBackedActiveSubmissionPolicy`; tests that assert mapper behavior must use `countActiveSubmission(...)`.

Update `RepositoryBackedActiveSubmissionPolicy.hasActiveSubmission`:

```java
return applicationSubmissionMapper.countActiveSubmission(
        applicantUserId,
        itemCode,
        academicYear,
        term,
        excludeApplicationId
) > 0;
```

`APPROVED` is counted as active because it represents an awarded claim for the same student, item, academic year, and term; allowing a second claim would duplicate the awarded record. `REJECTED` and `WITHDRAWN` are not counted because they no longer represent an active or awarded claim.

- [ ] **Step 5: Re-run Task 3 verification**

Run the Task 3 command again.

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationOrgMembershipValidator.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisApplicationOrgMembershipValidator.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/OrgMembershipMapper.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationSubmissionMapper.java whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionCommandApplicationService.java whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionStateMachineTest.java whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionCommandApplicationServiceTest.java whut-eval-app/src/test/java/edu/whut/eval/app/student/MybatisPlusApplicationSubmissionRepositoryIntegrationTest.java
git commit -m "fix(b): align student state and membership rules"
```

### Task 4: Scoring snapshot persistence through application_fact

**Files:**
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/ApplicationFactDO.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationFactMapper.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationSubmissionRepository.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionCommandApplicationService.java`
- Modify: `whut-eval-domain/src/main/java/edu/whut/eval/domain/config/RuleEngineService.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/nacos/rule/RulesEngineServiceImpl.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionCommandApplicationServiceTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/student/MybatisPlusApplicationSubmissionRepositoryIntegrationTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/student/ApplicationSubmissionFileIdIntegrationTest.java`

- [ ] **Step 1: Write failing submit snapshot service tests**

Add tests:

```java
@Test
void shouldRejectSubmitWithoutOptionCodeWhenItemHasOptions() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
    given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(savedDraft()));
    given(applicationSubmissionWindowPolicy.isWindowOpen(10L, "competition", "item-1", "2025-2026", "1")).willReturn(true);
    given(ruleEngineService.requiresOption("item-1")).willReturn(true);

    assertThatThrownBy(() -> applicationService.submit(new SubmitApplicationCommand(1L, 0L, null, null)))
            .isInstanceOf(ValidationException.class)
            .hasMessage("optionCode 不能为空");
}

@Test
void shouldRejectSubmitWithOptionCodeWhenItemHasNoOptions() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
    given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(savedDraft()));
    given(applicationSubmissionWindowPolicy.isWindowOpen(10L, "competition", "item-1", "2025-2026", "1")).willReturn(true);
    given(ruleEngineService.requiresOption("item-1")).willReturn(false);

    assertThatThrownBy(() -> applicationService.submit(new SubmitApplicationCommand(1L, 0L, null, "OPTION_A")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("当前项目不需要选择评分选项");
}

@Test
void shouldAttachScoringSnapshotWhenSubmittingWithOptionCode() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
    given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(savedDraft()));
    given(applicationSubmissionWindowPolicy.isWindowOpen(10L, "competition", "item-1", "2025-2026", "1")).willReturn(true);
    given(ruleEngineService.calculatePoints("item-1", "OPTION_A", any(StudentContext.class))).willReturn(new BigDecimal("2.00"));
    given(ruleEngineService.calculateMaxPoints(eq("item-1"), any(StudentContext.class))).willReturn(new BigDecimal("6.00"));
    given(applicationSubmissionRepository.save(any(ApplicationSubmission.class))).willAnswer(invocation -> invocation.getArgument(0));

    ApplicationSubmissionView result = applicationService.submit(new SubmitApplicationCommand(1L, 0L, null, "OPTION_A"));

    assertThat(result.getAppliedPoints()).isEqualByComparingTo("2.00");
    assertThat(result.getMaxPoints()).isEqualByComparingTo("6.00");
    assertThat(result.isExceedsMaxPoints()).isFalse();
    ArgumentCaptor<ApplicationSubmission> submissionCaptor = ArgumentCaptor.forClass(ApplicationSubmission.class);
    verify(applicationSubmissionRepository).save(submissionCaptor.capture());
    assertThat(submissionCaptor.getValue().getScoringSnapshot()).isNotNull();
    assertThat(submissionCaptor.getValue().getScoringSnapshot().optionCode()).isEqualTo("OPTION_A");
    assertThat(submissionCaptor.getValue().getScoringSnapshot().evidenceCount()).isEqualTo(1);
}
```

Reuse existing test helpers such as `savedDraft()` and `draft(String fileId, String storageKey)` when present. If a helper is missing or its signature has drifted, add it in the same test class using the current `ApplicationSubmission.createDraft(...)` factory and the attachment shape shown in the repository tests.

Add `requiresOption(String itemCode)` to `RuleEngineService`, implement it in `RulesEngineServiceImpl`, and update every anonymous test implementation of `RuleEngineService`.

- [ ] **Step 2: Write failing repository snapshot test**

In `MybatisPlusApplicationSubmissionRepositoryIntegrationTest`, extend the existing `@BeforeEach setUpSchema()` JDBC DDL with:

```java
jdbcTemplate.execute(
        "CREATE TABLE application_fact (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "application_id BIGINT NOT NULL, " +
                "score_value DECIMAL(10,2) NULL, " +
                "display_text VARCHAR(1000) NULL, " +
                "evidence_count INT NOT NULL, " +
                "extra_json VARCHAR(2000) NULL, " +
                "created_at DATETIME NOT NULL, " +
                "updated_at DATETIME NOT NULL, " +
                "UNIQUE KEY uk_application_fact_application_id (application_id))"
);
```

Then assert save/reload preserves snapshot:

```java
ApplicationSubmission submitted = draft("file-1", "uploads/a.pdf")
        .submit(0L, new ApplicationScoringSnapshot("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null));
ApplicationSubmission saved = applicationSubmissionRepository.save(submitted);
ApplicationSubmission reloaded = applicationSubmissionRepository.findById(saved.getApplicationId()).orElseThrow();

assertThat(reloaded.getScoringSnapshot()).isNotNull();
assertThat(reloaded.getScoringSnapshot().optionCode()).isEqualTo("OPTION_A");
assertThat(reloaded.getScoringSnapshot().appliedPoints()).isEqualByComparingTo("2.00");
```

- [ ] **Step 3: Run tests to verify failure**

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ApplicationSubmissionCommandApplicationServiceTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest test
```

Expected: compile/test failure because the submit overload, `ApplicationFactMapper`, `ApplicationFactDO`, and `RuleEngineService.requiresOption` do not exist yet.

- [ ] **Step 4: Implement snapshot model and repository mapper**

Update these scoring snapshot call sites:

- `ApplicationSubmission.submit(...)`: keep the public `submit(long expectedVersion)` compatibility overload, but make it fail fast with `ValidationException("申请评分快照不能为空")`; add `submit(long expectedVersion, ApplicationScoringSnapshot scoringSnapshot)` for the real submit path and pass the supplied snapshot as the final constructor argument.
- `MybatisPlusApplicationSubmissionRepository.toDomain(...)`: pass the snapshot loaded from `ApplicationFactMapper`.

The compatibility overload stays only to avoid a broad source break, but it must not silently submit without a fact snapshot:

```java
public ApplicationSubmission submit(long expectedVersion) {
    throw new ValidationException("申请评分快照不能为空");
}

public ApplicationSubmission submit(long expectedVersion, ApplicationScoringSnapshot scoringSnapshot) {
    assertEditable();
    assertExpectedVersion(expectedVersion);
    if (title == null || title.isBlank()) {
        throw new ValidationException("申请标题不能为空");
    }
    if (description == null || description.isBlank()) {
        throw new ValidationException("申请说明不能为空");
    }
    if (evidenceAttachments.isEmpty()) {
        throw new ValidationException("申请附件不能为空");
    }
    if (scoringSnapshot == null) {
        throw new ValidationException("申请评分快照不能为空");
    }
    Instant now = Instant.now();
    return new ApplicationSubmission(
            applicationId,
            applicantUserId,
            orgUnitId,
            categoryCode,
            itemCode,
            academicYear,
            term,
            title,
            description,
            evidenceAttachments,
            ApplicationSubmissionStatus.SUBMITTED,
            now,
            createdAt,
            now,
            version + 1,
            scoringSnapshot
    );
}
```

Update all domain tests that previously used `submit(0L)` to call `submit(0L, submittedSnapshot())`. Run `rg -n "\.submit\([^,)]*\)" whut-eval-domain whut-eval-application whut-eval-infra whut-eval-app whut-eval-interfaces` and ensure no production call sites use the one-argument aggregate method. It may remain covered only by a negative domain test asserting the fail-fast `ValidationException`.

Create `ApplicationFactDO`:

```java
package edu.whut.eval.infra.persistence.dataobject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ApplicationFactDO {
    private Long id;
    private Long applicationId;
    private BigDecimal scoreValue;
    private String displayText;
    private Integer evidenceCount;
    private String extraJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public BigDecimal getScoreValue() { return scoreValue; }
    public void setScoreValue(BigDecimal scoreValue) { this.scoreValue = scoreValue; }
    public String getDisplayText() { return displayText; }
    public void setDisplayText(String displayText) { this.displayText = displayText; }
    public Integer getEvidenceCount() { return evidenceCount; }
    public void setEvidenceCount(Integer evidenceCount) { this.evidenceCount = evidenceCount; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

Mapping rules:

- `ApplicationFactDO.scoreValue` stores `ApplicationScoringSnapshot.appliedPoints`.
- `ApplicationFactDO.displayText` stores `ApplicationScoringSnapshot.warningMessage`.
- `ApplicationFactDO.evidenceCount` stores `ApplicationScoringSnapshot.evidenceCount()`.
- `ApplicationFactDO.extraJson` stores `optionCode`, `maxPoints`, and `exceedsMaxPoints`. `warningMessage` is not duplicated in `extraJson`; it maps from `displayText` when reading the snapshot.
- `ApplicationFactDO.createdAt` and `updatedAt` are set in `MybatisPlusApplicationSubmissionRepository` using `LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)`, matching the repository's existing UTC conversion helpers.

Create `ApplicationFactMapper`:

```java
@Mapper
public interface ApplicationFactMapper {
    @Select("SELECT id, application_id AS applicationId, score_value AS scoreValue, display_text AS displayText, evidence_count AS evidenceCount, extra_json AS extraJson, created_at AS createdAt, updated_at AS updatedAt FROM application_fact WHERE application_id = #{applicationId}")
    ApplicationFactDO selectLatestByApplicationId(@Param("applicationId") Long applicationId);

    @Delete("DELETE FROM application_fact WHERE application_id = #{applicationId}")
    int deleteByApplicationId(@Param("applicationId") Long applicationId);

    @Insert("INSERT INTO application_fact (application_id, score_value, display_text, evidence_count, extra_json, created_at, updated_at) VALUES (#{applicationId}, #{scoreValue}, #{displayText}, #{evidenceCount}, #{extraJson}, #{createdAt}, #{updatedAt})")
    int insert(ApplicationFactDO applicationFactDO);
}
```

Use explicit column aliases in `selectLatestByApplicationId`; do not rely on global `mapUnderscoreToCamelCase` for this new mapper.
This local aliasing is intentional for the new mapper because Task 4 introduces it with annotation SQL and the plan wants mapping correctness independent of test or runtime MyBatis settings.

Update `MybatisPlusApplicationSubmissionRepository` constructor dependencies:

```java
private final ApplicationFactMapper applicationFactMapper;
private final ObjectMapper objectMapper;

public MybatisPlusApplicationSubmissionRepository(ApplicationSubmissionMapper applicationSubmissionMapper,
                                                  ApplicationAttachmentMapper applicationAttachmentMapper,
                                                  ApplicationFactMapper applicationFactMapper,
                                                  ObjectMapper objectMapper) {
    this.applicationSubmissionMapper = applicationSubmissionMapper;
    this.applicationAttachmentMapper = applicationAttachmentMapper;
    this.applicationFactMapper = applicationFactMapper;
    this.objectMapper = objectMapper;
}
```

In `MybatisPlusApplicationSubmissionRepositoryIntegrationTest`, add `ApplicationFactMapper.class` to the test `@MapperScan(basePackageClasses = {...})` and add an `ObjectMapper` bean if the test context does not already provide one.

In `ApplicationSubmissionFileIdIntegrationTest`, make the same repository dependency updates because that test imports `MybatisPlusApplicationSubmissionRepository`:

- add `DROP TABLE IF EXISTS application_fact` before dropping `application_submission`;
- create the same `application_fact` table in `setUpSchema()`;
- add `ApplicationFactMapper.class` to `@MapperScan(basePackageClasses = {...})`;
- add an `ObjectMapper` bean if the test context does not already provide one.

Encode `extraJson` as a Jackson-generated string containing:

```json
{"optionCode":"OPTION_A","maxPoints":"6.00","exceedsMaxPoints":false}
```

Use this mapper helper in `MybatisPlusApplicationSubmissionRepository`:

```java
private static final String EXTRA_OPTION_CODE = "optionCode";
private static final String EXTRA_MAX_POINTS = "maxPoints";
private static final String EXTRA_EXCEEDS_MAX_POINTS = "exceedsMaxPoints";

private String toExtraJson(ApplicationScoringSnapshot snapshot) {
    try {
        ObjectNode node = objectMapper.createObjectNode();
        if (snapshot.optionCode() == null) {
            node.putNull(EXTRA_OPTION_CODE);
        } else {
            node.put(EXTRA_OPTION_CODE, snapshot.optionCode());
        }
        if (snapshot.maxPoints() == null) {
            node.putNull(EXTRA_MAX_POINTS);
        } else {
            node.put(EXTRA_MAX_POINTS, snapshot.maxPoints().toPlainString());
        }
        node.put(EXTRA_EXCEEDS_MAX_POINTS, snapshot.exceedsMaxPoints());
        return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException exception) {
        throw new SystemException("申请评分快照序列化失败", exception);
    }
}
```

When saving a submitted aggregate, build the fact row from the aggregate snapshot:

```java
private ApplicationFactDO toApplicationFactDO(Long applicationId, ApplicationScoringSnapshot snapshot) {
    ApplicationFactDO fact = new ApplicationFactDO();
    fact.setApplicationId(applicationId);
    fact.setScoreValue(snapshot.appliedPoints());
    fact.setDisplayText(snapshot.warningMessage());
    fact.setEvidenceCount(snapshot.evidenceCount());
    fact.setExtraJson(toExtraJson(snapshot));
    return fact;
}
```

Use this mapper helper in `MybatisPlusApplicationSubmissionRepository` to read the snapshot:

```java
private ApplicationScoringSnapshot toScoringSnapshot(ApplicationFactDO fact) {
    if (fact == null) {
        return null;
    }
    try {
        JsonNode extra = objectMapper.readTree(fact.getExtraJson() == null ? "{}" : fact.getExtraJson());
        String optionCode = extra.path(EXTRA_OPTION_CODE).asText(null);
        String maxPointsText = extra.path(EXTRA_MAX_POINTS).asText(null);
        BigDecimal maxPoints = parseOptionalBigDecimal(maxPointsText);
        boolean exceedsMaxPoints = extra.path(EXTRA_EXCEEDS_MAX_POINTS).asBoolean(false);
        return new ApplicationScoringSnapshot(optionCode, fact.getScoreValue(), maxPoints, fact.getEvidenceCount(), exceedsMaxPoints, fact.getDisplayText());
    } catch (JsonProcessingException | NumberFormatException exception) {
        throw new SystemException("申请评分快照反序列化失败", exception);
    }
}

private BigDecimal parseOptionalBigDecimal(String text) {
    return text == null || text.isBlank() ? null : new BigDecimal(text);
}
```

Before calling `ApplicationFactMapper.insert`, set:

```java
LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
fact.setCreatedAt(now);
fact.setUpdatedAt(now);
```

`ApplicationFactMapper.deleteByApplicationId` and `ApplicationFactMapper.insert` must be invoked inside the existing transactional `MybatisPlusApplicationSubmissionRepository.save(...)` method so the scoring snapshot replacement is atomic with the submission update.

Before editing, verify `MybatisPlusApplicationSubmissionRepository.save(...)` is annotated with `@Transactional`. If it has drifted, add `@Transactional` to `save(...)` before introducing `application_fact` delete/insert replacement.

After the main submission row is inserted or updated and the generated `applicationId` is known:

```java
applicationFactMapper.deleteByApplicationId(applicationSubmissionDO.getApplicationId());
if (applicationSubmission.getScoringSnapshot() != null) {
    ApplicationFactDO fact = toApplicationFactDO(applicationSubmissionDO.getApplicationId(), applicationSubmission.getScoringSnapshot());
    LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    fact.setCreatedAt(now);
    fact.setUpdatedAt(now);
    applicationFactMapper.insert(fact);
}
```

This leaves drafts without an `application_fact` row and replaces a previous submit-time snapshot when a returned application is resubmitted.

- [ ] **Step 5: Implement service submit semantics**

Add `RuleEngineService.requiresOption(String itemCode)` to `whut-eval-domain/src/main/java/edu/whut/eval/domain/config/RuleEngineService.java`:

```java
boolean requiresOption(String itemCode);
```

Implement it in `whut-eval-infra/src/main/java/edu/whut/eval/infra/nacos/rule/RulesEngineServiceImpl.java`:

Before editing, verify the current `RulesEngineServiceImpl` still has `EVALUATION_ITEMS_CONFIG`, `configRepository`, `EvaluationItemsConfig`, and the private helper `findItemByCode(...)`. If any of these names have drifted, reuse the existing `calculateMaxPoints(...)`/`resolveOptionsKey(...)` pattern in that class rather than introducing a second config lookup style.

```java
@Override
public boolean requiresOption(String itemCode) {
    EvaluationItemsConfig config = configRepository.find(EVALUATION_ITEMS_CONFIG, EvaluationItemsConfig.class)
            .orElseThrow(() -> new ConfigLoadException("evaluation-items config not found"));
    EvaluationItemsConfig.EvaluationItem item = findItemByCode(config, itemCode);
    return item.getOptionsKey() != null && !item.getOptionsKey().isBlank();
}
```

Update anonymous `RuleEngineService` test implementations. In `ApplicationSubmissionFileIdIntegrationTest`, add this method to the existing anonymous `RuleEngineService` bean:

```java
@Override
public boolean requiresOption(String itemCode) {
    return false;
}
```

Submit behavior:

```java
boolean optionRequired = ruleEngineService.requiresOption(submission.getItemCode());
boolean optionProvided = command.getOptionCode() != null && !command.getOptionCode().isBlank();
if (!optionProvided && optionRequired) {
    throw new ValidationException("optionCode 不能为空");
}
if (optionProvided && !optionRequired) {
    throw new ValidationException("当前项目不需要选择评分选项");
}
```

Create snapshot before saving submitted aggregate. `RuleEngineService.allowsCustomPoints(String itemCode, String optionCode)` already exists in `whut-eval-domain/src/main/java/edu/whut/eval/domain/config/RuleEngineService.java`. `SubmitApplicationCommand.appliedPoints` is ignored for fixed-point options and used only when `ruleEngineService.allowsCustomPoints(submission.getItemCode(), command.getOptionCode())` returns true and `calculatePoints(...)` returns null. If `calculatePoints(...)` returns null and `allowsCustomPoints(...)` returns false, throw `ValidationException("当前选项不允许自定义分值")`.

Use this explicit branch:

Before editing this block, verify `ApplicationSubmissionCommandApplicationService` still has the existing helpers `isPartyMember(UserAuthorizationContext)` and `requiredExpectedVersion(Long)`. Reuse those helpers; if either helper has drifted, add or restore the equivalent implementation in the same service:

```java
private boolean isPartyMember(UserAuthorizationContext authorizationContext) {
    return "PARTY_MEMBER".equalsIgnoreCase(authorizationContext.getIdentity())
            || authorizationContext.hasRole("PARTY_MEMBER")
            || authorizationContext.hasRole("ROLE_PARTY_MEMBER");
}

private long requiredExpectedVersion(Long expectedVersion) {
    if (expectedVersion == null) {
        throw new ValidationException("expectedVersion 不能为空");
    }
    return expectedVersion;
}
```

```java
StudentContext studentContext = StudentContext.builder()
        .studentId(authorizationContext.getUserNo())
        .studentName(authorizationContext.getUserName())
        .partyMember(isPartyMember(authorizationContext))
        .build();

BigDecimal configuredPoints = command.getOptionCode() == null || command.getOptionCode().isBlank()
        ? null
        : ruleEngineService.calculatePoints(submission.getItemCode(), command.getOptionCode(), studentContext);
BigDecimal appliedPoints = configuredPoints;
if (configuredPoints == null && command.getOptionCode() != null && !command.getOptionCode().isBlank()) {
    if (!ruleEngineService.allowsCustomPoints(submission.getItemCode(), command.getOptionCode())) {
        throw new ValidationException("当前选项不允许自定义分值");
    }
    if (command.getAppliedPoints() == null) {
        throw new ValidationException("appliedPoints 不能为空");
    }
    appliedPoints = command.getAppliedPoints();
}

BigDecimal maxPoints = null;
boolean exceedsMaxPoints = false;
String warningMessage = null;
if (appliedPoints != null) {
    maxPoints = ruleEngineService.calculateMaxPoints(submission.getItemCode(), studentContext);
    if (maxPoints != null && appliedPoints.compareTo(maxPoints) > 0) {
        exceedsMaxPoints = true;
        warningMessage = String.format(
                "您申请的分值(%.2f分)超过该指标的最高分值上限(%.2f分)，申请已提交，但审核时将按最高分值计算",
                appliedPoints,
                maxPoints
        );
    }
}
```

Then create the snapshot and pass it into the aggregate submit overload:

```java
ApplicationScoringSnapshot scoringSnapshot = new ApplicationScoringSnapshot(
        command.getOptionCode(),
        appliedPoints,
        maxPoints,
        submission.getEvidenceAttachments().size(),
        exceedsMaxPoints,
        warningMessage
);
ApplicationSubmission saved = applicationSubmissionRepository.save(submission.submit(
        requiredExpectedVersion(command.getExpectedVersion()),
        scoringSnapshot
));
```

- [ ] **Step 6: Re-run Task 4 verification**

Run the Task 4 command again.

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/application/model/ApplicationSubmission.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/ApplicationFactDO.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationFactMapper.java whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusApplicationSubmissionRepository.java whut-eval-domain/src/main/java/edu/whut/eval/domain/config/RuleEngineService.java whut-eval-infra/src/main/java/edu/whut/eval/infra/nacos/rule/RulesEngineServiceImpl.java whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionCommandApplicationService.java whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionCommandApplicationServiceTest.java whut-eval-app/src/test/java/edu/whut/eval/app/student/MybatisPlusApplicationSubmissionRepositoryIntegrationTest.java whut-eval-app/src/test/java/edu/whut/eval/app/student/ApplicationSubmissionFileIdIntegrationTest.java
git commit -m "feat(b): persist application scoring snapshot"
```

### Task 5: Student-owned application detail endpoint

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ApplicationSubmissionDetailView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ApplicationAttachmentView.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionDetailApplicationService.java`
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentApplicationSubmissionController.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/student/StudentApplicationSubmissionControllerWebMvcTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionDetailApplicationServiceTest.java`

- [ ] **Step 1: Write failing detail service test**

Create `ApplicationSubmissionDetailApplicationServiceTest`:

```java
private final UserAuthorizationContextAssembler userAuthorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
private final ApplicationSubmissionRepository applicationSubmissionRepository = mock(ApplicationSubmissionRepository.class);
private final ApplicationSubmissionDetailApplicationService service =
        new ApplicationSubmissionDetailApplicationService(userAuthorizationContextAssembler, applicationSubmissionRepository);

@Test
void shouldReturnOwnedApplicationDetailWithoutStorageFields() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
    given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(submittedWithSnapshot()));

    ApplicationSubmissionDetailView detail = service.getOwnedDetail(1L);

    assertThat(detail.getApplicationId()).isEqualTo(1L);
    assertThat(detail.getOptionCode()).isEqualTo("OPTION_A");
    assertThat(detail.getAppliedPoints()).isEqualByComparingTo("2.00");
    assertThat(detail.getAttachments()).hasSize(1);
    assertThat(detail.getAttachments().get(0).getFileId()).isEqualTo("file-1");
}

@Test
void shouldRejectDetailForAnotherUser() {
    given(userAuthorizationContextAssembler.requiredAuthorizationContext()).willReturn(currentUser());
    given(applicationSubmissionRepository.findById(1L)).willReturn(Optional.of(otherUsersDraft()));

    assertThatThrownBy(() -> service.getOwnedDetail(1L))
            .isInstanceOf(AccessDeniedAppException.class)
            .hasMessage("当前用户无权查看该申请");
}
```

Add helpers in the same test class:

```java
private ApplicationSubmission submittedWithSnapshot() {
    return application(
            1001L,
            ApplicationSubmissionStatus.SUBMITTED,
            Instant.parse("2026-07-06T10:00:00Z"),
            1L,
            List.of(new AttachmentRef("file-1", "uploads/a.pdf", "a.pdf", "application/pdf", 128L, 1001L)),
            new ApplicationScoringSnapshot("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null)
    );
}

private ApplicationSubmission otherUsersDraft() {
    return application(2002L, ApplicationSubmissionStatus.DRAFT, null, 0L, List.of(), null);
}

private ApplicationSubmission application(Long applicantUserId,
                                          ApplicationSubmissionStatus status,
                                          Instant submittedAt,
                                          Long version,
                                          List<AttachmentRef> attachments,
                                          ApplicationScoringSnapshot scoringSnapshot) {
    return new ApplicationSubmission(
            1L,
            applicantUserId,
            2010L,
            "INTELLECTUAL",
            "INTELLECTUAL_PAPER",
            "2025-2026",
            "上学期",
            "申请标题",
            "申请说明",
            attachments,
            status,
            submittedAt,
            Instant.parse("2026-07-06T09:00:00Z"),
            Instant.parse("2026-07-06T09:00:00Z"),
            version,
            scoringSnapshot
    );
}
```

- [ ] **Step 2: Write failing controller detail test**

Extend `StudentApplicationSubmissionControllerWebMvcTest`:

```java
StudentApplicationSubmissionController controller = new StudentApplicationSubmissionController(
        applicationSubmissionCommandApplicationService,
        applicationSubmissionDetailApplicationService
);
given(applicationSubmissionDetailApplicationService.getOwnedDetail(1L)).willReturn(detailView());

standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build()
        .perform(get("/api/student/applications/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.applicationId").value(1))
        .andExpect(jsonPath("$.data.attachments[0].fileId").value("file-1"))
        .andExpect(jsonPath("$.data.attachments[0].storageKey").doesNotExist())
        .andExpect(jsonPath("$.data.optionCode").value("OPTION_A"));
```

- [ ] **Step 3: Run tests to verify failure**

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentApplicationSubmissionControllerWebMvcTest,ApplicationSubmissionDetailApplicationServiceTest test
```

Expected: compile failure because detail classes and endpoint do not exist.

- [ ] **Step 4: Implement detail view and service**

`ApplicationAttachmentView` fields:

```java
fileId, originalFilename, contentType, size, sortNo
```

`ApplicationSubmissionDetailView` fields:

```java
applicationId, orgUnitId, categoryCode, itemCode,
academicYear, term, title, description, status, submittedAt, createdAt,
updatedAt, version, optionCode, appliedPoints, maxPoints, evidenceCount,
exceedsMaxPoints, warningMessage, attachments
```

Service maps `ApplicationSubmission` and checks owner.

Implement `ApplicationSubmissionDetailView` as a normal DTO with no-arg constructor plus setters, or with a builder, so the service does not depend on a long positional constructor.

Do not expose `applicantUserId` in the detail response. The service uses it only for owner validation; the frontend already knows it is reading the current user's own application.

`evidenceCount` means the number of evidence attachments represented by the current detail view. For submitted applications with a persisted scoring snapshot, it returns the submit-time value from `application_fact.evidence_count`. For draft applications with `submission.getScoringSnapshot() == null`, no submit-time snapshot exists yet, so `ApplicationSubmissionDetailView` returns `optionCode=null`, `appliedPoints=null`, `maxPoints=null`, `evidenceCount=attachments.size()`, `exceedsMaxPoints=false`, and `warningMessage=null`.

- [ ] **Step 5: Add controller endpoint**

Inject both command and detail services:

```java
@GetMapping("/{applicationId}")
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_VIEW_SELF)")
public ApiResponse<ApplicationSubmissionDetailView> getDetail(@PathVariable Long applicationId) {
    return ApiResponse.success(applicationSubmissionDetailApplicationService.getOwnedDetail(applicationId));
}
```

- [ ] **Step 6: Re-run Task 5 verification**

Run Task 5 command again.

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ApplicationSubmissionDetailView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/query/ApplicationAttachmentView.java whut-eval-application/src/main/java/edu/whut/eval/application/application/service/ApplicationSubmissionDetailApplicationService.java whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentApplicationSubmissionController.java whut-eval-app/src/test/java/edu/whut/eval/app/student/StudentApplicationSubmissionControllerWebMvcTest.java whut-eval-app/src/test/java/edu/whut/eval/app/application/ApplicationSubmissionDetailApplicationServiceTest.java
git commit -m "feat(b): add student application detail"
```

### Task 6: Security filters and IAM seed coverage

**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentApplicationSubmissionController.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/student/StudentApplicationWriteSecurityIntegrationTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerSeedAccountLoginIntegrationTest.java`

- [ ] **Step 1: Write failing security integration test**

Create `StudentApplicationWriteSecurityIntegrationTest` using the pattern from `MinimalEReadSecurityIntegrationTest`:

Before writing the imports, verify these current security classes exist and keep the imports aligned with their actual packages: `JwtConfigurationValidator`, `SecurityContextCurrentUserProvider`, and `SecurityContextUserAuthorizationContextAssembler`.

```java
@WebMvcTest(controllers = StudentApplicationSubmissionController.class)
@ContextConfiguration(classes = StudentApplicationWriteSecurityIntegrationTest.TestApplication.class)
@Import({
    StudentApplicationSubmissionController.class,
    SecurityConfiguration.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    JwtAuthenticationFilter.class,
    JwtTokenResolver.class,
    JwtClaimsParser.class,
    JwtClaimsToCurrentUserMapper.class,
    SecurityContextCurrentUserProvider.class,
    SecurityContextUserAuthorizationContextAssembler.class,
    JwtConfigurationValidator.class
})
@TestPropertySource(properties = {
    "infra.security.jwt.enabled=true",
    "infra.security.jwt.algorithm=HS256",
    "infra.security.jwt.issuer=whut-eval",
    "infra.security.jwt.audience=whut-eval-api",
    "infra.security.jwt.access-token-ttl-seconds=7200",
    "infra.security.jwt.refresh-token-ttl-seconds=604800",
    "infra.security.jwt.clock-skew-seconds=60",
    "infra.security.jwt.secret=test-jwt-secret-should-be-long-enough-1234567890",
    "infra.security.jwt.user-id-claim=uid",
    "infra.security.jwt.user-no-claim=uno",
    "infra.security.jwt.user-name-claim=uname",
    "infra.security.jwt.identity-claim=identity",
    "infra.security.jwt.roles-claim=roles",
    "infra.security.jwt.authorities-claim=authorities",
    "infra.security.jwt.token-type-claim=token_type",
    "infra.security.jwt.access-token-type=access",
    "infra.security.jwt.refresh-token-type=refresh"
})
class StudentApplicationWriteSecurityIntegrationTest {
    private static final String SECRET = "test-jwt-secret-should-be-long-enough-1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApplicationSubmissionCommandApplicationService applicationSubmissionCommandApplicationService;

    @MockBean
    private ApplicationSubmissionDetailApplicationService applicationSubmissionDetailApplicationService;

    @MockBean
    private UserAuthorizationContextLoader userAuthorizationContextLoader;

    @MockBean
    private AccessSessionService accessSessionService;

    @BeforeEach
    void setUpSecurityContext() {
        given(userAuthorizationContextLoader.load(any(UserAuthorizationContextLoadRequest.class))).willReturn(
                new UserAuthorizationContext(
                        1001L,
                        "2024305999",
                        "Test Student",
                        "student",
                        Set.of("student"),
                        Set.of("application.submit", "application.update", "application.view.self"),
                        List.of()
                )
        );
        ApplicationSubmissionView writeView = new ApplicationSubmissionView(
                1L,
                ApplicationSubmissionStatus.DRAFT,
                "申请标题",
                "申请说明",
                1,
                0L,
                null,
                null,
                false,
                null
        );
        ApplicationSubmissionDetailView detailView = detailView();
        given(applicationSubmissionCommandApplicationService.createDraft(any(CreateApplicationDraftCommand.class))).willReturn(writeView);
        given(applicationSubmissionCommandApplicationService.submit(any(SubmitApplicationCommand.class))).willReturn(writeView);
        given(applicationSubmissionCommandApplicationService.updateDraft(any(UpdateApplicationDraftCommand.class))).willReturn(writeView);
        given(applicationSubmissionCommandApplicationService.withdraw(any(WithdrawApplicationCommand.class))).willReturn(writeView);
        given(applicationSubmissionDetailApplicationService.getOwnedDetail(1L)).willReturn(detailView);
    }

    @Test
    void shouldRejectAnonymousCreateDraft() throws Exception {
        mockMvc.perform(post("/api/student/applications/drafts")
                .contentType(APPLICATION_JSON)
                .content(validCreateDraftJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnonymousDetailRead() throws Exception {
        mockMvc.perform(get("/api/student/applications/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowStudentWithSubmitAuthorityToCreateDraft() throws Exception {
        mockMvc.perform(post("/api/student/applications/drafts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities("application.submit"))
                .contentType(APPLICATION_JSON)
                .content(validCreateDraftJson()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowStudentWithSubmitAuthorityToSubmit() throws Exception {
        mockMvc.perform(post("/api/student/applications/1/submit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities("application.submit"))
                .contentType(APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"optionCode\":\"OPTION_A\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectStudentWithoutSubmitAuthorityOnSubmit() throws Exception {
        mockMvc.perform(post("/api/student/applications/1/submit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities("application.update"))
                .contentType(APPLICATION_JSON)
                .content("{\"expectedVersion\":1,\"optionCode\":\"OPTION_A\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectStudentWithoutUpdateAuthorityOnWithdraw() throws Exception {
        mockMvc.perform(post("/api/student/applications/1/withdraw")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities("application.submit"))
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"x\",\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectStudentWithoutUpdateAuthorityOnUpdateDraft() throws Exception {
        mockMvc.perform(put("/api/student/applications/1/draft")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities("application.submit"))
                .contentType(APPLICATION_JSON)
                .content("{\"title\":\"新标题\",\"description\":\"新说明\",\"attachmentFileIds\":[\"file-1\"],\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowStudentWithViewSelfAuthorityToReadDetail() throws Exception {
        mockMvc.perform(get("/api/student/applications/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities("application.view.self")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectStudentWithoutViewSelfAuthorityOnDetail() throws Exception {
        mockMvc.perform(get("/api/student/applications/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities("application.submit")))
                .andExpect(status().isForbidden());
    }
}
```

Add helpers in the same test class:

```java
private String validCreateDraftJson() {
    return """
            {
              "orgUnitId": 2010,
              "categoryCode": "INTELLECTUAL",
              "itemCode": "INTELLECTUAL_PAPER",
              "academicYear": "2025-2026",
              "term": "上学期",
              "title": "申请标题",
              "description": "申请说明",
              "attachmentFileIds": ["file-1"]
            }
            """;
}

private String tokenWithAuthorities(String... authorities) {
    Instant now = Instant.now();
    return Jwts.builder()
            .id("access-jti-" + UUID.randomUUID())
            .subject("1001")
            .issuer("whut-eval")
            .audience().add("whut-eval-api").and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(3600)))
            .claim("uid", 1001L)
            .claim("uno", "2024305999")
            .claim("uname", "Test Student")
            .claim("identity", "student")
            .claim("sid", "session-no-123")
            .claim("roles", List.of("student"))
            .claim("authorities", Arrays.asList(authorities))
            .claim("token_type", "access")
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
}

private ApplicationSubmissionDetailView detailView() {
    ApplicationSubmissionDetailView view = new ApplicationSubmissionDetailView();
    view.setApplicationId(1L);
    view.setOrgUnitId(2010L);
    view.setCategoryCode("INTELLECTUAL");
    view.setItemCode("INTELLECTUAL_PAPER");
    view.setAcademicYear("2025-2026");
    view.setTerm("上学期");
    view.setTitle("申请标题");
    view.setDescription("申请说明");
    view.setStatus(ApplicationSubmissionStatus.DRAFT);
    view.setCreatedAt(Instant.parse("2026-07-06T09:00:00Z"));
    view.setUpdatedAt(Instant.parse("2026-07-06T09:00:00Z"));
    view.setVersion(0L);
    view.setEvidenceCount(1);
    view.setExceedsMaxPoints(false);
    view.setAttachments(List.of(new ApplicationAttachmentView("file-1", "a.pdf", "application/pdf", 128L, 0)));
    return view;
}

@SpringBootConfiguration
@EnableAutoConfiguration
static class TestApplication {
}
```

Keep the `@WebMvcTest`, `@ContextConfiguration`, `@Import`, and `@TestPropertySource` structure aligned with the existing `MinimalEReadSecurityIntegrationTest`. Do not introduce a second security-test scaffold unless the copied pattern fails to start in this codebase.
The explicit `@Import` list is intentionally copied from the existing security integration test so the filter chain under test is visible in the test file and no `addFilters=false` shortcut is used.

- [ ] **Step 2: Write IAM seed coverage assertion**

In `AuthControllerSeedAccountLoginIntegrationTest`, assert the seed student effective authorities include:

```java
assertThat(authorities).contains("application.submit", "application.update", "application.view.self");
```

- [ ] **Step 3: Run tests to verify failure**

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentApplicationWriteSecurityIntegrationTest,AuthControllerSeedAccountLoginIntegrationTest test
```

Expected: fail because controller lacks `@PreAuthorize` and authenticated users without the required authority are not rejected yet.

- [ ] **Step 4: Add required annotations**

In `StudentApplicationSubmissionController`:

Before adding method-level `@PreAuthorize`, verify the controller class has no class-level `@PreAuthorize` or `@Secured`. If a class-level security annotation has been added since this plan was written, remove or reconcile it so each endpoint has one clear effective permission.

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_SUBMIT)")
@PostMapping("/drafts")
public ApiResponse<ApplicationSubmissionView> createDraft(@Valid @RequestBody CreateApplicationDraftRequest request) {
    ApplicationSubmissionView view = applicationSubmissionCommandApplicationService.createDraft(new CreateApplicationDraftCommand(
            request.getOrgUnitId(),
            request.getCategoryCode(),
            request.getItemCode(),
            request.getAcademicYear(),
            request.getTerm(),
            request.getTitle(),
            request.getDescription(),
            request.getAttachmentFileIds()
    ));
    return ApiResponse.success(view);
}

@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_UPDATE)")
@PutMapping("/{applicationId}/draft")
public ApiResponse<ApplicationSubmissionView> updateDraft(@PathVariable Long applicationId,
                                                          @Valid @RequestBody UpdateApplicationDraftRequest request) {
    ApplicationSubmissionView view = applicationSubmissionCommandApplicationService.updateDraft(new UpdateApplicationDraftCommand(
            applicationId,
            request.getTitle(),
            request.getDescription(),
            request.getAttachmentFileIds(),
            request.getExpectedVersion()
    ));
    return ApiResponse.success(view);
}

@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_SUBMIT)")
@PostMapping("/{applicationId}/submit")
public ApiResponse<ApplicationSubmissionView> submit(@PathVariable Long applicationId,
                                                     @Valid @RequestBody SubmitApplicationRequest request) {
    ApplicationSubmissionView view = applicationSubmissionCommandApplicationService.submit(
            new SubmitApplicationCommand(applicationId, request.getExpectedVersion(), request.getAppliedPoints(), request.getOptionCode())
    );
    return ApiResponse.success(view);
}

@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_UPDATE)")
@PostMapping("/{applicationId}/withdraw")
public ApiResponse<ApplicationSubmissionView> withdraw(@PathVariable Long applicationId,
                                                       @Valid @RequestBody WithdrawApplicationRequest request) {
    ApplicationSubmissionView view = applicationSubmissionCommandApplicationService.withdraw(
            new WithdrawApplicationCommand(applicationId, request.getReason(), request.getExpectedVersion())
    );
    return ApiResponse.success(view);
}
```

Do not modify `student-self-bootstrap.sql` in this implementation round. Minimal B requires deployments and local integration databases to run the full A-group seed, because `docs/team-delivery/group-a-identity-user-admin.sql` already grants `application.submit`, `application.update`, and `application.view.self` to the `STUDENT` role. Task 6 adds tests that prove this seed coverage. If the test fails against the full A-group seed, fix `docs/team-delivery/group-a-identity-user-admin.sql`; do not add a second partial bootstrap path.

Permission split is intentional: create draft and submit use `APPLICATION_SUBMIT`; update draft and withdraw use `APPLICATION_UPDATE`. Normal student seed accounts must have both authorities, while security tests keep them separable to prove each endpoint enforces the narrower command permission.

The inserted SELF scope rules are consumed by the existing A-group authorization pipeline: `RepositoryUserAuthorizationContextLoader` loads active `iam_scope_rule` rows into `UserAuthorizationContext`, `DefaultAuthorizationScopeEvaluator` filters them by permission code, `DefaultScopePredicateBuilder` builds a SELF predicate, and `MybatisPlusApplicationQueryRepository` applies that predicate through `ApplicationScopeSqlTranslator`. Minimal B does not add a new scope evaluator.

The existing test suite already uses JJWT in `SecurityProbeControllerWebMvcTest` and `MinimalEReadSecurityIntegrationTest`; no new dependency is required for `Jwts.builder()`.

- [ ] **Step 5: Re-run Task 6 verification**

Run Task 6 command again.

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/student/StudentApplicationSubmissionController.java whut-eval-app/src/test/java/edu/whut/eval/app/student/StudentApplicationWriteSecurityIntegrationTest.java whut-eval-app/src/test/java/edu/whut/eval/app/security/AuthControllerSeedAccountLoginIntegrationTest.java
git commit -m "fix(b): enforce student write authorities"
```

### Task 7: Focused regression and documentation updates

**Files:**
- Modify: `docs/superpowers/plans/proposal/2026-07-06/2026-07-06-minimal-b-application-write-closure.md` progress sections during execution.

- [ ] **Step 1: Run full focused regression**

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentApplicationSubmissionControllerWebMvcTest,StudentApplicationWriteSecurityIntegrationTest,ApplicationSubmissionCommandApplicationServiceTest,ApplicationSubmissionStateMachineTest,ApplicationSubmissionDetailApplicationServiceTest,ApplicationSubmissionFileIdIntegrationTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest,StudentQueryControllerWebMvcTest,AuthControllerSeedAccountLoginIntegrationTest,TeamDeliverySqlConsistencyTest test
```

Expected: `BUILD SUCCESS`, all specified tests pass, 0 failures, 0 errors, 0 skipped.

- [ ] **Step 2: Run security annotation text check**

```bash
rg -n "AutoConfigureMockMvc\\(addFilters = false\\)" whut-eval-app/src/test/java
```

Expected: no output.

- [ ] **Step 3: Verify existing student list endpoint security annotation**

Run:

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentQueryControllerSecurityAnnotationTest test
```

Expected: `BUILD SUCCESS`; the existing test proves `GET /api/student/query/applications` keeps `APPLICATION_VIEW_SELF`.

- [ ] **Step 4: Run safe SQL text checks**

```bash
rg -n "DROP TABLE|ENGINE=|CHARSET|COLLATE|COMMENT=" docs/team-delivery/group-b-student-application.safe-init.sql
```

Expected: no output.

- [ ] **Step 5: Update completion sections**

Update the active plan progress, evidence log, and completion summary with exact test output counts.

- [ ] **Step 6: Commit verification docs**

```bash
git add docs/superpowers/plans/proposal/2026-07-06/2026-07-06-minimal-b-application-write-closure.md
git commit -m "docs(b): record application write verification"
```

## Final Verification

Before reporting completion, run:

```bash
git status --short --branch
```

Expected: clean worktree on the implementation branch.

Then run:

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentApplicationSubmissionControllerWebMvcTest,StudentApplicationWriteSecurityIntegrationTest,ApplicationSubmissionCommandApplicationServiceTest,ApplicationSubmissionStateMachineTest,ApplicationSubmissionDetailApplicationServiceTest,ApplicationSubmissionFileIdIntegrationTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest,StudentQueryControllerWebMvcTest,AuthControllerSeedAccountLoginIntegrationTest,TeamDeliverySqlConsistencyTest test
```

Expected: `BUILD SUCCESS`, 0 failures, 0 errors, 0 skipped.

## Self-Review Checklist

- Spec §3 in-scope items map to Tasks 1-7.
- Spec §6 create/update/submit/withdraw/detail/list contracts map to Tasks 3-7; list is existing `StudentQueryController` behavior covered by `StudentQueryControllerWebMvcTest`.
- Spec §7 SQL safety maps to Task 1 and Task 7.
- Spec §8 permissions map to Task 6.
- Spec §9 error semantics map to Tasks 3, 4, and 6.
- Spec §10 acceptance criteria map to Tasks 1-7.
- No implementation starts until this plan is reviewed and moved out of proposal.

## Execution Evidence

- Task 1: `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=TeamDeliverySqlConsistencyTest test` passed with 9 tests, 0 failures, 0 errors, 0 skipped.
- Task 2: `mvn -pl whut-eval-app -am -DskipTests compile` passed.
- Task 3: `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ApplicationSubmissionStateMachineTest,ApplicationSubmissionCommandApplicationServiceTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest test` passed with 16 tests, 0 failures, 0 errors, 0 skipped.
- Task 4: `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ApplicationSubmissionCommandApplicationServiceTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest test` passed with 14 tests, 0 failures, 0 errors, 0 skipped. The expanded Task 4 regression including `ApplicationSubmissionStateMachineTest` and `ApplicationSubmissionFileIdIntegrationTest` passed with 23 tests, 0 failures, 0 errors, 0 skipped.
- Task 5: `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentApplicationSubmissionControllerWebMvcTest,ApplicationSubmissionDetailApplicationServiceTest test` passed with 7 tests, 0 failures, 0 errors, 0 skipped. The expanded Task 5 regression passed with 30 tests, 0 failures, 0 errors, 0 skipped.
- Task 6: `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentApplicationWriteSecurityIntegrationTest,AuthControllerSeedAccountLoginIntegrationTest test` passed with 13 tests, 0 failures, 0 errors, 0 skipped.
- Task 7 focused regression: `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentApplicationSubmissionControllerWebMvcTest,StudentApplicationWriteSecurityIntegrationTest,ApplicationSubmissionCommandApplicationServiceTest,ApplicationSubmissionStateMachineTest,ApplicationSubmissionDetailApplicationServiceTest,ApplicationSubmissionFileIdIntegrationTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest,StudentQueryControllerWebMvcTest,AuthControllerSeedAccountLoginIntegrationTest,TeamDeliverySqlConsistencyTest test` passed with 55 tests, 0 failures, 0 errors, 0 skipped.
- `rg -n "DROP TABLE|ENGINE=|CHARSET|COLLATE|COMMENT=" docs/team-delivery/group-b-student-application.safe-init.sql` returned no output.
- `mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentQueryControllerSecurityAnnotationTest test` passed with 2 tests, 0 failures, 0 errors, 0 skipped.
- `rg -n "AutoConfigureMockMvc\\(addFilters = false\\)" whut-eval-app/src/test/java/edu/whut/eval/app/student/StudentApplicationWriteSecurityIntegrationTest.java` returned no output. A broader scan of `whut-eval-app/src/test/java` still reports pre-existing unrelated tests that intentionally disable filters; those files were not modified by this Minimal B implementation.

## Handoff

Implementation complete on branch `feature/minimal-b-application-write`. The next required phase is code review; do not merge until review reports no blocking findings.
