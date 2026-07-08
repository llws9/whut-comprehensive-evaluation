# Minimal D Final Record Workflow Design

## 1. Goal

Build the smallest D-group final-record loop after Minimal C.

Minimal D closes the backend path from approved application facts to final-record snapshots:

- safe, repeatable initialization for `final_record` and `final_component_score`;
- student reads their final-record header and components;
- student submits their final record;
- admin/reviewer reads organization-scoped final records and details;
- admin/reviewer confirms a submitted final record;
- all admin single-record access is protected by whole-record organization scope rules.

This is not the full D-group target state. Imports, Excel exports, unsubmitted-student lists, batch operations, and platform-window enforcement remain out of scope for this round.

## 2. Current Baseline

The codebase now provides:

- Minimal B application write/read foundation;
- Minimal C review workflow and `APPROVED` application state;
- persisted `application_fact` scoring snapshots for submitted applications;
- score query infrastructure under `/api/student/query/scores` and `/api/admin/query/scores`;
- `ScoreScopeSqlTranslator`, `DefaultScoreScopePredicateBuilder`, and `ResourceScopeAccessEvaluator.canAccessScore(...)`;
- score scope rules can express category/item restrictions, but Minimal D final-record admin access must authorize an entire aggregate record;
- A-group seed permissions for `final.submit.self`, `final.view.self`, `score.view.assigned`, and `score.export.assigned`.

The current code does not yet provide:

- non-destructive D safe-init SQL;
- `final_record` / `final_component_score` domain models or repositories;
- `/api/student/final-records/**` or `/api/admin/final-records/**` controllers;
- final-record submission or confirmation transitions;
- final-record-specific resource scope evaluation; the current `ResourceScopeAccessEvaluator` only exposes application and score resource methods, and `ScoreResourceContext` carries category/item fields that are unsafe for whole-record final-record authorization;
- a `score.confirm.assigned` permission constant/seed entry:
  - no `AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED`;
  - no `iam_permission` row for `score.confirm.assigned`;
  - no role-permission bindings for counselor or college reviewer roles;
  - no final-record confirmation scope rules for the existing counselor or college reviewer assignments.

## 3. Scope

### 3.1 In Scope

Minimal D covers:

- `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`;
- `GET /api/student/final-records/{academicYear}`;
- `GET /api/student/final-records/{academicYear}/components`;
- `POST /api/student/final-records/submit`;
- `GET /api/admin/final-records`;
- `GET /api/admin/final-records/{recordId}`;
- `POST /api/admin/final-records/{recordId}/confirm`;
- `final_record` state transitions:
  - `DRAFT -> SUBMITTED`;
  - `SUBMITTED -> CONFIRMED`;
- aggregation from `application_submission.status = 'APPROVED'` plus `application_fact`;
- admin visibility using whole-record organization scope, not component-level partial visibility;
- focused tests for SQL safety, aggregation, state transitions, scope behavior, controller contracts, and security annotations/filters.

### 3.2 Out of Scope

Minimal D does not cover:

- D-7 mentor/fixed-score import;
- D-8 lecture import;
- D-9 cultural/sports activity import;
- D-10 Excel export;
- D-11 unsubmitted-student list;
- platform-rule submit-window enforcement;
- confirmed-record reversal, reject-after-confirm, or admin unconfirm workflows;
- asynchronous import/export job tables;
- historical reconciliation against the old system;
- category/item-scoped partial final-record detail views or partial totals;
- frontend pages.

## 4. Chosen Approach

Use final-record-specific domain/application/infra/interface slices, mirroring the Minimal C structure.

### 4.1 Safe-Init SQL

Create `group-d-score-finalization-import-export.safe-init.sql` rather than using the existing destructive `group-d-score-finalization-import-export.sql`.

The safe-init SQL must:

- use `CREATE TABLE IF NOT EXISTS`;
- not contain `DROP TABLE`;
- not contain MySQL-only table options such as `ENGINE=`, `CHARSET`, `COLLATE`, or `COMMENT=`;
- be rerunnable in H2 MySQL mode without overwriting existing runtime final records;
- define `final_record` and `final_component_score` indexes needed by the query paths.

Recommended table shape:

