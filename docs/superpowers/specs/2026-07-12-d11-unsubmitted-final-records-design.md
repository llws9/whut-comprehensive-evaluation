# D-11 Unsubmitted Final Records Design

## 1. Goal

Implement D-11: `GET /api/admin/final-records/unsubmitted`.

The endpoint lets an authorized counselor or college reviewer page through current active students in their assigned organization scope who have not yet reached `SUBMITTED` or `CONFIRMED` for the requested academic year.

For D-11, "expected to submit" is deliberately defined as the current active student roster in scope. It is not derived from application activity, approved score facts, historical enrollment snapshots, or import/export rows.

This is the first deferred D-group function after Minimal D. It must reuse the Minimal D final-record authorization model and must not introduce import/export behavior.

## 2. Current Baseline

Minimal D already provides:

- student final-record header/detail reads;
- student final-record submission;
- admin final-record list/detail reads for `SUBMITTED` and `CONFIRMED`;
- admin final-record confirmation;
- `score.view.assigned` organization-scope authorization for admin final-record reads;
- whole-record scope evaluation through `FinalRecordAccessContext` and `FinalRecordScopePredicateBuilder`;
- `final_record` status values `DRAFT`, `SUBMITTED`, and `CONFIRMED`;
- A-group identity data where `org_unit.path` stores code paths such as `/WHUT/CS/CS2022/CS2201`.

The current code does not provide:

- `/api/admin/final-records/unsubmitted`;
- a query type for unsubmitted students;
- a response DTO for `UnsubmittedStudentView`;
- repository and SQL-provider methods that start from active student membership instead of starting from `final_record`;
- tests proving that `DRAFT` final records remain unsubmitted while still exposing `lastUpdatedAt`;
- tests proving that a student with no `final_record` is included when they are otherwise in scope.

The existing `FinalRecordPageQuery` and `FinalRecordQuerySqlProvider.baseSelect(...)` are intentionally unsuitable for D-11 as-is:

- `FinalRecordPageQuery` validates admin final-record statuses and only allows `SUBMITTED` or `CONFIRMED`;
- `baseSelect(...)` always filters `fr.status IN ('SUBMITTED', 'CONFIRMED')`;
- D-11 must include students with no `final_record` and students whose latest record for the year is `DRAFT`.

## 3. Scope

### 3.1 In Scope

- Add `GET /api/admin/final-records/unsubmitted`.
- Add `UnsubmittedFinalRecordQuery` with validation for:
  - required `academicYear`;
  - optional `grade`;
  - optional `classes`;
  - normalized pagination.
- Add `UnsubmittedStudentView`.
- Add application service, repository, mapper, and SQL-provider paths for unsubmitted students.
- Reuse `score.view.assigned`.
- Reuse whole-record organization scope, mapped to active primary student membership.
- Return `ApiResponse<PageResult<UnsubmittedStudentView>>`.
- Return an empty page when no matching students exist.
- Cover service, repository, and controller/security contracts with focused tests.

### 3.2 Out of Scope

- D-7 mentor/fixed-score import.
- D-8 lecture import.
- D-9 cultural/sports activity import.
- D-10 final-score export.
- New IAM permissions or scope-rule seed data.
- Creating `final_record` rows from this query.
- Recomputing final-record totals.
- Deriving candidate students from applications, application facts, or review tasks.
- Frontend pages.

## 4. Endpoint Contract

### 4.1 Route

`GET /api/admin/final-records/unsubmitted`

### 4.2 Permission

`score.view.assigned`

