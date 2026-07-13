# D-10 Final Score Export Design

## Goal

Implement D-10: `GET /api/admin/exports/final-scores`.

The endpoint synchronously exports authorized final-score records to an Excel workbook. It reuses the final-record query and authorization model already used by D-5, but evaluates `score.export.assigned` rather than `score.view.assigned`. It does not introduce export job tables, async status APIs, object-storage persistence, import behavior changes, or the D-11 unsubmitted roster.

## Existing Contracts

Source contract:

- `docs/team-delivery/group-d-score-finalization-import-export.md` freezes the D-10 route, permission, query parameters, file response headers, and request-level error surface.
- `docs/team-delivery/database-schema-confirmation.md` states that D group phase one uses synchronous import/export and does not create `import_job`, `import_error_item`, or `export_job`.
- `docs/team-delivery/group-a-identity-user-admin.sql` already defines `score.export.assigned` as permission `5011` and binds it to counselor, college reviewer, and platform admin roles.
- The current A seed does not define `iam_scope_rule` rows for `score.export.assigned`; D-10 must add D-owned safe-init scope rows so seeded counselor and college reviewer accounts can export the same organization subtree they can view.

Current implementation facts:

- `AdminFinalRecordController` owns `/api/admin/final-records` for D-5, D-6, and D-12. D-10's route is under `/api/admin/exports`, so it should use a separate export controller instead of overloading the final-record controller's base mapping.
- `FinalRecordQueryApplicationService.pageAdminFinalRecords(...)` evaluates `score.view.assigned` and returns paged JSON rows. D-10 needs a sibling export path that evaluates `score.export.assigned` and returns an unpaged, deterministic list for workbook generation.
- `FinalRecordQueryRepository` and `MybatisPlusFinalRecordQueryRepository` already centralize final-record scope SQL through `FinalRecordScopePredicateBuilder` and `ApplicationScopeSqlTranslator`.
- `FinalRecordQuerySqlProvider` already joins `final_record`, `iam_user`, primary active `org_membership`, and `org_unit`, and hides `DRAFT` records from admin list/detail queries.
- Apache POI `poi-ooxml` is already available in `whut-eval-infra` for D-7, D-8, and D-9 Excel parsing.

## Scope

In scope:

- Add `GET /api/admin/exports/final-scores`.
- Accept `academicYear`, optional `status`, optional `grade`, and optional `classes` query parameters.
- Evaluate `score.export.assigned` authority and data scopes.
- Export only `SUBMITTED` and `CONFIRMED` final records.
- Apply grade/class filters against the student's current primary organization path, using existing `org_unit` rows.
- Generate an `.xlsx` workbook synchronously and return it as the HTTP response body.
- Return `404 / RES-4040` when the caller is authorized but no records match.
- Add D safe-init rows for `score.export.assigned` scope rules for seeded counselor and college reviewer roles.
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
| `status` | no | Trimmed, blank becomes absent. If present, must be `SUBMITTED` or `CONFIRMED`. |
| `grade` | no | Trimmed, blank becomes absent. Matches the current primary class's parent GRADE by exact `org_unit.unit_code` or `org_unit.unit_name`. Unknown values are not request errors; they produce no matching rows and therefore `404`. |
| `classes` | no | May be sent as repeated query params (`classes=CS2201&classes=CS2202`) or comma-separated tokens (`classes=CS2201,CS2202`). Each token is trimmed; blank tokens are ignored. Non-blank tokens match current primary CLASS by exact `org_unit.unit_code` or `org_unit.unit_name`. Unknown values are not request errors; they produce no matching rows and therefore `404` if nothing else matches. |

Successful response:

- HTTP status: `200`
- `Content-Type`: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition`: `attachment; filename="final-scores-{academicYear}.xlsx"`
- Body: `.xlsx` workbook bytes

Failure responses:

| Scenario | HTTP | Code | Message rule |
|---|---:|---|---|
| Missing or invalid `academicYear` | `400` | `VAL-4001` | `academicYear 不合法` |
| Invalid `status` | `400` | `VAL-4001` | `status 仅允许 SUBMITTED 或 CONFIRMED` |
| Caller lacks `score.export.assigned` authority | `403` | `AUTH-4030` | Existing security error response. |
| Caller has authority but no matching authorized records | `404` | `RES-4040` | `无匹配导出数据` |
| Workbook generation fails | `503` | `EXT-5033` | `Excel 生成失败` |

No-data semantics:

- D-10 returns `404 / RES-4040` when all filters and scopes are applied and the resulting export row list is empty.
- D-10 does not return an empty workbook.
- If an authorized caller has only unsupported scope rules for `score.export.assigned`, the repository returns no rows and the service returns `404 / RES-4040`, matching the D-5 unsupported-scope empty-result behavior.

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
- Export only `fr.status IN ('SUBMITTED', 'CONFIRMED')`.
- Use the student's active primary membership only: `org_membership.status = 'ACTIVE' AND is_primary = 1`.

D safe-init must add scope rules for default export accounts:

| Reserved id | Assignment | Permission | Scope | Org |
|---:|---:|---|---|---:|
| `8023` | `7010` counselor | `score.export.assigned` | `ORG_SUBTREE` | `2002` |
| `8024` | `7011` college reviewer | `score.export.assigned` | `ORG_SUBTREE` | `2002` |

The collision guard must fail deterministically if either reserved id is already occupied by an unrelated row. The inserts must include every non-null IAM column required by the documented A schema, including `created_at`.

The platform admin role already owns `score.export.assigned`, but no D-10 smoke scenario depends on platform-admin export. D-10 does not add a platform-admin ALL scope in this slice; that requires a separate approved spec with platform-admin final-score export acceptance tests.

## Grade and Class Filtering

The A-group organization tree stores class and grade as organization units:

- GRADE example: `unit_type = 'GRADE'`, `unit_code = 'CS2022'`, `unit_name = '计算机 2022 级'`, `path = '/WHUT/CS/CS2022'`.
- CLASS example: `unit_type = 'CLASS'`, `unit_code = 'CS2201'`, `unit_name = '计算机 2201 班'`, `path = '/WHUT/CS/CS2022/CS2201'`.

D-10 must not invent `student_profile.grade` or `student_profile.class_name`. It derives export fields and filters from the student's current primary organization:

- `classCode`/`className`: the primary active organization when `unit_type = 'CLASS'`.
- `gradeCode`/`gradeName`: the parent active organization when the class parent has `unit_type = 'GRADE'`.
- If a visible final record has no primary class membership, it remains exportable when no grade/class filter is present, with blank grade/class cells.
- Grade or class filters exclude rows whose derived grade/class fields are missing.

Filtering rules:

- `grade` matches `gradeCode` or `gradeName` exactly after trimming.
- `classes` matches `classCode` or `className` exactly after trimming.
- Multiple `classes` tokens are ORed.
- `grade` and `classes` together are ANDed.
- `status` is ANDed with all organization filters and the authorization scope predicate.

## Export Row Model

Create an application-level row model for export, for example `FinalScoreExportRow`, with these fields:

| Field | Source |
|---|---|
| `finalRecordId` | `final_record.id` |
| `studentUserId` | `final_record.student_user_id` |
| `studentUserNo` | `iam_user.user_no` |
| `studentUserName` | `iam_user.user_name` |
| `gradeCode` | parent GRADE `org_unit.unit_code`, nullable |
| `gradeName` | parent GRADE `org_unit.unit_name`, nullable |
| `classCode` | current primary CLASS `org_unit.unit_code`, nullable |
| `className` | current primary CLASS `org_unit.unit_name`, nullable |
| `academicYear` | `final_record.academic_year` |
| `status` | `final_record.status` |
| `moralTotal` | `final_record.moral_total` |
| `intellectualTotal` | `final_record.intellectual_total` |
| `physicalTotal` | `final_record.physical_total` |
| `laborTotal` | `final_record.labor_total` |
| `grandTotal` | `final_record.grand_total` |
| `submittedAt` | `final_record.submitted_at` |
| `confirmedAt` | `final_record.confirmed_at` |

Ordering is deterministic:

1. `gradeCode ASC NULLS LAST`
2. `classCode ASC NULLS LAST`
3. `studentUserNo ASC`
4. `finalRecordId ASC`

For H2/MySQL compatibility, express null ordering with portable SQL such as `CASE WHEN grade_ou.unit_code IS NULL THEN 1 ELSE 0 END`.

## Workbook Contract

Workbook format:

- One worksheet named `final-scores`.
- Header row at row 1.
- Freeze the first row.
- Use a consistent default font. The implementation can rely on POI defaults plus bold header styling; no formulas are required.
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

Timestamp cells use `Instant.toString()` text, for example `2026-07-07T12:00:00Z`. This avoids server-timezone drift in tests and keeps parity with existing JSON view models.

Numeric totals are written as numeric cells using `BigDecimal.doubleValue()` and a `0.00` cell style. The exported workbook does not contain calculated formulas; persisted totals are the source of truth.

## Component Boundaries

New application classes:

- `FinalScoreExportQuery`: validates and normalizes query parameters.
- `FinalScoreExportRow`: row view consumed by the workbook writer.
- `FinalScoreExportFile`: immutable filename, content type, and byte array.
- `FinalScoreExportWorkbookWriter`: application port for writing export rows to workbook bytes.
- `FinalScoreExportApplicationService`: orchestrates auth, query, no-data handling, and workbook generation.
- `FinalScoreExportGenerationException`: maps workbook write failures to `EXT-5033`.

Repository changes:

- Add `listAdminFinalScoreExportRows(FinalRecordAccessContext accessContext, FinalScoreExportQuery query)` to `FinalRecordQueryRepository`.
- Implement it in `MybatisPlusFinalRecordQueryRepository` using the existing scope-fragment path.
- Add mapper/provider methods that extend the current final-record admin query joins with `class_ou` and `grade_ou` aliases.

Infrastructure:

- Add `PoiFinalScoreExportWorkbookWriter` in `whut-eval-infra`.
- Keep workbook generation in infra because Apache POI is an infrastructure dependency.

Interface:

- Add `AdminFinalScoreExportController` under `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin`.
- Return `ResponseEntity<byte[]>` with explicit headers.
- Convert `classes` query params from `List<String>` to the application query; splitting and trimming can live in `FinalScoreExportQuery` so MVC and service tests share behavior.

Configuration:

- Register the POI writer as a Spring bean through component scanning (`@Component`) or existing application configuration if component scanning does not pick up the infra package in tests.

## Validation

`FinalScoreExportQuery` owns parameter validation:

- `academicYear` is required and must match `^\d{4}-\d{4}$`.
- The academic-year end must equal start + 1.
- `status` is optional and must be `SUBMITTED` or `CONFIRMED` after trimming.
- `grade` is optional. Blank becomes `null`.
- `classes` is optional. Each raw value may contain comma-separated tokens. Trim tokens, drop blanks, de-duplicate preserving encounter order, and store an immutable list.

`pageNo` and `pageSize` are not D-10 parameters. If clients send them, Spring leaves them unused; D-10 ignores them rather than treating them as errors.

## Error Mapping

Use existing exception flow where possible:

- Validation failures throw `ValidationException`.
- No rows throws `ResourceNotFoundException("无匹配导出数据")`.
- Missing authority throws `AccessDeniedAppException` or is blocked by Spring Security.
- Workbook writer failures are wrapped in a D-10-specific `BaseAppException` using `CommonErrorCode.FILE_STORAGE_FAILED` so the HTTP code is `503` and response code is `EXT-5033`.
- Unexpected `DataAccessException` continues to use existing global DB/system mapping.

The application service must catch only workbook generation failures from the writer. It must not convert authorization or validation failures to `EXT-5033`.

## Tests

Spec-phase acceptance tests for the implementation plan:

- Query normalization rejects missing/invalid `academicYear`, rejects `DRAFT`, normalizes repeated and comma-separated classes, and drops blank class tokens.
- Controller security annotation requires `SCORE_EXPORT_ASSIGNED`.
- Controller returns xlsx content type, attachment filename, and workbook bytes for a successful export.
- Controller returns `404 / RES-4040` when the service reports no matching data.
- Workbook writer creates the exact header row, writes numeric total cells, writes timestamp text using `Instant.toString()`, and emits no formulas.
- Application service uses `score.export.assigned`, not `score.view.assigned`, when building the access context.
- Application service returns `RES-4040` for an empty authorized row list.
- Repository export query applies status, grade, class, and scope filters together.
- Repository keeps records with no primary membership exportable to ALL scope when no grade/class filter is present, with blank grade/class fields.
- Repository excludes no-membership records when grade or class filters are present.
- Repository returns an empty list for unsupported-scope-only callers.
- D safe-init consistency tests verify `score.export.assigned` scope rules `8023` and `8024`, deterministic collision guards, rerunnable inserts, and required `created_at` columns.
- Spring context smoke test verifies `FinalScoreExportApplicationService` and the POI writer are wired.

Focused implementation verification should include at least:

```bash
mvn -pl whut-eval-app -am -Dtest=FinalScoreExport*Test,AdminFinalScoreExportControllerWebMvcTest,FinalRecordControllerSecurityAnnotationTest,MybatisPlusFinalRecordQueryRepositoryIntegrationTest,TeamDeliverySqlConsistencyTest,GroupAIdentitySqlSeedConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Before merge, also run the D-7/D-8/D-9 import regression command used for D-9 plus D-10 export tests, because D-10 will touch shared final-record query repository code.

## Open Decisions Closed By This Spec

- D-10 exports totals only, not component details.
- D-10 derives grade/class from `org_unit`, not a new student profile table.
- D-10 accepts repeated and comma-separated `classes` query params.
- D-10 ignores `pageNo/pageSize` if supplied, because the delivery contract does not define pagination for exports.
- D-10 returns `404` for authorized empty result sets rather than an empty workbook.
- D-10 adds counselor and college reviewer export scope rules in D safe-init; it does not add platform-admin ALL export scope in this slice.
