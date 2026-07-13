# D-10 Final Score Export Design

## Goal

Implement D-10: `GET /api/admin/exports/final-scores`.

The endpoint synchronously exports authorized final-score records to an Excel workbook. It reuses the final-record query and authorization model already used by D-5, but evaluates `score.export.assigned` rather than `score.view.assigned`. It does not introduce export job tables, async status APIs, object-storage persistence, import behavior changes, or the D-11 unsubmitted roster.

## Existing Contracts

Source contract:

- `docs/team-delivery/group-d-score-finalization-import-export.md` freezes the D-10 route, permission, query parameters, file response headers, and request-level error surface.
- `docs/team-delivery/database-schema-confirmation.md` states that D group phase one uses synchronous import/export and does not create `import_job`, `import_error_item`, or `export_job`.
- `docs/team-delivery/group-a-identity-user-admin.sql` already defines `score.export.assigned` as permission `5011` and binds it to counselor, college reviewer, and platform admin roles.
- The current A seed does not define `iam_scope_rule` rows for `score.export.assigned`; D-10 must add D-owned safe-init scope rows so seeded counselor and college reviewer accounts can export the same organization subtree they can view, and so the seeded platform admin can execute its existing platform-wide export authority.

Current implementation facts:

- `AdminFinalRecordController` owns `/api/admin/final-records` for D-5, D-6, and D-12. D-10's route is under `/api/admin/exports`, so it should use a separate export controller instead of overloading the final-record controller's base mapping.
- `FinalRecordQueryApplicationService.pageAdminFinalRecords(...)` evaluates `score.view.assigned` and returns paged JSON rows. D-10 needs a sibling export path that evaluates `score.export.assigned` and returns an unpaged, deterministic list for workbook generation.
- `FinalRecordQueryRepository` and `MybatisPlusFinalRecordQueryRepository` already centralize final-record scope SQL through `FinalRecordScopePredicateBuilder` and `ApplicationScopeSqlTranslator`.
- `FinalRecordQuerySqlProvider` already joins `final_record`, `iam_user`, primary active `org_membership`, and `org_unit`, and hides `DRAFT` records from admin list/detail queries.
- Apache POI `poi-ooxml` is already available in `whut-eval-infra` for D-7, D-8, and D-9 Excel parsing.

## Scope

In scope:

- Add `GET /api/admin/exports/final-scores`.
- Accept `academicYear`, optional `status`, optional `grade`, and optional `classes` query parameters. `status` is limited to `SUBMITTED` or `CONFIRMED`; `DRAFT` is never exportable.
- Evaluate `score.export.assigned` authority and data scopes.
- Export only `SUBMITTED` and `CONFIRMED` final records.
- Apply grade/class filters against the student's current primary organization path, using existing `org_unit` rows.
- Generate an `.xlsx` workbook synchronously and return it as the HTTP response body.
- Return `404 / RES-4040` when the caller is authorized but no records match.
- Add D safe-init rows for `score.export.assigned` scope rules for seeded counselor, college reviewer, and platform admin roles.
- Add focused domain/query, repository, workbook writer, MVC, security, and seed-consistency tests.

Out of scope:

- D-7, D-8, or D-9 import behavior changes.
- D-11 unsubmitted roster.
- Export job tables, export history pages, object-storage persistence, downloadable async artifacts, or progress polling.
- Component-level export. D-10 exports final-record totals only.
- Student profile tables or per-year organization snapshots. D-10 uses the same current primary organization binding as D-5.
- Frontend download UI.

## Approach Decision

Recommended approach: add a D-10-specific export slice that reuses the existing final-record query authorization path and introduces a narrow workbook writer port.

Trade-offs:

- Reusing `pageAdminFinalRecords(...)` directly would avoid a repository method, but pagination is part of the JSON list contract and would silently truncate exports unless the controller invented a huge page size. D-10 needs an explicit unpaged export query.
- Adding a generic export framework would be premature. There is only one export endpoint in this phase, and future async/export-history behavior is explicitly out of scope.
- A separate export service and controller keeps HTTP file response concerns out of the existing JSON final-record controller while still sharing repository scope logic.

## HTTP Contract

Endpoint:

`GET /api/admin/exports/final-scores`

Permission:

`score.export.assigned`

