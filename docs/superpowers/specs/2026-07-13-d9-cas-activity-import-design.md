# D-9 CAS Activity Import Design

## Goal

Implement D-9: `POST /api/admin/imports/cas-activities`.

The endpoint synchronously imports cultural and sports activity participation scores into draft final records. It follows the D-group "synchronous import + immediate receipt" model, fails the whole request for invalid activity metadata, returns row-level student failures in `failedRows`, and does not introduce async jobs, import history tables, D-10 export, D-11 unsubmitted roster, or B-9 candidate-source tables.

## Existing Contracts

Source contract:

- `docs/team-delivery/group-d-score-finalization-import-export.md` freezes the D-9 route, permission, multipart request shape, response fields, and request-level error surface.
- D-9 uses the existing `score.import` authority.
- D-9 writes into the existing D-owned `final_record` and `final_component_score` tables only.
- Activity metadata errors are request-level failures. Student-row data errors are returned as row-level `failedRows`.
- The E-owned `evaluation_item` table is the source of truth for `itemCode`; D-9 reads it but does not write or extend E metadata.

Current implementation patterns:

- Keep module boundaries used by D-7 and D-8: domain records in `whut-eval-domain`, application services and ports in `whut-eval-application`, POI/MyBatis adapters in `whut-eval-infra`, HTTP DTO/controller code in `whut-eval-interfaces`, and integration tests in `whut-eval-app`.
- `AdminScoreImportController` owns `/api/admin/imports` and already exposes D-7 and D-8.
- `LectureImportApplicationService` proves the closest shape for D-9: request metadata normalization, deterministic batch id, DB-backed batch lock, current-organization scope checks, DRAFT-only final-record mutation, insert-only imported components, total recalculation, and row-level failure responses.
- `ExcelLectureImportParser` proves the workbook parser contract: first sheet only, exact ordered headers, `DataFormatter`, 5 MB limit, 5000 non-blank data-row limit, and template errors mapped to `ValidationException`.
- `MybatisLectureImportRepository` proves final-record creation/reload, `SELECT ... FOR UPDATE`, duplicate final-record race handling, and category total recalculation.

## Scope

In scope:

- Add `POST /api/admin/imports/cas-activities`.
- Parse `.xlsx` or `.xls` activity participant Excel files synchronously.
- Validate `file`, `title`, `itemCode`, `scoreValue`, `heldAt`, and `academicYear`.
- Validate `itemCode` by reading an active E-owned `evaluation_item` row and requiring `category_code = 'SPORTS'`.
- Generate deterministic `activityBatchId`.
- Insert one imported final-score component for each successful participant row.
- Use request-level `scoreValue` for every successful row in the batch.
- Use the active `evaluation_item.category_code` and `item_code` in `final_component_score`.
- Enforce `score.import` authority and active `score.import` organization scope.
- Keep valid row imports when other rows fail row-level validation.
- Reject duplicate activity batches with `409 / BIZ-4090`.
- Add focused parser, application, repository, MVC, security, and regression tests.

Out of scope:

- D-7 mentor import behavior changes.
- D-8 lecture import behavior changes except a narrow shared helper if it reduces real duplication without changing public contracts.
- D-10 final-score export.
- D-11 unsubmitted roster.
- B-9 student lecture/activity candidate query.
- New activity catalog, activity ids, attendance state, claim eligibility state, or candidate-source storage.
- Frontend upload UI.
- Import batch tables, downloadable failure files, object-storage persistence, or async job status APIs.

Cross-group boundary:

- D-9 imports final-score activity participation rows after an activity has already been selected outside this endpoint.
- D-9 does not close B-9's candidate-source dependency. B-9 still needs a separate contract for `lectureId`, `title`, `heldAt`, `maxScore`, and `attendanceStatus`.
- D-9 reuses E's item metadata only to validate `itemCode` and determine `category_code`. It does not interpret all E `cap_rule_json` rules beyond the request-level score upper bound described below.

## Approach Decision

Recommended approach: build a D-9-specific import slice that mirrors D-8 where the shape is genuinely identical, with only small shared helpers extracted after tests expose useful duplication.

Trade-offs:

