# Minimal C Review Workflow Design

## 1. Goal

Close the smallest C-group review loop after Minimal B.

The target is a stable reviewer-facing contract for:

- listing reviewable applications;
- reading one application review detail;
- approving, returning, or rejecting a submitted application;
- writing an immutable review log for every review action;
- enforcing A-group review permissions and scope rules.

This is not the full C-group target state. Dashboard summaries, batch approval, attachment access URL aggregation, review metadata filters, and complex multi-node workflows remain out of scope for this round.

## 2. Current Baseline

The current code now provides the B-side write and read foundation:

- student create/update/submit/withdraw endpoints under `/api/student/applications`;
- student-owned detail endpoint `GET /api/student/applications/{applicationId}`;
- application states `DRAFT`, `SUBMITTED`, `RETURNED`, `APPROVED`, `REJECTED`, `WITHDRAWN`;
- submitted applications carry a persisted `application_fact` scoring snapshot;
- application attachments are stored as application-bound snapshots;
- A-group IAM seed includes `application.review`, `review.task.view`, and review scope rules;
- existing application query infrastructure can evaluate A-group authorization scope rules for `application.review`.

The current code does not yet provide `/api/review/**` controllers, review action services, or `application_review_log` safe-init SQL. The `ApplicationSubmission` aggregate also has no review transitions yet: it exposes only `createDraft`, `updateDraft`, `submit`, and `withdraw`. Minimal C adds the review transitions (§4.1).

## 3. Scope

### 3.1 In Scope

Minimal C covers:

- `GET /api/review/applications`
- `GET /api/review/applications/{applicationId}`
- `POST /api/review/applications/{applicationId}/approve`
- `POST /api/review/applications/{applicationId}/return`
- `POST /api/review/applications/{applicationId}/reject`
- non-destructive `application_review_log` safe-init SQL;
- service-level owner/scope validation for single application resources;
- state transitions:
  - `SUBMITTED -> APPROVED`
  - `SUBMITTED -> RETURNED`
  - `SUBMITTED -> REJECTED`
- one `application_review_log` row per review action;
- focused tests for state transitions, review logs, security annotations, security filters, SQL safety, and B/C integration.

### 3.2 Out of Scope

- `GET /api/review/tasks/summary`
- `GET /api/review/meta/grades`
- `GET /api/review/applications/{applicationId}/attachments` as a separate endpoint
- `GET /api/review/applications/{applicationId}/logs` as a separate endpoint
- batch approval
- multi-step node assignment
- review task claiming
- final score aggregation and D-group exports
- direct file access URL generation in C
- changing A-group current-user or scope-rule APIs

Minimal C detail may include already-bound attachment metadata from B, but it must not expose `storageKey`, `bucket`, `objectKey`, or raw storage internals. Preview/download still goes through E file read endpoints.

## 4. Chosen Approach

Reuse the existing `ApplicationSubmission` aggregate and application query foundation.

### 4.1 Aggregate transitions (required domain change)

The current `ApplicationSubmission` aggregate only exposes `createDraft`, `updateDraft`, `submit`, and `withdraw`; it has no review transitions. Minimal C must add three transition methods on the aggregate itself, consistent with the existing pattern where every state change lives in the aggregate and returns a new immutable instance:

- `approve(long expectedVersion)` -> `SUBMITTED -> APPROVED`
- `returnForFix(long expectedVersion)` -> `SUBMITTED -> RETURNED`
- `reject(long expectedVersion)` -> `SUBMITTED -> REJECTED`

Each method must:

- call a shared `assertReviewable()` guard that throws `ConflictException` (mapped to `BIZ-4090`, see §6) when `status != SUBMITTED`, matching the frozen C target semantics for non-`SUBMITTED` review attempts;
- call the existing `assertExpectedVersion(expectedVersion)` guard (which throws `ConflictException` -> `BIZ-4090` on mismatch);
- return a new `ApplicationSubmission` with `version + 1` and `updatedAt = Instant.now()`.

