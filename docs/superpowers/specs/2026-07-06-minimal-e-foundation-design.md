# Minimal E Foundation Design

## 1. Goal

Build the smallest E-group backend foundation needed to unblock B-group student application development.

This spec deliberately covers only platform reads, evaluation item reads, file metadata reads, file access-url reads, public attachment reads, and the supporting E-owned relational tables. It does not cover E-group full platform governance, item CRUD, public attachment publishing workflows, or AI reporting.

## 2. Context

A-group IAM is implemented and provides the login, current-user, permission, organization, and scope-rule foundation. B-group application writing depends on E-group for three runtime questions:

- Is the student application window open, and what deadline should be displayed to clients?
- Which evaluation categories and items can the student apply for?
- Which file IDs can be bound to an application, either because the current user uploaded them or because they are published public attachments?

Current code already has part of this foundation:

- `POST /api/files/upload` stores the object, registers `file_asset`, and returns a stable `fileId`.
- Nacos typed config exists for platform rules and evaluation item configuration.
- Existing dynamic evaluation config endpoints are currently exposed under `/api/config/evaluation/**`; minimal E adds B-facing `/api/platform/**` routes without removing the existing config routes.
- `group-e-platform-governance-attachment-ai.sql` defines E-owned tables and seed data for `evaluation_category`, `evaluation_item`, `file_asset`, and `public_attachment_entry`.
- Student application attachment resolution already expects `file_asset` plus `public_attachment_entry` semantics.

The missing piece is a stable minimal E HTTP surface that B can use without depending on target-state E features.

## 3. Scope

### 3.1 In Scope

- Ensure the E-owned tables from `docs/team-delivery/group-e-platform-governance-attachment-ai.sql` exist locally and in deployment DDL:
  - `evaluation_category`
  - `evaluation_item`
  - `file_asset`
  - `public_attachment_entry`
- Keep the existing upload endpoint as the file creation path:
  - `POST /api/files/upload`
- Add or standardize read-only platform and item endpoints:
  - `GET /api/platform/menu/status`
  - `GET /api/platform/menu/deadline`
  - `GET /api/platform/evaluation-items`
- Keep existing dynamic option endpoints as the option source for this iteration:
  - `GET /api/config/evaluation/options/{itemCode}`
- Add read-only file endpoints:
  - `GET /api/files/{fileId}`
  - `GET /api/files/{fileId}/access-url`
  - `GET /api/files/public-attachments`
- Ensure B-group can bind:
  - current user's own `ACTIVE` uploaded files
  - `PUBLISHED + ALL` public attachments
- Add focused tests for the new contracts and repository behavior.

### 3.2 Out of Scope

- `PATCH /api/platform/menu/status`
- `PUT /api/platform/menu/deadline`
- `POST /api/platform/evaluation-items`
- `PATCH /api/platform/evaluation-items/{itemCode}`
- `POST /api/files/public-attachments`
- `PATCH /api/files/public-attachments/{entryId}/offline`
- AI report generation and query APIs
- Role-scoped or org-scoped public attachment consumption by B-group
- New async jobs, batch upload, chunked upload, or virus scanning

## 4. Design Decisions

### 4.1 Runtime Source of Truth

Platform rule reads use Nacos typed config as the runtime source of truth.

Evaluation item reads should expose B-facing item definitions from Nacos typed config for dynamic behavior, while E-owned relational tables are still created and seeded as the database baseline for governance and future admin pages. The first implementation should not introduce write-back from Nacos to the database.

File metadata and public attachment reads use MySQL tables:

- `file_asset`
- `public_attachment_entry`

B-facing file responses must not expose infrastructure location fields. `bucket`, `storage_key`, `objectKey`, raw `publicUrl`, and uploader identifiers are internal implementation details for storage, authorization, or migration only.

### 4.2 Minimal Contract Over Target-State Completeness

The E-group document describes a broad target state. This spec narrows the first E iteration to contracts that immediately unblock B. If an endpoint is not needed by B's create/update/submit flow, it stays out of this implementation round.

### 4.3 Divergence From Target E Permissions

The target E-group document marks platform and item reads as governance/admin endpoints. Minimal E intentionally exposes the B-facing read endpoints to any authenticated user:

- students need `/api/platform/menu/status` and `/api/platform/menu/deadline` to render the application entry and deadline;
- students need `/api/platform/evaluation-items` to build the application form;
- write-side governance endpoints remain out of scope and must stay admin-only in later specs.