- A generic import framework would reduce some repetition now, but D-7 upserts components, D-8 inserts lecture batches under `INTELLECTUAL_LECTURE`, and D-9 inserts request-scored activity components under dynamic SPORTS item codes. A broad abstraction would hide important semantic differences.
- Copying D-8 and changing literals is faster, but it risks carrying lecture-specific names, messages, and item-code constants into D-9.
- A D-9-specific slice with narrow shared helpers keeps the public contracts explicit and still allows later consolidation when D-10 export and more import variants prove stable reuse points.

## HTTP Contract

Endpoint:

`POST /api/admin/imports/cas-activities`

Consumes:

`multipart/form-data`

Permission:

`score.import`

Controller annotation:

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)")
```

Request parameters:

| Parameter | Required | Rule |
|---|---:|---|
| `file` | yes | Non-empty `.xlsx` or `.xls` Excel file. Other workbook formats such as `.xlsm`, `.csv`, or text files fail before parsing with `导入模板错误：文件不可解析`. |
| `title` | yes | Non-blank after trim, max 255 Unicode code points. Leading and trailing whitespace are removed. The trimmed title is used byte-for-byte for display and deterministic batch identity; internal whitespace, control characters, zero-width characters, case, and Unicode normalization form are otherwise preserved. This means duplicate-batch detection is metadata-exact, not visual-similarity based. |
| `itemCode` | yes | Non-blank after trim, max 64 characters, must resolve to an active `evaluation_item` row whose `category_code = 'SPORTS'`. |
| `scoreValue` | yes | Trimmed before validation; strict decimal text matching `^[0-9]+(\.[0-9]+)?$`, `0 <= value <= 99999999.99`, at most 2 decimal places. Negative values, thousand separators, percentages, currency symbols, and scientific notation are invalid. |
| `heldAt` | yes | Trimmed before validation; ISO local date-time parsed with `LocalDateTime.parse(heldAt)`. Minimum accepted precision is `yyyy-MM-ddTHH:mm`; date-only input and date-hour input without minutes are invalid. Omitted seconds default to `00`, fractional seconds are truncated, and timezone offsets are not accepted. The batch id uses whole seconds. |
| `academicYear` | yes | Trimmed before validation; must match `^\d{4}-\d{4}$`, and the second year must equal first year + 1. |

Successful response:

```json
{
  "success": true,
  "data": {
    "activityBatchId": "ACTIVITY-20252026-20260518143000-5E7A1B2C9D44",
    "title": "校运会志愿服务",
    "itemCode": "SPORTS_COMPETITION",
    "scoreValue": 0.50,
    "totalCount": 3,
    "successCount": 2,
    "failedCount": 1,
    "failedRows": [
      {
        "rowNo": 4,
        "code": "STUDENT_NOT_FOUND",
        "message": "studentNo 对应学生不存在或未启用",
        "rawValue": {
          "studentNo": "2022305999",
          "displayText": "志愿服务签到"
        }
      }
    ]
  }
}
```

Successful responses return normalized metadata for fields present in the frozen D-9 response:

- `title` is trimmed.
- `itemCode` is the canonical `evaluation_item.item_code` resolved from the trimmed request value. With the current schema this matches the trimmed request value, because `item_code` is unique and lookup is by exact `item_code`.
- `scoreValue` is returned at scale 2 as a JSON number.
- `heldAt` and `academicYear` are validated and normalized for `activityBatchId`, but they are not returned as top-level data fields because the D-9 delivery table does not list them.

`failedRows` are returned in ascending `rowNo` order. `rowNo` is the Excel worksheet's 1-based physical row number; the header is row 1 and the first data row is row 2.

`failedRows` fields are frozen as:

`rowNo`, `code`, `message`, `rawValue`

`rawValue` is frozen as an object containing exactly `studentNo` and `displayText`. Each value is the trimmed `DataFormatter` string read from the workbook before row-level normalization; blank strings are returned as `null`.

Response count semantics:

- `totalCount` is the number of non-blank data rows after the header.
- `successCount` is the number of rows that successfully inserted an activity component.
- `failedCount` is `failedRows.size()`.
- `successCount + failedCount = totalCount`.
- A workbook that contains only the header still validates metadata, generates the deterministic batch id, acquires the batch lock, and checks duplicates. If another same-batch import is running, it returns the in-flight `409`; if the same `activityBatchId` already has persisted components, it returns the already-imported `409`. Otherwise it returns `200` using the same success response shape: `activityBatchId`, normalized `title`, canonical `itemCode`, scale-2 `scoreValue`, `totalCount = 0`, `successCount = 0`, `failedCount = 0`, and `failedRows = []`. It persists no batch marker. See Import Semantics for the authoritative zero-success retry rule.

Request-level failures:

| Scenario | HTTP | Code | Message rule |
|---|---:|---|---|
| Missing or empty file | `400` | `VAL-4001` | `上传文件不能为空` |
| Missing or blank title | `400` | `VAL-4001` | `title 不能为空` |
| Title too long | `400` | `VAL-4001` | `title 长度不能超过 255` |
| Missing or blank `itemCode` | `400` | `VAL-4001` | `itemCode 不能为空` |
| `itemCode` too long | `400` | `VAL-4001` | `itemCode 长度不能超过 64` |
| Missing or invalid `scoreValue`, including negative values, thousand separators, percentages, currency symbols, and scientific notation | `400` | `VAL-4001` | `scoreValue 必须是数字` |
| `scoreValue > 99999999.99` | `400` | `VAL-4001` | `scoreValue 必须在 0 到 99999999.99 之间` |
| `scoreValue` has more than 2 decimal places | `400` | `VAL-4001` | `scoreValue 最多保留 2 位小数` |
| `scoreValue` exceeds item `maxPoints` when `allowOverflow = false` | `400` | `VAL-4001` | `scoreValue 必须在 0 到项目允许范围之间` |
| Missing or invalid `heldAt` | `400` | `VAL-4001` | `heldAt 格式非法` |
| Missing or invalid `academicYear` | `400` | `VAL-4001` | `academicYear 不合法` |
| File too large or too many data rows | `400` | `VAL-4001` | `文体活动导入文件最多支持 5000 行且不超过 5MB` |
| Missing required header, header mismatch, no sheet, unreadable workbook | `400` | `VAL-4001` | Starts with `导入模板错误：` |
| Active SPORTS `evaluation_item` not found for `itemCode` | `404` | `RES-4040` | `对应项目定义不存在` |
| Active SPORTS `evaluation_item` has missing, invalid, or unsupported `cap_rule_json` | `404` | `RES-4040` | `对应项目定义不存在` |
| Missing authority | `403` | `AUTH-4030` | Existing security handler response. Service-level defensive checks must map to the same external HTTP code surface and must not introduce a second public 403 contract. |
| Same activity batch currently running | `409` | `BIZ-4090` | `同一活动批次正在导入，请稍后重试` |
| Same activity batch already imported | `409` | `BIZ-4090` | `同一活动批次已导入` |
| Multipart bytes cannot be read | `503` | `EXT-5033` | `文件处理失败，请稍后重试` |
| Batch-lock storage unavailable, batch-lock SQL error, or unexpected database access failure | `500` | `SYS-5000` | Existing `DataAccessException` handler response: `数据访问异常，请稍后重试` |

Row-level validation failures return HTTP 200 and appear in `failedRows`.

For request-level `scoreValue`, trim leading and trailing whitespace before validation, then evaluate validation in this order: blank or non-strict decimal text, numeric value above `99999999.99`, scale greater than 2 decimals, then item-specific max when `allowOverflow = false`. `0`, `0.0`, and `0.00` are valid. Negative numbers fail as non-strict decimal text with `scoreValue 必须是数字`.

Frozen row-level failure mapping:

| Condition | `code` | `message` |
|---|---|---|
| `studentNo` blank | `STUDENT_NO_REQUIRED` | `studentNo 不能为空` |
| `displayText` longer than 1000 Unicode code points after trim | `DISPLAY_TEXT_TOO_LONG` | `displayText 长度不能超过 1000` |
| duplicate field-valid `studentNo` in the same workbook | `DUPLICATE_STUDENT` | `同一活动批次中学生重复` |
| eligible student not found | `STUDENT_NOT_FOUND` | `studentNo 对应学生不存在或未启用` |
| row target outside `score.import` scope | `OUT_OF_SCOPE` | `当前用户无权导入该学生文体活动成绩` |
| existing final record is not `DRAFT` | `FINAL_RECORD_LOCKED` | `已提交或已确认的最终成绩不允许导入覆盖` |

`eligible student not found` includes: no active user for `studentNo`, inactive user, no active primary membership, and inactive or missing primary organization. `OUT_OF_SCOPE` applies only after an eligible target has been found and the target's active primary organization does not match the caller's effective `score.import` scope.

When more than one row-level condition applies, use the first matching condition in the table above. Duplicate-student detection runs only after `studentNo` and `displayText` field validation pass, so field-invalid rows do not consume the duplicate key. A field-valid row consumes its normalized `studentNo` duplicate key even if it later fails student lookup, scope, or final-record lock checks.

## Excel Template

Only the first sheet is parsed.

Header row is row 1. Header names are case-sensitive and must appear in this exact order:

| Column | Header | Required | Rule |
|---:|---|---:|---|
| A | `studentNo` | yes | Existing active `iam_user.user_no`. |
| B | `displayText` | value optional | Column B and its header must exist. Cell values may be blank. Max 1000 Unicode code points after trim. When blank, the normalized row display text is the normalized request title. |

Extra columns after column B are ignored.

Blank data rows are ignored and do not count toward `totalCount`. A blank data row is a row where columns A and B are both missing or trim to blank after `DataFormatter` conversion.

`totalCount` counts non-blank data rows after the header, including rows that later fail validation.

Cell values are read with `DataFormatter`, trimmed, and blank strings become null. The parser must not evaluate formulas beyond POI's formatted value behavior.

`normalizedStudentNo` is the trimmed `DataFormatter` string from column A. D-9 does not apply case folding, zero-width/control-character stripping, Unicode normalization, or leading-zero normalization.

D-9 accepts `.xlsx` and `.xls` workbook content. Other uploaded formats are rejected by filename in the controller before byte reading when possible, and by POI/template parsing otherwise.

The controller must reject missing, empty, oversized, or unsupported-extension files before reading multipart bytes, matching D-8's defense-in-depth behavior. The parser must also reject oversized byte arrays before opening the workbook, because it is a public port and may be tested or reused outside the controller.

The 5 MB limit means 5 * 1024 * 1024 bytes and applies to the uploaded file bytes before POI parsing. The parser rejects oversized byte arrays before opening the workbook, and rejects workbooks with more than 5000 non-blank data rows using `ValidationException("文体活动导入文件最多支持 5000 行且不超过 5MB")`. Exactly 5000 non-blank data rows are allowed.

Header error messages:

- Missing header row: `导入模板错误：缺少表头`.
- Header mismatch in the first two columns: `导入模板错误：第{columnIndex}列表头应为 {expectedHeader}`.
- Workbook with no sheets: `导入模板错误：缺少工作表`.
- Unreadable workbook: `导入模板错误：文件不可解析`.

## Import Semantics

### Activity Component Identity

D-9 always writes activity scores as:

- `category_code = evaluation_item.category_code`, after item validation has required it to be `SPORTS`;
- `item_code = canonicalItemCode`, where `canonicalItemCode` is `evaluation_item.item_code` resolved from the trimmed request `itemCode`;
- `source_type = 'IMPORT'`;
- `source_ref_id = activityBatchId`.

D-9 uses insert-only semantics like D-8, not D-7's upsert semantics. A student may have several imported activity components in the same academic year when they participated in several distinct activity batches, including multiple batches under the same `itemCode`.

The request-level `scoreValue` is applied to every successful row in the batch. Per-row scores are intentionally not part of the D-9 template because the frozen D-9 delivery doc defines `scoreValue` as request metadata and states that activity metadata errors fail the whole request.

### Activity Batch Id

`activityBatchId` is deterministic from normalized request metadata:

`ACTIVITY-<academicYearWithoutDash>-<heldAt yyyyMMddHHmmss>-<uppercase 12-char hash>`

The hash input is:

`normalizedAcademicYear + "|" + normalizedHeldAt + "|" + normalizedTitle + "|" + normalizedItemCode + "|" + normalizedScoreValue`

Use SHA-256 over the UTF-8 hash input, uppercase the hexadecimal digest, and take the first 12 characters.

Normalization is fixed as:

- `normalizedAcademicYear`: request `academicYear` after trim.
- `normalizedTitle`: request `title` after trim; no internal whitespace collapsing, case folding, or Unicode normalization is applied.
- `normalizedItemCode`: canonical `evaluation_item.item_code` resolved from the trimmed request `itemCode`.
- `normalizedScoreValue`: request `scoreValue` after trim, then parsed and formatted at scale 2 with `toPlainString`.
- `normalizedHeldAt`: request `heldAt` after trim, then parsed, truncated to whole seconds, and formatted as `yyyyMMddHHmmss`.
- D-9 does not return `heldAt`; the normalized whole-second value is used only for deterministic batch identity.

`activityBatchId` and duplicate-batch detection are intentionally metadata-exact. The service does not collapse visually similar titles that differ by internal whitespace, control characters, zero-width characters, case, or Unicode normalization form. Such inputs are distinct normalized metadata and therefore distinct activity batches in Minimal D-9.

The generated `activityBatchId` format is `^ACTIVITY-[0-9]{8}-[0-9]{14}-[0-9A-F]{12}$`. Its length is 45 characters, which fits the documented `final_component_score.source_ref_id VARCHAR(64)` constraint.

The service may perform an optional duplicate-batch fast-path check before acquiring the batch lock. The authoritative duplicate-batch check must run after acquiring the batch lock and before any row mutation. Both checks use the same predicate: any existing `final_component_score` joined to the same `academicYear` with `category_code = 'SPORTS'`, `item_code = normalizedItemCode`, `source_type = 'IMPORT'`, and `source_ref_id = activityBatchId`. If either check finds a match, the whole request fails with `409 / BIZ-4090` and message `同一活动批次已导入`.

Because this phase does not introduce an import batch table, duplicate-batch detection is backed by existing final-component rows. If a previous import had zero successful rows, there is no persisted batch marker and a retry is accepted. This zero-success retry exception is intentional: the system treats an upload with no persisted activity components as not imported.

If a previous same-batch import partially succeeded, the persisted successful components make the whole deterministic `activityBatchId` imported. Minimal D-9 therefore rejects a retry with the same normalized metadata as duplicate and does not support same-batch "fill only missing students" semantics. Operators must use the immediate `failedRows` receipt to correct the source data and either import a distinct administrative correction batch with distinct normalized metadata or wait for a later dedicated batch-management feature. This avoids silent duplicate components while D-9 has no import batch table or idempotent per-student batch state.

### Evaluation Item Validation

D-9 reads `evaluation_item` by `item_code`.

Valid item:

- `status = 'ACTIVE'`;
- `category_code = 'SPORTS'`;
- `cap_rule_json` is available to the application layer for score cap validation.

Invalid item:

- no row;
- inactive row;
- row exists but belongs to another category.

All invalid cases return `404 / RES-4040` with message `对应项目定义不存在`. This keeps the public contract stable and avoids leaking inactive or wrong-category metadata distinctions.

D-9 does not mutate `evaluation_item`. It does not parse option lists or create new project definitions.

Score cap rule:

- Minimal D-9 parses only the existing E seed shape: a JSON object with numeric `maxPoints` and boolean `allowOverflow`.
- `cap_rule_json` is valid only when it is non-null, parses as a JSON object, contains both fields, has `maxPoints` as a non-negative JSON number not above `99999999.99`, and has `allowOverflow` as a JSON boolean.
- If `cap_rule_json` is null, malformed JSON, a non-object value, missing either field, or has a field with the wrong type or invalid numeric bound, the item definition is treated as unavailable and the request fails with `404 / RES-4040` and message `对应项目定义不存在`. D-9 must not default to "no cap" or silently allow overflow for malformed metadata.
- The database-safe upper bound `99999999.99` and two-decimal scale are always enforced before item-specific caps.
- When `allowOverflow = false`, request `scoreValue` must be `<= maxPoints`; otherwise the request fails with `400 / VAL-4001` and message `scoreValue 必须在 0 到项目允许范围之间`.
- When `allowOverflow = true`, request `scoreValue` may exceed `maxPoints` but must still satisfy the database-safe upper bound and scale.
- D-9 does not evaluate other `cap_rule_json` variants such as option-level scoring in this phase. If later D/E integration requires richer cap semantics, that should be a separate spec update with frozen messages and tests.

### Concurrency and Transaction Boundaries

D-9 must protect the batch and each final record with concrete database-level serialization.

Batch-level concurrency:

- Serialize imports for the same `activityBatchId` before row mutation.
- Use an application-level lock behind an `ActivityImportBatchLock` port, mirroring D-8's `LectureImportBatchLock`.
- Production wiring derives one final MySQL named-lock string as `D9_ACTIVITY:` + `activityBatchId`. For example, `ACTIVITY-20252026-20260518143000-ABCDEF123456` maps to `D9_ACTIVITY:ACTIVITY-20252026-20260518143000-ABCDEF123456`.
- Production wiring uses parameterized `GET_LOCK(?, 30)` and `RELEASE_LOCK(?)` on the request transaction owner connection, passing that same final lock string to both calls. It must not pass the raw `activityBatchId` to one call and the prefixed lock string to the other.
- H2 tests use an explicit keyed JVM lock fake or mock.
- The lock must be released on every exit path, including duplicate-batch `409`, zero-row success, row-processing success, rollback, and unexpected persistence failures.
- If the lock cannot be acquired, return `409 / BIZ-4090` with `同一活动批次正在导入，请稍后重试`. For MySQL, `GET_LOCK` returning `0` after the 30-second wait is the same external in-flight conflict. `GET_LOCK` returning `NULL` or throwing an unexpected SQL error is a storage-unavailable path and must surface as the request-level database access failure defined above.
- If the lock is acquired but persisted components already exist, return `409 / BIZ-4090` with `同一活动批次已导入`.

Per-student final-record concurrency:

- Resolve all valid participant rows before mutation.
- Sort mutation candidates by `student_user_id ASC`, then `rowNo ASC`.
- For each row, lock the target `(student_user_id, academic_year)` final record with `SELECT ... FOR UPDATE`.
- If no final record exists, insert a `DRAFT` record with zero totals and reload it for update.
- If a concurrent insert creates the same `(student_user_id, academic_year)`, catch duplicate-key errors by SQLState `23000` or MySQL error code `1062`, then reload the row for update.
- If the locked final record is not `DRAFT`, return row-level `FINAL_RECORD_LOCKED` and do not mutate that student's row. Minimal D-9 only has three supported final-record states, so this includes `SUBMITTED` and `CONFIRMED`; any future non-`DRAFT` state must also fail closed until a later spec explicitly allows it.
- Successful rows insert components and recalculate totals while the record remains locked.

Transaction and lock ordering:

- The request runs in one transaction for metadata duplicate checks and row mutations.
- D-9 follows D-8's existing service shape: validation and workbook parsing happen before the transaction; the application service enters `TransactionOperations.execute(...)`; inside that transaction it acquires the batch lock, registers release through transaction synchronization when synchronization is active, performs the authoritative duplicate-batch check, then mutates rows. The MySQL named lock is connection-bound rather than transaction-bound, so the implementation must release it after transaction completion on the same owner connection, or in `finally` if no transaction synchronization is active.
- Field-level and lookup/scope row failures do not roll back successful rows.
- Unexpected persistence errors roll back all inserted components in that request.
- The repository method used directly in integration tests may remain `@Transactional` if needed for rollback coverage; do not remove transactional boundaries without replacing that coverage.

## Authorization

Request-level authority:

- The controller uses `@PreAuthorize`.
- The application service also checks `UserAuthorizationContext.hasAuthority(SCORE_IMPORT)` as a defensive guard. If absent, it throws `AccessDeniedAppException("当前用户无导入权限")`, which must still be exposed through the existing `403 / AUTH-4030` handler surface; tests should assert code/status rather than treating the service message as a second public HTTP contract.

Row-level scope:

- D-9 uses the same current-membership target lookup and `score.import` scope matching semantics as D-8.
- Minimal D-9 reads current primary active membership only; it does not implement historical organization membership as of `heldAt`.
- `ORG_UNIT` and `ORG_SUBTREE` matching must use real organization paths, not ids substituted into path predicates.
- Unsupported or empty scope grants no rows, not all rows.

## Persistence Details

For each successful row, insert into `final_component_score`:

| Column | Value |
|---|---|
| `final_record_id` | Locked or newly created DRAFT final record id. |
| `category_code` | `evaluation_item.category_code`, already validated as `SPORTS`. |
| `item_code` | `canonicalItemCode`, the resolved `evaluation_item.item_code`. |
| `score_value` | Normalized request `scoreValue` scaled to 2 decimals. |
| `display_text` | Row `displayText` after trim, or normalized request `title` when blank. |
| `source_type` | `IMPORT`. |
| `source_ref_id` | `activityBatchId`. |
| `created_at` | Request processing timestamp. |

After each successful insert, recalculate final-record totals with the same category mapping used by D-7/D-8:

- `MORAL` -> `moral_total`;
- `INTELLECTUAL` -> `intellectual_total`;
- `SPORTS` -> `physical_total`;
- `LABOR` -> `labor_total`.

Then update `grand_total`, `updated_at`, and increment `version` if the record is still `DRAFT`.

All reads, locks, inserts, duplicate checks, and created DRAFT `final_record.academic_year` values use the normalized request `academicYear`. D-9 never derives `final_record.academic_year` from `heldAt`.

Duplicate-batch detection does not reject different activity batches for the same student and same `itemCode`. Only the same deterministic `activityBatchId` is considered a duplicate import.

## Expected File Map

Create:

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportRow.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportFailedRow.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportResult.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportActivitiesCommand.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportApplicationService.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportBatchLock.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportParser.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportRepository.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportStudentTarget.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportedComponent.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportItemDefinition.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelActivityImportParser.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/MySqlActivityImportBatchLock.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ActivityImportMapper.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisActivityImportRepository.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportStudentTargetRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportedComponentRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportItemDefinitionRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityScoreCategoryTotalRow.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/ActivityImportFailedRowResponse.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/ActivityImportResultResponse.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportParserTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportApplicationServiceTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisActivityImportRepositoryTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisActivityImportRepositoryIntegrationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportBatchLockTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportApplicationContextSmokeTest.java`

Modify:

- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java`
- `docs/team-delivery/group-d-score-finalization-import-export.md` only to update D-9 status after implementation is complete.

Do not modify:

- D-7 mentor import semantics.
- D-8 lecture import public contracts.
- E-owned `evaluation_item` DDL or seed data unless a review proves a missing seed blocks D-9 verification.
- B-9 student candidate-query contracts.

## Testing Plan

Parser tests:

- accepts `.xlsx` and `.xls` content through `WorkbookFactory`;
- rejects unreadable bytes with `导入模板错误：文件不可解析`;
- rejects missing sheet/header/header mismatch;
- ignores extra columns;
- ignores blank data rows;
- counts non-blank rows and preserves Excel row numbers;
- rejects more than 5000 non-blank rows;
- preserves raw trimmed `studentNo` and `displayText`.

Application service tests:

- missing/blank/too-long request parameters map to frozen `ValidationException` messages;
- `scoreValue` and `heldAt` are trimmed before strict parsing;
- date-only `heldAt` and date-hour values without minutes fail with `heldAt 格式非法`;
- invalid `scoreValue` variants map to request-level failures;
- active SPORTS `evaluation_item` is required;
- item `cap_rule_json.maxPoints` is enforced when `allowOverflow = false`;
- null, malformed, missing-field, wrong-type, or out-of-bound `cap_rule_json` returns `ResourceNotFoundException` with `对应项目定义不存在`;
- inactive, missing, or non-SPORTS item definitions return `ResourceNotFoundException` with `对应项目定义不存在`;
- deterministic `activityBatchId` uses normalized metadata and score scale;
- deterministic `activityBatchId`, duplicate-batch detection, persistence, and response all use the canonical item code resolved from `evaluation_item.item_code`;
- visually similar but byte-different titles are distinct metadata and generate distinct `activityBatchId` values;
- duplicate existing activity batch returns `ConflictException("同一活动批次已导入")`;
- partially successful same-batch retries are rejected as duplicate when any component from that batch was persisted;
- zero-success same-batch retries are accepted because no component marks the batch as imported;
- same-batch in-flight lock conflict returns `ConflictException("同一活动批次正在导入，请稍后重试")`;
- MySQL `GET_LOCK` timeout maps to the same in-flight lock conflict, while `NULL` or SQL errors surface through the existing database access failure response;
- production lock tests assert the exact same final lock string is passed to both `GET_LOCK` and `RELEASE_LOCK`;
- transaction orchestration tests or focused service tests cover acquiring the batch lock inside the request transaction before the authoritative duplicate check and releasing it after transaction completion or in the non-synchronized fallback;
- lock release happens after success, request-level duplicate after lock, row-level failures, and exceptions;
- field validation ordering matches the frozen failure table;
- duplicate students are detected only after field validation;
- row target lookup, scope checks, and locked final records produce row-level failures;
- successful rows use request-level `scoreValue`, request `itemCode`, and default display text from title;
- separate activity batches for the same student and item accumulate components.

Repository tests:

- active student target lookup requires active user, active primary membership, and active org;
- active users without active primary membership, and active users whose primary org is inactive or missing, return row-level `STUDENT_NOT_FOUND`;
- active SPORTS item lookup filters inactive and wrong-category rows;
- active SPORTS item lookup treats invalid `cap_rule_json` as an unavailable definition;
- duplicate-batch check filters by academic year, category, item, source type, and source ref;
- duplicate-batch and final-record mutation use the normalized request `academicYear`, not a value derived from `heldAt`;
- missing final record creates DRAFT record and inserts component;
- existing DRAFT record inserts component and increments totals/version;
- any non-DRAFT final record, including SUBMITTED and CONFIRMED, produces `FINAL_RECORD_LOCKED`;
- two different activity batches for the same student and item create two components;
- activity import accumulates with existing D-7 mentor and D-8 lecture components without overwriting them;
- MySQL duplicate final-record insert errors identified by `1062` / `23000` reload the locked draft;
- unexpected totals update failure rolls back all inserted components in direct repository integration tests.

Controller/MVC tests:

- route is exactly `/api/admin/imports/cas-activities`;
- method consumes multipart;
- required params missing return `400 / VAL-4001`, not Spring binding `500`;
- oversized files return `文体活动导入文件最多支持 5000 行且不超过 5MB` before `getBytes()`;
- unsupported filename extensions return `导入模板错误：文件不可解析` before service invocation;
- service response maps all frozen D-9 fields and failed-row raw values;
- service validation/conflict/file-processing/database-access exceptions map through the frozen request-level error surface and existing handlers;
- security annotation requires `score.import`.

Regression/smoke tests:

- D-8 lecture import tests still pass after any shared helper extraction.
- D-7 mentor import tests still pass.
- Activity Spring context smoke verifies controller/service/parser/repository/mapper wiring with a test/mocked batch lock instead of MySQL `GET_LOCK`.

Focused verification command after implementation:

```bash
mvn -pl whut-eval-app -am -Dtest=ActivityImportParserTest,ActivityImportApplicationServiceTest,MybatisActivityImportRepositoryTest,MybatisActivityImportRepositoryIntegrationTest,ActivityImportBatchLockTest,ActivityImportApplicationContextSmokeTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest,LectureImportParserTest,LectureImportApplicationServiceTest,MybatisLectureImportRepositoryTest,MybatisLectureImportRepositoryIntegrationTest,LectureImportBatchLockTest,LectureImportApplicationContextSmokeTest test -Dsurefire.failIfNoSpecifiedTests=false
```

## Acceptance Criteria

- `POST /api/admin/imports/cas-activities` exists and requires `score.import`.
- Missing multipart params return frozen `VAL-4001` messages instead of framework binding errors.
- Unsupported workbook filename extensions are rejected before reading bytes.
- Activity metadata is validated before row mutation.
- Only active SPORTS item definitions are importable.
- `scoreValue` respects item `maxPoints` when `allowOverflow = false`.
- Valid rows insert `SPORTS/<itemCode>` imported components with `source_ref_id = activityBatchId`.
- The request-level `scoreValue` is used for every successful row.
- Separate activity batches accumulate; the same deterministic batch is rejected only when it already has persisted components. Zero-success batches have no persisted marker and remain retryable.
- Row-level failures do not roll back other valid rows.
- Unexpected persistence failures roll back the request.
- D-7 upsert and D-8 insert-only semantics remain unchanged.
- D-9 does not introduce D-10, D-11, B-9 candidate-source tables, async jobs, or import history tables.