Review transition logic must not be hand-written in the service layer. The service orchestrates authorization, scope checks, log construction, and persistence; the state change stays in the aggregate.

### 4.2 Scoring snapshot preservation (required)

`MybatisPlusApplicationSubmissionRepository.save()` unconditionally calls `replaceScoringSnapshot(...)`, which deletes `application_fact` and only re-inserts when the in-memory aggregate still carries `scoringSnapshot`. Therefore every review transition method **must** construct the new aggregate instance carrying the existing `scoringSnapshot` (and `submittedAt`) forward. If a transition drops `scoringSnapshot`, approving an application silently wipes `application_fact`, which D-group consumes.

This is a hard requirement: the execution plan must include a test that approves a submitted application and asserts the `application_fact` row (score + snapshot fields) still exists and is unchanged after approve.

### 4.3 Services and persistence

Add review-specific application services around the current aggregate:

- a command service for `approve`, `return`, and `reject`;
- a query service for reviewer-visible list/detail;
- a repository/mapper for review logs (`@Options(useGeneratedKeys = true, keyProperty = "id")` so the generated `reviewLogId` can be returned per §5);
- a safe-init SQL for `application_review_log`.

This avoids replacing B's submission model and keeps C's ownership boundary narrow: C can only move `SUBMITTED` applications into review terminal or returned states and append review logs.

## 5. HTTP Contracts

All responses use `ApiResponse<T>`.

### 5.1 List Review Applications

Endpoint:

```http
GET /api/review/applications
```

Auth:

- authenticated user;
- must have `application.review`;
- must apply A-group scope rules for `application.review`.

Query parameters:

| Name | Type | Required | Default | Notes |
|---|---|---:|---|---|
| `pageNo` | `long` | no | `1` | must be `>= 1` |
| `pageSize` | `long` | no | `20` | bounded by existing query rules |
| `academicYear` | `string` | no | - | exact match |
| `categoryCode` | `string` | no | - | exact match |
| `itemCode` | `string` | no | - | exact match |
| `status` | `string` | no | `SUBMITTED` | allowed: `SUBMITTED`, `APPROVED`, `RETURNED`, `REJECTED` |
| `keyword` | `string` | no | - | applicant user name or user number fuzzy search |
| `orgUnitId` | `long` | no | - | optional scope-constrained filter |

Response data:

```json
{
  "total": 1,
  "records": [
    {
      "applicationId": 21013,
      "applicantUserId": 1001,
      "applicantUserName": "张三",
      "applicantUserNo": "20210001",
      "orgUnitId": 2010,
      "orgUnitName": "计算机 2101 班",
      "categoryCode": "INTELLECTUAL",
      "itemCode": "INTELLECTUAL_PAPER",
      "title": "期刊论文录用申请",
      "status": "SUBMITTED",
      "submittedAt": "2026-07-06T10:00:00Z",
      "currentReviewNode": "SINGLE_REVIEW"
    }
  ]
}
```

Minimal C must introduce a review-specific list query and response model. Do not return the existing `ApplicationRecordView` directly: it only carries scope-checking fields (`applicationId`, applicant/org ids, `orgPath`, category/item) and does not satisfy the C-3 contract above.

Implementation constraints:

- introduce a dedicated query object such as `ReviewApplicationPageQuery` that supports all C-3 filters above, especially `status`, `academicYear`, and `keyword`;
- introduce a dedicated `ReviewApplicationListItemView` (or equivalent response DTO) with the fields shown above;
- reuse the existing A-group scope evaluation and SQL translator where possible, but extend the review list query row/SQL to load the reviewer-facing fields from `application_submission` plus applicant and org metadata;
- keep `currentReviewNode` deterministic. Minimal C has no multi-node assignment, so the value is the literal `SINGLE_REVIEW`;
- do not include attachment storage internals or file storage fields in the list response.

### 5.2 Review Detail

Endpoint:

```http
GET /api/review/applications/{applicationId}
```

Auth:

- must have `application.review`;
- service must verify the single application is visible under the reviewer's A-group scope rules.

Response data:

```json
{
  "application": {
    "applicationId": 21013,
    "status": "SUBMITTED",
    "title": "期刊论文录用申请",
    "description": "论文已收到录用通知，申请智育加分。",
    "categoryCode": "INTELLECTUAL",
    "itemCode": "INTELLECTUAL_PAPER",
    "academicYear": "2025-2026",
    "term": "上学期",
    "submittedAt": "2026-07-06T10:00:00Z",
    "version": 1,
    "scoringSnapshot": {
      "optionCode": "PAPER_CORE_FIRST_AUTHOR",
      "appliedPoints": 2.00,
      "maxPoints": 6.00,
      "evidenceCount": 1,
      "exceedsMaxPoints": false,
      "warningMessage": null
    }
  },
  "applicant": {
    "userId": 1001,
    "userNo": "20210001",
    "userName": "张三",
    "orgUnitId": 2010,
    "orgUnitName": "计算机 2101 班"
  },
  "attachments": [
    {
      "fileId": "file-1",
      "originalFilename": "a.pdf",
      "contentType": "application/pdf",
      "size": 128,
      "sortNo": 0
    }
  ],
  "reviewLogs": [
    {
      "reviewLogId": 31001,
      "action": "RETURN",
      "reviewerId": 1010,
      "reviewerName": "辅导员一",
      "reviewRole": "COUNSELOR",
      "reason": "请补充证明材料。",
      "reviewedAt": "2026-07-06T10:05:00Z"
    }
  ],
  "allowedActions": ["APPROVE", "RETURN", "REJECT"]
}
```

Minimal C review detail must align with the frozen C-4 top-level shape: `application`, `applicant`, `attachments`, `reviewLogs`, and `allowedActions`. It must introduce a dedicated `ReviewApplicationDetailView` (or equivalent response DTO), not expose the B student detail DTO directly.

Detail rules:

- `application.scoringSnapshot` carries the B scoring snapshot needed by reviewers while preserving the C-4 top-level shape;
- `applicant` may expose user id, number, name, and org summary because reviewers need to identify the applicant. Student-facing detail must continue to hide applicant identity fields;
- `reviewLogs` is populated from `application_review_log` for the target application, sorted by `reviewed_at ASC, id ASC`; it is empty only when no log rows exist;
- `allowedActions` is `["APPROVE", "RETURN", "REJECT"]` only when the current status is `SUBMITTED` and the reviewer has resource access; otherwise it is empty;
- attachments must not expose `storageKey`, `bucket`, `objectKey`, raw OSS URLs, or any other storage internals.

### 5.3 Approve

Endpoint:

```http
POST /api/review/applications/{applicationId}/approve
```

Request:

```json
{
  "expectedVersion": 1,
  "comment": "材料完整，予以通过。"
}
```

`comment` is optional for approve, matching the frozen C target (C-7). Do not rename it to `reason`; return/reject use required `reason` instead.

Behavior:

- application must exist;
- reviewer must have `application.review` and resource scope access;
- current status must be `SUBMITTED`;
- version must match;
- submission status becomes `APPROVED`;
- append log action `APPROVE`;
- response returns the review action result (see §5.6).

### 5.4 Return

Endpoint:

```http
POST /api/review/applications/{applicationId}/return
```

Request:

```json
{
  "expectedVersion": 1,
  "reason": "请补充证明材料。"
}
```

Behavior:

- same authorization and version rules as approve;
- current status must be `SUBMITTED`;
- reason is required and non-blank;
- submission status becomes `RETURNED`;
- append log action `RETURN`;
- B-side update flow can edit returned applications.

### 5.5 Reject

Endpoint:

```http
POST /api/review/applications/{applicationId}/reject
```

Request:

```json
{
  "expectedVersion": 1,
  "reason": "证明材料不足，不予认定。"
}
```

