# Minimal D Final Record Workflow Design

## 1. Goal

Build the smallest D-group final-record loop after Minimal C.

Minimal D closes the backend path from approved application facts to final-record snapshots:

- safe, repeatable initialization for `final_record` and `final_component_score`;
- student reads their final-record header and components;
- student submits a draft final record;
- admin/reviewer reads scoped final records and details;
- admin/reviewer confirms a submitted final record;
- all single-record access is protected by existing A-group scope rules.

This is not the full D-group target state. Imports, Excel exports, unsubmitted-student lists, batch operations, and platform-window enforcement remain out of scope for this round.

## 2. Current Baseline

The codebase now provides:

- Minimal B application write/read foundation;
- Minimal C review workflow and `APPROVED` application state;
- persisted `application_fact` scoring snapshots for submitted applications;
- score query infrastructure under `/api/student/query/scores` and `/api/admin/query/scores`;
- `ScoreScopeSqlTranslator`, `DefaultScoreScopePredicateBuilder`, and `ResourceScopeAccessEvaluator.canAccessScore(...)`;
- A-group seed permissions for `final.submit.self`, `final.view.self`, `score.view.assigned`, and `score.export.assigned`.

The current code does not yet provide:

- non-destructive D safe-init SQL;
- `final_record` / `final_component_score` domain models or repositories;
- `/api/student/final-records/**` or `/api/admin/final-records/**` controllers;
- final-record submission or confirmation transitions;
- a `score.confirm.assigned` permission constant/seed entry.

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
- scoped admin visibility using score/final-record resource context;
- focused tests for SQL safety, aggregation, state transitions, scope behavior, controller contracts, and security annotations/filters.

### 3.2 Out of Scope

Minimal D does not cover:

- D-7 mentor/fixed-score import;
- D-8 lecture import;
- D-9 cultural/sports activity import;
- D-10 Excel export;
- D-11 unsubmitted-student list;
- platform-rule submit-window enforcement;
- asynchronous import/export job tables;
- historical reconciliation against the old system;
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
  - `version BIGINT NOT NULL`;
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
- `submit(long expectedVersion)`:
  - only allowed from `DRAFT`;
  - requires optimistic version match;
  - sets `submittedAt = Instant.now()`;
  - increments version;
- `confirm(long expectedVersion)`:
  - only allowed from `SUBMITTED`;
  - requires optimistic version match;
  - sets `confirmedAt = Instant.now()`;
  - increments version.

Use `ConflictException` for invalid state transitions and version mismatches.

### 4.3 Aggregation Source

Student submission must freeze the current approved application facts for the student and academic year.

Source rows:

- `application_submission.status = 'APPROVED'`;
- same `student_user_id`;
- same `academic_year`;
- joined to `application_fact` by `application_id`;
- one component per approved application fact.

Component mapping:

- `category_code` and `item_code` from `application_submission`;
- `score_value` from `application_fact.score_value`;
- `display_text` from `application_fact.display_text`;
- `source_type = 'APPLICATION'`;
- `source_ref_id = application_id` as string.

Totals:

- `moral_total`: sum components with `category_code = 'MORAL'`;
- `intellectual_total`: sum components with `category_code = 'INTELLECTUAL'`;
- `physical_total`: sum components with `category_code = 'SPORTS'`;
- `labor_total`: sum components with `category_code = 'LABOR'`;
- `grand_total`: sum all component scores.

If no approved facts exist for the student/year, submission fails with `ConflictException`.

### 4.4 Student Endpoints

`GET /api/student/final-records/{academicYear}`

- permission: `final.view.self`;
- returns only the current user's record for the academic year;
- returns `404 / RES-4040` when absent.

`GET /api/student/final-records/{academicYear}/components`

- permission: `final.view.self`;
- returns components for the current user's record;
- returns `404 / RES-4040` when the header is absent.

`POST /api/student/final-records/submit`

- permission: `final.submit.self`;
- request: `academicYear`, `expectedVersion`;
- creates or refreshes a `DRAFT` snapshot from approved facts, then transitions it to `SUBMITTED`;
- if a final record already exists in `SUBMITTED` or `CONFIRMED`, returns `409 / BIZ-4090`;
- if `expectedVersion` is omitted, returns `400 / VAL-4001`.

For Minimal D, submit-window enforcement is explicitly deferred. The service should leave a narrow extension point rather than hard-code platform-rule behavior.

### 4.5 Admin Endpoints

`GET /api/admin/final-records`

- permission: `score.view.assigned`;
- required query: `academicYear`;
- optional filters: `status`, `keyword`, `orgUnitId`, `pageNo`, `pageSize`;
- status allowed values: `DRAFT`, `SUBMITTED`, `CONFIRMED`;
- page size bounded to 100;
- returns a page of final-record list items with student identity and totals.

`GET /api/admin/final-records/{recordId}`

- permission: `score.view.assigned`;
- loads the record unscoped first for existence;
- validates the current user can access the score/final-record resource;
- out-of-scope existing records return `403`, not `404`;
- returns header, student summary, and components.

`POST /api/admin/final-records/{recordId}/confirm`

- permission: `score.confirm.assigned`;
- request: optional `comment`, required `expectedVersion`;
- loads the record unscoped first for existence;
- validates resource scope;
- only `SUBMITTED` records can be confirmed;
- returns final record id, `CONFIRMED`, `confirmedAt`, and new version.

### 4.6 Permission Additions

Add:

- `AuthorizationPermissionCodes.SCORE_CONFIRM_ASSIGNED = "score.confirm.assigned"`;
- A-group safe seed entry for `score.confirm.assigned`;
- appropriate role assignment and scope rule seed for the counselor/admin reviewer role, matching `score.view.assigned` scope as closely as possible.

This keeps confirmation as a distinct write permission instead of overloading `score.view.assigned`.

## 5. Data Contracts

### 5.1 Student Final Record View

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
  "version": 1
}
```

### 5.2 Component View

```json
{
  "categoryCode": "INTELLECTUAL",
  "itemCode": "INTELLECTUAL_PAPER",
  "itemName": null,
  "scoreValue": 2.00,
  "displayText": "论文已审核通过",
  "sourceType": "APPLICATION",
  "sourceRefId": "21013"
}
```

Minimal D may return `itemName = null` because item-name enrichment depends on E-group platform definitions. The code should keep the field in the DTO so E can fill it later without contract churn.

### 5.3 Admin Detail View

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

## 6. Error Semantics

- missing final record: `ResourceNotFoundException`;
- missing permission: `AccessDeniedAppException`;
- out-of-scope existing resource: `AccessDeniedAppException`;
- invalid state transition: `ConflictException`;
- version mismatch: `ConflictException`;
- invalid query/status/page size/request body: `ValidationException`.

## 7. Testing Strategy

Required focused tests:

- safe-init SQL tests for D tables and rerun preservation;
- aggregate transition tests for `DRAFT -> SUBMITTED -> CONFIRMED`;
- aggregation repository tests proving only `APPROVED` application facts are frozen;
- final-record repository tests for generated ids, component replacement, and ordered component listing;
- query repository tests for student self lookup, admin scoped list/detail, out-of-scope detail existence semantics, status filtering, keyword filtering, and page-size bound;
- application service tests for submit/confirm transitions and error mapping;
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
- Admin detail/confirm must load unscoped for existence, then validate scope, matching the Minimal C 403-vs-404 fix.
- D safe-init must be non-destructive; the existing D SQL remains target-state sample data, not runtime-safe initialization.
- Submit-window enforcement is deferred because platform rules are not yet wired for final submission.
