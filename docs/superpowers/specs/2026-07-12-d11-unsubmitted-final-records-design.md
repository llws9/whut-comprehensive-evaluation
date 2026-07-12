# D-11 Unsubmitted Final Records Design

## 1. Goal

Implement D-11: `GET /api/admin/final-records/unsubmitted`.

The endpoint lets an authorized counselor or college reviewer page through students in their assigned organization scope who are expected to submit a final record for an academic year but have not yet reached `SUBMITTED` or `CONFIRMED`.

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
|---|---|---:|---|---|
| `academicYear` | string | yes | - | trim; blank is invalid |
| `grade` | string | no | - | trim; matched against grade organization code or name |
| `classes` | string | no | - | trim; matched against class organization code or name |
| `pageNo` | long | no | `1` | values `<= 0` normalize to `1` |
| `pageSize` | long | no | `20` | values `<= 0` normalize to `20`; values `> 100` cap at `100` |

Invalid `academicYear` returns `400 / VAL-4001`.

`grade` and `classes` are filter names from the target D-group contract. The implementation maps them to organization metadata:

- `classes` filters the active primary student class unit (`class_ou.unit_code` or `class_ou.unit_name`);
- `grade` filters the parent grade unit of that class (`grade_ou.unit_code` or `grade_ou.unit_name`);
- blank values are ignored.

The parameter name remains `classes`, not `className`, to match the team-delivery contract.

### 4.4 Success Response

`ApiResponse<PageResult<UnsubmittedStudentView>>`

`UnsubmittedStudentView` fields:

| Field | Type | Rule |
|---|---|---|
| `studentUserId` | number | `iam_user.id` |
| `userNo` | string | `iam_user.user_no` |
| `userName` | string | `iam_user.user_name` |
| `grade` | string | parent grade `org_unit.unit_name`; `null` if the class has no grade parent |
| `className` | string | active primary class `org_unit.unit_name` |
| `status` | string | fixed `UNSUBMITTED` |
| `lastUpdatedAt` | string/null | `final_record.updated_at` when a `DRAFT` row exists for the academic year; otherwise `null` |

When no data matches, return `200` with `total = 0` and `records = []`.

## 5. Business Semantics

### 5.1 Candidate Student Set

A candidate student is a user row that satisfies all of the following:

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

If a `DRAFT` final record exists for the same student/year, the student is still unsubmitted. The draft row only supplies `lastUpdatedAt`.

Minimal D enforces one `final_record` per `(student_user_id, academic_year)`, so there should be at most one draft row. The D-11 query must still be defensive and only join draft rows by `fr.status = 'DRAFT'`.

### 5.3 Source of Truth

The candidate roster comes from A-group IAM and organization tables:

- `iam_user`;
- `org_membership`;
- `org_unit` as class organization;
- `org_unit` as parent grade organization.

The submission state comes from D-group final-record tables:

- `final_record`.

The query must not use `application_submission`, `application_fact`, `application_review_log`, or import/export tables to decide who should appear. D-11 is a final-record submission-monitoring endpoint, not a score-eligibility calculation.

## 6. Authorization and Scope Behavior

### 6.1 Scope Reuse

D-11 reuses `score.view.assigned` and `FinalRecordAccessContext`.

`FinalRecordQueryApplicationService` should assemble:

```java
new FinalRecordAccessContext(..., AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED)
```

and pass it to the repository, matching `pageAdminFinalRecords(...)`.

### 6.2 Scope Target

For D-11, the scope predicate applies to the student's active primary class membership:

- `applicant_user_id` maps to `u.id`;
- `org_unit_id` maps to `class_ou.id`;
- `org_path` maps to `class_ou.path`;
- category/item scope fragments remain unsupported for whole-record student roster visibility and should produce an empty page through the existing `FinalRecordScopePredicateBuilder` behavior.

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

The current translator may emit path predicates from ids. D-11's implementation must either:

- use a final-record-specific translator that resolves root org ids to path strings before SQL generation; or
- translate `ORG_SUBTREE` into an `EXISTS` predicate against `org_unit root_ou` so the comparison uses real code paths.

The same rule must be preserved for existing admin final-record list/detail access if the implementation touches shared scope translation. A D-11 fix must not regress Minimal D admin list visibility.

## 7. SQL Shape

D-11 should add a separate mapper/provider path rather than overloading the submitted-record list query.

Recommended count query:

```sql
SELECT COUNT(1)
FROM iam_user u
JOIN org_membership om
  ON om.user_id = u.id
 AND om.membership_type = 'STUDENT'
 AND om.is_primary = 1
 AND om.status = 'ACTIVE'
JOIN org_unit class_ou
  ON class_ou.id = om.org_unit_id
 AND class_ou.unit_type = 'CLASS'
 AND class_ou.status = 'ACTIVE'
LEFT JOIN org_unit grade_ou
  ON grade_ou.id = class_ou.parent_id
LEFT JOIN final_record draft_fr
  ON draft_fr.student_user_id = u.id
 AND draft_fr.academic_year = #{query.academicYear}
 AND draft_fr.status = 'DRAFT'
WHERE u.status = 'ACTIVE'
  AND <scope predicate>
  AND <optional grade/classes filters>
  AND NOT EXISTS (
    SELECT 1
    FROM final_record submitted_fr
    WHERE submitted_fr.student_user_id = u.id
      AND submitted_fr.academic_year = #{query.academicYear}
      AND submitted_fr.status IN ('SUBMITTED', 'CONFIRMED')
  )
```

Recommended select columns:

```sql
u.id AS student_user_id,
u.user_no AS user_no,
u.user_name AS user_name,
grade_ou.unit_name AS grade,
class_ou.unit_name AS class_name,
'UNSUBMITTED' AS status,
draft_fr.updated_at AS last_updated_at
```

Recommended ordering:

```sql
ORDER BY grade_ou.unit_code ASC,
         class_ou.unit_code ASC,
         u.user_no ASC,
         u.id ASC
```

This order is stable for pagination and matches how admins scan a roster.

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

- throw `ValidationException("academicYear 不能为空")` for blank academic year;
- normalize optional filters by trimming blank to `null`;
- normalize pagination like existing `FinalRecordPageQuery`.

### 8.2 Row DTO

Create `UnsubmittedStudentRow` for mapper/repository results.

Fields:

- `studentUserId`;
- `userNo`;
- `userName`;
- `grade`;
- `className`;
- `lastUpdatedAt`.

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
        @RequestParam String academicYear,
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
| blank `academicYear` | `400` | `VAL-4001` | `ValidationException` from query object |
| no `score.view.assigned` authority | `403` | `AUTH-4030` | `AccessDeniedAppException` or method security |
| valid request with no matches | `200` | - | empty page |

D-11 does not return `404` for no data.

## 11. Tests

### 11.1 Domain Query Object Tests

Add tests that prove:

- blank academic year throws `ValidationException`;
- blank `grade` and `classes` normalize to `null`;
- `pageNo <= 0` becomes `1`;
- `pageSize <= 0` becomes `20`;
- `pageSize > 100` becomes `100`.

These can live near existing final-record query tests.

### 11.2 Application Service Tests

Extend `FinalRecordQueryApplicationServiceTest`:

- returns `PageResult<UnsubmittedStudentView>` with fixed `status = "UNSUBMITTED"`;
- passes `score.view.assigned` access context to the repository;
- denies callers without `score.view.assigned`;
- preserves `lastUpdatedAt` from a draft row;
- returns an empty page without throwing.

### 11.3 Repository Integration Tests

Extend `MybatisPlusFinalRecordQueryRepositoryIntegrationTest` or add a focused sibling test:

- includes active students in authorized org scope with no `final_record`;
- includes students with `DRAFT` final records and maps `lastUpdatedAt`;
- excludes students with `SUBMITTED`;
- excludes students with `CONFIRMED`;
- excludes inactive users;
- excludes inactive memberships;
- excludes non-primary student memberships;
- filters by `grade`;
- filters by `classes`;
- returns empty page for unsupported category-only scopes;
- resolves `ORG_SUBTREE` against real `org_unit.path` code paths, proving a scope rooted at org id `2002` can see classes whose path starts with `/WHUT/CS`.

The last test is mandatory because a numeric-id path comparison such as `LIKE '%/2002/%'` would return zero rows against the seeded organization path format.

### 11.4 Controller Tests

Add or extend admin final-record controller tests to prove:

- `GET /api/admin/final-records/unsubmitted?academicYear=2025-2026` returns the page shape;
- blank `academicYear` returns `400 / VAL-4001`;
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
- `lastUpdatedAt` is draft `final_record.updated_at` or `null`.
- No data returns an empty page, not `404`.
- `ORG_SUBTREE` matches the real code-path format in `org_unit.path`.
- The implementation does not introduce D-7, D-8, D-9, or D-10 behavior.
