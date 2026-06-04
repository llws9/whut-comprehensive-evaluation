# Team Delivery SQL And Docs Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `docs/team-delivery` and SQL scripts reflect the current implemented service boundaries without misleading teams or breaking local database initialization.

**Architecture:** Treat current Java code and mapper SQL as the runtime source of truth, and treat `docs/team-delivery` as delivery guidance. Fix executable SQL first, then align A group documentation, then label B/C/D/E target-state documents clearly where code is not implemented yet.

**Tech Stack:** Java 21, Spring Boot, MyBatis annotations, MySQL DDL/seed SQL, Markdown delivery docs.

---

### Task 1: Fix IAM Student Self Resource SQL

**Files:**
- Modify: `whut-eval-app/src/main/resources/sql/iam/student-self-bootstrap.sql`
- Modify: `whut-eval-app/src/main/resources/sql/iam/student-self-permissions-init.sql`
- Modify: `whut-eval-app/src/main/resources/sql/iam/student-self-scope-rules-init.sql`
- Reference: `docs/team-delivery/group-a-identity-user-admin.sql`

- [ ] **Step 1: Align permission inserts with current schema**

Update every `iam_permission` insert to include `id`, `permission_group`, and `created_at` because `docs/team-delivery/group-a-identity-user-admin.sql` defines those columns as `NOT NULL`.

```sql
INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
SELECT 5004, 'application.view.self', '查看本人申请', 'application', 'ACTIVE', '2026-05-01 09:30:03'
WHERE NOT EXISTS (
    SELECT 1
    FROM iam_permission
    WHERE permission_code = 'application.view.self'
);
```

- [ ] **Step 2: Align role permission inserts with current schema**

Update every `iam_role_permission` insert to include `id` and `created_at`.

```sql
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6004, r.id, p.id, '2026-05-01 09:40:03'
FROM iam_role r
JOIN iam_permission p ON p.permission_code = 'application.view.self'
WHERE r.role_code = 'STUDENT'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
```

- [ ] **Step 3: Align scope rule inserts with current schema**

Update every `iam_scope_rule` insert to include `id` and `created_at`; use deterministic IDs outside the A group seed range where the same rule is a supplement.

```sql
INSERT INTO iam_scope_rule (
    id,
    assignment_id,
    permission_code,
    scope_type,
    org_unit_id,
    category_code,
    item_code,
    expression_json,
    priority,
    status,
    created_at
)
SELECT
    81000 + ura.id,
    ura.id,
    'application.view.self',
    'SELF',
    NULL,
    NULL,
    NULL,
    JSON_OBJECT('owner', 'self'),
    10,
    'ACTIVE',
    '2026-06-05 00:00:00'
FROM iam_user_role_assignment ura
JOIN iam_role r ON r.id = ura.role_id
WHERE r.role_code = 'STUDENT'
  AND ura.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM iam_scope_rule sr
      WHERE sr.assignment_id = ura.id
        AND sr.permission_code = 'application.view.self'
        AND sr.scope_type = 'SELF'
        AND sr.status = 'ACTIVE'
  );
```

- [ ] **Step 4: Run text checks**

Run:

```bash
grep -R "role_code = 'student'" whut-eval-app/src/main/resources/sql/iam
grep -R "INSERT INTO iam_permission (permission_code" whut-eval-app/src/main/resources/sql/iam
grep -R "INSERT INTO iam_role_permission (role_id" whut-eval-app/src/main/resources/sql/iam
grep -R "INSERT INTO iam_scope_rule (" whut-eval-app/src/main/resources/sql/iam
```

Expected:
- The first three commands produce no output.
- The fourth command only shows inserts that include `id` in the following column list.

### Task 2: Fix Runtime-Level Team Delivery SQL Mismatches

**Files:**
- Modify: `docs/team-delivery/group-b-student-application.sql`
- Modify: `docs/team-delivery/group-d-score-finalization-import-export.sql`
- Modify: `docs/team-delivery/group-e-platform-governance-attachment-ai.sql`

- [ ] **Step 1: Align B group application primary key column**

Rename `application_submission.id` to `application_id`, update `INSERT` column lists, and preserve sample IDs.

```sql
CREATE TABLE `application_submission` (
  `application_id` BIGINT NOT NULL,
  ...
  PRIMARY KEY (`application_id`)
);
```

- [ ] **Step 2: Fix B group attachment seed mismatch**

Change attachment `23004` from `FILE-0005` to the active public template `FILE-0008`, with matching snapshot fields.

```sql
(23004, 21002, 'FILE-0008', 'PUBLIC_POOL', 2, '综测申请模板.pdf', 'application/pdf', 142000, 'attachments/2026/05/guide-template.pdf', '2026-05-13 09:22:00')
```

- [ ] **Step 3: Align E group file table with upload mapper**