- `final_record`
  - `id BIGINT NOT NULL AUTO_INCREMENT`;
  - `student_user_id BIGINT NOT NULL`;
  - `academic_year VARCHAR(32) NOT NULL`;
  - `status VARCHAR(32) NOT NULL`;
  - `moral_total DECIMAL(10,2) NOT NULL`;
  - `intellectual_total DECIMAL(10,2) NOT NULL`;
  - `physical_total DECIMAL(10,2) NOT NULL`;
  - `labor_total DECIMAL(10,2) NOT NULL`;
  - `grand_total DECIMAL(10,2) NOT NULL`;
  - `submitted_at DATETIME DEFAULT NULL`;
  - `confirmed_at DATETIME DEFAULT NULL`;
  - `confirm_comment VARCHAR(1000) DEFAULT NULL`;
  - `version BIGINT NOT NULL`;
  - `created_at DATETIME NOT NULL`;
  - `updated_at DATETIME NOT NULL`;
  - unique key on `(student_user_id, academic_year)`;
  - indexes on `student_user_id`, `academic_year`, and `status`.

- `final_component_score`
  - `id BIGINT NOT NULL AUTO_INCREMENT`;
  - `final_record_id BIGINT NOT NULL`;
  - `category_code VARCHAR(64) NOT NULL`;
  - `item_code VARCHAR(64) NOT NULL`;
  - `score_value DECIMAL(10,2) NOT NULL`;
  - `display_text VARCHAR(1000) DEFAULT NULL`;
  - `source_type VARCHAR(32) NOT NULL`;
  - `source_ref_id VARCHAR(64) DEFAULT NULL`;
  - `created_at DATETIME NOT NULL`;
  - indexes on `final_record_id`, `category_code`, and `item_code`.

### 4.2 Final Record Aggregate

Introduce a small immutable aggregate around final-record status transitions:

- `FinalRecordStatus`: `DRAFT`, `SUBMITTED`, `CONFIRMED`;
- `FinalRecord`: header totals, timestamps, version, and transition methods;
- newly created draft snapshots start at `version = 0`, with `created_at = updated_at = Instant.now()` set once at aggregate creation inside the submit transaction; `created_at` is immutable after creation and every later transition only advances `updated_at`;
- `submit(long expectedVersion)`:
  - only allowed from `DRAFT`;
  - requires optimistic version match;
  - sets `submittedAt = Instant.now()`;
  - sets `updatedAt = Instant.now()`;
  - increments version;
- `confirm(long expectedVersion, String confirmComment)`:
  - only allowed from `SUBMITTED`;
  - requires optimistic version match;
  - sets `confirmedAt = Instant.now()`;
  - stores the passed `confirmComment` on the record (may be `null`); the request layer rejects values longer than the 1000-character limit before this method runs, so no server-side truncation happens here;
  - sets `updatedAt = Instant.now()`;
  - increments version.

Use `ConflictException` for invalid state transitions and version mismatches.

### 4.3 Aggregation Source

Student submission must freeze the current approved application facts for the student and academic year.

Source rows:

- `application_submission.status = 'APPROVED'`;
- same `student_user_id`;
- same `academic_year`;
- joined to `application_fact` by `application_id`;
- one component per approved application fact;
- the join is a strict inner join, but an APPROVED submission with zero `application_fact` rows is not silently dropped: it means the approved snapshot is incomplete. Detection is explicit rather than implied by the join — the aggregation step first loads the set of APPROVED `application_submission` ids for the student/year, then loads the `application_fact` rows joined to them, and asserts that every APPROVED submission id appears at least once among the fact rows. If any APPROVED submission id is missing from the fact set, submission fails with `ConflictException` / `409 / BIZ-4090` (same "incomplete approved snapshot" semantics as the null-score case below), so a missing fact can never silently reduce `grand_total`.

Component mapping:

- `category_code` and `item_code` from `application_submission`;
- `score_value` from `application_fact.score_value`;
- `display_text` from `application_fact.display_text`;
- `source_type = 'APPLICATION'`;
- `source_ref_id = application_id` as string.
- each approved application fact becomes its own component, even when multiple facts share the same `category_code` and `item_code`.
- component read APIs return a stable order by `category_code ASC`, `item_code ASC`, `id ASC`; this keeps repeated reads deterministic while still preserving duplicate approved facts as separate component rows. "Stable" here means deterministic ordering within a single materialized record; because Minimal D materializes each record's components exactly once at submit time and never re-aggregates them, component ids and their relative order do not change for that record. Cross-version ordering stability is not a concern in Minimal D since a committed record is never re-aggregated.

Null score handling:

- B-group safe-init allows `application_fact.score_value` to be `NULL`, while Minimal D `final_component_score.score_value` and header totals are `NOT NULL`;
- Minimal D must not coerce `NULL` to zero and must not silently skip those facts;
- if any approved source fact for the student/year has `score_value IS NULL`, submission fails with `ConflictException` / `409 / BIZ-4090` before creating the final record;
- the error means the approved application snapshot is incomplete and must be repaired before final submission.

Totals:

- `moral_total`: sum components with `category_code = 'MORAL'`;
- `intellectual_total`: sum components with `category_code = 'INTELLECTUAL'`;
- `physical_total`: sum components with `category_code = 'SPORTS'`;
- `labor_total`: sum components with `category_code = 'LABOR'`;
- `grand_total`: sum all component scores;
- the four category totals must partition the full component set: `category_code` must be one of `{MORAL, INTELLECTUAL, SPORTS, LABOR}`. If any approved component carries a `category_code` outside this set, submission fails with `ConflictException` / `409 / BIZ-4090` before creating the final record, because such a component would inflate `grand_total` without belonging to any category total. This preserves the invariant `grand_total = moral_total + intellectual_total + physical_total + labor_total`.

If no approved facts exist for the student/year, submission fails with `ConflictException`.

### 4.4 Student Endpoints

`GET /api/student/final-records/{academicYear}`

- permission: `final.view.self`;
- returns only the current user's record for the academic year;
- returns `404 / RES-4040` when absent.
- does not create a final record. Minimal D materializes a snapshot only inside the submit command, so a student with approved facts but no final record yet still receives `404` until they call submit successfully.

`GET /api/student/final-records/{academicYear}/components`

- permission: `final.view.self`;
- returns components for the current user's record;
- returns `404 / RES-4040` when the header is absent.
- uses the stable component order defined in section 4.3.

`POST /api/student/final-records/submit`

- permission: `final.submit.self`;
- request: `academicYear`, `expectedVersion`;
- aggregates a `DRAFT` snapshot from approved facts and transitions it to `SUBMITTED` within a single transaction; every successful submit commits at `SUBMITTED`, never leaving a committed `DRAFT` record;
- all submit steps must run in one transaction: read approved facts, create the `final_record`, insert component rows, update totals, and transition to `SUBMITTED`;
- component writes use batch-insert within that transaction; the path also issues a defensive `DELETE FROM final_component_score WHERE final_record_id = ?` before inserting the aggregated rows, which is a no-op for the freshly created record;
- the create path uses synthetic `expectedVersion = 0`;
- the snapshot is materialized as `DRAFT` with `version = 0` inside the transaction and returns `version = 1` after the transition commits;
- if no final record exists and `expectedVersion` is not `0`, returns `409 / BIZ-4090`;
- if two create-path submissions race, the unique key on `(student_user_id, academic_year)` makes one request win and the other request fail with `409 / BIZ-4090`;
- because every successful submit commits at `SUBMITTED`, any already-persisted record is in `SUBMITTED` or `CONFIRMED`; submitting again against such a record returns `409 / BIZ-4090`, and Minimal D never re-aggregates an existing record;
- if `expectedVersion` is omitted, returns `400 / VAL-4001`.

`DRAFT` is a submit-transaction-internal transient state in Minimal D, not an externally observable record state. The create path materializes it only to run aggregation and the optimistic-lock transition, and the same transaction commits at `SUBMITTED`; no API path leaves a committed `DRAFT` record. Read endpoints never initialize a final record because aggregation is a mutating freeze operation and must remain inside the submit transaction with optimistic-lock and source-data validation. The `DRAFT` enum value is retained for the in-transaction domain transition and forward compatibility.

For Minimal D, submit-window enforcement is explicitly deferred. Add a narrow domain-service extension point without enforcing platform rules yet. Mirror the existing `edu.whut.eval.domain.application.service.ApplicationSubmissionWindowPolicy` precedent: the interface and its Minimal D no-op implementation live in `edu.whut.eval.domain.finalrecord.service`. `SubmitWindowClosedException` is a `ConflictException` subtype and therefore lives alongside `ConflictException` in the shared `edu.whut.eval.common.exception` package:

```java
public interface FinalSubmissionWindowPolicy {
    // Throws SubmitWindowClosedException when a future implementation
    // rejects submission for the given student/year/time.
    void assertSubmitAllowed(long studentUserId, String academicYear, Instant now);
}
```

