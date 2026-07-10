# Minimum Business Loop Demo Runbook

## Purpose

This runbook describes the smallest demonstrable business loop across the
A/E/B/C/D delivery slices:

```text
login
  -> student application draft and submit
  -> reviewer approve
  -> student final-record submit
  -> counselor final-record confirm
```

Use it before a team demo, release rehearsal, or cross-group handoff to verify
that the seeded IAM, organization scope, attachment, review, and final-record
contracts still work together.

## Scope

The runbook is intentionally narrow. It proves one happy path:

- A-group seed accounts can log in.
- E-group item and attachment seed data can support a student application.
- B-group application write tables receive one submitted application and fact.
- C-group review action moves the application to `APPROVED` and writes a review
  log.
- D-group final-record submission consumes the approved application fact and
  counselor confirmation moves the record to `CONFIRMED`.

It does not replace full feature acceptance for import/export, batch review,
public attachment governance, AI reports, or every data-scope variant.

## Required Seed State

Start from the same initialization order as
`full-seed-init-smoke-gate.md`:

1. `docs/team-delivery/group-a-identity-user-admin.sql`
2. `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql`
3. `docs/team-delivery/group-b-student-application.safe-init.sql`
4. `docs/team-delivery/group-c-review-workflow.safe-init.sql`
5. `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`

The minimum demo depends on these seeded records:

| Contract | Value | Source |
|---|---|---|
| Student credential | `2022010101` / `ChangeMe123!` | A-group seed user `1001` |
| Counselor credential | `T20260001` / `ChangeMe123!` | A-group seed user `1010` |
| Student org unit | `2010` | A-group `org_membership` |
| Counselor scope root | `2002` | D-group `score.confirm.assigned` scope rules |
| Academic year | `2025-2026` | B/D demo data convention |
| Term | `上学期` | B-group application contract |
| Category/item | `INTELLECTUAL` / `INTELLECTUAL_PAPER` | E-group item seed |
| Option code | `PAPER_CORE_FIRST_AUTHOR` | B/D option payload contract |
| Demo points | `2.00` | Below E item cap `6.00` |
| Manual attachment example | `FILE-0001` or `FILE-0008` | E-group `ACTIVE` / public seed files |
| Confirm permission | `score.confirm.assigned` | D-group safe-init permission |

`MinimumBusinessLoopSmokeIntegrationTest` uses `FILE-SMOKE-001` through a
test-only attachment resolver. For a real safe-init database, use an existing
seed file such as `FILE-0001` for student `1001`, or a published `ALL` public
file such as `FILE-0008`.

## Manual Demo Flow

The exact host and token handling depend on the local runtime profile. The
examples below use `http://localhost:8080` and assume the caller stores the
returned access tokens and response IDs between steps.

### 1. Login As Student

```bash
curl -s -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"credential":"2022010101","password":"ChangeMe123!"}'
```

Expected result:

- HTTP `200`
- `code` is `OK`
- `data.accessToken` and `data.refreshToken` are non-empty

Use `data.accessToken` as the student bearer token in steps 2, 3, and 5.

### 2. Create Application Draft

```bash
curl -s -X POST "http://localhost:8080/api/student/applications/drafts" \
  -H "Authorization: Bearer <student-access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "orgUnitId": 2010,
    "categoryCode": "INTELLECTUAL",
    "itemCode": "INTELLECTUAL_PAPER",
    "academicYear": "2025-2026",
    "term": "上学期",
    "title": "最小演示闭环论文申请",
    "description": "用于验证登录、申请、审核、最终成绩汇总与确认的演示数据",
    "attachmentFileIds": ["FILE-0001"]
  }'
```

Expected result:

- `data.status` is `DRAFT`
- `data.attachmentCount` is `1`
- Save `data.applicationId` and `data.version`

If the attachment is rejected, confirm that the selected `fileId` is `ACTIVE`
and either uploaded by student user `1001` or published in the public attachment
pool with `scope_type = 'ALL'`.

### 3. Submit Application

Use the draft `applicationId` and `version` from step 2.