This divergence is limited to read-only endpoints required by B-group. It must not be copied to future E write APIs.

### 4.4 Database Initialization Safety

The existing E SQL script contains `DROP TABLE IF EXISTS` statements. Implementation must not blindly run that script against a database that may already contain uploaded file metadata. For local initialization, the implementation plan should use one of these safe paths:

- run the script only when the E-owned tables do not exist yet, or
- create a non-destructive initialization script at `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql` that uses `CREATE TABLE IF NOT EXISTS` and seed inserts guarded by primary-key or unique-key checks.

This is especially important for `file_asset`, because `POST /api/files/upload` already writes runtime data there.

The preferred implementation artifact is the safe-init SQL file. `group-e-platform-governance-attachment-ai.safe-init.sql` is runtime-compatible DDL, not a byte-for-byte copy of the frozen team-delivery SQL. It may correct mapper-required details such as generated primary keys and missing runtime columns while preserving table names, business columns, seed semantics, and non-destructive behavior. If the implementation plan chooses a manual DBeaver-only procedure instead, it must document the exact procedure and include a verification query proving existing `file_asset` rows survive a rerun.

Runtime DDL must stay aligned with current mappers:

- `file_asset.id` must be `BIGINT NOT NULL AUTO_INCREMENT`, because `FileAssetWriteMapper` inserts uploaded files without supplying `id` and expects generated keys.
- `public_attachment_entry.id` should also be `BIGINT NOT NULL AUTO_INCREMENT` so future publish flows can insert rows without hand-allocating IDs.
- `public_attachment_entry.updated_at` must exist in runtime DDL because current `PublicAttachmentEntryMapper` selects it and `PublicAttachmentEntryDO` maps it.
- Safe-init seed inserts for `public_attachment_entry` must provide `updated_at`, because the frozen SQL seed currently omits it while runtime DDL requires it. Use the same value as `created_at` for deterministic seed rows unless a row has a more specific update timestamp.
- Seed rows may continue to provide explicit IDs; subsequent runtime inserts must still auto-generate IDs.

### 4.5 Fail Closed for File Reads

File metadata and access-url endpoints must fail closed:

- Missing `fileId` returns `404 RES-4040`.
- Non-`ACTIVE` file assets are not readable through normal file endpoints.
- A user can read their own active file.
- Public attachment reads only expose entries with `status=PUBLISHED`, `scope_type=ALL`, and an active backing `file_asset`.
- Review-side file access based on application binding is intentionally deferred to C/E integration work.

### 4.6 Access URL Semantics

The minimal `GET /api/files/{fileId}/access-url` endpoint returns a URL derived from `file_asset.storage_key` plus the current OSS typed config.

For this iteration:

- If `OssStorageConfig.publicBaseUrl` is configured, return `{publicBaseUrl}/{storage_key}` as `accessUrl`.
- If no usable URL can be derived from current config, return `503 EXT-5033` rather than exposing `storage_key` as a fake URL.
- Return `expiresAt = null` for public-base-url derived URLs.

Presigned private-bucket URL generation can be added later without changing the endpoint shape.

## 5. Data Model

### 5.1 `evaluation_category`

Owned by E-group. Used as the relational category baseline.

Required fields:

- `id`
- `category_code`
- `category_name`
- `display_name`
- `description`
- `sort_no`
- `status`
- `created_at`
- `updated_at`

### 5.2 `evaluation_item`

Owned by E-group. Used as the relational item baseline.

Required fields:

- `id`
- `category_code`
- `item_code`
- `item_name`
- `apply_mode`
- `review_mode`
- `score_mode`
- `cap_rule_json`
- `description`
- `sort_no`
- `status`
- `created_at`
- `updated_at`

### 5.3 `file_asset`

Owned by E-group. Runtime source for uploaded file metadata.

Runtime primary key:

- `id` is an auto-generated database primary key.
- `file_id` is the stable business identifier exposed to clients.

Required fields:

- `id`
- `file_id`
- `storage_key`
- `bucket`
- `original_filename`
- `content_type`
- `size`
- `sha256`
- `uploader_user_id`
- `uploader_type`
- `upload_channel`
- `status`
- `created_at`
- `updated_at`

### 5.4 `public_attachment_entry`

Owned by E-group. Runtime source for public attachment listing.

Runtime primary key:

- `id` is the database primary key and maps to API `entryId`.

Required fields:

- `id`
- `file_id`
- `display_name`
- `description`
- `category_code`
- `scope_type`
- `scope_value`
- `status`
- `published_by`
- `published_at`
- `sort_no`
- `created_at`
- `updated_at`

B-group may consume only `status=PUBLISHED AND scope_type=ALL` in this iteration.

The implementation should not require database foreign keys between `public_attachment_entry.file_id` and `file_asset.file_id` for this iteration, because the current frozen SQL uses indexes but no foreign key. Repository queries must still join to `file_asset` and filter out entries whose backing file is missing or inactive.

## 6. HTTP Contracts

All responses use `ApiResponse<T>`.

### 6.1 `GET /api/platform/menu/status`

Purpose: expose platform switch state needed by B-group.

Auth: authenticated user. No admin permission is required for read access because students need to know whether the application entry is open.

Response data:

```json
{
  "studentApplyEnabled": true,
  "finalSubmitEnabled": false,
  "source": "NACOS"
}
```

Failure:

- typed config missing: `503 CFG-5031`

### 6.2 `GET /api/platform/menu/deadline`

Purpose: expose application and final submission deadline information for B/D clients.

Auth: authenticated user.

Response data:

```json
{
  "studentApplyDeadline": "2026-09-30T23:59:59+08:00",
  "finalSubmitDeadline": "2026-10-15T23:59:59+08:00",
  "source": "NACOS"
}
```

If the current `PlatformRuleConfig` lacks deadline fields, the implementation must extend the typed config model and tolerate absent fields as `null`.

The deadline fields added to `PlatformRuleConfig` should be:

- `studentApplyDeadline`
- `finalSubmitDeadline`

Both should be nullable strings in ISO-8601 format at the API boundary. The minimal implementation does not need to enforce the deadline; it only exposes the configured value for B/D clients.

Failure:

- typed config missing: `503 CFG-5031`

### 6.3 `GET /api/platform/evaluation-items`

Purpose: give B-group the active item catalog for creating and updating applications.

Auth: authenticated user.

Query parameters:

- `categoryCode` optional. When present, return only that category.

Response type: `ApiResponse<List<EvaluationItemResponse>>`.

Response shape: flat list, not grouped and not paged. This endpoint is the student/B-facing catalog surface, not the target-state management query from the E-group document.

Minimal E always returns enabled items only. It does not expose an `enabledOnly=false` management mode.

Response data item:

```json
{
  "categoryCode": "INTELLECTUAL",
  "categoryName": "智育",
  "itemCode": "INTELLECTUAL_PAPER",
  "itemName": "论文发表",
  "description": "学术论文发表加分",
  "maxPoints": null,
  "maxPointsExpression": null,
  "applyMode": "STUDENT_APPLY",
  "enabled": true,
  "sortOrder": 2,
  "optionsKey": "intellectual-paper"
}
```

`maxPoints`, `maxPointsExpression`, and `optionsKey` are read from the existing Nacos-backed evaluation item configuration. Relational `evaluation_item.cap_rule_json` stays a seeded governance baseline in this iteration and is not the runtime source for this B-facing endpoint.

Ordering:

- Sort the flat result by `categoryCode ASC`, then `sortOrder ASC`, then `itemCode ASC`.
- Do not rely on Java `Map` iteration order from typed config as an API contract.

Failure:

- config missing: `503 CFG-5031`
- unknown category with no matching items: `200 OK` with empty list

Option source for this iteration:

- `GET /api/platform/evaluation-items` returns `optionsKey` only.
- B clients continue to use the existing `GET /api/config/evaluation/options/{itemCode}` endpoint for option values and point previews.
- A later E spec may add `/api/platform/evaluation-items/{itemCode}/options`; that is not part of minimal E.

### 6.4 `POST /api/files/upload`

Purpose: existing upload path.

Auth: authenticated user.

Behavior remains:

- validate non-empty file
- validate size and content type
- store object through `FileStorageService`
- register metadata in `file_asset`
- return stable `fileId`

Response data for the B-facing contract:

```json
{
  "fileId": "file_xxx",
  "originalFilename": "证明材料.pdf",
  "contentType": "application/pdf",
  "size": 256000
}
```

This endpoint is considered part of minimal E as the file creation path. For this iteration, the response must use the safe subset above and must not contain `bucket`, `objectKey`, `storageKey`, or raw `publicUrl`. Storage location fields remain internal to the application and persistence layers.