The submit application service must call `FinalSubmissionWindowPolicy.assertSubmitAllowed(...)` before mutating `final_record` or `final_component_score`. Minimal D provides a no-op implementation that never throws. When a future platform-window implementation rejects a submission it must raise `SubmitWindowClosedException` (a `ConflictException` subtype mapping to `409 / BIZ-4090`), so the submit endpoint contract and error mapping stay stable without change. Section 6 records this mapping.

### 4.5 Admin Scope Semantics

Final records aggregate multiple component scores, so Minimal D treats admin final-record authorization as whole-record authorization.

Supported final-record admin scope types:

- `ALL`;
- `ORG_UNIT`;
- `ORG_SUBTREE`.

Rules for list, detail, and confirm:

- the resource context is the final record's student identity plus the student's **current** organization binding (the same current `orgUnitId` / `orgPath` used by A-group identity data); Minimal D has no per-year organization snapshot, so authorization for a historical academic year is evaluated against the student's current organization, matching the `student` block resolution in section 5.4. If a student changes organization, access to their earlier-year records follows the new organization; a per-year organization snapshot is deferred to a later milestone;
- `ORG_UNIT` grants access only when the student's `orgUnitId` matches the rule;
- `ORG_SUBTREE` grants access only when the student's organization path is inside the rule subtree;
- `ALL` grants access to the full record;
- `CATEGORY`, `ITEM`, `ORG_UNIT_ITEM`, and `CUSTOM_EXPRESSION` are not used for Minimal D final-record authorization because they cannot safely authorize the aggregate totals and component list as a whole;
- admin detail always returns full header totals and all components only after whole-record access succeeds;
- admin confirm requires the same whole-record scope semantics as admin detail, plus `score.confirm.assigned`;
- if the student has no current organization binding (`orgUnitId` / `orgPath` resolve to `NULL`), only an `ALL`-scoped rule can grant access; `ORG_UNIT` and `ORG_SUBTREE` rules never match a null organization, so such records are invisible to org-scoped admins (returning an empty page in the list and `403` on detail/confirm) rather than leaking to everyone. The admin detail `student` block still renders with `orgUnitId = null` and `orgUnitName = null` for an `ALL`-scoped caller.

If a user holds `score.view.assigned` / `score.confirm.assigned` but only through unsupported scope types (category/item-scoped rules and no `ALL` / `ORG_UNIT` / `ORG_SUBTREE` rule), the two endpoint shapes resolve consistently with their scope model rather than a single blanket outcome:

- the **list** endpoint returns `200` with an empty page, because unsupported scope rules translate to no visible rows (section 4.5 and the list filter rules) — it never returns `403` merely because the caller's rules are all unsupported;
- the **detail** and **confirm** endpoints operate on a single record and return `403` via `AccessDeniedAppException` once whole-record scope evaluation fails for that record;
- a caller that does not hold the permission at all is rejected earlier at the permission gate (`403`) for every endpoint, independent of scope type.

Implementation requirements:

- introduce `FinalRecordResourceContext implements ScopeResourceContext` with `finalRecordId`, `studentUserId`, `orgUnitId`, `orgPath`, and `academicYear`;
- `FinalRecordResourceContext.getOwnerUserId()` returns `studentUserId`;
- `FinalRecordResourceContext.getCategoryCode()` and `getItemCode()` return `null`;
- `FinalRecordResourceContext.getFieldValue(...)` only exposes final-record-safe fields: `finalRecordId`, `orgUnitId`, `orgPath`, `academicYear`, and the student user id, which is addressable by both field names `studentUserId` and `ownerUserId` returning the same value (the `ownerUserId` alias exists for scope rules written against the generic owner field). Any other field name resolves to `null` (never throws), so an unsupported `CUSTOM_EXPRESSION` clause referencing an unknown field simply fails to match rather than erroring; combined with the rule that `CUSTOM_EXPRESSION` does not grant final-record access, this keeps unknown-field lookups safe;
- extend `ResourceScopeAccessEvaluator` with `canAccessFinalRecord(UserAuthorizationContext authorizationContext, String permissionCode, FinalRecordResourceContext resourceContext)`;
- `canAccessFinalRecord(...)` must evaluate only `ALL`, `ORG_UNIT`, and `ORG_SUBTREE`; `SELF`, `CATEGORY`, `ITEM`, `ORG_UNIT_ITEM`, and `CUSTOM_EXPRESSION` do not grant admin final-record access. Any `expression_json` payload on a matched scope rule (for example the `scoreRole` hints in the section 4.7 seed rows) is ignored for final-record authorization; access is decided solely by `scope_type` and its organization target, and `expression_json` is retained only as descriptive metadata for other permission flows;
- admin list filtering must use a final-record-specific scope predicate/SQL translator, `FinalRecordScopePredicateBuilder` (placed in `edu.whut.eval.domain.finalrecord.service`, mirroring the existing `edu.whut.eval.domain.auth.service.ScopePredicateBuilder`). It exposes `ApplicationScopePredicate buildForFinalRecord(UserAuthorizationContext authorizationContext, AuthorizationScopeSet scopeSet)`, reusing the existing `ApplicationScopePredicate` factories: `allowAll(...)` for an `ALL` rule, `restricted(...)` with org-unit / org-subtree clauses for those scope types, and `denied(...)` / an `isEmptyResult()` predicate (contributing no rows) when the caller has only unsupported scope types. It never partially filters components; visibility is decided at the whole-record level with the same supported scope set as `canAccessFinalRecord(...)`, and any `CUSTOM_EXPRESSION` clause is dropped rather than applied.