The controller method must use the same style as the existing admin final-record list endpoint:

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")
```

The application service must also check the same authority before querying. Missing authority returns `403 / AUTH-4030` through the existing exception mapping.

### 4.3 Query Parameters

| Parameter | Type | Required | Default | Rule |
|---|---|---|---|---|
| `academicYear` | string | yes | - | trim; must match `YYYY-YYYY` and the second year must equal the first year plus 1 |
| `grade` | string | no | - | trim; exact match against grade organization code or name |
| `classes` | string | no | - | single value; trim; exact match against class organization code or name |
| `pageNo` | long | no | `1` | values `<= 0` normalize to `1` |
| `pageSize` | long | no | `20` | values `<= 0` normalize to `20`; values `> 100` cap at `100` |

Invalid `academicYear` returns `400 / VAL-4001`.

`grade` and `classes` are filter names from the target D-group contract. The implementation maps them to organization metadata:

- `classes` filters the active primary student class unit with exact equality: `class_ou.unit_code = classes OR class_ou.unit_name = classes`;
- `grade` filters the active parent grade unit with exact equality: `grade_ou.unit_code = grade OR grade_ou.unit_name = grade`;
- blank values are ignored.

No fuzzy match, prefix match, contains match, or case normalization is performed by application code. The database collation determines case sensitivity. The parameter name remains `classes`, not `className`, to match the team-delivery contract; despite the plural name, this D-11 increment accepts a single class filter value.

If a filter value exactly matches one organization unit's `unit_code` and another organization unit's `unit_name`, both matching units are included because the filter uses OR semantics. If `grade` is provided, students whose selected class has no active parent grade row are excluded because there is no grade code/name to match. Without a `grade` filter, those students may still appear with `grade = null`.

### 4.4 Success Response

`ApiResponse<PageResult<UnsubmittedStudentView>>`

`UnsubmittedStudentView` fields:

| Field | Type | Rule |
|---|---|---|
| `studentUserId` | number | `iam_user.id` |
| `userNo` | string | `iam_user.user_no` |
| `userName` | string | `iam_user.user_name` |
| `grade` | string | active parent grade `org_unit.unit_name`; `null` if the class has no active `GRADE` parent |
| `className` | string | active primary class `org_unit.unit_name` |
| `status` | string | fixed `UNSUBMITTED` |
| `lastUpdatedAt` | string/null | ISO-8601 UTC string serialized from `MAX(final_record.updated_at)` across `DRAFT` rows for the academic year; otherwise `null` |

When no data matches, return `200` with `total = 0` and `records = []`.

## 5. Business Semantics

### 5.1 Candidate Student Set

A candidate student is a user row in the current roster view that satisfies all of the following:

- `iam_user.status = 'ACTIVE'`;
- has an active primary organization membership:
  - `org_membership.membership_type = 'STUDENT'`;
  - `org_membership.is_primary = 1`;
  - `org_membership.status = 'ACTIVE'`;
- the primary organization is a class unit:
  - `class_ou.unit_type = 'CLASS'`;
  - `class_ou.status = 'ACTIVE'`;
- the class is within the caller's `score.view.assigned` organization scope.

This means disabled users, inactive memberships, non-primary memberships, and teacher/admin memberships are not candidates.

D-11 does not reconstruct a historical roster for `academicYear`. The roster is based on current active IAM and organization membership data at query time. If the school later needs historical enrollment semantics, that must be introduced as a separate roster source and is outside this increment.

A-group data is expected to have at most one active primary student membership per user. D-11 still guards against duplicated active primary rows: the query selects one deterministic membership per user using the lowest `org_membership.id`, and all class, grade, scope, and filter behavior is evaluated from that selected membership. This prevents duplicate rows and inflated pagination totals when dirty membership data exists.

### 5.2 Unsubmitted Definition

For the requested `academicYear`, a candidate student is unsubmitted when no final record for that student/year has reached `SUBMITTED` or `CONFIRMED`.

Equivalent SQL rule:

```sql
NOT EXISTS (
  SELECT 1
  FROM final_record submitted_fr
  WHERE submitted_fr.student_user_id = u.id
    AND submitted_fr.academic_year = #{query.academicYear}
    AND submitted_fr.status IN ('SUBMITTED', 'CONFIRMED')
)
```

If a `DRAFT` final record exists for the same student/year, the student is still unsubmitted. Draft records only supply `lastUpdatedAt`.

Minimal D enforces one `final_record` per `(student_user_id, academic_year)`, so there should be at most one draft row. The D-11 query must still be defensive in case dirty data contains multiple draft rows for the same student/year: draft data is joined through an aggregate subquery with one row per `student_user_id`, and `lastUpdatedAt` is `MAX(updated_at)`. Count and select SQL must both preserve one result row per student.

### 5.3 Source of Truth

The candidate roster comes from A-group IAM and organization tables:

- `iam_user`;
- `org_membership`;
- `org_unit` as class organization;
- `org_unit` as active parent grade organization.

The grade organization is only considered valid when the parent row satisfies `grade_ou.unit_type = 'GRADE'` and `grade_ou.status = 'ACTIVE'`. If the class parent is missing, inactive, or not a grade unit, D-11 treats the class as having no grade: `grade = null`, `grade` filters do not match it, and an unfiltered query may still return the student.

The submission state comes from D-group final-record tables:

- `final_record`.

The query must not use `application_submission`, `application_fact`, `application_review_log`, or import/export tables to decide who should appear. D-11 is a final-record submission-monitoring endpoint, not a score-eligibility calculation.

## 6. Authorization and Scope Behavior

### 6.1 Scope Reuse

D-11 reuses `score.view.assigned` and `FinalRecordAccessContext`.

`FinalRecordQueryApplicationService` should assemble the access context the same way as the existing admin list path:

```java
new FinalRecordAccessContext(context.getUserId(), context.getUserNo(), context.getUserName(),
        context.getIdentity(), context.getRoles(), context.getAuthorities(), context.getScopeRules(),
        AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED)