### 6.5 `GET /api/files/{fileId}`

Purpose: return file metadata by stable business file ID.

Auth: authenticated user.

Read rule:

- current user owns the active file, or
- file is published as `PUBLISHED + ALL` in `public_attachment_entry`

Response data:

```json
{
  "fileId": "file_xxx",
  "originalFilename": "证明材料.pdf",
  "contentType": "application/pdf",
  "size": 256000,
  "status": "ACTIVE",
  "createdAt": "2026-07-06T10:00:00"
}
```

The response is intentionally minimal. It must not expose raw credentials, `bucket`, `storageKey`, or uploader identifiers. Owner/private reads and public-attachment reads use the same public response shape; repository and service layers may use uploader fields internally for authorization.

Failure:

- file missing or inactive: `404 RES-4040`
- authenticated but not owner and not public: `403 AUTH-4030`

### 6.6 `GET /api/files/{fileId}/access-url`

Purpose: return a client-usable URL after the same read authorization as metadata.

Auth: authenticated user.

Response data:

```json
{
  "fileId": "file_xxx",
  "accessUrl": "https://cdn.whut.example.com/uploads/xxx.pdf",
  "expiresAt": null
}
```

Failure:

- same as metadata endpoint
- URL cannot be generated: `503 EXT-5033`

Implementation note:

- Build the URL from `OssStorageConfig.publicBaseUrl` and `file_asset.storage_key`.
- Normalize to exactly one slash between base URL and storage key.
- Do not return `bucket` or `storage_key` in this response.

### 6.7 `GET /api/files/public-attachments`

Purpose: list public attachments B-group can offer as reusable application materials.

Auth: authenticated user.

Query parameters:

- `categoryCode` optional.

Response type: `ApiResponse<List<PublicAttachmentResponse>>`.

Response shape: flat list, not grouped and not paged. This endpoint is the B-facing public material picker, not the target-state management query from the E-group document.

Filter:

- `public_attachment_entry.status = PUBLISHED`
- `public_attachment_entry.scope_type = ALL`
- backing `file_asset.status = ACTIVE`

Response data item:

```json
{
  "entryId": 14001,
  "fileId": "FILE-0008",
  "displayName": "综测申请模板",
  "description": "学生申请材料填写模板",
  "categoryCode": "INTELLECTUAL",
  "originalFilename": "综测申请模板.pdf",
  "contentType": "application/pdf",
  "size": 142000,
  "publishedAt": "2026-05-11T09:00:00",
  "sortNo": 10
}
```

Ordering:

- `sortNo ASC`
- `publishedAt DESC`
- `entryId ASC`

Failure:

- no matching attachments: `200 OK` with empty list

## 7. Permission Model

Minimal E read endpoints are available to any authenticated user unless noted otherwise.

Implementation should express this explicitly as authenticated access, not admin permission. Prefer controller-level `@PreAuthorize("isAuthenticated()")` on the new B-facing read controllers when the global security configuration alone does not make the requirement obvious in tests.

Rationale:

- B-group student application pages need these reads.
- Admin-only permissions would block normal student flows.
- Write-side governance endpoints are out of scope and will later use admin permissions.

Upload keeps its existing authenticated-user requirement.

Tests for these endpoints must cover authenticated access without E-admin authorities and reject anonymous access through the existing security layer.

Security tests must run with the security filter chain enabled. Controller-only `@WebMvcTest` slices that disable filters can cover request/response mapping, but they are not sufficient evidence for anonymous rejection or authenticated access.

## 8. Integration With B-Group

B-group can rely on the following sequence:

1. User logs in through A.
2. Client calls `GET /api/platform/menu/status` and `GET /api/platform/menu/deadline`.
3. Client calls `GET /api/platform/evaluation-items?categoryCode=...`.
4. User uploads personal files with `POST /api/files/upload`.
5. Client optionally lists `GET /api/files/public-attachments?categoryCode=...`.
6. B-group create/update request sends only `attachmentFileIds`.
7. B-group application service validates those file IDs through existing attachment resolver semantics:
   - owner active file
   - or published public `ALL` attachment

B-group must not receive, depend on, or store `storageKey`, `objectKey`, `bucket`, or raw upload `publicUrl` from the frontend. When a client needs to display or download a file, it must call `GET /api/files/{fileId}/access-url`.

## 9. Error Semantics

