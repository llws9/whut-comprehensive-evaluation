# D-8 Lecture Import Design

## Goal

Implement D-8: `POST /api/admin/imports/lectures`.

The endpoint synchronously imports lecture attendance score rows from an Excel file into draft final records. It keeps the D-group "synchronous import + immediate receipt" model, returns row-level failures in `failedRows`, and does not introduce async jobs, import history pages, exports, CAS activity import, or the D-11 unsubmitted roster.

## Existing Contracts

Source contract:

- `docs/team-delivery/group-d-score-finalization-import-export.md` freezes the D-8 route, permission, request parameters, response fields, and error-code surface.
- D-8 uses `score.import`, which is already present after D-7.
- D-8 must not introduce an import/export batch table in this phase. The documented data dependencies remain `final_record` and `final_component_score`.
- `final_record` has one row per `(student_user_id, academic_year)`.
- `final_component_score` stores imported lecture components as final-score details.

Current implementation patterns:

- The repository is a multi-module Maven project. D-8 should keep the current module boundaries: domain records in `whut-eval-domain`, application services in `whut-eval-application`, POI/MyBatis code in `whut-eval-infra`, HTTP DTO/controller code in `whut-eval-interfaces`, and tests in `whut-eval-app`.
- `AdminScoreImportController` already owns `/api/admin/imports`.
- `MentorScoreImportApplicationService` proves the D-side import pattern: method-level authority, service-level `UserAuthorizationContext`, row-level scope checks, active primary membership lookup, DRAFT-only final-record mutation, total recalculation, and `failedRows`.
- `ExcelMentorScoreImportParser` uses Apache POI with `WorkbookFactory` and `DataFormatter`.
- Application services receive bytes and primitive request fields, not `MultipartFile`.
- Errors should use the existing `ValidationException`, `ConflictException`, `AccessDeniedAppException`, and `FileStorageException` mappings.

## Scope

In scope:

- Add `POST /api/admin/imports/lectures`.
- Parse lecture attendance score Excel files synchronously.
- Validate `file`, `title`, `heldAt`, and `academicYear`.
- Return `lectureBatchId`, `title`, `heldAt`, `academicYear`, `totalCount`, `successCount`, `failedCount`, and `failedRows`.
- Write successful rows into `DRAFT` final records and `source_type = 'IMPORT'` final components.
- Enforce `score.import` authority and active `score.import` organization scope.
- Keep valid row imports when other rows fail row-level validation.
- Reject duplicate lecture batches with `409 / BIZ-4090`.
- Add focused parser, application, repository, MVC, security, and regression tests.

Out of scope:

- D-7 behavior changes except small shared helpers if needed.
- D-9 CAS activity import.
- D-10 final-score export.
- D-11 unsubmitted roster.
- Student lecture candidate query implementation.
- Frontend upload UI.
- Import batch tables, downloadable failure files, object-storage persistence, or async job status APIs.

## HTTP Contract

Endpoint:

`POST /api/admin/imports/lectures`

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
| `file` | yes | Non-empty Excel file. |
| `title` | yes | Non-blank after trim, max 255 characters. Leading and trailing whitespace are removed; internal whitespace, control characters, zero-width characters, case, and Unicode normalization form are otherwise preserved. |
| `heldAt` | yes | ISO local date-time parsed with `LocalDateTime.parse(heldAt)`, which uses `DateTimeFormatter.ISO_LOCAL_DATE_TIME`. Accepted examples include `2026-05-18T14:30`, `2026-05-18T14:30:00`, and `2026-05-18T14:30:00.123`; omitted seconds default to `00`, fractional seconds are truncated, and timezone offsets are not accepted. The value is normalized to whole seconds in the response and batch id. |
| `academicYear` | yes | Trimmed before validation; must match `^\d{4}-\d{4}$`, and the second year must equal first year + 1. |

Successful response:

```json
{
  "success": true,
  "data": {
    "lectureBatchId": "LECTURE-20252026-20260518143000-8F3A2B1C4D6E",
    "title": "学院学术讲座",
    "heldAt": "2026-05-18T14:30:00",
    "academicYear": "2025-2026",
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
          "scoreValue": "0.50",
          "displayText": "签到成功"
        }
      }
    ]
  }
}
```

