# D-7 Mentor Score Import Design

## Goal

Implement D-7: `POST /api/admin/imports/mentor-scores`.

The endpoint imports mentor or fixed-score rows from an Excel file into the existing final-score tables. It must not introduce import job tables, export behavior, lecture import, CAS activity import, or the D-11 unsubmitted roster.

## Existing Contracts

Source contract:

- `docs/team-delivery/group-d-score-finalization-import-export.md` freezes the route, permission, request parameters, response fields, and error-code surface for D-7.
- `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql` owns D-side final-score tables and D-side permission seed extensions.
- `final_record` has one row per `(student_user_id, academic_year)`.
- `final_component_score` stores component details for a final record.
- Minimal D recognizes only `DRAFT`, `SUBMITTED`, and `CONFIRMED` final-record statuses.

Current implementation patterns:

- Multipart controller handling follows `UserAdminController#importUsers(...)` and `FileUploadController#upload(...)`.
- Excel parsing uses Apache POI through `WorkbookFactory` and `DataFormatter`.
- Application services do not pass `MultipartFile` below the interface layer.
- Domain and application layers use `ValidationException`, `ConflictException`, `AccessDeniedAppException`, and `FileStorageException` for the documented error-code mapping.

## Scope

In scope:

- Add `POST /api/admin/imports/mentor-scores`.
- Add `AuthorizationPermissionCodes.SCORE_IMPORT = "score.import"`.
- Seed `score.import` in the D safe-init SQL with deterministic collision checks and `created_at` values.
- Parse `.xlsx` or `.xls` content synchronously.
- Return `importBatchId`, `totalCount`, `successCount`, `failedCount`, `failedRows`, and `processedAt`.
- Write successful rows to `final_record` and `final_component_score`.
- Enforce `score.import` authority and row-level organization scope.
- Keep valid row imports even when other rows fail validation.
- Add focused unit, MVC, parser, repository, SQL seed, and security tests.

Out of scope:

- D-8 lecture import.
- D-9 CAS activity import.
- D-10 Excel export.
- D-11 unsubmitted roster.
- Frontend upload UI.
- Import history tables, async jobs, object-storage persistence, or downloadable failure files.
- Student final-record submission or confirmation state-machine changes.

## HTTP Contract

Endpoint:

`POST /api/admin/imports/mentor-scores`

Consumes:

`multipart/form-data`

Permission:

`score.import`