```

and pass it to the repository, matching `pageAdminFinalRecords(...)`.

### 6.2 Scope Target

For D-11, the scope predicate applies to the student's active primary class membership:

- `applicant_user_id` maps to `u.id`;
- `org_unit_id` maps to `class_ou.id`;
- `org_path` maps to `class_ou.path`;
- category/item scope fragments remain unsupported for whole-record student roster visibility.

Scope merging rule:

- supported fragments are `ALL`, `ORG_UNIT`, and `ORG_SUBTREE`;
- unsupported `CATEGORY`, `ITEM`, `ORG_UNIT_ITEM`, and custom expression fragments are ignored when at least one supported fragment exists;
- if every granted fragment for `score.view.assigned` is unsupported, the result is an empty page;
- mixed scopes therefore behave like existing `FinalRecordScopePredicateBuilder`: an `ORG_SUBTREE` plus a category fragment returns the organization-scoped roster, while category-only returns no rows.

### 6.3 ORG_SUBTREE Path Rule

The implementation must not compare an `ORG_SUBTREE` root id directly against `org_unit.path`.

The seeded A-group organization path stores code paths:

- college: `/WHUT/CS`;
- grade: `/WHUT/CS/CS2022`;
- class: `/WHUT/CS/CS2022/CS2201`.

An `ORG_SUBTREE` rule rooted at org id `2002` must resolve the root org path (`/WHUT/CS`) and match:

```sql
class_ou.path = root.path
OR class_ou.path LIKE CONCAT(root.path, '/%')
```

Chosen implementation approach: add a final-record roster/list scope SQL path that translates `ORG_SUBTREE` into an `EXISTS` predicate against `org_unit root_ou`.

Example fragment:

```sql
EXISTS (
  SELECT 1
  FROM org_unit root_ou
  WHERE root_ou.id = #{scopeParam}
    AND root_ou.status = 'ACTIVE'
    AND (
      class_ou.path = root_ou.path
      OR class_ou.path LIKE CONCAT(root_ou.path, '/%')
    )
)
```

This keeps the comparison in SQL, uses the real code-path values stored by A-group data, and avoids resolving org paths in Java before query execution. `ORG_UNIT` remains a direct equality check on `class_ou.id`. `ALL` produces no additional predicate.

The same rule must be preserved for existing admin final-record list/detail access if the implementation touches shared scope translation. A D-11 fix must not regress Minimal D admin list visibility.

## 7. SQL Shape

D-11 should add a separate mapper/provider path rather than overloading the submitted-record list query.

Recommended count query:

```sql
SELECT COUNT(1)
FROM iam_user u
JOIN (
  SELECT om1.*
  FROM org_membership om1
  JOIN (
    SELECT user_id, MIN(id) AS membership_id
    FROM org_membership
    WHERE membership_type = 'STUDENT'
      AND is_primary = 1
      AND status = 'ACTIVE'
    GROUP BY user_id
  ) picked_om
    ON picked_om.membership_id = om1.id
) om
  ON om.user_id = u.id
JOIN org_unit class_ou
  ON class_ou.id = om.org_unit_id
 AND class_ou.unit_type = 'CLASS'
 AND class_ou.status = 'ACTIVE'
LEFT JOIN org_unit grade_ou
  ON grade_ou.id = class_ou.parent_id
 AND grade_ou.unit_type = 'GRADE'
 AND grade_ou.status = 'ACTIVE'