Successful responses return the same normalized metadata used for `lectureBatchId`: `title` is trimmed, `academicYear` is trimmed, and `heldAt` is truncated to whole seconds.

`failedRows` are returned in ascending `rowNo` order. `rowNo` is the Excel worksheet's 1-based physical row number; the header is row 1 and the first data row is row 2. This keeps the response stable even if successful row mutations are executed in a different lock order internally.

`failedRows` fields are frozen as:

`rowNo`, `code`, `message`, `rawValue`

`rawValue` is frozen as an object containing exactly `studentNo`, `scoreValue`, and `displayText`. Each value is the trimmed `DataFormatter` string read from the workbook before row-level normalization; blank strings are returned as `null`.

Response count semantics:

- `totalCount` is the number of non-blank data rows after the header.
- `successCount` is the number of rows that successfully inserted a lecture component.
- `failedCount` is `failedRows.size()`.
- `successCount + failedCount = totalCount`.
- A workbook that contains only the header returns `200` with `totalCount = 0`, `successCount = 0`, `failedCount = 0`, and `failedRows = []`; it persists no batch marker, so retry is allowed.

Request-level failures:

| Scenario | HTTP | Code | Message rule |
|---|---:|---|---|
| Missing or empty file | `400` | `VAL-4001` | `上传文件不能为空` |
| Missing or blank title | `400` | `VAL-4001` | `title 不能为空` |
| Title too long | `400` | `VAL-4001` | `title 长度不能超过 255` |
| Missing or invalid `heldAt` | `400` | `VAL-4001` | `heldAt 格式非法` |
| Missing or invalid `academicYear` | `400` | `VAL-4001` | `academicYear 不合法` |
| File too large or too many data rows | `400` | `VAL-4001` | `讲座导入文件最多支持 5000 行且不超过 5MB` |
| Missing required header, header mismatch, no sheet, unreadable workbook | `400` | `VAL-4001` | Starts with `导入模板错误：` |
| Missing authority | `403` | `AUTH-4030` | Existing security handler response. |
| Same lecture batch currently running | `409` | `BIZ-4090` | `同一讲座批次正在导入，请稍后重试` |
| Same lecture batch already imported | `409` | `BIZ-4090` | `同一讲座批次已导入` |
| Multipart bytes cannot be read | `503` | `EXT-5033` | `文件处理失败，请稍后重试` |

Row-level validation failures return HTTP 200 and appear in `failedRows`.

Frozen row-level failure mapping:

| Condition | `code` | `message` |
|---|---|---|
| `studentNo` blank | `STUDENT_NO_REQUIRED` | `studentNo 不能为空` |
| `scoreValue` blank | `SCORE_VALUE_REQUIRED` | `scoreValue 不能为空` |
| `scoreValue` does not match strict decimal text | `SCORE_VALUE_INVALID` | `scoreValue 必须是数字` |
| `scoreValue > 99999999.99` | `SCORE_VALUE_OUT_OF_RANGE` | `scoreValue 必须在 0 到 99999999.99 之间` |
| `scoreValue` has more than 2 decimal places | `SCORE_VALUE_SCALE_INVALID` | `scoreValue 最多保留 2 位小数` |
| `displayText` longer than 1000 characters after trim | `DISPLAY_TEXT_TOO_LONG` | `displayText 长度不能超过 1000` |
| duplicate field-valid `studentNo` in the same workbook | `DUPLICATE_STUDENT` | `同一讲座批次中学生重复` |
| eligible student not found | `STUDENT_NOT_FOUND` | `studentNo 对应学生不存在或未启用` |
| row target outside `score.import` scope | `OUT_OF_SCOPE` | `当前用户无权导入该学生讲座成绩` |
| existing final record is `SUBMITTED` or `CONFIRMED` | `FINAL_RECORD_LOCKED` | `已提交或已确认的最终成绩不允许导入覆盖` |

When more than one row-level condition applies, use the first matching condition in the table above. The strict decimal-text validation excludes negative numbers, so `-1` fails with `SCORE_VALUE_INVALID`; `SCORE_VALUE_OUT_OF_RANGE` only handles numeric text above the upper bound. Duplicate-student detection runs only after the row passes `studentNo`, `scoreValue`, and `displayText` field validation, so field-invalid rows do not consume the duplicate key. A field-valid row consumes its normalized `studentNo` duplicate key even if it later fails student lookup, scope, or final-record lock checks.