Controller annotation:

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_EXPORT_ASSIGNED)")
```

Query parameters:

| Parameter | Required | Rule |
|---|---:|---|
| `academicYear` | yes | Trimmed, non-blank, must match `yyyy-yyyy`, and second year must equal first year + 1. |
| `status` | no | Trimmed, blank becomes absent. If absent, exports both `SUBMITTED` and `CONFIRMED`. If present, it is case-sensitive and must exactly equal uppercase `SUBMITTED` or `CONFIRMED`. Lowercase, mixed-case, and `DRAFT` values are invalid and never exportable. |
| `grade` | no | Trimmed, blank becomes absent. Matches the current primary class's parent GRADE by exact, case-sensitive `org_unit.unit_code` or `org_unit.unit_name`. Unknown values are not request errors; they produce no matching rows and therefore `404`. |
| `classes` | no | May be sent as repeated query params (`classes=CS2201&classes=CS2202`) or comma-separated tokens (`classes=CS2201,CS2202`). Normalize by processing the received `classes` value list, splitting each by comma, trimming, dropping blanks, and de-duplicating by first appearance in the flattened trimmed token sequence. If all tokens are blank, `classes` is equivalent to absent and does not force `404`. The normalized token count must be at most `500`; more tokens fail with `400 / VAL-4001` and message `classes 参数过多`. Non-blank tokens match current primary CLASS by exact, case-sensitive `org_unit.unit_code` or `org_unit.unit_name`. Unknown values are not request errors; they produce no matching rows and therefore `404` if nothing else matches. |

`pageNo` and `pageSize` are not accepted for D-10 because exports are unpaged. To keep the frozen delivery document's "pagination parameter error" branch observable, any request containing `pageNo` or `pageSize` fails with `400 / VAL-4001` rather than silently ignoring those parameters.

Raw HTTP request-shape validation happens before `FinalScoreExportQuery` construction. The controller first checks `pageNo`/`pageSize` presence; if either key is present, including blank or repeated values, the pagination-parameter branch wins and the message is `导出接口不支持分页参数`. After that, `academicYear`, `status`, and `grade` must not appear more than once. Repeated non-pagination single-value parameters fail with `400 / VAL-4001` and message `导出接口不支持重复单值参数`. `classes` is the only repeatable D-10 query parameter.
Unknown query parameters other than `pageNo` and `pageSize` are ignored, matching the current MVC binding style; D-10 does not introduce a strict unknown-parameter rejection branch.

Successful response:

- HTTP status: `200`
- `Content-Type`: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition`: `attachment; filename="final-scores-{academicYear}.xlsx"`, where `{academicYear}` is the normalized `FinalScoreExportQuery.academicYear` value after trimming.
- Body: `.xlsx` workbook bytes

Failure responses:

| Scenario | HTTP | Code | Message rule |
|---|---:|---|---|
| Missing or invalid `academicYear` | `400` | `VAL-4001` | `academicYear 不合法` |
| Invalid `status` | `400` | `VAL-4001` | `status 仅允许 SUBMITTED 或 CONFIRMED` |
| `pageNo` or `pageSize` is present | `400` | `VAL-4001` | `导出接口不支持分页参数` |
| Repeated `academicYear`, `status`, or `grade` | `400` | `VAL-4001` | `导出接口不支持重复单值参数` |
| More than `500` normalized `classes` tokens | `400` | `VAL-4001` | `classes 参数过多` |
| Authenticated caller lacks `score.export.assigned` authority | `403` | `AUTH-4030` | Existing security error response. |
| Caller has authority but no matching authorized records | `404` | `RES-4040` | `无匹配导出数据` |
| Workbook generation fails or synchronous row cap is exceeded | `503` | `EXT-5033` | `Excel 生成失败` |

Failure response bodies use the existing global exception handler's standard JSON shape. The table freezes only the D-10-relevant HTTP status, public code, and public message.

Unauthenticated requests are out of D-10's public response contract and follow the existing Spring Security entry-point behavior. D-10 tests only need to prove the endpoint is protected; they must not force a new unauthenticated 401/403 contract unless the global security layer already freezes one.

No-data semantics:

- D-10 returns `404 / RES-4040` when all filters and scopes are applied and the resulting export row list is empty.
- D-10 does not return an empty workbook.
- No-data failures return the existing standard JSON error body from the global exception handler, not an empty `.xlsx` body.
- If an authorized caller has only unsupported scope rules for `score.export.assigned`, the repository returns no rows and the service returns `404 / RES-4040`, matching the D-5 unsupported-scope empty-result behavior.
- If an authorized caller has no active scope rules for `score.export.assigned`, the behavior is the same empty-result authorization path: the repository returns no rows and the service returns `404 / RES-4040`.

## Authorization and Scope

The application service must:

1. Load the current `UserAuthorizationContext` through `UserAuthorizationContextAssembler.requiredAuthorizationContext()`.
2. Require `AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED`.
3. Build `FinalRecordAccessContext` with permission code `score.export.assigned`.
4. Ask `FinalRecordQueryRepository` for export rows using that access context.

The repository must:

- Reuse `DefaultAuthorizationScopeEvaluator`, `FinalRecordScopePredicateBuilder`, and `ApplicationScopeSqlTranslator`.
- Apply the translated scope predicate against final-record student and current primary organization fields just as D-5 does.
- Preserve the real A-group `org_unit.path` format, for example `/WHUT/CS/CS2022/CS2201`; do not introduce numeric-id path matching.
- Filter `fr.academic_year = query.academicYear`.
- Export only `fr.status IN ('SUBMITTED', 'CONFIRMED')` when `status` is absent. If `status` is present, additionally filter to that single status. `DRAFT` must not be exported in either branch.
- Use the student's active primary membership only: `org_membership.status = 'ACTIVE' AND is_primary = 1`.
- Use `LEFT OUTER JOIN` from `final_record` to `org_membership`, `class_ou`, and `grade_ou`, and place `ACTIVE`, `is_primary`, `unit_type`, and org status predicates inside the join conditions rather than in the `WHERE` clause.
- The join path is fixed: `LEFT OUTER JOIN org_membership om ON om.user_id = fr.student_user_id AND om.status = 'ACTIVE' AND om.is_primary = 1`; `LEFT OUTER JOIN org_unit class_ou ON class_ou.id = om.org_unit_id AND class_ou.unit_type = 'CLASS' AND class_ou.status = 'ACTIVE'`; `LEFT OUTER JOIN org_unit grade_ou ON grade_ou.id = class_ou.parent_id AND grade_ou.unit_type = 'GRADE' AND grade_ou.status = 'ACTIVE'`.
- Preserve final records that have no derived active CLASS organization unit only for callers whose `score.export.assigned` scope resolves to `ALL`, and only when no grade/class filter is present. This includes students with no active primary membership and students whose active primary membership is not attached to an active `unit_type = 'CLASS'` org unit. Organization-scoped callers (`ORG_UNIT` or `ORG_SUBTREE`) must not see no-derived-class rows because the translated scope predicate compares `org_path`/`org_unit_id` against `class_ou.path`/`class_ou.id`, which are `NULL` for those rows and therefore do not match.
- Apply grade/class predicates only when the corresponding filter is present. These filter predicates must be appended to the `WHERE` clause, not to the `LEFT OUTER JOIN ... ON` conditions, so rows with missing derived grade/class fields are excluded when a grade or class filter exists.
- When `grade` is present, append `(grade_ou.unit_code = #{query.grade} OR grade_ou.unit_name = #{query.grade})` in `WHERE`, using the repository's case-sensitive comparison helper for both MySQL and H2 so collation differences cannot make lowercase or mixed-case values match.
- When normalized `classes` is non-empty, append `(class_ou.unit_code IN (...) OR class_ou.unit_name IN (...))` in `WHERE` using safe MyBatis/provider parameters for every token and the repository's case-sensitive comparison helper for both MySQL and H2. The query object rejects more than `500` normalized class tokens before repository execution. The accepted boundary therefore expands to at most `1000` bound SQL parameters for the two `IN` predicates; repository integration tests must execute the `500`-token boundary query on the H2 MySQL-mode integration path rather than only unit-testing query normalization. If normalized `classes` is empty, append no class predicate.

D safe-init must add scope rules for default export accounts:

| Reserved id | Assignment | Permission | Scope | Org | Category | Item | Expression JSON | Priority | Status | Created at |
|---:|---:|---|---|---:|---|---|---|---:|---|---|
| `8023` | `7010` counselor | `score.export.assigned` | `ORG_SUBTREE` | `2002` | `NULL` | `NULL` | `{"scoreRole":"counselor"}` | `80` | `ACTIVE` | `CURRENT_TIMESTAMP()` |
| `8024` | `7011` college reviewer | `score.export.assigned` | `ORG_SUBTREE` | `2002` | `NULL` | `NULL` | `{"scoreRole":"college_reviewer"}` | `70` | `ACTIVE` | `CURRENT_TIMESTAMP()` |
| `8025` | `7012` platform admin | `score.export.assigned` | `ALL` | `NULL` | `NULL` | `NULL` | `{"superAdmin":true}` | `1000` | `ACTIVE` | `CURRENT_TIMESTAMP()` |