Use existing common error codes:

| Scenario | HTTP | Code |
|---|---:|---|
| invalid request parameter | 400 | `VAL-4001` |
| unauthenticated | 401 | auth token code from security layer |
| no file visibility | 403 | `AUTH-4030` |
| missing file | 404 | `RES-4040` |
| config not materialized | 503 | `CFG-5031` |
| storage URL generation failure | 503 | `EXT-5033` |

## 10. Testing Strategy

### 10.0 Acceptance Criteria

This spec is ready for implementation when the following are true:

- A fresh local database can be initialized with the four E-owned tables.
- Re-running local initialization does not drop or overwrite existing `file_asset` runtime rows.
- `GET /api/platform/menu/status` returns current Nacos platform switch values.
- `GET /api/platform/menu/deadline` returns nullable deadline fields without enforcing them.
- `GET /api/platform/evaluation-items` returns enabled B-facing items and supports `categoryCode`.
- `POST /api/files/upload` returns the safe B-facing subset, includes `fileId`, omits storage location fields, and registers `file_asset`.
- A real upload write can insert `file_asset` without supplying `id`; the database auto-generates `file_asset.id`.
- `GET /api/files/{fileId}` allows owner/public reads and denies private non-owner reads.
- `GET /api/files/{fileId}/access-url` derives a URL only from configured `publicBaseUrl`; missing base URL returns `EXT-5033`.
- `GET /api/files/public-attachments` returns only `PUBLISHED + ALL` entries backed by active files.

### 10.1 Controller Tests

Add WebMvc tests for:

- platform status read
- platform deadline read with null deadlines allowed
- evaluation item list with and without `categoryCode`
- upload response exposes `fileId` and omits `bucket`, `objectKey`, `storageKey`, and raw `publicUrl`
- file metadata visible to owner
- file metadata denied to non-owner
- public attachment list filters out `ROLE`, `ORG_UNIT`, `DRAFT`, `OFFLINE`, and inactive files
- access-url returns a usable URL when derivable

### 10.2 Application Service Tests

Add service tests for:

- item sorting and enabled-item filtering
- file visibility decision for owner
- file visibility decision for `PUBLISHED + ALL`
- denial for inactive file
- denial for non-owner private file

### 10.3 Repository Tests

Add MyBatis integration tests for:

- `file_asset.id` auto-generation on upload-style insert
- `file_asset` lookup by `fileId`
- public attachment list join with `file_asset`
- category filter behavior
- status/scope filtering

### 10.4 Verification Command

The implementation plan should include a focused Maven command similar to:

```bash
mvn -pl whut-eval-app -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=FileQueryControllerWebMvcTest,PlatformReadControllerWebMvcTest,EvaluationItemQueryControllerWebMvcTest,FileQueryApplicationServiceTest,PublicAttachmentQueryRepositoryIntegrationTest,MinimalEReadSecurityIntegrationTest test
```

The exact test class names may be adjusted during planning, but the command must cover all new minimal E behavior, including the security-focused test class.

Add one security-focused MVC or integration test class with filters enabled for:

- authenticated non-admin users can call B-facing read endpoints
- anonymous users are rejected by the existing security layer

## 11. Non-Goals and Follow-Up Work

Follow-up specs should handle:

- platform governance write APIs
- evaluation item CRUD and database-backed governance UI
- public attachment publish/offline workflows
- role/org scoped public attachment visibility
- C/E review attachment access based on application binding
- D/E final-score platform rules
- AI report generation and report query

## 12. Self-Review

- No target-state write API is included in this minimal spec.
- B-group has every E dependency needed for the next application-write spec.
- File reads fail closed and do not expose infrastructure credentials.
- B-facing upload and file query contracts do not require frontend storage of `bucket`, `storageKey`, `objectKey`, or raw `publicUrl`.
- Nacos remains the platform rule and dynamic item runtime source for this iteration.
- Relational E tables are still required now so DBeaver/local database state matches the frozen table matrix, but initialization must be non-destructive when runtime data already exists.

---

[2026-07-06 17:47:36+08:00] 修改说明：补充 B-facing evaluation item 响应字段来源，并明确 safe-init SQL 为 `public_attachment_entry` 种子提供 `updated_at` 的要求；理由是避免实现阶段误用关系表 `cap_rule_json` 作为运行时来源，或在新增 `updated_at` DDL 后保留不可执行的种子 INSERT。