## Excel Template

Only the first sheet is parsed.

Header row is row 1. Header names are case-sensitive and must appear in this exact order:

| Column | Header | Required | Rule |
|---:|---|---:|---|
| A | `studentNo` | yes | Existing active `iam_user.user_no`. |
| B | `scoreValue` | yes | Strict decimal text matching `^[0-9]+(\.[0-9]+)?$`, `0 <= value <= 99999999.99`, at most 2 decimal places. Thousand separators, percentages, currency symbols, and scientific notation are invalid. The upper bound follows `DECIMAL(10,2)`. |
| C | `displayText` | no | Max 1000 characters after trim. When blank, the normalized row display text is the normalized request title followed by ` 讲座签到`. |

Extra columns after column C are ignored.

Blank data rows are ignored and do not count toward `totalCount`. A blank data row is a row where columns A, B, and C are all missing or trim to blank after `DataFormatter` conversion.

`totalCount` counts non-blank data rows after the header, including rows that later fail validation.

Cell values are read with `DataFormatter`, trimmed, and blank strings become null. The parser must not evaluate formulas beyond POI's formatted value behavior.

The 5 MB limit applies to the uploaded file bytes before POI parsing. The parser rejects oversized byte arrays before opening the workbook, and rejects workbooks with more than 5000 non-blank data rows using `ValidationException("讲座导入文件最多支持 5000 行且不超过 5MB")`.

Header error messages:

- Missing header row: `导入模板错误：缺少表头`.
- Header mismatch in the first three columns: `导入模板错误：第{columnIndex}列表头应为 {expectedHeader}`.
- Workbook with no sheets: `导入模板错误：缺少工作表`.
- Unreadable workbook: `导入模板错误：文件不可解析`.

## Import Semantics

### Lecture Component Identity

D-8 always writes lecture scores as:

- `category_code = 'INTELLECTUAL'`;
- `item_code = 'INTELLECTUAL_LECTURE'`;
- `source_type = 'IMPORT'`;
- `source_ref_id = lectureBatchId`.

This is intentionally different from D-7. D-7 upserts one imported component per `(student, academicYear, categoryCode, itemCode)`. D-8 must allow multiple lecture batches in the same academic year, so it must not reuse the D-7 category/item-only overwrite key. A student may have several `INTELLECTUAL_LECTURE` components when they attended several distinct lectures.

### Lecture Batch Id

`lectureBatchId` is deterministic from normalized request metadata:

`LECTURE-<academicYearWithoutDash>-<heldAt yyyyMMddHHmmss>-<uppercase 12-char hash>`

The hash input is:

`normalizedAcademicYear + "|" + normalizedHeldAt + "|" + normalizedTitle`

Use SHA-256 over the UTF-8 hash input, uppercase the hexadecimal digest, and take the first 12 characters.

Normalization is fixed as:

- `normalizedAcademicYear`: request `academicYear` after trim.
- `normalizedTitle`: request `title` after trim; no internal whitespace collapsing, case folding, or Unicode normalization is applied.
- `normalizedHeldAt`: parsed `heldAt` truncated to whole seconds and formatted as `yyyyMMddHHmmss`.
- response `heldAt`: the same whole-second value formatted as ISO local date-time, for example `2026-05-18T14:30:00`.

The generated `lectureBatchId` format is `^LECTURE-[0-9]{8}-[0-9]{14}-[0-9A-F]{12}$`. Its length is 44 characters, which fits the documented `final_component_score.source_ref_id VARCHAR(64)` constraint.

Before any row mutation, the service checks whether any existing `final_component_score` joined to the same `academicYear` has `category_code = 'INTELLECTUAL'`, `item_code = 'INTELLECTUAL_LECTURE'`, `source_type = 'IMPORT'`, and `source_ref_id = lectureBatchId`. If yes, the whole request fails with `409 / BIZ-4090` and message `同一讲座批次已导入`.

Because this phase does not introduce an import batch table, duplicate-batch detection is backed by the existing final-component rows. If a previous import had zero successful rows, there is no persisted batch marker and a retry is accepted.

This zero-success retry behavior is intentional for D-8: an upload that produced no persisted lecture component is not considered imported. Callers may retry the same metadata and workbook after fixing row data or authorization.

