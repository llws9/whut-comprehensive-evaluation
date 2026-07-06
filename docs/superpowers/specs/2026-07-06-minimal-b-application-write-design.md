# Minimal B Application Write Closure Design

## 1. Goal

Close the smallest B-group student application write loop that can be safely integrated with A-group IAM and the newly completed Minimal E foundation.

The target is not the full B-group final state. The target is a stable contract for:

- reading the student-facing platform and item prerequisites from Minimal E;
- uploading or choosing file IDs through E-owned file APIs;
- creating and updating an application draft;
- submitting and withdrawing the current student's own application;
- listing and reading the current student's own application data for the application page.

## 2. Current Baseline

The current code already implements a meaningful part of B:

- `POST /api/student/applications/drafts`
- `PUT /api/student/applications/{applicationId}/draft`
- `POST /api/student/applications/{applicationId}/submit`
- `POST /api/student/applications/{applicationId}/withdraw`
- `GET /api/student/query/applications`
- `ApplicationSubmission` state machine with `DRAFT`, `SUBMITTED`, `RETURNED`, `APPROVED`, `REJECTED`, `WITHDRAWN`
- optimistic locking through `expectedVersion`
- `attachmentFileIds` request contract
- owner/public file binding through `ApplicationAttachmentResolver`
- duplicate file ID rejection inside one application
- active-submission conflict check for the same student, item, academic year, and term
- submit-time scoring hooks through `RuleEngineService`

Minimal E is also now available:

- `GET /api/platform/menu/status`
- `GET /api/platform/menu/deadline`
- `GET /api/platform/evaluation-items`
- existing `GET /api/config/evaluation/options/{itemCode}`
- `POST /api/files/upload`
- `GET /api/files/{fileId}`
- `GET /api/files/{fileId}/access-url`
- `GET /api/files/public-attachments`

## 3. Scope

### 3.1 In Scope

This spec covers the next B-group implementation round:

- Freeze the student application write HTTP contract.
- Make write endpoints explicit about required student permissions.
- Ensure the write path is secured by Spring Security filters, not only service-level current-user loading.
- Align runtime B DDL with mapper expectations:
  - `application_submission.application_id` must support generated keys.
  - `application_attachment.id` must support generated keys.
  - runtime `application_attachment` columns must match `ApplicationAttachmentMapper` and `ApplicationAttachmentDO`.
- Add a non-destructive safe-init SQL for B-owned tables.
- Add a student-owned application detail endpoint so edit pages can reload the current version and attachment list.
- Keep `attachmentFileIds` as the only file-binding input.
- Preserve optimistic locking for update, submit, and withdraw.
- Add focused tests for write security, DDL safety, mapper alignment, and B/E integration contract.

### 3.2 Out of Scope

- Counselor/teacher/college review APIs.
- Review pass, reject, return, or score adjustment flows.
- Final-score aggregation and D-group export.
- Full B-group dashboard analytics.
- Multipart/chunked upload behavior.
- Public attachment publishing or scoped public attachment consumption.
- AI report generation.
- Replacing Nacos item/option config with database-backed governance.

## 4. Chosen Approach

Use the existing B write model as the base and close the gaps around security, persistence compatibility, and frontend-read contract.

This is preferred over rewriting the application module because the current domain and application services already model the core state machine, attachment binding, active-submission conflict, and version checks. The risk is that some existing table and response contracts are still narrower than the target B document. The implementation should therefore add focused behavior and tests rather than replace the module.

## 5. End-to-End Client Flow

The B frontend should use this sequence:

1. User logs in through A.
2. Client calls `GET /api/platform/menu/status`.
3. Client calls `GET /api/platform/menu/deadline`.
4. Client calls `GET /api/platform/evaluation-items?categoryCode=...`.
5. Client calls `GET /api/config/evaluation/options/{itemCode}` when the selected item has `optionsKey`.
6. Client resolves the current student's primary `orgUnitId` from A-group current-user or identity context before creating a draft. If no current endpoint exposes primary membership cleanly, Minimal B must add a B-facing read helper or extend the existing current-user response in the execution plan.
7. User uploads personal evidence with `POST /api/files/upload`, or selects public material from `GET /api/files/public-attachments`.
8. Client submits only `attachmentFileIds` to B write APIs.
9. B validates file binding through owner-active or `PUBLISHED + ALL` public file semantics.
10. Client uses `GET /api/files/{fileId}` and `GET /api/files/{fileId}/access-url` for preview/download, not B write responses.

The frontend must not send or store `storageKey`, `bucket`, `objectKey`, or raw upload `publicUrl`.

## 6. HTTP Contracts

All responses use `ApiResponse<T>`.

### 6.1 Create Draft