### 4.6 Admin Endpoints

`GET /api/admin/final-records`

- permission: `score.view.assigned`;
- required query: `academicYear`;
- optional filters: `status`, `keyword`, `orgUnitId`, `pageNo`, `pageSize`;
- status allowed values: `SUBMITTED`, `CONFIRMED`; `DRAFT` is not an accepted filter value because Minimal D never persists a committed `DRAFT` record, and an unsupported status value returns `400 / VAL-4001`;
- `keyword` matches `studentUserNo` and `studentUserName` using a case-insensitive substring (`LIKE %keyword%`) match on either field; a blank or whitespace-only `keyword` is ignored;
- `pageNo` defaults to `1`;
- `pageSize` defaults to `20` and is bounded to `100`;
- filters records using the whole-record scope types defined in section 4.5;
- all optional filters combine with the whole-record scope as `AND`: the scope predicate first restricts the visible set, then `status`, `keyword`, and `orgUnitId` narrow within that visible set; an `orgUnitId` (or keyword) that resolves outside the caller's scope simply yields an empty page rather than a `403`, so filters can never widen visibility beyond scope;
- results use a deterministic default sort of `submittedAt DESC, finalRecordId DESC` so pagination is stable across requests; ties on `submittedAt` are broken by the unique `finalRecordId`;
- returns a page of final-record list items with student identity and totals.

`GET /api/admin/final-records/{recordId}`

- permission: `score.view.assigned`;
- loads the record unscoped first for existence;
- validates whole-record scope before returning totals or components;
- out-of-scope existing records return `403`, not `404`;
- returns header, student summary, and components.

`POST /api/admin/final-records/{recordId}/confirm`

- permission: `score.confirm.assigned`;
- request: optional `comment` (max 1000 characters, matching the `confirm_comment` column), required `expectedVersion`;
- a `comment` longer than 1000 characters is rejected with `400 / VAL-4001`; the service does not silently truncate, so request validation and the stored column length stay consistent;
- loads the record unscoped first for existence;
- validates whole-record scope before state transition;
- only `SUBMITTED` records can be confirmed;
- persists optional `comment` to `final_record.confirm_comment`; absence leaves the column `NULL`;
- returns final record id, `CONFIRMED`, `confirmedAt`, `confirmComment`, and new version.

### 4.7 Permission Additions

All IAM seed rows in this section (the `iam_permission`, `iam_role_permission`, and `iam_scope_rule` inserts below) belong to the Minimal D safe-init file `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql` (the same file listed in section 3.1), not to any A-group identity seed. They are appended there so the whole D permission surface installs and re-runs from one idempotent script.

Add:

- `AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED = "score.confirm.assigned"`;
- A-group safe seed entry for `score.confirm.assigned`:
  - `iam_permission.id = 5023`;
  - `permission_code = 'score.confirm.assigned'`;
  - `permission_name = '确认授权范围最终成绩'`;
  - `permission_group = 'score'`;
  - `status = 'ACTIVE'`.
- role-permission bindings:
  - `iam_role_permission.id = 6048`, `role_id = 4003`, `permission_id = 5023`;
  - `iam_role_permission.id = 6049`, `role_id = 4004`, `permission_id = 5023`.
