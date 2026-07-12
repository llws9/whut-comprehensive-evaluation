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
| `title` | yes | Non-blank after trim, max 255 characters. Leading and trailing whitespace are removed; internal whitespace, case, and Unicode normalization form are preserved. |
| `heldAt` | yes | ISO local date-time accepted by `LocalDateTime.parse`; normalized to whole seconds in the response and batch id. |
| `academicYear` | yes | Trimmed before validation; must match `yyyy-yyyy`, and the second year must equal first year + 1. |

Successful response:

```json
{
  "success": true,
  "data": {
    "lectureBatchId": "LECTURE-20252026-20260518143000-8F3A2B",
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

`failedRows` fields are frozen as:

`rowNo`, `code`, `message`, `rawValue`

Request-level failures:

| Scenario | HTTP | Code | Message rule |
|---|---:|---|---|
| Missing or empty file | `400` | `VAL-4001` | `上传文件不能为空` |
| Missing or blank title | `400` | `VAL-4001` | `title 不能为空` |
| Title too long | `400` | `VAL-4001` | `title 长度不能超过 255` |
| Missing or invalid `heldAt` | `400` | `VAL-4001` | `heldAt 格式非法` |
| Missing or invalid `academicYear` | `400` | `VAL-4001` | `academicYear 不合法` |
| Missing required header, header mismatch, no sheet, unreadable workbook | `400` | `VAL-4001` | Starts with `导入模板错误：` |
| Missing authority | `403` | `AUTH-4030` | Existing security handler response. |
| Same lecture batch already imported | `409` | `BIZ-4090` | `同一讲座批次已导入` |
| Multipart bytes cannot be read | `503` | `EXT-5033` | `文件处理失败，请稍后重试` |

Row-level validation failures return HTTP 200 and appear in `failedRows`.

Frozen row-level failure mapping:

| Condition | `code` | `message` |
|---|---|---|
| `studentNo` blank | `STUDENT_NO_REQUIRED` | `studentNo 不能为空` |
| `scoreValue` blank | `SCORE_VALUE_REQUIRED` | `scoreValue 不能为空` |
| `scoreValue` is not a decimal number | `SCORE_VALUE_INVALID` | `scoreValue 必须是数字` |
| `scoreValue < 0` or `scoreValue > 99999999.99` | `SCORE_VALUE_OUT_OF_RANGE` | `scoreValue 必须在 0 到 99999999.99 之间` |
| `scoreValue` has more than 2 decimal places | `SCORE_VALUE_SCALE_INVALID` | `scoreValue 最多保留 2 位小数` |
| `displayText` longer than 1000 characters | `DISPLAY_TEXT_TOO_LONG` | `displayText 长度不能超过 1000` |
| duplicate field-valid `studentNo` in the same workbook | `DUPLICATE_STUDENT` | `同一讲座批次中学生重复` |
| eligible student not found | `STUDENT_NOT_FOUND` | `studentNo 对应学生不存在或未启用` |
| row target outside `score.import` scope | `OUT_OF_SCOPE` | `当前用户无权导入该学生讲座成绩` |
| existing final record is `SUBMITTED` or `CONFIRMED` | `FINAL_RECORD_LOCKED` | `已提交或已确认的最终成绩不允许导入覆盖` |

When more than one row-level condition applies, use the first matching condition in the table above. Duplicate-student detection runs only after the row passes `studentNo`, `scoreValue`, and `displayText` field validation, so field-invalid rows do not consume the duplicate key. A field-valid row consumes its normalized `studentNo` duplicate key even if it later fails student lookup, scope, or final-record lock checks.

## Excel Template

Only the first sheet is parsed.

Header row is row 1. Header names are case-sensitive and must appear in this exact order:

| Column | Header | Required | Rule |
|---:|---|---:|---|
| A | `studentNo` | yes | Existing active `iam_user.user_no`. |
| B | `scoreValue` | yes | Decimal, `0 <= value <= 99999999.99`, at most 2 decimal places. |
| C | `displayText` | no | Max 1000 characters after trim. Blank becomes the normalized request title followed by ` 讲座签到`. |

Blank data rows are ignored and do not count toward `totalCount`.

`totalCount` counts non-blank data rows after the header, including rows that later fail validation.

Cell values are read with `DataFormatter`, trimmed, and blank strings become null. The parser must not evaluate formulas beyond POI's formatted value behavior.

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

`LECTURE-<academicYearWithoutDash>-<heldAt yyyyMMddHHmmss>-<uppercase 6-char hash>`

The hash input is:

`normalizedAcademicYear + "|" + normalizedHeldAt + "|" + normalizedTitle`

Use SHA-256 over the UTF-8 hash input, uppercase the hexadecimal digest, and take the first 6 characters.

Normalization is fixed as:

- `normalizedAcademicYear`: request `academicYear` after trim.
- `normalizedTitle`: request `title` after trim; no internal whitespace collapsing, case folding, or Unicode normalization is applied.
- `normalizedHeldAt`: parsed `heldAt` truncated to whole seconds and formatted as `yyyyMMddHHmmss`.
- response `heldAt`: the same whole-second value formatted as ISO local date-time, for example `2026-05-18T14:30:00`.

The generated `lectureBatchId` format is `^LECTURE-[0-9]{8}-[0-9]{14}-[0-9A-F]{6}$`. Its length is 37 characters, which fits the documented `final_component_score.source_ref_id VARCHAR(64)` constraint.

Before any row mutation, the service checks whether any existing `final_component_score` joined to the same `academicYear` has `category_code = 'INTELLECTUAL'`, `item_code = 'INTELLECTUAL_LECTURE'`, `source_type = 'IMPORT'`, and `source_ref_id = lectureBatchId`. If yes, the whole request fails with `409 / BIZ-4090` and message `同一讲座批次已导入`.

Because this phase does not introduce an import batch table, duplicate-batch detection is backed by the existing final-component rows. If a previous import had zero successful rows, there is no persisted batch marker and a retry is accepted.

This zero-success retry behavior is intentional for D-8: an upload that produced no persisted lecture component is not considered imported. Callers may retry the same metadata and workbook after fixing row data or authorization.

### Concurrency and Transaction Boundaries

D-8 must protect the batch and each final record with concrete database-level serialization, not only a pre-check in application memory.

Batch-level concurrency:

- The implementation must serialize imports for the same `lectureBatchId` before row mutation.
- The preferred implementation is an application-level lock backed by the database, such as `SELECT GET_LOCK(CONCAT('D8_LECTURE:', ?), timeout)` on MySQL and an H2-test equivalent lock abstraction.
- The batch lock lifetime must cover the entire import request, including all row-level mutation transactions or savepoints.
- An implementation may use an equivalent durable claim or unique-key strategy, but it must not add a general import batch table in this D-8 scope.
- If the lock cannot be acquired because the same batch is already running or has just been persisted by another transaction, the request fails with `409 / BIZ-4090` and message `同一讲座批次已导入`.
- While holding the batch lock, the service performs the existing-component duplicate check again inside the transaction before any row mutation.

Per-student final-record concurrency:

- Every successful row mutation must lock the target `final_record` with `SELECT ... FOR UPDATE`.
- If no record exists, insert the DRAFT record and then re-read it with `SELECT ... FOR UPDATE`; concurrent insert races must be handled the same way D-7 handles the `(student_user_id, academic_year)` unique key.
- Component insert and total recalculation must occur while the target record is locked.
- The totals update must include `WHERE id = ? AND status = 'DRAFT'`.
- If the conditional totals update affects zero rows, treat that row as `FINAL_RECORD_LOCKED` when the transaction can continue; if the database operation has already failed the transaction, roll back the current row transaction and surface `FINAL_RECORD_LOCKED` for that row through the service layer.

Transaction scope:

- Request-level validation, parsing, duplicate-batch locking, and duplicate-batch pre-check happen before row mutations.
- Each row mutation runs in its own transaction or savepoint-equivalent unit so row-level failures do not roll back earlier successful rows.
- Unexpected infrastructure failures during a row mutation roll back that row's transaction and propagate as a request-level exception; already committed earlier row transactions remain committed.
- `final_record.version` is an optimistic-change counter for final-record consumers. D-8 increments it on every successful row mutation. D-8 does not require the caller to provide an expected version, but the conditional `status = 'DRAFT'` update protects against concurrent submit/confirm transitions.

### Student Eligibility

A row can import only when:

- `iam_user.user_no = studentNo`;
- `iam_user.status = 'ACTIVE'`;
- the user has an active primary `org_membership`;
- the primary membership points to an active class-like `org_unit`.

The implementation must not depend on a nonexistent `iam_user.identity` column.

If no eligible student is found, the row fails with:

- `code = "STUDENT_NOT_FOUND"`;
- `message = "studentNo 对应学生不存在或未启用"`.

### Authorization Scope

The method-level guard checks `score.import` authority.

The application service must also build the current `UserAuthorizationContext` and apply active `score.import` scope rules to each row's active primary organization.

Scope semantics:

- `ALL` allows all eligible students.
- `ORG_UNIT` allows only the exact active primary org unit.
- `ORG_SUBTREE` resolves the root `org_unit.path` and matches the student's active primary org path by real path prefix.
- `SELF`, `CATEGORY`, `ITEM`, `ORG_UNIT_ITEM`, `CUSTOM_EXPRESSION`, or unsupported empty scope sets do not grant D-8 import access.

`ORG_SUBTREE` must use the real organization code path format, such as `/WHUT/CS/CS2022/CS2201`; it must not match numeric ids with expressions like `LIKE '%/2002/%'`.

### Final Record Mutation

For each successful row:

1. Locate `final_record` by `(student_user_id, academic_year)`.
2. If no row exists, create a `DRAFT` final record with zero totals and then insert the lecture component.
3. If an existing row is `DRAFT`, insert the lecture component for this lecture batch.
4. If an existing row is `SUBMITTED` or `CONFIRMED`, the row fails with:
   - `code = "FINAL_RECORD_LOCKED"`;
   - `message = "已提交或已确认的最终成绩不允许导入覆盖"`.
5. Recalculate all four totals and `grand_total` from all components currently attached to that draft final record.
6. Update `final_record.updated_at` and increment `version` for every successful mutation.

D-8 never submits or confirms a final record.

### Component Mutation

D-8 is insert-only for successful rows:

- Insert one `final_component_score` per successful student row.
- Use `displayText` when present; otherwise default to the normalized request title followed by ` 讲座签到`.
- Use the request-derived `lectureBatchId` as `sourceRefId`.
- Preserve existing imported and application components.
- Do not update existing D-7 mentor/fixed-score components.
- Do not update prior lecture-batch components.

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

All persisted totals use scale 2.

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
- missing header fails with `ValidationException`;
- mismatched header fails with the expected column message;
- unreadable bytes fail with `导入模板错误：文件不可解析`.

### Application Tests

Add tests proving:

- invalid `academicYear` fails with `academicYear 不合法`;
- blank or overlong `title` fails with the frozen message;
- invalid `heldAt` fails with `heldAt 格式非法`;
- deterministic `lectureBatchId` is returned for the same normalized title, heldAt, and academicYear;
- title trimming and whole-second heldAt normalization feed both `lectureBatchId` and response `heldAt`;
- a duplicate existing `lectureBatchId` throws `ConflictException`;
- a zero-success import leaves no persisted batch marker and allows a later retry;
- a valid row inserts a new draft record and lecture component;
- two distinct lecture batches for the same student accumulate two components and totals;
- duplicate `studentNo` inside the same workbook produces a `DUPLICATE_STUDENT` failed row;
- a field-valid row that later fails student lookup or scope still consumes the duplicate-student key;
- a row for a missing or inactive student appears in `failedRows`;
- a row outside `score.import` scope appears in `failedRows`;
- `SUBMITTED` and `CONFIRMED` targets appear in `failedRows` and are not mutated;
- successful rows update totals and increment version;
- mixed valid and invalid rows return HTTP 200 with accurate counts;
- row-level business failures do not roll back previously committed successful row mutations;
- same-batch concurrent imports result in one successful import and one `ConflictException` mapped to `409 / BIZ-4090`;
- a concurrent submit/confirm transition between target lookup and totals update is reported as `FINAL_RECORD_LOCKED` and does not mutate that row.

### Repository Integration Tests

Use H2 MySQL mode and local test schema to verify:

- active student lookup does not require `iam_user.identity`;
- active primary membership is required;
- inactive users and inactive memberships are excluded;
- `ORG_SUBTREE` scope uses real path-prefix matching and rejects similar prefixes;
- inserted draft records satisfy all non-null `final_record` columns;
- inserted lecture components satisfy all non-null `final_component_score` columns;
- generated `lectureBatchId` fits `final_component_score.source_ref_id VARCHAR(64)`;
- two lecture batches for the same student create two components instead of overwriting;
- duplicate-batch existence checks join through `final_record.academic_year`;
- duplicate-batch locking or equivalent serialization prevents two same-batch transactions from both inserting rows;
- totals are recalculated from all current components.

### MVC and Security Tests

Add tests proving:

- route is exactly `/api/admin/imports/lectures`;
- controller consumes multipart requests;
- empty file returns `400 VAL-4001`;
- missing title returns `400 VAL-4001`;
- invalid heldAt returns `400 VAL-4001`;
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