### Concurrency and Transaction Boundaries

D-8 must protect the batch and each final record with concrete database-level serialization, not only a pre-check in application memory.

Batch-level concurrency:

- The implementation must serialize imports for the same `lectureBatchId` before row mutation.
- The preferred implementation is an application-level lock backed by the database, such as `SELECT GET_LOCK(CONCAT('D8_LECTURE:', ?), 30)` on MySQL and an H2-test equivalent lock abstraction.
- The lock must be exposed behind a narrow `LectureImportBatchLock` port with a minimal contract equivalent to `boolean tryAcquire(String lectureBatchId, Duration timeout)` plus `void release(String lectureBatchId)` on the same owner. Production wiring must use the MySQL connection-bound named-lock implementation. H2 tests must wire an explicit test implementation, such as a keyed JVM `ReentrantLock`, so service-level same-batch serialization is testable even though H2 has no `GET_LOCK`; that test implementation must not be used in production profiles.
- The batch lock must be acquired and released on the same database connection that owns the request transaction, so the lock lifetime covers the whole import transaction.
- An implementation may use an equivalent durable claim or unique-key strategy, but it must not add a general import batch table in this D-8 scope.
- If the lock cannot be acquired because the same batch is already running, the request fails with `409 / BIZ-4090` and message `同一讲座批次正在导入，请稍后重试`. This is an in-flight conflict, not a persisted imported marker; callers may retry after the running request finishes.
- If the lock is acquired but the duplicate-batch check finds persisted components for the same `lectureBatchId`, the request fails with `409 / BIZ-4090` and message `同一讲座批次已导入`.
- While holding the batch lock, the service performs the existing-component duplicate check again inside the transaction before any row mutation.

Per-student final-record concurrency:

- Every successful row mutation must lock the target `final_record` with `SELECT ... FOR UPDATE`.
- If no record exists, insert the DRAFT record and then re-read it with `SELECT ... FOR UPDATE`; concurrent insert races must catch the unique-key conflict on `(student_user_id, academic_year)` and re-read the existing row with `SELECT ... FOR UPDATE`, matching D-7.
- Component insert and total recalculation must occur while the target record is locked.
- The totals update must include `WHERE id = ? AND status = 'DRAFT'`.
- If the conditional totals update affects zero rows, record `FINAL_RECORD_LOCKED` for that row, do not count it as a success, and leave no inserted lecture component for that row. The implementation may satisfy this by confirming `status = 'DRAFT'` before insert under the row lock and treating any later zero-row update as an unexpected persistence failure, or by using a row-level savepoint/delete cleanup before collecting the failure.
- Under this model, a concurrent submit or confirm cannot commit between `SELECT ... FOR UPDATE` and the totals update for the same `final_record`; tests should verify the lock and conditional update behavior, not an impossible mid-lock state transition.
- To reduce cross-batch deadlocks, field-valid, non-duplicate, eligible candidate rows that need final-record mutation must be locked and mutated in a deterministic order by target `student_user_id` ascending, then `rowNo` ascending. Duplicate-student detection and response counts still use workbook row order, and the final `failedRows` response is sorted by `rowNo`.

Transaction scope:

- The whole import runs in one request transaction after request-level validation and parsing pass.
- Duplicate-batch locking, duplicate-batch pre-check, student eligibility lookup, active primary organization resolution, `score.import` scope matching, component inserts, and total updates all occur inside that one transaction and on the same database connection when the chosen lock implementation is connection-bound.
- Expected row-level business failures, including missing student, out-of-scope target, locked final record, and duplicate student, are collected in `failedRows` and must not be thrown as transaction-rolling exceptions.
- Unexpected infrastructure or persistence failures roll back the whole request transaction. In that case the HTTP response is a request-level failure and no partial successful rows are committed.
- `final_record.version` is a monotonic change counter for final-record consumers. D-8 increments it on every successful row mutation. It does not participate in D-8 concurrency control; D-8 relies on `SELECT ... FOR UPDATE` plus the conditional `status = 'DRAFT'` update to protect against concurrent submit/confirm transitions.

### Student Eligibility

A row can import only when:

- `iam_user.user_no = studentNo`;
- `iam_user.status = 'ACTIVE'`;
- the user has an `org_membership` row with `is_primary = 1` and `status = 'ACTIVE'`;
- that primary membership points to an `org_unit` with `status = 'ACTIVE'`.