- confirmation scope rules:
  - `iam_scope_rule.id = 8019`, `assignment_id = 7010`, `permission_code = 'score.confirm.assigned'`, `scope_type = 'ORG_SUBTREE'`, `org_unit_id = 2002`, `category_code = NULL`, `item_code = NULL`, `expression_json = JSON_OBJECT('scoreRole', 'counselor')`, `priority = 80`, `status = 'ACTIVE'`;
  - `iam_scope_rule.id = 8020`, `assignment_id = 7011`, `permission_code = 'score.confirm.assigned'`, `scope_type = 'ORG_SUBTREE'`, `org_unit_id = 2002`, `category_code = NULL`, `item_code = NULL`, `expression_json = JSON_OBJECT('scoreRole', 'college_reviewer')`, `priority = 70`, `status = 'ACTIVE'`.

The safe seed must be idempotent **and never misbind a role to the wrong permission**. If the safe-init style uses `INSERT ... SELECT ... WHERE NOT EXISTS`, the guard must reconcile the natural key and the fixed id together rather than skipping on either one alone:

- the `iam_permission` row is looked up by its natural key `permission_code = 'score.confirm.assigned'`. If a permission with that code already exists, reuse **its** id for the role bindings and scope rules instead of the literal `5023`; the literal ids below are only used when the row is created fresh. This guarantees `iam_role_permission.permission_id` always references the `score.confirm.assigned` permission even if `5023` is occupied by an unrelated permission or the code already lives at a different id;
- each insert is guarded by its own natural key (`permission_code`; `role_id + permission_id`; `assignment_id + permission_code + scope_type + org_unit_id`) so re-running is a no-op;
- the fixed ids (`5023`, `6048`, `6049`, `8019`, `8020`) are reserved from the current A-group seed maxima and are used only when that id range is free. If a fixed id is already occupied by an unrelated row, the seed must fail loudly (surface the collision) rather than silently reassigning or reusing it, so the reserved range can be corrected before release.

This keeps confirmation as a distinct write permission instead of overloading `score.view.assigned`.

## 5. Data Contracts

### 5.1 Student Final Record Submit Request

```json
{
  "academicYear": "2025-2026",
  "expectedVersion": 0
}
```

`academicYear` uses the same camelCase request field style as the existing interface DTOs. `expectedVersion` is a number and is required; for the first-time create path it must be `0`.

### 5.2 Student Final Record View

```json
{
  "finalRecordId": 41001,
  "academicYear": "2025-2026",
  "status": "SUBMITTED",
  "moralTotal": 0.80,
  "intellectualTotal": 5.00,
  "physicalTotal": 0.60,
  "laborTotal": 1.20,
  "grandTotal": 7.60,
  "submittedAt": "2026-07-07T12:00:00Z",
  "confirmedAt": null,
  "version": 1
}
```

The successful `POST /api/student/final-records/submit` response uses this same Student Final Record View shape, so the student read and submit paths share one contract. The student view intentionally omits `confirmComment`; the confirmation note is an admin-facing field exposed only in the admin detail view (section 5.4).

### 5.3 Component View

`GET /api/student/final-records/{academicYear}/components` returns a top-level JSON object with a single `components` array (never a bare array), so the shape can grow without contract churn:

```json
{
  "components": [
    {
      "categoryCode": "INTELLECTUAL",
      "itemCode": "INTELLECTUAL_PAPER",
      "itemName": null,
      "scoreValue": 2.00,
      "displayText": "论文已审核通过",
      "sourceType": "APPLICATION",
      "sourceRefId": "21013"
    }
  ]
}
```

Each element of `components` is a Component DTO with the fields shown above. An empty result returns `{ "components": [] }` (only once the header exists; an absent header returns `404 / RES-4040` per section 4.4). Minimal D may return `itemName = null` because item-name enrichment depends on E-group platform definitions. The code should keep the field in the DTO so E can fill it later without contract churn.

### 5.4 Admin Detail View

```json
{
  "record": {
    "finalRecordId": 41001,
    "academicYear": "2025-2026",
    "status": "SUBMITTED",
    "moralTotal": 0.80,
    "intellectualTotal": 5.00,
    "physicalTotal": 0.60,
    "laborTotal": 1.20,
    "grandTotal": 7.60,
    "submittedAt": "2026-07-07T12:00:00Z",
    "confirmedAt": null,
    "confirmComment": null,
    "version": 1
  },
  "student": {
    "studentUserId": 1001,
    "studentUserNo": "20210001",
    "studentUserName": "张三",
    "orgUnitId": 2010,
    "orgUnitName": "计算机 2101 班"
  },
  "components": []
}
```