Add `sha256 VARCHAR(128) DEFAULT NULL` to `file_asset`, and add `sha256` values to the seed insert column list and rows.

```sql
  `sha256` VARCHAR(128) DEFAULT NULL,
```

- [ ] **Step 4: Remove non-approved application references from D final component seed**

Keep only rows whose `source_type='APPLICATION'` references B group `application_submission.status='APPROVED'`. Preserve import rows because they do not depend on application approval status.

- [ ] **Step 5: Run SQL text checks**

Run:

```bash
grep -R "WHERE application_id" whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ApplicationSubmissionMapper.java
grep -n "`id` BIGINT NOT NULL" docs/team-delivery/group-b-student-application.sql
grep -n "sha256" docs/team-delivery/group-e-platform-governance-attachment-ai.sql
```

Expected:
- Mapper still references `application_id`.
- B SQL no longer defines `application_submission.id`.
- E SQL contains `sha256`.

### Task 3: Update A Group Delivery Documentation

**Files:**
- Modify: `docs/team-delivery/group-a-identity-user-admin.md`
- Modify: `docs/team-delivery/group-a-identity-user-admin-relay-checklist.md`
- Modify: `docs/team-delivery/iam-scope-rule-ui-storage-query-design.md`

- [ ] **Step 1: Correct A group table ownership**

Remove `evaluation_category` and `evaluation_item` from the A group owned/dependent table list, and state that they are E group owned read dependencies.

- [ ] **Step 2: Replace stale permission examples**

Replace `manage.review.view` with `application.review`, and replace `manage.students.view` with `application.view.assigned`.

- [ ] **Step 3: Correct role assignment API shape**

State that current A group implementation uses `roleCode` and `orgUnitId`, while `scopeRules` are created by `POST /api/admin/role-assignments/{assignmentId}/scope-rules`.

- [ ] **Step 4: Mark relay checklist as closed**

Rewrite stale "current problem" bullets for completed items to "已完成 / historical context", and keep remaining checklist items only if they are still valid.

### Task 4: Add Current-State Boundaries To B/C/D/E Docs

**Files:**
- Modify: `docs/team-delivery/README.md`
- Modify: `docs/team-delivery/group-b-student-application.md`
- Modify: `docs/team-delivery/group-c-review-workflow.md`
- Modify: `docs/team-delivery/group-d-score-finalization-import-export.md`
- Modify: `docs/team-delivery/group-e-platform-governance-attachment-ai.md`

- [ ] **Step 1: Add a status convention to README**

Add a section explaining:

```markdown
## 当前实现状态标识

- `CURRENT_IMPLEMENTED`：已与当前 Java Controller / Mapper / Service 对齐。
- `PARTIAL_IMPLEMENTED`：已有部分接口或表结构落地，但文档仍包含目标态内容。
- `TARGET_BLUEPRINT`：交付设计目标，不代表当前代码已经实现。
- `SQL_NEEDS_SYNC`：脚本曾发现与运行代码不一致，使用前必须先跑校验。
```

- [ ] **Step 2: Add per-group status notes**

Add concise status blocks to B/C/D/E documents. Example:

```markdown
> 当前状态：`PARTIAL_IMPLEMENTED`
>
> 本文档包含目标态接口。当前代码只实现了学生申请写链路的一部分、配置查询/计分接口和查询接口；未实现的接口不得直接作为联调契约。
```

- [ ] **Step 3: Run markdown text checks**

Run:

```bash
grep -R "manage.review.view\|manage.students.view" docs/team-delivery/*.md
grep -R "CURRENT_IMPLEMENTED\|PARTIAL_IMPLEMENTED\|TARGET_BLUEPRINT\|SQL_NEEDS_SYNC" docs/team-delivery/*.md
```

Expected:
- No stale `manage.*` permission examples remain in A/IAM docs.
- README and group docs include explicit status labels.

### Task 5: Verification

**Files:**
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/GroupAIdentitySqlSeedConsistencyTest.java`

- [ ] **Step 1: Run existing SQL consistency test**

Run:

```bash
mvn -q -pl whut-eval-app -Dtest=GroupAIdentitySqlSeedConsistencyTest test
```

Expected: build exits with code `0`.

- [ ] **Step 2: Run focused app tests affected by SQL/doc-adjacent code**

Run:

```bash
mvn -q -pl whut-eval-app -Dtest=FileUploadApplicationServiceTest,ApplicationSubmissionCommandApplicationServiceTest test
```

Expected: build exits with code `0`.

- [ ] **Step 3: Inspect git diff**

Run:

```bash
git diff -- docs/team-delivery whut-eval-app/src/main/resources/sql/iam docs/superpowers/plans/2026-06-05-team-delivery-sql-doc-sync.md
```

Expected:
- SQL changes are limited to schema/seed alignment.
- Markdown changes only clarify current state and remove stale examples.