D-8 imposes no `org_unit.unit_type` filter. Any active primary organization is eligible for lookup; import authorization is then decided by the `score.import` scope rules against that active primary organization. If dirty data contains more than one active primary membership for the same user, D-8 deterministically selects the row with the smallest `org_membership.id` after joining to active `org_unit`; it does not make authorization depend on undefined SQL row order.

The implementation must not depend on a nonexistent `iam_user.identity` column.

If no eligible student is found, the row fails with:

- `code = "STUDENT_NOT_FOUND"`;
- `message = "studentNo 对应学生不存在或未启用"`.

### Authorization Scope

The method-level guard checks `score.import` authority.

The application service must also build the current `UserAuthorizationContext` and apply active `score.import` scope rules to each row's active primary organization.

Scope semantics:

- Multiple active `score.import` scope rules are evaluated with union semantics. A row is allowed when any supported active scope rule matches. Unsupported scope types are ignored for D-8 and must not override a matching supported allow rule. If there is no supported match, the row fails with `OUT_OF_SCOPE`.
- `ALL` allows all eligible students.
- `ORG_UNIT` allows only the exact active primary org unit.
- `ORG_SUBTREE` resolves the root `org_unit.path` and matches the student's active primary org path by real path prefix.
- `SELF`, `CATEGORY`, `ITEM`, `ORG_UNIT_ITEM`, `CUSTOM_EXPRESSION`, or unsupported empty scope sets do not grant D-8 import access.

`ORG_SUBTREE` must use the real organization code path format, such as `/WHUT/CS/CS2022/CS2201`; it must not match numeric ids with expressions like `LIKE '%/2002/%'`.

### Final Record Mutation

For each successful row:

1. Locate `final_record` by `(student_user_id, academic_year)`.
2. If no row exists, create a `DRAFT` final record with zero totals, `version = 0`, and then insert the lecture component. The successful component insertion and total update increments `version` to `1`.
3. If an existing row is `DRAFT`, insert the lecture component for this lecture batch.
4. If an existing row is `SUBMITTED` or `CONFIRMED`, the row fails with:
   - `code = "FINAL_RECORD_LOCKED"`;
   - `message = "已提交或已确认的最终成绩不允许导入覆盖"`.
5. Recalculate all four totals and `grand_total` from all components currently attached to that draft final record.
6. Update `final_record.updated_at` and increment `version` for every successful mutation.

D-8 never submits or confirms a final record.

New `final_record` rows must populate these fields:

| Field | Value |
|---|---|
| `id` | Database-generated id. |
| `student_user_id` | Eligible target `iam_user.id`. |
| `academic_year` | Normalized request academic year. |
| `status` | `DRAFT`. |
| `moral_total`, `intellectual_total`, `physical_total`, `labor_total`, `grand_total` | `0.00` before component insertion; recalculated after the successful component insert. |
| `submitted_at`, `confirmed_at`, `confirm_comment` | `NULL`. |
| `version` | `0` at insert time; the successful component insertion and totals update increments it to `1`. |
| `created_at`, `updated_at` | The request transaction timestamp. `updated_at` is refreshed on every successful mutation. |

### Component Mutation

D-8 is insert-only for successful rows:

- Insert one `final_component_score` per successful student row.
- Use the normalized row display text from the Excel Template section.
- Use the request-derived `lectureBatchId` as `sourceRefId`.
- Preserve existing imported and application components.
- Do not update existing D-7 mentor/fixed-score components.
- Do not update prior lecture-batch components.

New `final_component_score` rows must populate these fields:

| Field | Value |
|---|---|
| `id` | Database-generated id. |
| `final_record_id` | Locked target `final_record.id`. |
| `category_code` | `INTELLECTUAL`. |
| `item_code` | `INTELLECTUAL_LECTURE`. |
| `score_value` | Parsed `scoreValue` persisted at scale 2. |
| `display_text` | Normalized row display text. |
| `source_type` | `IMPORT`. |
| `source_ref_id` | Request-derived `lectureBatchId`. |
| `created_at` | The request transaction timestamp. |

Duplicate non-blank `studentNo` values inside the same workbook are deterministic row-level failures:

- the first field-valid occurrence consumes the duplicate key and remains eligible for later student lookup, scope, and final-record lock checks;
- later duplicate rows fail with `DUPLICATE_STUDENT`;
- duplicates that are blank or already field-invalid are handled by the earlier field failure rule and do not consume the duplicate key.

Row-level failures do not roll back earlier successful mutations in the same request. Request-level failures abort before mutation.

### Totals

Lecture import contributes only to `intellectual_total` because the frozen component identity is `INTELLECTUAL / INTELLECTUAL_LECTURE`.

Totals must still be recalculated from all current components:

| `categoryCode` | Total field |
|---|---|
| `MORAL` | `moral_total` |
| `INTELLECTUAL` | `intellectual_total` |
| `SPORTS` | `physical_total` |
| `LABOR` | `labor_total` |

`grand_total = moral_total + intellectual_total + physical_total + labor_total`.

All persisted totals use scale 2. Totals are calculated with exact `BigDecimal` addition from current component values and persisted with scale 2 using `RoundingMode.HALF_UP` if scaling is needed.

## Architecture

Add a focused D-8 import slice beside the existing D-7 import slice.

Suggested package layout:

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing`
  - `LectureImportRow`
  - `LectureImportFailedRow`
  - `LectureImportResult`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing`
  - `ImportLecturesCommand`
  - `LectureImportApplicationService`
  - `LectureImportParser`
  - `LectureImportRepository`
  - `LectureImportedComponent`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing`
  - `ExcelLectureImportParser`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository`
  - `MybatisLectureImportRepository`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper`
  - `LectureImportMapper`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin`
  - extend `AdminScoreImportController`
  - response DTOs for lecture import result and failed rows.

The D-8 repository may reuse row DTO shapes from D-7 only when names remain accurate. It should not call the D-7 `upsertDraftComponent(...)` method because that method uses the D-7 overwrite key.

If helper extraction is useful, keep it narrow and behavior-preserving:

- academic-year validation;
- final-record totals calculation;
- active org subtree path matching;
- student target lookup row mapping.

Do not introduce broad generic import abstractions until D-9 proves a second genuinely shared shape.

## Testing Requirements

### Parser Tests

Add tests proving:

- valid workbook rows parse with correct `rowNo`;
- blank rows are skipped and not counted;
- extra columns after `displayText` are ignored;
- byte arrays larger than 5 MB fail before POI opens the workbook with the frozen size message;
- workbooks over 5000 non-blank data rows fail with the frozen size message;
- missing header fails with `ValidationException`;
- mismatched header fails with the expected column message;
- workbooks with no sheets fail with `导入模板错误：缺少工作表`;
- formatted values that are not strict decimal text, such as `-1`, `1,234.56`, `50%`, or `5E-1`, fail with `SCORE_VALUE_INVALID`;
- numeric text above `99999999.99` fails with `SCORE_VALUE_OUT_OF_RANGE`;
- numeric text with more than 2 decimal places fails with `SCORE_VALUE_SCALE_INVALID`;
- unreadable bytes fail with `导入模板错误：文件不可解析`.

### Application Tests

Add tests proving:

- invalid `academicYear` fails with `academicYear 不合法`;
- `academicYear` accepts only the concrete `^\d{4}-\d{4}$` format with the second year equal to first year + 1;
- blank or overlong `title` fails with the frozen message;
- invalid `heldAt` fails with `heldAt 格式非法`;
- `heldAt` accepts omitted seconds, truncates fractional seconds, and rejects timezone offsets;
- deterministic `lectureBatchId` is returned for the same normalized title, heldAt, and academicYear;
- title trimming and whole-second heldAt normalization feed both `lectureBatchId` and response `heldAt`;
- successful responses return normalized `title` and normalized `academicYear`;
- `lectureBatchId` uses the 12-character SHA-256 prefix and fits `source_ref_id VARCHAR(64)`;
- a duplicate existing `lectureBatchId` throws `ConflictException`;
- a same-batch in-flight lock conflict throws `ConflictException` with `同一讲座批次正在导入，请稍后重试`;
- a zero-success import leaves no persisted batch marker and allows a later retry;
- a header-only workbook returns a result with all counts zero and no batch marker;
- a valid row inserts a new draft record and lecture component;
- a new final record starts at `version = 0` and reaches `version = 1` after the successful row mutation;
- a row with blank `displayText` stores the normalized request title followed by ` 讲座签到`;
- two distinct lecture batches for the same student accumulate two components and totals;
- duplicate `studentNo` inside the same workbook produces a `DUPLICATE_STUDENT` failed row;
- `failedRows.rowNo` uses Excel 1-based physical row numbers;
- a field-valid row that later fails student lookup or scope still consumes the duplicate-student key;
- a row for a missing or inactive student appears in `failedRows`;
- a row outside `score.import` scope appears in `failedRows`;
- multiple active `score.import` scopes use union semantics and unsupported scopes do not override a supported match;
- `SUBMITTED` and `CONFIRMED` targets appear in `failedRows` and are not mutated;
- successful rows update totals and increment version;
- mixed valid and invalid rows return a result with accurate counts;
- mixed failure scenarios return `failedRows` in ascending `rowNo` order;
- expected row-level business failures are collected without rolling back other successful rows in the same request transaction;
- an unexpected persistence failure rolls back the whole request transaction and commits no partial successful rows;
- same-batch concurrent imports result in one successful import and one `ConflictException`;
- final-record row locking prevents a concurrent submit/confirm transition from committing between row lock acquisition and totals update;
- cross-batch imports that touch the same students acquire final-record locks in deterministic student id order.

### Repository Integration Tests

Use H2 MySQL mode and local test schema to verify:

- active student lookup does not require `iam_user.identity`;
- active primary membership and active `org_unit` are required, without filtering by `org_unit.unit_type`;
- multiple active primary memberships are resolved by the smallest `org_membership.id`;
- inactive users and inactive memberships are excluded;
- `ORG_SUBTREE` scope uses real path-prefix matching and rejects similar prefixes;
- inserted draft records satisfy all non-null `final_record` columns;
- inserted lecture components satisfy all non-null `final_component_score` columns;
- generated `lectureBatchId` fits `final_component_score.source_ref_id VARCHAR(64)`;
- two lecture batches for the same student create two components instead of overwriting;
- duplicate-batch existence checks join through `final_record.academic_year`;
- duplicate-batch locking or the explicit H2 test lock adapter prevents two same-batch request transactions from both inserting rows;
- totals are recalculated from all current components and persisted at scale 2.

### MVC and Security Tests

Add tests proving:

- route is exactly `/api/admin/imports/lectures`;
- controller consumes multipart requests;
- empty file returns `400 VAL-4001`;
- missing title returns `400 VAL-4001`;
- invalid heldAt returns `400 VAL-4001`;
- a header-only workbook returns HTTP 200 with all counts zero;
- service conflict returns `409 BIZ-4090`;
- multipart read failure returns `503 EXT-5033`;
- controller method declares `SCORE_IMPORT`.

### Regression Tests

Keep D-7 tests passing and add a regression proving D-8 does not change D-7 overwrite semantics:

- D-7 still updates one imported component for the same `(student, academicYear, categoryCode, itemCode)`;
- D-8 still inserts separate components for separate lecture batches.

## Verification Commands

Focused D-8 verification:

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportParserTest,LectureImportApplicationServiceTest,MybatisLectureImportRepositoryIntegrationTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

D-7/D-8 import regression:

```bash
mvn -pl whut-eval-app -am -Dtest=MentorScoreImportParserTest,MentorScoreImportApplicationServiceTest,MybatisMentorScoreImportRepositoryIntegrationTest,LectureImportParserTest,LectureImportApplicationServiceTest,MybatisLectureImportRepositoryIntegrationTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Full verification before merge:

```bash
git diff --check
mvn test
```

## Acceptance Checklist

- D-8 endpoint exists and returns the documented response shape.
- `score.import` protects the method and service-level row scope.
- Valid rows mutate only draft final records.
- Submitted and confirmed records are not overwritten.
- Same-batch concurrent requests cannot both insert lecture components.
- Row-level failures appear in `failedRows`.
- Request-level validation and duplicate-batch errors match the documented HTTP codes.
- Lecture import writes `INTELLECTUAL / INTELLECTUAL_LECTURE`.
- Separate lecture batches for the same student accumulate; they do not overwrite each other.
- Import scope uses real org paths and does not match numeric ids inside path strings.
- No D-9, D-10, D-11, async job, batch table, import history, or frontend behavior is introduced.