Endpoint:

```http
POST /api/student/applications/drafts
```

Auth:

- authenticated user;
- must have `application.submit`;
- service must still load current user from `UserAuthorizationContextAssembler`.

Request:

```json
{
  "orgUnitId": 2010,
  "categoryCode": "INTELLECTUAL",
  "itemCode": "INTELLECTUAL_PAPER",
  "academicYear": "2025-2026",
  "term": "上学期",
  "title": "期刊论文录用申请",
  "description": "论文已收到录用通知，申请智育加分。",
  "attachmentFileIds": ["file_xxx", "FILE-0008"]
}
```

Response data:

```json
{
  "applicationId": 21013,
  "status": "DRAFT",
  "title": "期刊论文录用申请",
  "description": "论文已收到录用通知，申请智育加分。",
  "attachmentCount": 2,
  "version": 0,
  "appliedPoints": null,
  "maxPoints": null,
  "exceedsMaxPoints": false,
  "warningMessage": null
}
```

Behavior:

- applicant is always the current authenticated user;
- `attachmentFileIds` are processed in request order;
- duplicate file IDs in one request fail with `409 BIZ-4090`;
- missing/blank file ID fails with `400 VAL-4001`;
- file binding accepts only current user's active uploaded file or `PUBLISHED + ALL` public file;
- same student, item, academic year, and term cannot have another `DRAFT`, `SUBMITTED`, `RETURNED`, or `APPROVED` application;
- `REJECTED` and `WITHDRAWN` do not block a new application for the same student, item, academic year, and term because they no longer represent an active or awarded claim.

### 6.2 Update Draft

Endpoint:

```http
PUT /api/student/applications/{applicationId}/draft
```

Request:

```json
{
  "title": "期刊论文录用申请（补充材料）",
  "description": "补充检索截图和录用通知。",
  "attachmentFileIds": ["file_updated"],
  "expectedVersion": 0
}
```

Behavior:

- only owner can update;
- only `DRAFT` and `RETURNED` are editable by student;
- update replaces the full attachment set;
- version mismatch returns `409 BIZ-4090`;
- response version increments by one.

### 6.3 Submit

Endpoint:

```http
POST /api/student/applications/{applicationId}/submit
```

Request:

```json
{
  "expectedVersion": 1,
  "optionCode": "PAPER_CORE_FIRST_AUTHOR",
  "appliedPoints": null
}
```

Behavior:

- only owner can submit;
- only `DRAFT` and `RETURNED` can be submitted;
- title, description, and attachment list must be non-empty;
- application window must be open;
- when `optionCode` is supplied, B calculates points through `RuleEngineService.calculatePoints`;
- when custom points are allowed for the option, B can use `appliedPoints`;
- when `optionCode` is absent, submit is valid only for items without configured options. In that case B does not calculate option points and returns `appliedPoints=null`, `maxPoints=<calculated or null>`, `exceedsMaxPoints=false`, and `warningMessage=null`;
- when the selected item has options and `optionCode` is absent, submit fails with `400 VAL-4001`;
- when computed/applied points exceed `calculateMaxPoints`, submission still succeeds and returns warning fields.

Scoring fact persistence decision:

- Minimal B persists the submit-time scoring snapshot in `application_fact` so the application detail page and later review flow can show the same values even if Nacos scoring rules change. The submit path writes one `application_fact` row per application submission and replaces that row on resubmission from `RETURNED`. The B-facing detail response must include the persisted `optionCode`, `appliedPoints`, `maxPoints`, `exceedsMaxPoints`, and `warningMessage` fields.

### 6.4 Withdraw

Endpoint:

```http
POST /api/student/applications/{applicationId}/withdraw
```

Request:

```json
{
  "reason": "材料需要重新整理",
  "expectedVersion": 1
}
```

Behavior:

- only owner can withdraw;
- Minimal B must allow `SUBMITTED -> WITHDRAWN`;
- `DRAFT` and `RETURNED` remain editable through update, not withdraw;
- `APPROVED`, `REJECTED`, and `WITHDRAWN` cannot be withdrawn again.

### 6.5 List Own Applications

Existing endpoint:

```http
GET /api/student/query/applications
```

Auth:

- authenticated user with `application.view.self`.

Minimal B keeps this endpoint paged as `ApiResponse<PageResult<ApplicationRecordView>>`. The target compact row shape is:

```json
{
  "total": 1,
  "records": [
    {
      "applicationId": 21001,
      "applicantUserId": 1001,
      "orgUnitId": 2010,
      "orgPath": "/WHUT/CS/CS2022/CS2201",
      "categoryCode": "INTELLECTUAL",
      "itemCode": "INTELLECTUAL_PAPER"
    }
  ]
}
```