The collision guard must fail deterministically if any reserved id is already occupied by an unrelated row. The inserts must include every non-null IAM column required by the documented A schema, including `created_at`.
Deterministic failure means the SQL script must raise a database error, not only log or return a warning. Use the existing temporary guard-table pattern: seed one guard row, then insert the same guard primary key only when a reserved id is occupied by an unrelated row. Re-running the script after successful D-10 inserts must not trip the guard because the existing rows match the expected natural keys and column values. Application/database initialization must abort with a `SQLException`/duplicate-key style failure before any D-10 export scope rows are inserted when a real collision exists. The safe-init script must run all `8023`/`8024`/`8025` collision checks before executing any D-10 `iam_scope_rule` insert; guard checks and inserts must not be interleaved. If the initializer runs the script inside a database transaction, a guard failure must roll back the whole D-10 seed block, but the guard-first ordering is still required for non-transactional runners.
The guard table name and shape are fixed for D-10: `CREATE TEMPORARY TABLE IF NOT EXISTS d_seed_collision_guard (id BIGINT NOT NULL PRIMARY KEY)`, followed by `DELETE FROM d_seed_collision_guard` and `INSERT INTO d_seed_collision_guard (id) VALUES (1)`. Each collision check must use `INSERT INTO d_seed_collision_guard (id) SELECT 1 ...` so a matching collision attempts to insert the existing guard primary key and raises a duplicate-key error. The expected signature must include all frozen semantic columns: `assignment_id`, `permission_code`, `scope_type`, `org_unit_id`, `category_code`, `item_code`, `expression_json`, `priority`, and `status`; `created_at` is excluded because reruns may preserve the original timestamp. For nullable semantic columns, the guard comparison rule is fixed and portable: when the expected value is `NULL`, use `IS NULL`; when the expected value is non-null, use `= <expected value>` for scalar columns and the JSON text comparison below for `expression_json`. D-10's `category_code` and `item_code` expectations are `NULL` for all three rows; `org_unit_id` is `2002` for `8023` and `8024`, and `NULL` for `8025`; `expression_json` is non-null for all three D-10 rows. The guard SQL must be executable in both MySQL and the existing H2 MySQL-mode seed tests, so it must not depend on MySQL-only JSON extraction functions such as `JSON_EXTRACT` or `JSON_UNQUOTE`. For the three D-10 single-key JSON literals, the comparison algorithm is fixed: compute `REPLACE(REPLACE(REPLACE(REPLACE(CAST(expression_json AS CHAR), ' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), '')` and compare the result exactly with one of the compact literals `{"scoreRole":"counselor"}`, `{"scoreRole":"college_reviewer"}`, or `{"superAdmin":true}`. The D-10 safe-init inserts must use those same compact literal strings, so key order and boolean casing are deterministic across MySQL and H2. For example, `8023` must check the non-JSON columns plus `org_unit_id = 2002`, `category_code IS NULL`, `item_code IS NULL`, and the fixed normalized `expression_json` comparison against `{"scoreRole":"counselor"}`; `8025` must use `org_unit_id IS NULL`, `category_code IS NULL`, and `item_code IS NULL` plus the `{"superAdmin":true}` expression comparison.
After all guard checks pass, each D-10 `iam_scope_rule` seed row must be inserted with an idempotent conditional insert, not a plain `INSERT`. Use `INSERT INTO iam_scope_rule (...) SELECT ... WHERE NOT EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = <reserved id>)` for `8023`, `8024`, and `8025`. This ordering is fixed: run all guard checks first, then run all conditional inserts. On a clean first run the rows are inserted; on a clean rerun the guard sees matching rows and the `WHERE NOT EXISTS` insert skips them as a no-op; on a real reserved-id collision the guard fails before any conditional insert executes.
The priority values intentionally mirror existing A seed conventions; larger priority numbers sort later in the current evaluator but D-10 scope rows are additive OR clauses, so the values are for consistency and auditability rather than conflict resolution.

For `iam_scope_rule`, the safe-init insert statements must use the documented column list exactly:

`id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at`

The non-null columns that must always be populated are `id`, `assignment_id`, `permission_code`, `scope_type`, `priority`, `status`, and `created_at`. Nullable columns (`org_unit_id`, `category_code`, `item_code`, `expression_json`) must still appear in the insert list so the script remains portable across MySQL and the H2 compatibility tests.

## Grade and Class Filtering

The A-group organization tree stores class and grade as organization units:

- GRADE example: `unit_type = 'GRADE'`, `unit_code = 'CS2022'`, `unit_name = '计算机 2022 级'`, `path = '/WHUT/CS/CS2022'`.
- CLASS example: `unit_type = 'CLASS'`, `unit_code = 'CS2201'`, `unit_name = '计算机 2201 班'`, `path = '/WHUT/CS/CS2022/CS2201'`.

D-10 must not invent `student_profile.grade` or `student_profile.class_name`. It derives export fields and filters from the student's current primary organization:

- `classCode`/`className`: the primary active organization when `unit_type = 'CLASS'`.
- `gradeCode`/`gradeName`: the parent active organization when the class parent has `unit_type = 'GRADE'`.
- If a final record is visible through `ALL` scope and has no primary class membership, it remains exportable when no grade/class filter is present, with blank grade/class cells.
- Grade or class filters exclude rows whose derived grade/class fields are missing.

Filtering rules:

- `grade` matches `gradeCode` or `gradeName` exactly and case-sensitively after trimming.
- `classes` matches `classCode` or `className` exactly and case-sensitively after trimming. If the request contains only blank class tokens after splitting and trimming, the normalized class list is empty and D-10 applies no class filter.
- `grade` is a filter expression, not a lookup that resolves to one organization row. If the same request value matches one grade's `gradeCode` and another grade's `gradeName`, rows from both grades are included and final output order is still determined only by the ordering rules below.
- `classes` tokens are filter expressions, not lookups that resolve to one organization row. If the same token matches one class's `classCode` and another class's `className`, rows from both classes are included. If a token matches both `classCode` and `className` on the same class row, the SQL `OR` predicate still returns that final record once; D-10 must not duplicate workbook rows for multiple predicate hits on the same final record.
- Multiple `classes` tokens are ORed.
- `grade` and `classes` together are ANDed.
- `status` is ANDed with all organization filters and the authorization scope predicate.

## Export Row Model

Create an application-level row model for export, for example `FinalScoreExportRow`, with these fields:

| Field | Java type | Source |
|---|---|---|
| `finalRecordId` | `Long` | `final_record.id` |
| `studentUserId` | `Long` | `final_record.student_user_id` |
| `studentUserNo` | `String` | `iam_user.user_no` |
| `studentUserName` | `String` | `iam_user.user_name` |
| `gradeCode` | nullable `String` | parent GRADE `org_unit.unit_code`, nullable |
| `gradeName` | nullable `String` | parent GRADE `org_unit.unit_name`, nullable |
| `classCode` | nullable `String` | current primary CLASS `org_unit.unit_code`, nullable |
| `className` | nullable `String` | current primary CLASS `org_unit.unit_name`, nullable |
| `academicYear` | `String` | `final_record.academic_year` |
| `status` | `String` | `final_record.status` |
| `moralTotal` | `BigDecimal` | `final_record.moral_total` |
| `intellectualTotal` | `BigDecimal` | `final_record.intellectual_total` |
| `physicalTotal` | `BigDecimal` | `final_record.physical_total` |
| `laborTotal` | `BigDecimal` | `final_record.labor_total` |
| `grandTotal` | `BigDecimal` | `final_record.grand_total` |
| `submittedAt` | nullable `Instant` | `final_record.submitted_at` |
| `confirmedAt` | nullable `Instant` | `final_record.confirmed_at` |

Ordering is deterministic:

1. `gradeCode ASC NULLS LAST`
2. `classCode ASC NULLS LAST`
3. `studentUserNo ASC`
4. `finalRecordId ASC`

For H2/MySQL compatibility, express null ordering with portable SQL such as `CASE WHEN grade_ou.unit_code IS NULL THEN 1 ELSE 0 END`.
Apply that portable `NULLS LAST` pattern to both `gradeCode` and `classCode`, for example sort by `CASE WHEN grade_ou.unit_code IS NULL THEN 1 ELSE 0 END`, then `grade_ou.unit_code`, then `CASE WHEN class_ou.unit_code IS NULL THEN 1 ELSE 0 END`, then `class_ou.unit_code`, before `studentUserNo` and `finalRecordId`.

## Workbook Contract

Workbook format:

- One worksheet named `final-scores`.
- Header row at row 1.
- Freeze the first row.
- Use a consistent default font. The implementation can rely on POI defaults plus bold header styling; no formulas are required.
- Set practical fixed column widths or auto-size after writing rows so headers, class names, and timestamps are readable in common spreadsheet tools. Tests only need a minimum usability check: after generation, every column width must be greater than `8 * 256` POI width units, and timestamp/name columns must be at least `headerText.length() * 256` POI width units. Exact widths are not part of the public contract.
- No formulas are written, so formula recalculation is not part of D-10.

Columns are frozen in this exact order:

| Column | Header | Source | Cell type |
|---:|---|---|---|
| A | `最终成绩ID` | `finalRecordId` | numeric |
| B | `学年` | `academicYear` | text |
| C | `学号` | `studentUserNo` | text |
| D | `姓名` | `studentUserName` | text |
| E | `年级编码` | `gradeCode` | text, blank if null |
| F | `年级` | `gradeName` | text, blank if null |
| G | `班级编码` | `classCode` | text, blank if null |
| H | `班级` | `className` | text, blank if null |
| I | `状态` | `status` | text |
| J | `德育总分` | `moralTotal` | numeric, scale 2 display |
| K | `智育总分` | `intellectualTotal` | numeric, scale 2 display |
| L | `体育总分` | `physicalTotal` | numeric, scale 2 display |
| M | `劳育总分` | `laborTotal` | numeric, scale 2 display |
| N | `总分` | `grandTotal` | numeric, scale 2 display |
| O | `提交时间` | `submittedAt` | text ISO-8601 instant, blank if null |
| P | `确认时间` | `confirmedAt` | text ISO-8601 instant, blank if null |

Timestamp cells use UTC `Instant` text truncated to seconds, for example `2026-07-07T12:00:00Z`. If a source value has milliseconds or nanoseconds, truncate rather than round before calling `Instant.toString()`. This avoids server-timezone drift and sub-second test flakiness.

Numeric totals are written as numeric cells using `BigDecimal.setScale(2, RoundingMode.HALF_UP).doubleValue()` and a `0.00` cell style. D-owned totals are persisted as `DECIMAL(10,2) NOT NULL`, so repository rows should never contain null totals. The workbook writer must still be null-safe: if a row model contains a null total, write a blank cell rather than `0` or throwing `NullPointerException`. The exported workbook does not contain calculated formulas; persisted totals are the source of truth.

Operational capacity assumption:

- D-10 is a synchronous MVP export for one required academic year and final-record totals only. The expected deployment size is school cohort scale, not open-ended multi-year platform dumps.
- D-10 caps synchronous workbook generation at `20_000` export rows. Define a single application-layer constant on `FinalScoreExportApplicationService`, for example `public static final int MAX_SYNC_EXPORT_ROWS = 20_000`, and derive the repository probe limit as `MAX_SYNC_EXPORT_ROWS + 1`; do not scatter hard-coded `20_000`/`20_001` values across service, repository, and tests. Tests must reference the service constant or a service-owned accessor rather than duplicating the numeric values. The repository receives the derived `limit` method parameter and must not directly reference the application-layer constant. The repository must enforce the detection mechanism with a bounded export-row query that applies all filters, scope predicates, and the deterministic `ORDER BY` first, then applies `LIMIT #{limit}`. Do not use an unbounded full read followed by an in-memory size check. The service asks for up to `MAX_SYNC_EXPORT_ROWS + 1` authorized rows; if the returned list size is greater than `MAX_SYNC_EXPORT_ROWS`, the service fails before workbook allocation with `FinalScoreExportGenerationException("Excel 生成失败")`, returning the existing `503 / EXT-5033` response branch. This intentionally merges "too many rows for the synchronous MVP" and workbook-generation failures into the same frozen public response because the D delivery contract only exposes `EXT-5033 / Excel 生成失败` for export generation failure. D-10 must not introduce a distinct public error code, HTTP status, or message for row-cap overflow; the follow-up path for larger exports is an out-of-scope async/export-job design.