The `student` block is resolved from the current user and organization binding tables used by A-group identity data. `orgUnitId` is the student's bound organization id, and `orgUnitName` is resolved from the organization unit table for display. This is the same current organization binding used for final-record authorization (section 4.5), so display and access control stay consistent even for historical academic years.

### 5.5 Admin Final Record List View

```json
{
  "total": 1,
  "records": [
    {
      "finalRecordId": 41001,
      "academicYear": "2025-2026",
      "status": "SUBMITTED",
      "studentUserId": 1001,
      "studentUserNo": "20210001",
      "studentUserName": "张三",
      "orgUnitId": 2010,
      "orgUnitName": "计算机 2101 班",
      "moralTotal": 0.80,
      "intellectualTotal": 5.00,
      "physicalTotal": 0.60,
      "laborTotal": 1.20,
      "grandTotal": 7.60,
      "submittedAt": "2026-07-07T12:00:00Z",
      "confirmedAt": null,
      "version": 1
    }
  ]
}
```

The list response must use the repository's existing `PageResult<T>` shape: `total` for the number of records matching filters and whole-record scope, and `records` for the current page. `pageNo` and `pageSize` are request/query controls only; they are not echoed in the response unless a future iteration introduces a new wrapper type.

### 5.6 Admin Final Record Confirm Request

```json
{
  "comment": "辅导员已复核，无异议",
  "expectedVersion": 1
}
```

`comment` is optional and at most 1000 characters; `expectedVersion` is a required number. Field-name mapping across layers: the interface layer uses `comment`, the domain aggregate uses `confirmComment`, and the database column is `confirm_comment`. All three refer to the same value.

### 5.7 Admin Final Record Confirm Response

```json
{
  "finalRecordId": 41001,
  "status": "CONFIRMED",
  "confirmedAt": "2026-07-07T13:00:00Z",
  "confirmComment": "辅导员已复核，无异议",
  "version": 2
}
```

`confirmComment` echoes the persisted note (or `null` when omitted). The confirmed record is also readable through the admin detail view (section 5.4), which now carries `confirmComment`.

## 6. Error Semantics

- missing final record: `ResourceNotFoundException`;
- missing permission: `AccessDeniedAppException`;
- out-of-scope existing resource: `AccessDeniedAppException`;
- invalid state transition: `ConflictException`;
- version mismatch: `ConflictException`;
- concurrent create conflict on `(student_user_id, academic_year)`: `ConflictException`;
- submit-window rejection (future `FinalSubmissionWindowPolicy` implementations only; the Minimal D no-op never throws): `SubmitWindowClosedException extends ConflictException`, mapping to `409 / BIZ-4090`;
- invalid query/status/page size/request body: `ValidationException`.

## 7. Testing Strategy

Required focused tests:

- safe-init SQL tests for D tables and rerun preservation;
- safe-init seed tests for the `score.confirm.assigned` permission and its bindings (section 4.7), covering all three safe-seed branches: (a) when a permission with `permission_code = 'score.confirm.assigned'` already exists at an id other than `5023`, the role bindings and scope rules must reuse that existing id rather than the literal `5023`, so `iam_role_permission.permission_id` always references the `score.confirm.assigned` permission; (b) re-running the whole seed is idempotent — the permission, role bindings (`role_id + permission_id`), and scope rules (`assignment_id + permission_code + scope_type + org_unit_id`) are not duplicated and never rebind a role to a different permission; (c) when a reserved fixed id (`5023`, `6048`, `6049`, `8019`, `8020`) is already occupied by an unrelated row, the seed fails loudly (surfaces the collision) instead of silently reassigning, reusing, or skipping it;
- aggregate transition tests for the domain `DRAFT -> SUBMITTED -> CONFIRMED` sequence, verifying these as in-memory aggregate method transitions rather than externally observable committed `DRAFT` states;
- aggregation repository tests proving only `APPROVED` application facts are frozen;
- aggregation repository tests proving approved facts with `NULL` `score_value` fail submission instead of becoming zero-valued or skipped components;
- aggregation tests proving an `APPROVED` submission with zero matching `application_fact` rows fails submission with `409 / BIZ-4090` (incomplete approved snapshot) rather than silently omitting the submission from `grand_total`;
- aggregation tests proving a component whose `category_code` falls outside `{MORAL, INTELLECTUAL, SPORTS, LABOR}` fails submission with `409 / BIZ-4090`, protecting the `grand_total = sum(category totals)` invariant;
- final-record repository tests for generated ids, component insertion, and ordered component listing;
- query repository tests for student self lookup, admin scoped list/detail, out-of-scope detail existence semantics, status filtering, keyword filtering, default `pageSize = 20`, and page-size bound;
- application service tests for submit/confirm transitions and error mapping;
- application service tests proving `FinalSubmissionWindowPolicy.assertSubmitAllowed(...)` is invoked before final-record mutation;
- transaction-boundary tests proving submit does not leave partial record/component state when component insertion or the transition fails;
- concurrent create-path tests proving only one first submission wins and the losing request maps to `ConflictException`;
- create-path optimistic-lock tests proving missing records require `expectedVersion = 0` and return submitted `version = 1`;
- resubmit tests proving a second submit against an already `SUBMITTED` or `CONFIRMED` record returns `409 / BIZ-4090` and does not re-aggregate;
- submit tests proving no committed `DRAFT` record is externally observable after a successful submission (the record commits at `SUBMITTED`), and that `status = DRAFT` is rejected by the admin list filter with `400 / VAL-4001`;
- final-record scope tests proving category/item-only rules do not expose aggregate records or allow confirmation;
- final-record scope tests for an unsupported-scope-only caller (holds `score.view.assigned` / `score.confirm.assigned` but only through category/item-scoped rules with no `ALL` / `ORG_UNIT` / `ORG_SUBTREE` rule), asserting the differentiated returns from section 4.5 across both service and controller layers: the list endpoint returns `200` with `records = []` (never `403` merely because every rule is unsupported), while detail and confirm return `403`; and contrasting this with a caller that holds no permission at all, which is rejected at the permission gate with `403` on every endpoint — so "has permission but only unsupported scope" and "no permission" are never conflated;
- final-record scope tests proving `canAccessFinalRecord(...)` grants only `ALL`, `ORG_UNIT`, and `ORG_SUBTREE`;
- controller WebMvc tests for route contracts and JSON shape;
- security annotation tests for `final.view.self`, `final.submit.self`, `score.view.assigned`, and `score.confirm.assigned`;
- security filter tests for unauthorized/forbidden/authorized access.

Final verification:

- focused Minimal D regression command;
- existing Minimal C review tests;
- existing B application tests;
- full `mvn test`.

## 8. Risks and Decisions

- Full D import/export is deliberately excluded to keep Minimal D reviewable.
- `score.confirm.assigned` must be added because confirmation is a write action.
- Use fixed seed ids for Minimal D additions: permission `5023`, role permissions `6048` and `6049`, and scope rules `8019` and `8020`, based on the current A-group seed maxima.
- First-time student submission uses `expectedVersion = 0` for the absent-record create path.
- Approved application facts with `NULL` `score_value` are treated as incomplete source data and block submission with `409 / BIZ-4090`.
- Submit is transactional; record creation, component insertion, total recalculation, and the `DRAFT -> SUBMITTED` transition must commit or roll back together.
- `DRAFT` is a submit-transaction-internal transient state in Minimal D: it is materialized only inside the submit transaction to run aggregation and the optimistic-lock transition, and every successful submit commits at `SUBMITTED`. No API path produces a committed `DRAFT` record, so the admin list rejects a `DRAFT` status filter and the `DRAFT` enum value is retained only for the in-transaction transition and forward compatibility.
- Component writes use batch-insert; a defensive delete-before-insert guards the create path but is a no-op for the freshly created record. Re-aggregation of an already-committed record is out of scope for Minimal D.
- Concurrent first submissions resolve through the `(student_user_id, academic_year)` unique key; the losing request returns `409 / BIZ-4090` and should refetch before retrying with the current record version.
- Admin list/detail/confirm are whole-record organization-scoped in Minimal D; component-level partial visibility is deferred.
- Admin detail/confirm must load unscoped for existence, then validate whole-record scope, matching the Minimal C 403-vs-404 fix.
- Multiple approved facts with the same `category_code` and `item_code` intentionally produce separate components and contribute separately to totals; any merge or de-duplication policy is deferred to a later iteration.
- `physical_total` is a historical frozen-table field name; it maps to `category_code = 'SPORTS'`, whose business meaning is sports and arts.
- `created_at` and `updated_at` are included in Minimal D safe-init even though the old target-state D SQL omitted them, because submission and confirmation transition auditing needs record-level timestamps.
- D safe-init must be non-destructive; the existing D SQL remains target-state sample data, not runtime-safe initialization.
- Submit-window enforcement is deferred because platform rules are not yet wired for final submission; Minimal D only adds the no-op `FinalSubmissionWindowPolicy` extension point.