Behavior:

- same authorization and version rules as approve;
- current status must be `SUBMITTED`;
- reason is required and non-blank;
- submission status becomes `REJECTED`;
- append log action `REJECT`;
- rejected applications do not block a new B application for the same student/item/year/term.

### 5.6 Review Action Response

All three actions (`approve`, `return`, `reject`) return the same shape, aligned with the frozen C target success `data` (C-7/C-8/C-9). Minimal C must not narrow this to only status/version, because the target fields are frozen:

```json
{
  "applicationId": 21013,
  "status": "APPROVED",
  "version": 2,
  "reviewLogId": 31001,
  "reviewedAt": "2026-07-06T10:05:00Z"
}
```

- `status` is fixed per action (`APPROVED` / `RETURNED` / `REJECTED`).
- `reviewLogId` is the generated primary key of the `application_review_log` row written in the same transaction; the log mapper must use generated keys so this id is available.
- `reviewedAt` is the log's `reviewed_at` in UTC, consistent with existing repositories.

## 6. State And Error Semantics

Allowed C transitions:

| From | Action | To |
|---|---|---|
| `SUBMITTED` | `APPROVE` | `APPROVED` |
| `SUBMITTED` | `RETURN` | `RETURNED` |
| `SUBMITTED` | `REJECT` | `REJECTED` |

Rejected C transitions:

- `DRAFT`, `RETURNED`, `APPROVED`, `REJECTED`, and `WITHDRAWN` cannot be reviewed.

Action naming is frozen as command verbs: `APPROVE`, `RETURN`, and `REJECT`. These values are used for `application_review_log.action`, detail `reviewLogs[].action`, `allowedActions`, tests, and service commands. Response `status` remains the state name (`APPROVED`, `RETURNED`, `REJECTED`). Do not mix past-tense state names into the log action enum.

Error mapping:

| Scenario | HTTP | Code | Thrown as |
|---|---:|---|---|
| missing/blank reason for return/reject | 400 | `VAL-4001` | `ValidationException` |
| review attempt on non-`SUBMITTED` status | 409 | `BIZ-4090` | `ConflictException` |
| version mismatch | 409 | `BIZ-4090` | `ConflictException` |
| application not found | 404 | `RES-4040` | `ResourceNotFoundException` |
| no review authority or out of scope | 403 | `AUTH-4030` | `AccessDeniedAppException` (or method-security denial) |

Note on the transition guard: the existing aggregate throws `ValidationException` (-> `VAL-4001`, 400) for illegal states such as non-editable `updateDraft`. Review transitions must **not** reuse that. Per §4.1, the review guard `assertReviewable()` throws `ConflictException` so a non-`SUBMITTED` review attempt returns `409 BIZ-4090`, matching the frozen C target (C-7/C-8/C-9). Missing/blank `reason` is a request-level validation and stays `400 VAL-4001`.

## 7. Persistence

### 7.1 Safe Init SQL

Create:

`docs/team-delivery/group-c-review-workflow.safe-init.sql`

The safe-init SQL must:

- create `application_review_log` if missing;
- not contain `DROP TABLE`;
- not contain `ENGINE=`, `CHARSET`, `COLLATE`, or `COMMENT=`;
- use `BIGINT NOT NULL AUTO_INCREMENT` for `id`;
- be re-runnable without deleting existing review logs;
- be executable in MySQL and H2 MySQL-mode tests.

Minimal table:

```sql
CREATE TABLE IF NOT EXISTS `application_review_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `application_id` BIGINT NOT NULL,
  `action` VARCHAR(32) NOT NULL,
  `reviewer_id` BIGINT NOT NULL,
  `review_role` VARCHAR(64) NOT NULL,
  `reason` VARCHAR(1000) DEFAULT NULL,
  `reviewed_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_application_review_log_application_id` (`application_id`),
  KEY `idx_application_review_log_reviewer_id` (`reviewer_id`)
);
```

### 7.2 Review Log Rules

- Append one log row inside the same transaction as the application status update.
- `action` must be one of the command verbs `APPROVE`, `RETURN`, or `REJECT`.
- The log mapper must use generated keys (`@Options(useGeneratedKeys = true, keyProperty = "id")`) so the response can return `reviewLogId` (see §5.6).
- `reviewer_id` is the current authenticated user id.
- `review_role` uses a deterministic source, in this precedence: (1) `UserAuthorizationContext.getIdentity()` when non-blank; (2) otherwise a stable, sorted-first role from the context roles; (3) otherwise the literal `UNKNOWN`. Do not use "the first role from an unordered `Set`" — that produces non-deterministic audit data. The plan phase picks the exact rule but it must be deterministic.
- `reviewed_at` uses UTC conversion consistent with existing repositories.

Persistence note: `ApplicationSubmissionMapper.updateWithVersion` only updates `org_unit_id/category_code/item_code/academic_year/term/title/description/status/submitted_at/updated_at/version` and never touches `applicant_user_id` or `created_at`. Review transitions must therefore only change `status/version/updated_at` and must not depend on any immutable column being rewritten. The review save must go through this existing optimistic-lock update; a zero-row update means a concurrent change and must surface as `BIZ-4090`.

## 8. Authorization And Scope

Controller methods must use:

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).APPLICATION_REVIEW)")
```

Service-level resource checks are still required. Method security alone only proves the user has the review authority; it does not prove the user can see the specific application.

Minimal C should reuse existing A-group scope evaluation infrastructure where possible:

- `UserAuthorizationContextAssembler`
- `AuthorizationScopeEvaluator`
- `ResourceScopeAccessEvaluator.canAccessApplication(...)`
- existing application query repository and scope translator for list filtering

For single detail/action checks, the service must evaluate whether the current reviewer has access to the target application via `ResourceScopeAccessEvaluator.canAccessApplication(...)`, denying with `AccessDeniedAppException` (`AUTH-4030`) when not allowed.

Critical constraint — the resource context must carry `orgPath`. `DefaultResourceScopeAccessEvaluator` matches `ORG_SUBTREE` scopes by testing `resourceContext.getOrgPath()` for the segment `"/" + orgUnitId + "/"`. The `ApplicationSubmission` aggregate carries only `orgUnitId`, not `orgPath`. If the single-resource `ApplicationResourceContext` is built from the bare aggregate, `orgPath` is null and any reviewer whose visibility comes from an `ORG_SUBTREE` rule is wrongly denied. Therefore the single-application access check must build `ApplicationResourceContext` from a source that includes `org_path` — i.e. load the target through the scope-aware application query repository / query row (which already selects `org_path AS orgPath`), the same way list filtering resolves resource contexts. The bare aggregate is acceptable only for the state transition itself, not for the subtree scope decision.

If no direct single-resource helper exists, the execution plan must add a small application-access validator that reuses the same scope predicate semantics (and the `orgPath`-bearing resource context) rather than hand-writing role branches. The plan must include a test proving an `ORG_SUBTREE` reviewer can access an in-subtree application and is denied an out-of-subtree one.

## 9. Tests

Required focused coverage:

- safe-init SQL shape and rerun idempotence for `application_review_log`, added to `TeamDeliverySqlConsistencyTest` following the existing pattern: one `shouldProvideNonDestructiveCGroupSafeInitSql` method (asserts `CREATE TABLE IF NOT EXISTS`, no `DROP TABLE`/`ENGINE=`/`CHARSET`/`COLLATE`/`COMMENT=`, `AUTO_INCREMENT` on `id`) and one H2 MySQL-mode rerun test that inserts a runtime log row and asserts it survives a second run;
- review list contract:
  - supports `status`, `academicYear`, `categoryCode`, `itemCode`, `keyword`, and `orgUnitId` filters;
  - returns the dedicated reviewer list fields, including applicant name/number, org name, `status`, `submittedAt`, and deterministic `currentReviewNode`;
  - does not return storage internals;