For the application page, Minimal B adds a detail endpoint for a single owned application. List rows stay compact.

### 6.6 Get Own Application Detail

New endpoint:

```http
GET /api/student/applications/{applicationId}
```

Response data:

```json
{
  "applicationId": 21013,
  "applicantUserId": 1001,
  "orgUnitId": 2010,
  "categoryCode": "INTELLECTUAL",
  "itemCode": "INTELLECTUAL_PAPER",
  "academicYear": "2025-2026",
  "term": "上学期",
  "title": "期刊论文录用申请",
  "description": "论文已收到录用通知，申请智育加分。",
  "status": "DRAFT",
  "submittedAt": null,
  "createdAt": "2026-07-06T10:00:00",
  "updatedAt": "2026-07-06T10:00:00",
  "version": 0,
  "optionCode": "PAPER_CORE_FIRST_AUTHOR",
  "appliedPoints": 2.00,
  "maxPoints": 6.00,
  "exceedsMaxPoints": false,
  "warningMessage": null,
  "attachments": [
    {
      "fileId": "file_xxx",
      "originalFilename": "award.pdf",
      "contentType": "application/pdf",
      "size": 128,
      "sortNo": 0
    }
  ]
}
```

Detail response must not expose `storageKey`, `bucket`, `objectKey`, or raw file URLs. File preview/download still goes through E file read endpoints.

## 7. Data Model and SQL Safety

B-owned runtime tables:

- `application_submission`
- `application_attachment`
- `application_fact`

The frozen B SQL contains destructive `DROP TABLE IF EXISTS` and MySQL-specific DDL decorations. Runtime initialization must not blindly run it against a database that may contain submitted applications.

Write operations must be transactional:

- create draft writes `application_submission` and all `application_attachment` rows in one transaction;
- update draft updates the submission row and replaces all attachment rows in one transaction;
- submit updates the submission status and writes or replaces the submit-time `application_fact` snapshot in one transaction;
- withdraw updates status/version in one transaction.

Required artifact:

```text
docs/team-delivery/group-b-student-application.safe-init.sql
```

Requirements:

- use `CREATE TABLE IF NOT EXISTS`;
- avoid `DROP TABLE`;
- keep MySQL and H2 MySQL-mode executable subset;
- set `application_submission.application_id BIGINT NOT NULL AUTO_INCREMENT`;
- set `application_attachment.id BIGINT NOT NULL AUTO_INCREMENT`;
- align `application_attachment` columns with `ApplicationAttachmentMapper`:
  - current mapper writes `storage_key`, `original_filename`, `content_type`, `size`, `uploaded_by`, `sort_no`;
  - frozen SQL uses `selected_source`, `snapshot_*`; Minimal B preserves current mapper names for runtime and documents this as a deliberate divergence from the frozen target-state SQL.
- preserve existing runtime rows on rerun;
- use guarded seed inserts if seed rows are included.

## 8. Permission Model

Write endpoints should express their permission requirements explicitly. Global `anyRequest().authenticated()` is not enough evidence for B write authorization.

Required annotations:

- create draft: `@PreAuthorize("hasAuthority(T(...AuthorizationPermissionCodes).APPLICATION_SUBMIT)")`
- update draft: `@PreAuthorize("hasAuthority(T(...AuthorizationPermissionCodes).APPLICATION_UPDATE)")`
- submit: `@PreAuthorize("hasAuthority(T(...AuthorizationPermissionCodes).APPLICATION_SUBMIT)")`
- withdraw: `@PreAuthorize("hasAuthority(T(...AuthorizationPermissionCodes).APPLICATION_UPDATE)")`
- detail/list own applications: `APPLICATION_VIEW_SELF`

The implementation plan must verify the active IAM initialization path gives normal student accounts all three authorities required by this spec: `application.submit`, `application.update`, and `application.view.self`. The frozen A-group SQL already assigns these to the `STUDENT` role; if a runtime bootstrap path omits one, Minimal B must update that bootstrap path or document that deployments must run the full A-group seed first.

Security tests must run with filters enabled. `standaloneSetup` and `@AutoConfigureMockMvc(addFilters = false)` are mapping tests only, not security evidence.

## 9. Error Semantics

Use existing common error codes:

| Scenario | HTTP | Code |
|---|---:|---|
| invalid request body | 400 | `VAL-4001` |
| unauthenticated | 401 | `AUTH-4010` or token-specific auth code |
| missing authority | 403 | `AUTH-4030` |
| non-owner operation | 403 | `AUTH-4030` |
| application not found | 404 | `RES-4040` |
| duplicate active application | 409 | `BIZ-4090` |
| duplicate attachment fileId | 409 | `BIZ-4090` |
| version mismatch | 409 | `BIZ-4090` |
| state does not allow operation | 409 | `BIZ-4090` |
| application window closed | 409 | `BIZ-4091` |