## Component Boundaries

New application classes:

- `FinalScoreExportQuery`: immutable value object that validates and normalizes semantic export request parameters after raw HTTP shape has been validated by the controller. Fields are `String academicYear` (required, normalized), `String status` (nullable; absent means `SUBMITTED` plus `CONFIRMED`), `String grade` (nullable), and immutable `List<String> classes` (never null; empty means absent). It does not accept `pageNo` or `pageSize`, because those are rejected before query construction.
- `FinalScoreExportRow`: row view consumed by the workbook writer.
- `FinalScoreExportFile`: immutable filename, content type, and workbook bytes. Because `byte[]` is mutable in Java, construct and expose it with defensive copies or an equivalent immutable byte container.
- `FinalScoreExportWorkbookWriter`: application port under `whut-eval-application`, with method `FinalScoreExportFile write(String academicYear, List<FinalScoreExportRow> rows)`. The returned `FinalScoreExportFile.contentType` is fixed to `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, and the returned `FinalScoreExportFile.filename` must be `final-scores-{academicYear}.xlsx` using the already-normalized `academicYear` method argument.
- `FinalScoreExportApplicationService`: application service under `whut-eval-application`, orchestrating auth, query, no-data handling, and workbook generation; its public export method is `FinalScoreExportFile export(FinalScoreExportQuery query)`.
- `FinalScoreExportGenerationException`: extends `BaseAppException`, uses `CommonErrorCode.FILE_STORAGE_FAILED`, and maps workbook write failures to `EXT-5033`.

Repository changes:

- Add `listAdminFinalScoreExportRows(FinalRecordAccessContext accessContext, FinalScoreExportQuery query, int limit)` to `FinalRecordQueryRepository`; D-10 callers pass `MAX_SYNC_EXPORT_ROWS + 1` so the service can detect more than `MAX_SYNC_EXPORT_ROWS` rows without loading an unbounded result set.
- Implement it in `MybatisPlusFinalRecordQueryRepository` using the existing scope-fragment path.
- Add mapper/provider methods that extend the current final-record admin query with `LEFT OUTER JOIN` aliases for `class_ou` and `grade_ou`, preserving no-derived-class rows unless grade/class filters are present.

Infrastructure:

- Add `PoiFinalScoreExportWorkbookWriter` in `whut-eval-infra`.
- Keep workbook generation in infra because Apache POI is an infrastructure dependency.

Interface:

- Add `AdminFinalScoreExportController` under `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin`.
- Keep MVC and controller security tests in `whut-eval-app/src/test`, matching the existing controller test pattern, because `whut-eval-app` depends on and compiles `whut-eval-interfaces`.
- Return `ResponseEntity<byte[]>` with explicit headers by calling `FinalScoreExportApplicationService.export(FinalScoreExportQuery query)`, receiving `FinalScoreExportFile`, and copying its `filename`, `contentType`, and `content` into the response.
- Inspect the raw request parameter map before constructing `FinalScoreExportQuery`. If `pageNo` or `pageSize` is present as a key, including blank or repeated values, throw `ValidationException("导出接口不支持分页参数")`; this branch has priority over repeated-parameter validation.
- Detect repeated non-pagination single-value parameters (`academicYear`, `status`, `grade`) from the raw request parameter map and convert them to `ValidationException("导出接口不支持重复单值参数")` before service execution. `classes` remains repeatable.
- Convert `classes` query params from `List<String>` to the application query; splitting and trimming live in `FinalScoreExportQuery` so MVC and service tests share behavior.

Configuration:

- Register the POI writer as a Spring bean with `@Component` in `whut-eval-infra`.
- If component scanning does not wire the infra writer in the application context smoke test, add an explicit `@Bean` in the existing D application configuration path instead of creating a new broad scan root.

## Validation

Raw HTTP request-shape validation is a controller responsibility because only MVC receives the original multi-value parameter map. `FinalScoreExportQuery` owns semantic export request validation after that shape check:

After raw request-shape validation passes, semantic validation order is fixed so public error messages are deterministic when multiple semantic errors are present:

1. Validate `academicYear`.
2. Validate `status`.
3. Normalize `grade`.
4. Normalize `classes` and validate the `500` token limit.

- `academicYear` is required, trimmed, non-blank, and must match `^\d{4}-\d{4}$`.
- The academic-year end must equal start + 1.
- `status` is optional. Trim first; blank becomes `null`/absent. If non-blank, matching is exact and case-sensitive: only uppercase `SUBMITTED` or `CONFIRMED` is valid. Lowercase or mixed-case variants fail with `ValidationException("status 仅允许 SUBMITTED 或 CONFIRMED")`.
- `grade` is optional. Trim first; blank becomes `null`. D-10 applies no `grade` format, length, or dictionary validation; any non-blank value is passed to the repository filter and unknown values produce `404` only after query execution returns no rows.
- `classes` is optional. Normalize by iterating the received repeated-parameter value list, splitting each raw value by comma, trimming every token, dropping blanks, and de-duplicating by first appearance in the flattened trimmed token sequence. For example, `classes=A,B&classes=B,C` normalizes to `[A, B, C]` when the MVC layer supplies values in that order. Store the result as an immutable list. An empty normalized list is equivalent to an absent `classes` parameter.
- If the request contains `classes` but every token is blank, the normalized list is empty and the request behaves exactly as if `classes` were absent. This is not a `400` and does not force a no-data `404`.
- If normalized `classes` contains more than `500` tokens, throw `ValidationException("classes 参数过多")` and map it to `400 / VAL-4001`.
- `FinalScoreExportQuery` must not define `pageNo` or `pageSize` fields. Pagination-parameter validation is complete before the query object is created.
- Repeated `academicYear`, `status`, or `grade` parameters are invalid HTTP request shape. The controller detects them before query construction and throws `ValidationException("导出接口不支持重复单值参数")`, mapping to `400 / VAL-4001`.

The controller converts HTTP parameters into `FinalScoreExportQuery` only after raw request-shape checks pass. Direct query/service tests cover semantic validation; controller/WebMvc tests cover `pageNo`/`pageSize` presence and repeated single-value request-shape errors.

## Error Mapping

Use existing exception flow where possible:

- Validation failures throw `ValidationException`.
- No rows throws `ResourceNotFoundException("无匹配导出数据")`.
- Missing authority throws `AccessDeniedAppException` or is blocked by Spring Security.
- Workbook writer failures and synchronous row-cap overflow are wrapped in `FinalScoreExportGenerationException("Excel 生成失败")`, which is a D-10-specific `BaseAppException` using `CommonErrorCode.FILE_STORAGE_FAILED`, so the HTTP code is `503`, response code is `EXT-5033`, and public message is exactly `Excel 生成失败`. This intentionally reuses the existing file-storage error code because the frozen D-10 delivery contract already names `EXT-5033` for file generation failure; D-10 must not introduce a second public error code or alternate public message for the same response branch.
- Unexpected `DataAccessException` continues to use existing global DB/system mapping.

The application service may create `FinalScoreExportGenerationException("Excel 生成失败")` only for synchronous row-cap overflow and workbook writer failures. It must not convert authorization, validation, no-data, or unexpected database failures to `EXT-5033`.

## Tests

Spec-phase acceptance tests for the implementation plan:

- Query normalization trims `academicYear`, rejects missing/invalid `academicYear`, treats blank `status` as absent, rejects lowercase/non-exact `status` values and `DRAFT`, treats blank `grade` as absent, normalizes repeated `classes=CS2201&classes=CS2202`, comma-separated `classes=CS2201,CS2202`, and mixed `classes=A,B&classes=B,C`, drops blank class tokens, de-duplicates by first appearance in the received value list, rejects more than `500` normalized class tokens with `classes 参数过多`, and treats `classes=,,` or `classes=&classes= ` as an absent class filter.
- Query normalization tests cover the fixed semantic validation priority after raw shape checks, including a request with invalid `academicYear`, invalid `status`, and too many `classes` returning `academicYear 不合法`.
- Controller/WebMvc tests verify present `pageNo/pageSize`, including `pageNo=`, `pageSize=`, and repeated `pageNo/pageSize`, return `400 / VAL-4001` with message `导出接口不支持分页参数` because the pagination-parameter branch has priority.
- Controller/WebMvc tests verify repeated non-pagination single-value parameters (`academicYear`, `status`, `grade`) return `400 / VAL-4001` with message `导出接口不支持重复单值参数`.
- Controller security annotation requires `SCORE_EXPORT_ASSIGNED`.
- `AdminFinalScoreExportControllerWebMvcTest` proves unauthenticated requests are rejected by the existing security filter chain without freezing a new D-10-specific 401/403 contract.
- `AdminFinalScoreExportControllerWebMvcTest` covers authenticated users without `SCORE_EXPORT_ASSIGNED` returning `403`, proving the new D-10 controller is protected by the export authority.
- Controller returns xlsx content type, attachment filename, and workbook bytes for a successful export.
- Controller returns `404 / RES-4040` when the service reports no matching data.
- WebMvc/global exception tests assert the frozen public error messages: `academicYear 不合法`, `status 仅允许 SUBMITTED 或 CONFIRMED`, `导出接口不支持分页参数`, `导出接口不支持重复单值参数`, `classes 参数过多`, `无匹配导出数据`, and `Excel 生成失败`.
- Workbook writer creates exactly one worksheet named `final-scores`, freezes the first row, creates the exact header row, writes numeric total cells with `RoundingMode.HALF_UP`, writes blank cells for unexpected null totals, writes blank E-H cells for null `gradeCode`/`gradeName`/`classCode`/`className` without shifting columns, writes UTC timestamp text truncated to seconds, emits no formulas, applies readable column widths or autosizing with the minimum-width check above, and returns the fixed xlsx content type.
- Application service uses `score.export.assigned`, not `score.view.assigned`, when building the access context.
- Application service returns `FinalScoreExportFile`, and controller copies its filename, content type, and bytes into the response.
- Application service wraps workbook writer failures as `FinalScoreExportGenerationException`; controller/WebMvc or global exception tests verify the public response remains `503 / EXT-5033`.
- Application service throws `ResourceNotFoundException("无匹配导出数据")` for an empty authorized row list; the global exception handler maps it to `404 / RES-4040`.
- Application service throws `ResourceNotFoundException("无匹配导出数据")` when unknown `grade` or unknown `classes` values produce an empty authorized row list; the global exception handler maps it to `404 / RES-4040`.
- Application service calls `listAdminFinalScoreExportRows(..., FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)` and rejects more than `FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS` returned export rows with `FinalScoreExportGenerationException`, mapping to `503 / EXT-5033`, before calling the workbook writer.
- Application service tests derive their row counts from `FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS` or a service-owned accessor, not numeric literals, and verify the off-by-one boundary: exactly `MAX_SYNC_EXPORT_ROWS` returned rows are passed to the workbook writer and export successfully, while `MAX_SYNC_EXPORT_ROWS + 1` returned rows fail before workbook generation.
- Application service and workbook writer preserve repository row order in the generated workbook, including rows whose `gradeCode` or `classCode` sort last because of portable `NULLS LAST` ordering.
- Repository export query applies status, grade, class, and scope filters together.
- Repository uses the explicit `org_membership` -> `class_ou` -> `grade_ou` join path above, including `class_ou.parent_id` for grade lookup.
- Repository defaults absent `status` to `SUBMITTED` plus `CONFIRMED`, and verifies `DRAFT` rows are excluded from exports even when matching academic year and scope.
- Repository keeps records with no derived active CLASS organization unit exportable to ALL scope when no grade/class filter is present, with blank grade/class fields; tests must cover both no active primary membership and active primary membership attached to a non-CLASS org unit.
- Repository excludes no-derived-class records for ORG_UNIT and ORG_SUBTREE callers because `class_ou.id`/`class_ou.path` are `NULL` and cannot satisfy organization predicates.
- Repository excludes no-derived-class records when grade or class filters are present.
- Repository covers ambiguous `grade` values that match one grade code and another grade name, proving both matching grade rows are included.
- Repository covers ambiguous `classes` tokens that match one class code and another class name, proving both matching class rows are included.
- Repository covers a token that matches both `classCode` and `className` on the same class row, proving the final record appears only once in the export rows.
- Repository executes the accepted `500` normalized `classes` token boundary in an H2 MySQL-mode integration test, proving the generated two-`IN` predicate with up to `1000` bound parameters is executable in the CI database path.
- Repository verifies portable null ordering for both `gradeCode` and `classCode`.
- Repository row-limit tests derive their boundary inputs from `FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS` or the same service-owned accessor used by application service tests; they must not hard-code `20000` or `20001`.
- Repository returns an empty list for unsupported-scope-only callers and callers with no active `score.export.assigned` scope rules.
- D safe-init consistency tests verify `score.export.assigned` scope rules `8023`, `8024`, and `8025`, deterministic guard-table collision checks, rerunnable inserts, and the complete `iam_scope_rule` column contract including required non-null `id`, `assignment_id`, `permission_code`, `scope_type`, `priority`, `status`, and `created_at`. The tests must assert the exact priority/status/expression values from the safe-init table above, prove a second clean run is a no-op success, and prove an unrelated reserved-id collision raises a SQL duplicate-key/database error instead of continuing with warnings. To prove all guards run before all inserts for non-transactional runners, add a middle-id collision case that pre-occupies `8024` with an unrelated row, executes the safe-init script, expects a duplicate-key/database error, and then verifies `8023` and `8025` were not inserted.
- Spring context smoke test verifies `FinalScoreExportApplicationService` and the POI writer are wired.

Focused implementation verification should include at least:

```bash
mvn -pl whut-eval-app,whut-eval-interfaces -am -Dtest=FinalScoreExport*Test,AdminFinalScoreExportControllerWebMvcTest,AdminFinalScoreExportControllerSecurityAnnotationTest,FinalRecordControllerSecurityAnnotationTest,MybatisPlusFinalRecordQueryRepositoryIntegrationTest,TeamDeliverySqlConsistencyTest,GroupAIdentitySqlSeedConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