- review detail contract:
  - uses the C-4 top-level shape (`application`, `applicant`, `attachments`, `reviewLogs`, `allowedActions`);
  - populates persisted review logs with command-verb actions;
  - hides storage internals;
- review state machine on the aggregate:
  - approve `SUBMITTED -> APPROVED`;
  - return `SUBMITTED -> RETURNED`;
  - reject `SUBMITTED -> REJECTED`;
  - each review action from a non-`SUBMITTED` state throws `ConflictException` (-> `BIZ-4090`);
- version conflict returns `BIZ-4090`;
- **scoring snapshot preservation: approving a submitted application leaves `application_fact` (score + snapshot fields) intact** (guards the §4.2 save-path trap);
- every action appends exactly one review log in the same transaction and the response carries the generated `reviewLogId`;
- action service rejects out-of-scope application with `AccessDeniedAppException`;
- `ORG_SUBTREE` reviewer can access an in-subtree application and is denied an out-of-subtree one (guards the §8 `orgPath` requirement);
- controller method annotations require `APPLICATION_REVIEW`;
- security filter integration rejects users without `application.review`;
- existing Minimal B tests still pass, especially returned application update and rejected/withdrawn active-claim behavior.

## 10. Acceptance Criteria

Minimal C is complete when:

- reviewer can list reviewable applications under A-group scope;
- reviewer can load one review detail under A-group scope;
- reviewer can approve, return, and reject submitted applications;
- every review action persists an `application_review_log` row;
- non-submitted applications cannot be reviewed;
- unauthorized or out-of-scope reviewers are denied;
- B-side returned application edit behavior remains intact;
- D-side can continue using `APPROVED` as the only score-eligible application state, and approve does not destroy the `application_fact` snapshot D consumes;
- focused Maven regression passes.

## 11. Open Decisions For Plan Phase

Resolved by this spec (do not re-open):

- action responses use the frozen shape in §5.6 (`applicationId/status/version/reviewLogId/reviewedAt`), not a reduced status-only view;
- review list uses a dedicated reviewer query and `ReviewApplicationListItemView`-style DTO; do not expose `ApplicationRecordView` directly (§5.1);
- review detail uses the C-4 top-level shape and a dedicated `ReviewApplicationDetailView`-style DTO; do not expose the B student detail DTO directly (§5.2);
- detail `reviewLogs` are populated in Minimal C; C-6 as a standalone logs endpoint remains out of scope (§5.2);
- review log actions are command verbs `APPROVE`, `RETURN`, and `REJECT`, while response statuses are state names (§6, §7.2);
- non-`SUBMITTED` review attempts map to `BIZ-4090` via `ConflictException` (§4.1, §6);
- `review_role` uses the deterministic precedence in §7.2 (never "first role from an unordered set");
- single-resource scope checks use an `orgPath`-bearing resource context loaded via the scope-aware query path (§8).

The execution plan must still decide:

- the concrete class/placement of the single-application access validator (new helper vs. extending an existing repository), given it must supply `orgPath`;
- the concrete mapper/repository placement for the review list/detail read models, given they need reviewer-facing fields beyond the existing `ApplicationRecordView`.

## 12. Self-Review

- This spec keeps C minimal and does not implement full target-state batch/dashboard endpoints.
- It preserves B ownership of student write behavior and D ownership of final score aggregation.
- It adds review transitions on the `ApplicationSubmission` aggregate rather than leaking state logic into the service layer (§4.1).
- It guards the two silent-failure traps found in review: the save path wiping `application_fact` (§4.2) and `ORG_SUBTREE` scope denial from a missing `orgPath` (§8), each with a required test.
- It aligns action responses and non-`SUBMITTED`/version error codes with the frozen C target instead of narrowing them (§5.6, §6).
- It requires safe SQL and tests before production code.
- It treats A-group scope as the source of truth for reviewer visibility.