Execution should avoid changing broad global exception mapping unless required. Branch-specific service exceptions should use existing exception classes that map to the target codes above. Because the current shared error enum has only one generic conflict code, clients that need different conflict handling must key off the HTTP status plus `message` until a future common-error-code expansion is specified.

## 10. Acceptance Criteria

The implementation plan is complete when these are true:

- B safe-init SQL can be rerun without dropping or overwriting runtime application rows.
- `application_submission` and `application_attachment` runtime DDL match current generated-key mapper behavior.
- Student write endpoints require authenticated users and appropriate student write authorities with filters enabled.
- Runtime IAM seed coverage is verified for `application.submit`, `application.update`, and `application.view.self`.
- Create draft binds current user as applicant and accepts only owner active or public `PUBLISHED + ALL` file IDs.
- Update draft replaces attachment bindings and requires the expected version.
- Submit enforces window-open and non-empty application content.
- Submit persists and later reloads `optionCode`, `appliedPoints`, `maxPoints`, `exceedsMaxPoints`, and `warningMessage` through the detail endpoint.
- Withdraw supports `SUBMITTED -> WITHDRAWN` and rejects `DRAFT`, `RETURNED`, `APPROVED`, `REJECTED`, and `WITHDRAWN`.
- Student can retrieve enough owned application data to reopen an edit page without storage internals.
- Focused tests cover controller contract, service state rules, repository persistence, SQL idempotency, and security filters.

## 11. Testing Strategy

Add or update focused tests:

- `StudentApplicationSubmissionControllerWebMvcTest`
  - request/response shape for create, update, submit, withdraw, detail if added.
- `StudentApplicationWriteSecurityIntegrationTest`
  - filters enabled;
  - anonymous rejected;
  - authenticated student with proper authorities allowed;
  - authenticated user lacking authority rejected.
- IAM seed consistency test
  - effective student login context contains `application.submit`, `application.update`, and `application.view.self`.
- `ApplicationSubmissionCommandApplicationServiceTest`
  - owner checks;
  - duplicate active application;
  - duplicate file IDs;
  - version mismatch;
  - submit warning fields.
- `ApplicationSubmissionStateMachineTest`
  - explicit withdraw rule, especially `SUBMITTED -> WITHDRAWN`.
- `ApplicationSubmissionFileIdIntegrationTest`
  - owner active file;
  - public `PUBLISHED + ALL` file;
  - non-owner private file rejected;
  - update replaces attachments.
- `MybatisPlusApplicationSubmissionRepositoryIntegrationTest`
  - generated keys for `application_submission` and `application_attachment`;
  - full attachment replacement on update;
  - persisted submit-time `application_fact` snapshot can be reloaded into detail response support types once implemented.
- `TeamDeliverySqlConsistencyTest`
  - B safe-init exists;
  - no `DROP TABLE`;
  - generated-key columns exist;
  - runtime `application_attachment` column names align with mapper;
  - H2 MySQL-mode rerun preserves runtime rows.

Suggested focused verification command:

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=StudentApplicationSubmissionControllerWebMvcTest,StudentApplicationWriteSecurityIntegrationTest,ApplicationSubmissionCommandApplicationServiceTest,ApplicationSubmissionStateMachineTest,ApplicationSubmissionFileIdIntegrationTest,MybatisPlusApplicationSubmissionRepositoryIntegrationTest,TeamDeliverySqlConsistencyTest test
```

## 12. Non-Goals and Follow-Up

Follow-up specs should handle:

- C-group review task workflow;
- reviewer return/reject/approve APIs;
- scoring fact finalization;
- D-group final score import/export;
- E-group governance writes for platform rules, items, and public attachments;
- richer student dashboard summaries.

## 13. Self-Review

- This spec does not duplicate Minimal E file storage behavior.
- B write APIs continue to pass only `attachmentFileIds`.
- Storage internals stay out of B-facing responses.
- The current implementation baseline is acknowledged instead of overwritten.
- Withdraw semantics are fixed as `SUBMITTED -> WITHDRAWN` for Minimal B.
- The largest persistence risk is frozen B SQL diverging from current runtime mapper column names; this spec chooses current mapper names for runtime and requires tests documenting the divergence.

---

[2026-07-06] 修改说明：创建 Minimal B Application Write Closure spec，作为 Minimal E 合入后的下一步 B 组学生申请写链路执行依据；范围聚焦联调闭环、权限、安全初始化 SQL、状态机和学生自有详情读取，不包含审核与最终成绩链路。