The required controller tests live under `whut-eval-app/src/test` and compile the controller from `whut-eval-interfaces` through the app module dependency. The verification output must show the D-10 controller tests (`AdminFinalScoreExportControllerWebMvcTest` and `AdminFinalScoreExportControllerSecurityAnnotationTest`, or their final agreed names) were actually executed.

`FinalRecordControllerSecurityAnnotationTest` remains in the focused command because D-10 extends the shared final-record query repository path; this guards the existing D-5/D-6/D-12 controller annotations while D-10 adds export-specific security tests.

Before merge, run both command groups below. The focused D-10 command above proves the export path and export-specific tests. The D-7/D-8/D-9 command below is import-only regression coverage for the shared final-record query/import surface that D-10 may touch; it does not replace the focused D-10 export command. The canonical import regression command is from [2026-07-13-d9-cas-activity-import.md](/Users/bytedance/whut/whutXX/rewrite/whut-comprehensive-evaluation/.worktrees/d10-final-record-export/docs/superpowers/plans/2026-07-13-d9-cas-activity-import.md:1296) and is inlined here:

```bash
mvn -pl whut-eval-application -am test-compile
mvn -pl whut-eval-app -am -Dtest=ActivityImportParserTest,ActivityImportApplicationServiceTest,MybatisActivityImportRepositoryTest,MybatisActivityImportRepositoryIntegrationTest,ActivityImportBatchLockTest,ActivityImportApplicationContextSmokeTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest,LectureImportParserTest,LectureImportApplicationServiceTest,MybatisLectureImportRepositoryTest,MybatisLectureImportRepositoryIntegrationTest,LectureImportBatchLockTest,LectureImportApplicationContextSmokeTest,MentorScoreImportParserTest,MentorScoreImportApplicationServiceTest,MybatisMentorScoreImportRepositoryTest,MybatisMentorScoreImportRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

## Open Decisions Closed By This Spec

- D-10 exports totals only, not component details.
- D-10 derives grade/class from `org_unit`, not a new student profile table.
- D-10 accepts repeated and comma-separated `classes` query params.
- D-10 rejects `pageNo/pageSize` if supplied, because exports are unpaged and the frozen delivery document still reserves a pagination-parameter error branch.
- D-10 returns `404` for authorized empty result sets rather than an empty workbook.
- D-10 adds counselor, college reviewer, and platform-admin export scope rules in D safe-init.