```bash
curl -s -X POST "http://localhost:8080/api/student/applications/<applicationId>/submit" \
  -H "Authorization: Bearer <student-access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "expectedVersion": <draft-version>,
    "appliedPoints": 2.00,
    "optionCode": "PAPER_CORE_FIRST_AUTHOR"
  }'
```

Expected result:

- `data.status` is `SUBMITTED`
- `data.appliedPoints` is `2.00`
- Save the returned `data.version`

### 4. Login As Counselor And Approve

```bash
curl -s -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"credential":"T20260001","password":"ChangeMe123!"}'
```

Use `data.accessToken` as the counselor bearer token.

```bash
curl -s -X POST "http://localhost:8080/api/review/applications/<applicationId>/approve" \
  -H "Authorization: Bearer <counselor-access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "expectedVersion": <submitted-version>,
    "comment": "通过"
  }'
```

Expected result:

- `data.status` is `APPROVED`
- `data.reviewLogId` is present
- Save the returned `data.version` only for audit; the final-record submit step
  works by academic year and current student context.

### 5. Submit Final Record As Student

For a first submission in this academic year, use `expectedVersion = 0`.

```bash
curl -s -X POST "http://localhost:8080/api/student/final-records/submit" \
  -H "Authorization: Bearer <student-access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "academicYear": "2025-2026",
    "expectedVersion": 0
  }'
```

Expected result:

- `data.status` is `SUBMITTED`
- `data.grandTotal` is `2.00`
- Save `data.finalRecordId` and `data.version`

### 6. Confirm Final Record As Counselor

Use the `finalRecordId` and `version` from step 5.

```bash
curl -s -X POST "http://localhost:8080/api/admin/final-records/<finalRecordId>/confirm" \
  -H "Authorization: Bearer <counselor-access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "expectedVersion": <final-record-version>,
    "comment": "确认无误"
  }'
```

Expected result:

- `data.status` is `CONFIRMED`
- `data.confirmComment` is `确认无误`

## Automated Acceptance Gate

Run the automated business-loop smoke gate from the repository root:

```bash
mvn -pl whut-eval-app -am -Dtest=MinimumBusinessLoopSmokeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

This test starts from the A/B/C/D safe-init scripts in an H2 MySQL-mode
database, logs in the student and counselor through `/api/auth/login`, and then
executes the same business transition through application services. It verifies:

- student and counselor seed credentials issue tokens;
- the application reaches `APPROVED`;
- exactly one `application_fact` row is written;
- one approve review log is written;
- final record reaches `CONFIRMED`;
- the final component points back to the approved application;
- final-record `grand_total` is `2.00`.

The GitHub workflow `Full Seed Init Smoke` runs this as the `Minimum business
loop` job on PRs and pushes that touch team-delivery docs, smoke tests,
application code, or the workflow itself.

## Failure Triage

| Symptom | Likely source | Check |
|---|---|---|
| Login fails for seed accounts | A-group seed or password hash drift | `group-a-identity-user-admin.sql` and `AuthControllerSeedAccountLoginIntegrationTest` |
| Draft creation says attachment is missing or unauthorized | E seed file or attachment resolver mismatch | `file_asset`, `public_attachment_entry`, selected `attachmentFileIds` |
| Submit fails with version conflict | Caller used stale `expectedVersion` | Use the `version` from the immediately previous response |
| Review approve returns forbidden | A/C scope or review permission drift | Counselor roles, `application.review`, resource org path |
| Final submit has no component or wrong total | B/D approved-fact aggregation drift | `application_submission`, `application_fact`, `final_component_score` |
| Final confirm list/detail cannot see the record | D `score.confirm.assigned` scope drift | `iam_scope_rule` rows `8019`/`8020`, org unit root `2002` |

## Merge Gate

Before merging changes that affect this flow, run:

```bash
mvn -pl whut-eval-app -am -Dtest=MinimumBusinessLoopSmokeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

If the change also touches team safe-init SQL, seed IDs, IAM scopes, or the
delivery initialization order, also run the full seed gates from
`full-seed-init-smoke-gate.md`.