WHERE u.status = 'ACTIVE'
  AND (
    #{scopeAllowsAll} = TRUE
    OR class_ou.id IN (#{orgUnitScopeId1}, #{orgUnitScopeId2})
    OR EXISTS (
      SELECT 1
      FROM org_unit root_ou
      WHERE root_ou.id IN (#{orgSubtreeRootId1}, #{orgSubtreeRootId2})
        AND root_ou.status = 'ACTIVE'
        AND (
          class_ou.path = root_ou.path
          OR class_ou.path LIKE CONCAT(root_ou.path, '/%')
        )
    )
  )
  AND (#{query.grade} IS NULL OR grade_ou.unit_code = #{query.grade} OR grade_ou.unit_name = #{query.grade})
  AND (#{query.classes} IS NULL OR class_ou.unit_code = #{query.classes} OR class_ou.unit_name = #{query.classes})
  AND NOT EXISTS (
    SELECT 1
    FROM final_record submitted_fr
    WHERE submitted_fr.student_user_id = u.id
      AND submitted_fr.academic_year = #{query.academicYear}
      AND submitted_fr.status IN ('SUBMITTED', 'CONFIRMED')
  )
```

The count query must not join draft records because draft data does not affect membership in the result set and a dirty duplicate draft row must never inflate `total`.

Recommended select query uses the same roster, scope, filter, and submitted/confirmed exclusion predicates, plus a one-row-per-student draft aggregate:

```sql
LEFT JOIN (
  SELECT student_user_id, MAX(updated_at) AS last_updated_at
  FROM final_record
  WHERE academic_year = #{query.academicYear}
    AND status = 'DRAFT'
  GROUP BY student_user_id
) draft_fr
  ON draft_fr.student_user_id = u.id
```

The SQL provider must generate the scope block from the evaluated scope set:

- when `ALL` is granted, set `scopeAllowsAll = TRUE` and omit empty `IN` fragments;
- when one or more `ORG_UNIT` rules exist, add the `class_ou.id IN (...)` fragment with bound parameters;
- when one or more `ORG_SUBTREE` rules exist, add the `root_ou.id IN (...)` fragment with bound parameters;
- when no supported scope fragments exist, return a deny-all predicate such as `1 = 0`;
- never emit `IN ()`.

Recommended select columns:

```sql
u.id AS student_user_id,
u.user_no AS user_no,
u.user_name AS user_name,
grade_ou.unit_name AS grade,
class_ou.unit_name AS class_name,
draft_fr.last_updated_at AS last_updated_at
```

Recommended ordering:

```sql
ORDER BY CASE WHEN grade_ou.unit_code IS NULL THEN 1 ELSE 0 END ASC,
         grade_ou.unit_code ASC,
         class_ou.unit_code ASC,
         u.user_no ASC,
         u.id ASC
```

This order is stable for pagination and matches how admins scan a roster. Rows with no active grade parent sort after rows with a grade.

## 8. Application Model

### 8.1 Query Object

Create `UnsubmittedFinalRecordQuery` under the final-record query package.

Fields:

- `academicYear`;
- `grade`;
- `classes`;
- `pageNo`;
- `pageSize`.

Validation:

- normalize `academicYear` by trimming before validation and storage;
- throw `ValidationException("academicYear 不能为空")` for a missing or blank academic year after trim;
- throw `ValidationException("academicYear 不合法")` when the trimmed value does not match `YYYY-YYYY` or the second year is not the first year plus 1;
- normalize optional filters by trimming blank to `null`;
- normalize pagination like existing `FinalRecordPageQuery`.

### 8.2 Row DTO

Create `UnsubmittedStudentRow` for mapper/repository results.

Fields:

- `Long studentUserId`;
- `String userNo`;
- `String userName`;
- `String grade`;
- `String className`;
- `Instant lastUpdatedAt`.

The row does not need a mutable `status` field because the application layer always exposes `UNSUBMITTED`.

### 8.3 View DTO

Create `UnsubmittedStudentView`.

Recommended Java record:

```java
public record UnsubmittedStudentView(
        Long studentUserId,
        String userNo,
        String userName,
        String grade,
        String className,
        String status,
        Instant lastUpdatedAt
) {
}
```

The service maps every row with `status = "UNSUBMITTED"`.

`lastUpdatedAt` must be serialized in the same external time format used by existing final-record APIs: an ISO-8601 UTC string from `Instant`, not a numeric epoch timestamp. If the global Jackson configuration does not already disable timestamp serialization for Java time values, this DTO must add a local annotation/configuration so the JSON contract remains a string.

### 8.4 Repository Contract

Extend `FinalRecordQueryRepository` with:

```java
PageResult<UnsubmittedStudentRow> pageUnsubmittedStudents(
        FinalRecordAccessContext accessContext,
        UnsubmittedFinalRecordQuery query
);
```

Implementation responsibilities:

- build the same authorization scope fragment used by admin final-record list;
- translate the fragment onto the D-11 roster aliases;
- count and select through new mapper methods;
- return `PageResult<>(total, records)`.

## 9. Controller and Service Flow

Controller:

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")
@GetMapping("/unsubmitted")
public ApiResponse<PageResult<UnsubmittedStudentView>> pageUnsubmittedFinalRecords(
        @RequestParam(required = false) String academicYear,
        @RequestParam(required = false) String grade,
        @RequestParam(required = false) String classes,
        @RequestParam(defaultValue = "1") long pageNo,
        @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.success(queryApplicationService.pageUnsubmittedStudents(
            new UnsubmittedFinalRecordQuery(academicYear, grade, classes, pageNo, pageSize)
    ));
}
```

Service:

```java
public PageResult<UnsubmittedStudentView> pageUnsubmittedStudents(UnsubmittedFinalRecordQuery query) {
    UserAuthorizationContext admin = userAuthorizationContextAssembler.requiredAuthorizationContext();
    ensurePermission(admin, AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED, "当前用户无未提交最终成绩名单查询权限");
    PageResult<UnsubmittedStudentRow> page = finalRecordQueryRepository.pageUnsubmittedStudents(
            toAccessContext(admin, AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED),
            query
    );
    return new PageResult<>(page.total(), page.records().stream().map(this::toUnsubmittedView).toList());
}
```

Route order must keep `/unsubmitted` from being captured by `/{recordId}`. In Spring MVC, a static segment is more specific than a path variable, but placing `/unsubmitted` before `/{recordId}` keeps the file readable and avoids accidental ambiguity.

## 10. Error Handling

| Scenario | HTTP | Code | Source |
|---|---:|---|---|
| missing `academicYear` | `400` | `VAL-4001` | `ValidationException` from query object |
| blank `academicYear` | `400` | `VAL-4001` | `ValidationException` from query object |
| malformed `academicYear` | `400` | `VAL-4001` | `ValidationException` from query object |
| no `score.view.assigned` authority | `403` | `AUTH-4030` | `AccessDeniedAppException` or method security |
| valid request with no matches | `200` | - | empty page |

D-11 does not return `404` for no data.

## 11. Tests

### 11.1 Domain Query Object Tests

Add tests that prove:

- blank academic year throws `ValidationException`;
- `academicYear = " 2025-2026 "` is accepted and normalized to `2025-2026`;
- malformed academic years such as `abc`, `2025`, and `2025-2027` throw `ValidationException`;
- `2025-2026` is accepted;
- blank `grade` and `classes` normalize to `null`;
- nonblank `grade` and `classes` are trimmed but not case-normalized;
- `pageNo <= 0` becomes `1`;
- `pageSize <= 0` becomes `20`;
- `pageSize = 100` remains `100`;
- `pageSize > 100` becomes `100`.

These can live near existing final-record query tests.

### 11.2 Application Service Tests

Extend `FinalRecordQueryApplicationServiceTest`:

- returns `PageResult<UnsubmittedStudentView>` with fixed `status = "UNSUBMITTED"`;
- passes `score.view.assigned` access context to the repository;
- denies callers without `score.view.assigned`;
- preserves `lastUpdatedAt` from a draft row;
- returns `lastUpdatedAt = null` for an unsubmitted student with no `final_record`;
- returns an empty page without throwing;
- maps every row to `status = "UNSUBMITTED"` without trusting mapper data for that value.

### 11.3 Repository Integration Tests

Extend `MybatisPlusFinalRecordQueryRepositoryIntegrationTest` or add a focused sibling test:

- includes active students in authorized org scope with no `final_record`;
- includes students with `DRAFT` final records and maps `lastUpdatedAt`;
- excludes students with `SUBMITTED`;
- excludes students with `CONFIRMED`;
- excludes inactive users;
- excludes inactive memberships;
- excludes non-primary student memberships;
- de-duplicates duplicate active primary memberships for the same student by selecting the lowest `org_membership.id`;
- filters by `grade`;
- filters by `classes`;
- filters by `grade` and `classes` together using intersection semantics;
- proves `grade` and `classes` filters match organization code and organization name by exact equality;
- proves a partial name such as `计算机` does not match `计算机 2022 级`;
- proves a filter value that matches one org's code and another org's name includes both exact matches;
- verifies stable ordering by grade code, class code, user no, and user id;
- verifies rows with `grade = null` sort after rows with an active grade;
- verifies two-page pagination has no duplicated student ids and the combined rows match the same stable order;
- returns empty page for unsupported category-only scopes;
- returns all visible current-roster rows for a pure `ALL` scope;
- returns only the exact class rows for a pure `ORG_UNIT` scope targeting a class id;
- returns only subtree rows for a pure `ORG_SUBTREE` scope targeting a college id, without relying on a mixed-scope rule;
- returns organization-scoped rows for a mixed scope containing one supported `ORG_SUBTREE` rule and one unsupported category rule;
- resolves `ORG_SUBTREE` against real `org_unit.path` code paths, proving a scope rooted at org id `2002` can see classes whose path starts with `/WHUT/CS`.
- proves an org path with a similar prefix but not a real child path, such as `/WHUT/CS2/CS2201`, is not visible to a root path `/WHUT/CS`;
- verifies a student with duplicate dirty draft final records still appears once and uses `MAX(updated_at)` for `lastUpdatedAt`;
- verifies a student with no `final_record` has `lastUpdatedAt = null`;
- verifies a class with no active `GRADE` parent row is excluded when `grade` is provided and can appear with `grade = null` when no `grade` filter is provided.

The last test is mandatory because a numeric-id path comparison such as `LIKE '%/2002/%'` would return zero rows against the seeded organization path format.

The repository integration test schema must include the real A-group columns used by the D-11 SQL path: `org_unit.parent_id`, `org_unit.unit_type`, `org_unit.unit_code`, `org_unit.unit_name`, `org_unit.path`, `org_unit.status`, and `org_membership.id`. Do not keep the older Minimal D simplified `org_unit` test fixture if it hides these contract fields.

### 11.4 Controller Tests

Add or extend admin final-record controller tests to prove:

- `GET /api/admin/final-records/unsubmitted?academicYear=2025-2026` returns the page shape;
- a draft `lastUpdatedAt` value is rendered as an ISO-8601 UTC JSON string, not a numeric timestamp;
- missing `academicYear` returns `400 / VAL-4001`;
- blank `academicYear` returns `400 / VAL-4001`;
- malformed `academicYear` returns `400 / VAL-4001`;
- `grade` and `classes` are forwarded as single exact-match filter values;
- a caller without `score.view.assigned` receives `403 / AUTH-4030`;
- the `/unsubmitted` route is not captured by `/{recordId}`.

If the project does not currently have focused MVC tests for this controller, a Spring slice test is preferred over broad end-to-end coverage for this D-11 increment.

## 12. Backward Compatibility

D-11 must not change:

- student final-record endpoints;
- admin submitted/confirmed final-record list semantics;
- admin final-record detail semantics;
- final-record submit or confirm state transitions;
- D safe-init seed data.

If shared scope translation is fixed to resolve `ORG_SUBTREE` by real org paths, existing Minimal D admin list/detail tests must be updated or extended to assert the corrected behavior rather than loosened.

## 13. Review Checklist

- The endpoint uses `score.view.assigned`.
- The route is exactly `/api/admin/final-records/unsubmitted`.
- `DRAFT` rows are treated as unsubmitted.
- `SUBMITTED` and `CONFIRMED` rows exclude the student.
- Students with no `final_record` are included when otherwise in scope.
- The roster starts from active primary student membership, not final records.
- The roster is current active membership, not a historical roster for the requested academic year.
- Duplicate active primary membership rows do not duplicate students in the result.
- Mixed supported and unsupported scope fragments return rows for the supported organization scope.
- Pure `ALL`, pure `ORG_UNIT`, and pure `ORG_SUBTREE` scopes are each tested on the D-11 roster query path.
- Unsupported-only scope fragments return an empty page.
- `grade` and `classes` filters are exact code/name matches.
- `grade` and `classes` together use intersection semantics.
- A provided `grade` filter excludes classes without an active `GRADE` parent row.
- Pagination order is deterministic and stable across pages.
- Null grade rows sort after non-null grade rows.
- ORG_SUBTREE path matching rejects similar-prefix non-child paths.
- Duplicate dirty draft final records do not duplicate students in the result.
- `lastUpdatedAt` is the draft aggregate `MAX(final_record.updated_at)` or `null`.
- No data returns an empty page, not `404`.
- `ORG_SUBTREE` matches the real code-path format in `org_unit.path`.
- The implementation does not introduce D-7, D-8, D-9, or D-10 behavior.