Controller annotation:

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)")
```

Request parameters:

| Parameter | Required | Default | Rule |
|---|---:|---|---|
| `file` | yes | - | Non-empty Excel file. |
| `academicYear` | yes | - | Must match `yyyy-yyyy`, and the second year must equal first year + 1. Invalid message: `academicYear 不合法`. |
| `importMode` | no | `UPSERT` | Only `UPSERT` or `STRICT_INSERT`. Invalid message: `importMode 仅允许 UPSERT 或 STRICT_INSERT`. |

Successful response:

```json
{
  "success": true,
  "data": {
    "importBatchId": "D7-20260713T083015Z-8F3A2B",
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
          "categoryCode": "MORAL",
          "itemCode": "MORAL_HONOR",
          "scoreValue": "1.00",
          "displayText": "导师评分",
          "sourceRefId": "mentor-001"
        }
      }
    ],
    "processedAt": "2026-07-13T08:30:15.123Z"
  }
}
```

`failedRows` fields are frozen as:

`rowNo`, `code`, `message`, `rawValue`

Request-level failures:

| Scenario | HTTP | Code | Message rule |
|---|---:|---|---|
| Missing or empty file | `400` | `VAL-4001` | `上传文件不能为空` |
| Missing or invalid academic year | `400` | `VAL-4001` | `academicYear 不合法` |
| Invalid import mode | `400` | `VAL-4001` | `importMode 仅允许 UPSERT 或 STRICT_INSERT` |
| Missing required header, header mismatch, no sheet, unreadable workbook | `400` | `VAL-4001` | Starts with `导入模板错误：` |
| Missing authority | `403` | `AUTH-4030` | Existing security handler response. |
| `STRICT_INSERT` duplicate target exists | `409` | `BIZ-4090` | `STRICT_INSERT 模式不允许覆盖` |
| Multipart bytes cannot be read | `503` | `EXT-5033` | `文件处理失败，请稍后重试` |

Row-level validation failures are returned in `failedRows` and do not make the HTTP response fail.

## Excel Template

Only the first sheet is parsed.

Header row is row 1. Header names are case-sensitive and must appear in this exact order:

| Column | Header | Required | Rule |
|---:|---|---:|---|
| A | `studentNo` | yes | Existing active `iam_user.user_no`. |
| B | `categoryCode` | yes | One of `MORAL`, `INTELLECTUAL`, `SPORTS`, `LABOR`. |
| C | `itemCode` | yes | Non-blank, max 64 characters. |
| D | `scoreValue` | yes | Decimal, `0 <= value <= 99999999.99`, at most 2 decimal places. |
| E | `displayText` | no | Max 1000 characters after trim. Blank becomes `导师/固定成绩导入`. |
| F | `sourceRefId` | no | Max 64 characters after trim. Blank becomes `<importBatchId>:<rowNo>`. |

Blank data rows are ignored and do not count toward `totalCount`.

`totalCount` counts non-blank data rows after the header. It includes rows that later fail validation.

Cell values are read with `DataFormatter`, trimmed, and blank strings become null. The parser must not evaluate formulas beyond POI's formatted value behavior.

## Import Semantics

### Target Identity

Each row targets one imported component identified by:

- `studentNo`;
- request `academicYear`;
- `categoryCode`;
- `itemCode`;
- `sourceType = "IMPORT"`.

`sourceRefId` is provenance, not the upsert key.

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

If the student is outside the importer's `score.import` scope, the row fails with:

- `code = "OUT_OF_SCOPE"`;
- `message = "当前用户无权导入该学生成绩"`.

### Authorization Scope

The method-level guard checks `score.import` authority.

The application service must also build the current `UserAuthorizationContext` and apply active `score.import` scope rules to each row's active primary organization.

Scope semantics:

- `ALL` allows all eligible students.
- `ORG_UNIT` allows only the exact active primary org unit.
- `ORG_SUBTREE` resolves the root `org_unit.path` and matches the student's active primary org path by real path prefix.
- `SELF`, `CATEGORY`, `ITEM`, `ORG_UNIT_ITEM`, `CUSTOM_EXPRESSION`, or unsupported empty scope sets do not grant import access for D-7.

`ORG_SUBTREE` must use the real organization code path format, such as `/WHUT/CS/CS2022/CS2201`; it must not match numeric ids with expressions like `LIKE '%/2002/%'`.

### Final Record Mutation

For each successful row:

1. Locate `final_record` by `(student_user_id, academic_year)`.
2. If no row exists, create a `DRAFT` final record with zero totals and then insert the imported component.
3. If an existing row is `DRAFT`, update the imported component for the target key.
4. If an existing row is `SUBMITTED` or `CONFIRMED`, the row fails with:
   - `code = "FINAL_RECORD_LOCKED"`;
   - `message = "已提交或已确认的最终成绩不允许导入覆盖"`.
5. Recalculate all four totals and `grand_total` from all components currently attached to that draft final record.
6. Update `final_record.updated_at` and increment `version` for every successful mutation.

D-7 never submits or confirms a final record. Students still use the existing final-record submit endpoint, and admins still use the existing confirm endpoint.

### Component Mutation

For `UPSERT`:

- If an imported component already exists for the target identity, update its `score_value`, `display_text`, `source_ref_id`, and `created_at` replacement timestamp.
- If none exists, insert a new `final_component_score`.
- Non-imported components for the same `categoryCode` and `itemCode` are not overwritten.

For `STRICT_INSERT`:

- Before writing any row, pre-scan all syntactically valid row targets.
- If any target already has an imported component, abort the whole request with `409 BIZ-4090`.
- Duplicate target keys inside the same Excel file also abort the whole request with `409 BIZ-4090`.
- The error message is `STRICT_INSERT 模式不允许覆盖`.

Rows that fail normal validation do not participate in the duplicate pre-scan.

### Totals

Category-to-total mapping:

| `categoryCode` | Total field |
|---|---|
| `MORAL` | `moral_total` |
| `INTELLECTUAL` | `intellectual_total` |
| `SPORTS` | `physical_total` |
| `LABOR` | `labor_total` |

`grand_total = moral_total + intellectual_total + physical_total + labor_total`.

All persisted totals use scale 2.

## Architecture

Add a focused D-7 import slice beside the existing final-record command/query code.

Suggested package layout:

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing`
  - `MentorScoreImportRow`
  - `MentorScoreImportMode`
  - `MentorScoreImportFailedRow`
  - `MentorScoreImportResult`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing`
  - `ImportMentorScoresCommand`
  - `MentorScoreImportApplicationService`
  - `MentorScoreImportParser`
  - `MentorScoreImportRepository`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing`
  - `ExcelMentorScoreImportParser`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository`
  - `MybatisMentorScoreImportRepository`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin`
  - `AdminScoreImportController`
  - response DTOs for import result and failed rows.

The repository may reuse `FinalRecordMapper` and `FinalComponentScoreMapper`, but D-7 should add narrow mapper methods for:

- student lookup with active primary membership and active org path;
- selecting imported component targets;
- inserting a draft final record;
- upserting imported components;
- recalculating and updating final-record totals.

Do not add generic repository methods that are only needed by D-7 unless another current caller needs them.

## Seed Contract

Add D safe-init seed rows for `score.import`.

Reserved ids:

| Table | Natural key | Reserved id |
|---|---|---:|
| `iam_permission` | `permission_code = 'score.import'` | `5024` |
| `iam_role_permission` | role `4003` + `score.import` | `6050` |
| `iam_role_permission` | role `4004` + `score.import` | `6051` |
| `iam_scope_rule` | assignment `7010` + `score.import` + `ORG_SUBTREE` + org `2002` | `8021` |
| `iam_scope_rule` | assignment `7011` + `score.import` + `ORG_SUBTREE` + org `2002` | `8022` |

The safe-init script must:

- include `created_at` for every insert into IAM tables;
- fail deterministically on reserved-id collisions;
- be rerunnable when natural keys already exist;
- bind role permissions to the natural permission id when `score.import` already exists with a non-reserved id;
- not create duplicate natural-key rows.

## Testing Requirements

### Parser Tests

Add tests proving:

- valid workbook rows parse with correct `rowNo`;
- blank rows are skipped and not counted;
- missing header fails with `ValidationException`;
- mismatched header fails with the expected column message;
- unreadable bytes fail with `导入模板错误：文件不可解析`.

### Domain/Application Tests

Add tests proving:

- invalid `academicYear` fails with `academicYear 不合法`;
- invalid `importMode` fails with the frozen message;
- `UPSERT` inserts a new draft record and imported component;
- `UPSERT` updates only the existing imported component for the same target key;
- non-imported components with the same item are preserved;
- a row for a missing or inactive student appears in `failedRows`;
- a row outside `score.import` scope appears in `failedRows`;
- `SUBMITTED` and `CONFIRMED` targets appear in `failedRows` and are not mutated;
- successful rows update totals and increment version;
- mixed valid and invalid rows return HTTP 200 with accurate counts;
- `STRICT_INSERT` duplicate in the database throws `ConflictException`;
- `STRICT_INSERT` duplicate inside the workbook throws `ConflictException`;
- service method has a transactional boundary.

### Repository Integration Tests

Use H2 MySQL mode and local test schema to verify:

- active student lookup does not require `iam_user.identity`;
- active primary membership is required;
- inactive users and inactive memberships are excluded;
- `ORG_SUBTREE` scope uses real path-prefix matching and rejects similar prefixes;
- inserted draft records satisfy all non-null `final_record` columns;
- imported components satisfy all non-null `final_component_score` columns;
- totals are recalculated from all current components.

### MVC and Security Tests

Add tests proving:

- route is exactly `/api/admin/imports/mentor-scores`;
- controller consumes multipart requests;
- empty file returns `400 VAL-4001`;
- invalid mode returns `400 VAL-4001`;
- service conflict returns `409 BIZ-4090`;
- multipart read failure returns `503 EXT-5033`;
- controller method declares `SCORE_IMPORT`.

### Seed Tests

Extend `TeamDeliverySqlConsistencyTest` or add an equivalent focused test proving:

- `AuthorizationPermissionCodes.SCORE_IMPORT` equals `score.import`;
- D safe-init inserts `created_at` for `iam_permission`, `iam_role_permission`, and `iam_scope_rule`;
- safe-init is rerunnable on the documented A-group IAM schema;
- natural-key preexistence binds role permissions and scope rules to the existing `score.import` permission id;
- reserved-id collisions fail deterministically.

## Verification Commands

Focused D-7 verification:

```bash
mvn -pl whut-eval-app -am -Dtest=MentorScoreImportParserTest,MentorScoreImportApplicationServiceTest,MybatisMentorScoreImportRepositoryIntegrationTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest,TeamDeliverySqlConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Broader final-record and IAM regression:

```bash
mvn -pl whut-eval-app -am -Dtest='*FinalRecord*Test,*Iam*Test,*UserAdmin*Test,TeamDeliverySqlConsistencyTest' test -Dsurefire.failIfNoSpecifiedTests=false
```

Full verification before merge:

```bash
git diff --check
mvn test
```

## Acceptance Checklist

- D-7 endpoint exists and returns the documented response shape.
- `score.import` exists as a permission constant and safe-init seed.
- Valid rows mutate only draft final records.
- Submitted and confirmed records are not overwritten.
- Row-level failures appear in `failedRows`.
- Request-level validation and conflict errors match the documented HTTP codes.
- Import scope uses real org paths and does not match numeric ids inside path strings.
- No D-8, D-9, D-10, D-11, async job, import history, or frontend behavior is introduced.
