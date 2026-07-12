# D-11 Unsubmitted Final Records Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build D-11, the team D unsubmitted-final-record list item: `GET /api/admin/final-records/unsubmitted` so authorized admins can page current active in-scope students who have not submitted or confirmed final records for an academic year.

**Scope lock:** This D-11 plan implements only the frozen unsubmitted list route above. It does not add Excel export, `classId`, `pageNum`, `pages`, or frontend behavior. Request fields are `academicYear`, optional `grade`, `classes` as a `string[]`, `pageNo`, and `pageSize`; `classes` accepts both repeated `classes=a&classes=b` and array-style `classes[]=a&classes[]=b` encodings. Response pagination remains `PageResult<T>` with only `total` and `records`. D-11 does not introduce dirty-data support that violates the frozen A/D SQL contracts, including missing `iam_user` columns, nullable organization path/code fields, nullable final-record status/update timestamps, or duplicate `final_record` rows for the same student and academic year. Nullable projection values from optional joins, such as no DRAFT record or no active grade parent, remain valid and are rendered as empty response strings where the API contract says so.

**Architecture:** Add a D-11 query path beside the existing Minimal D final-record list, but do not overload the submitted/confirmed list SQL. The new path starts from active IAM roster membership, applies whole-record organization scope to the selected class org, and excludes the whole student if that student has a `SUBMITTED` or `CONFIRMED` final record in the requested academic year. A missing final record and a single `DRAFT` final record both mean the student is unsubmitted; `DRAFT` contributes `lastUpdatedAt` from its non-null `updated_at`. Sort rows by active-grade-present first, grade code, class code, student number, then user id; `user_id ASC` is the final tie-breaker and part of the pagination contract. If a student has multiple visible primary class memberships after scope and filters, return the student once and display the class plus the active grade parent from the lowest numeric visible membership id; if that class has no active grade parent, display grade as null/empty rather than borrowing a grade from another membership. `grade` and `classes` filters both use case-sensitive code-or-name OR semantics, and both filters decide which memberships enter the visible set before the lowest visible membership is chosen. Final display selection still uses the lowest numeric visible membership id and never depends on request filter order or class-code lexical order.

**Frozen Schema Precedence:** If the source design and frozen SQL schema conflict, the frozen A/D schema wins for D-11 implementation and tests. `docs/team-delivery/group-a-identity-user-admin.sql` defines `org_unit.unit_code`, `org_unit.unit_name`, `org_unit.path`, and `org_unit.status` as `NOT NULL`; `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql` defines `final_record.status` and `final_record.updated_at` as `NOT NULL` and enforces `UNIQUE KEY uk_final_record_student_year (student_user_id, academic_year)`. Therefore D-11 must not add duplicate final-record rows for one student/year, nullable organization path/code cases, nullable final-record status/update timestamps, or missing IAM columns as tests or production behavior. Malformed but schema-valid path strings, such as empty strings, missing leading `/`, trailing `/`, or embedded SQL `LIKE` wildcard characters `%` and `_`, are still valid boundary cases for D-11 subtree guards and must be tested. D-11 treats such malformed subtree paths as non-matching instead of trying to repair or escape them at query time.

**Task Coverage Map:** This plan is intentionally split into five executable tasks: Task 1 query-object contract, Task 2 application service view mapping, Task 3 repository roster SQL, Task 4 controller route and request shape, and Task 5 regression/full verification. Task 4 and Task 5 appear after the Task 3 commit block and are required even though repository SQL is the largest section.

**Implementation Snippet Boundary:** Java and SQL blocks below are executable-oriented sketches that lock behavioral contracts, aliases, predicates, validation rules, and test intent. During implementation, keep the same observable contracts and guardrails, but adapt imports, helper placement, formatting, and minor method organization to the current repository style instead of mechanically copying a snippet when an existing local pattern is clearer.

## Pre-Implementation Verification Baseline

- [ ] Before creating D-11 code files or modifying production/test code, run:

```bash
mvn test
```

Record whether it passes. If it fails before D-11 implementation starts, record the failing test names and failure count in the execution notes. Task 5 may compare against this recorded pre-implementation baseline; if no pre-implementation baseline exists, do not claim "zero new failures" and report the final `mvn test` result as an observed state only.

If the full pre-implementation `mvn test` baseline is temporarily impractical because of local runtime constraints, record that blocker and also run the focused final-record baseline below before implementation:

```bash
mvn -pl whut-eval-app -am -Dtest='*FinalRecord*Test' test -Dsurefire.failIfNoSpecifiedTests=false
```

This focused baseline is only a fallback for pre-implementation comparison. It does not replace the final Task 5 full `mvn test` attempt and does not allow D-11-touched tests to fail.

Execution notes placeholder. The executor must write the observed baseline values back into this section before D-11 implementation starts. Do not use terminal scrollback or a handoff summary as the only baseline source; the final Task 5 comparison reads the fields below.

- Full Baseline Result: `PENDING` before implementation; replace with `PASS`, `FAIL`, or `BLOCKED`.
- Full Baseline Command: `mvn test`
- Full Baseline Failure Count: `0` when PASS; otherwise record the exact count.
- Full Baseline Failing Tests: `none` when PASS; otherwise list test class and method names.
- Full Baseline Blocker: `none` unless the full baseline is `BLOCKED`; otherwise record the concrete runtime blocker.
- Focused Fallback Baseline Result: `NOT_RUN` by default; replace with `PASS` or `FAIL` only if the full baseline is temporarily blocked.
- Focused Fallback Baseline Command: `mvn -pl whut-eval-app -am -Dtest='*FinalRecord*Test' test -Dsurefire.failIfNoSpecifiedTests=false`
- Focused Fallback Baseline Failure Count: `0` when PASS; otherwise record the exact count.
- Focused Fallback Baseline Failing Tests: `none` when PASS; otherwise list test class and method names.

Comparison rule: only a recorded full `mvn test` baseline allows a full-suite "zero new failures" comparison. If the full baseline is `BLOCKED` or still `PENDING` and only the focused fallback baseline exists, compare only D-11 and final-record related tests against the focused fallback; any later full-suite failures outside that focused set must be reported as observed state, not classified as new or pre-existing. D-11 added/modified tests and final-record tests explicitly run by this plan must pass in all cases.

**Tech Stack:** Java 21, Spring Boot 3.3.2, Spring MVC with `jakarta.servlet`, Spring Security method annotations, MyBatis provider SQL, H2 MySQL-mode integration tests, JUnit 5, AssertJ, Mockito.

---

## Source Spec

- Primary spec: `docs/superpowers/specs/2026-07-12-d11-unsubmitted-final-records-design.md`
- Frozen D contract: `docs/team-delivery/group-d-score-finalization-import-export.md`
- Existing admin controller: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java`
- Existing query service: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordQueryApplicationService.java`
- Existing query repository contract: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java`
- Existing mapper/provider: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java`, `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java`
- Existing repository implementation: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java`
- Existing global exception handler: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/exception/GlobalExceptionHandler.java`
  - `ValidationException` extends the common app-exception path handled by `handleBaseAppException(...)`, so controller-bound validation failures must return `VAL-4001`.
- Scope/predicate contracts reused by D-11:
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/auth/service/AuthorizationScopeEvaluator.java`
    - `AuthorizationScopeSet evaluate(UserAuthorizationContext authorizationContext, String permissionCode)` first checks authority and then normalizes active scope rules for the permission.
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/service/FinalRecordScopePredicateBuilder.java`
    - `ApplicationScopePredicate buildForFinalRecord(UserAuthorizationContext authorizationContext, AuthorizationScopeSet scopeSet)` converts only whole-record `ORG_UNIT` and `ORG_SUBTREE` rules into final-record predicates. `CATEGORY`, `ITEM`, and other score-only rules must not grant D-11 roster visibility.
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/auth/model/ApplicationScopePredicate.java`
    - `isGranted() == false` means deny all.
    - `isAllowAll() == true` means the caller can see all active roster candidates.
    - `isEmptyResult() == true` means the caller had the permission but no whole-record scope clauses survived filtering.
    - `getClauses()` returns immutable `ApplicationScopeClause` values for supported restricted scopes.
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/auth/model/ApplicationScopeClause.java`
    - D-11 reads `getScopeType()`, `getOrgUnitId()`, and `getOrgSubtreeRootId()` only. `getCategoryCode()`, `getItemCode()`, and `getExpressionJson()` are ignored for whole-record final-record roster access.
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/FinalRecordAccessContext.java`
    - D-11 converts this context into `UserAuthorizationContext` using the existing access-context fields for user id/no/name, identity, roles, authorities, and scope rules; `getPermissionCode()` must remain `score.view.assigned` for the unsubmitted route. The access-context `identity` field is authorization context data only; D-11 roster SQL must still not depend on an `iam_user.identity` database column.
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/SqlPredicateFragment.java`
    - Existing `allowAll()` returns a blank expression and `isAllowAll() == true` for existing translators. D-11 must add `alwaysTrue()` returning expression `1 = 1` with an empty parameter map because the D-11 provider treats blank `scopeExpression` as defensive deny-all.
    - `denyAll()` returns expression `1 = 0`.
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/ApplicationScopeSqlTranslator.java`
    - Existing submitted/confirmed paths can keep using this translator, but D-11 roster SQL must not pass through it because it emits predicates for pre-projected `org_path`/`org_unit_id` fields rather than live roster class aliases.
- Existing final-record query tests:
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordScopePredicateBuilderTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/security/ScopeSqlTranslatorTest.java`

## API Contract Summary

Request:

| Field | Type | Required | Constraint |
| --- | --- | --- | --- |
| `academicYear` | `String` | yes | Trimmed `yyyy-yyyy`, where end year is start year + 1; invalid or repeated values return `VAL-4001` with message `academicYear 不合法`. |
| `grade` | `String` | no | Trimmed exact code-or-name filter; blank becomes no filter; a normalized value longer than 256 characters returns `VAL-4001`; repeated values or `grade[]` return `VAL-4001`. |
| `classes` / `classes[]` | `List<String>` | no | Controller concatenates raw `classes` values first, then raw `classes[]` values. `UnsubmittedFinalRecordQuery` trims, drops blanks, de-duplicates in that merged-list order, rejects more than 500 normalized values, rejects any normalized value longer than 256 characters, and treats commas as ordinary exact-match characters. |
| `pageNo` | `long` | no | Defaults to `1`; non-numeric values return `VAL-4001`; values `<= 0` normalize to `1`; offset overflow returns `pageNo 不合法`. |
| `pageSize` | `long` | no | Defaults to `20`; non-numeric values return `VAL-4001`; values `<= 0` normalize to `20`; values `> 100` normalize to `100`. |

Response `data` remains `PageResult<UnsubmittedStudentView>` with exactly `total` and `records`. Each record has `studentUserId`, `userNo`, `userName`, `grade`, `className`, fixed `status = "UNSUBMITTED"`, and `lastUpdatedAt`. Nullable projection values are rendered as empty strings; `lastUpdatedAt` uses `Instant.toString()` when a DRAFT final record exists and is empty when no DRAFT final record exists.

Field naming map:

| Layer | Academic year | Class filters / display | Last update |
| --- | --- | --- | --- |
| Request | `academicYear` | `classes` / `classes[]` filters | n/a |
| Domain query | `academicYear` | `classes: List<String>` normalized exact filters | n/a |
| Repository row | `academicYear` input only | `className` property mapped from SQL alias `class_name` | `lastUpdatedAt: Instant` from SQL alias `last_updated_at` |
| API view / JSON | n/a | `className` display string | `lastUpdatedAt` string |

## File Structure

### Domain

- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/UnsubmittedFinalRecordQuery.java`
  - Validates `academicYear`.
  - Normalizes `grade`, `classes`, `pageNo`, and `pageSize`.
  - Exposes `isClassesEmpty()` and checked `offset`.

### Application Layer

- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/UnsubmittedStudentRow.java`
  - Mapper row with nullable projection values from optional joins, such as no DRAFT final record or no active grade parent. These nulls do not require nullable frozen table columns.
- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/UnsubmittedStudentView.java`
  - API view with string fallbacks and fixed status.
- Modify `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java`
  - Adds `pageUnsubmittedStudents(FinalRecordAccessContext, UnsubmittedFinalRecordQuery)`.
- Modify `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordQueryApplicationService.java`
  - Adds permission check, repository call, and row-to-view mapping.

### Infrastructure

- Modify `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java`
  - Adds count/select methods for D-11 roster query.
- Modify `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java`
  - Adds independent roster SQL and D-11 path-based scope predicates.
  - Leaves existing submitted/confirmed `baseSelect(...)` behavior untouched unless regression tests are added in the same task.
- Modify `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java`
  - Adds D-11 repository implementation and path-scope fragment construction for roster aliases.
- Modify `whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/SqlPredicateFragment.java`
  - Adds `alwaysTrue()` for D-11's non-blank allow-all SQL predicate while preserving existing `allowAll()` behavior.
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/D11ScopeSqlShape.java`
  - Centralizes D-11 scope SQL shape generation and whitelist validation.

### Interfaces

- Modify `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java`
  - Adds `/unsubmitted` route before `/{recordId}`.
  - Rejects multi-value `academicYear` and `grade`.
  - Merges repeated `classes` and array-style `classes[]`.
  - Leaves `classes` trim, blank dropping, de-duplication, value length checks, and `MAX_CLASSES = 500` enforcement to `UnsubmittedFinalRecordQuery` after both encodings are merged.

### Tests

- Create or modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/UnsubmittedFinalRecordQueryTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java`

This plan intentionally keeps `UnsubmittedFinalRecordQueryTest` in the existing `whut-eval-app` aggregate test module because the current repository has no `whut-eval-domain/src/test` tree and existing module-level tests are concentrated under `whut-eval-app/src/test`. Do not create a new domain test module layout as part of D-11 unless the repository-wide test organization is changed in a separate task.

Module naming note: `whut-eval-application` contains application-layer production sources, while `whut-eval-app` is the aggregate test module used by this repository for cross-module service, web, security, and persistence tests.

---

### Task 1: Query Object Contract

**Files:**
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/UnsubmittedFinalRecordQuery.java`
- Create or modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/UnsubmittedFinalRecordQueryTest.java`

- [ ] **Step 1: Write failing academic-year and pagination tests**

Create `UnsubmittedFinalRecordQueryTest`:

```java
package edu.whut.eval.app.finalrecord;

import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubmittedFinalRecordQueryTest {

    @Test
    void shouldNormalizeValidAcademicYearAndPagination() {
        UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery(" 2025-2026 ", null, null, -1, 200);
        UnsubmittedFinalRecordQuery zeroPage = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 0, 20);

        assertThat(query.getAcademicYear()).isEqualTo("2025-2026");
        assertThat(query.getPageNo()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(100);
        assertThat(query.getOffset()).isZero();
        assertThat(zeroPage.getPageNo()).isEqualTo(1);
        assertThat(zeroPage.getOffset()).isZero();
    }

    @Test
    void shouldRejectInvalidAcademicYear() {
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery(null, null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("   ", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("abc", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2027", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2026-2025", null, null, 1, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
    }

    @Test
    void shouldNormalizePageSizeLikeExistingFinalRecordPageQuery() {
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 0).getPageSize()).isEqualTo(20);
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, -1).getPageSize()).isEqualTo(20);
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 100).getPageSize()).isEqualTo(100);
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 101).getPageSize()).isEqualTo(100);
    }

    @Test
    void shouldRejectOffsetOverflow() {
        assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2026", null, null, Long.MAX_VALUE, 100))
                .isInstanceOf(ValidationException.class)
                .hasMessage("pageNo 不合法");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=UnsubmittedFinalRecordQueryTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compile failure because `UnsubmittedFinalRecordQuery` does not exist.

- [ ] **Step 3: Add failing grade/classes normalization tests**

Append to `UnsubmittedFinalRecordQueryTest`:

```java
@Test
void shouldNormalizeGradeAndClassesWithoutCaseFoldingOrCommaSplitting() {
    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery(
            "2025-2026",
            " CS2022 ",
            List.of(" CS2201 ", "", "CS2201", "Class,With,Comma", "class"),
            2,
            20
    );

    assertThat(query.getGrade()).isEqualTo("CS2022");
    assertThat(query.getClasses()).containsExactly("CS2201", "Class,With,Comma", "class");
    assertThat(query.isClassesEmpty()).isFalse();
    assertThat(query.getOffset()).isEqualTo(20);
}

@Test
void shouldTreatBlankClassesAsEmptyFilter() {
    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery(
            "2025-2026",
            " ",
            List.of(" ", ""),
            1,
            20
    );

    assertThat(query.getGrade()).isNull();
    assertThat(query.getClasses()).isEmpty();
    assertThat(query.isClassesEmpty()).isTrue();

    UnsubmittedFinalRecordQuery nullClasses = new UnsubmittedFinalRecordQuery(
            "2025-2026",
            null,
            null,
            1,
            20
    );
    assertThat(nullClasses.getClasses()).isEmpty();
    assertThat(nullClasses.isClassesEmpty()).isTrue();
}

@Test
void shouldAllowExactlyFiveHundredNormalizedClasses() {
    List<String> classes = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
        classes.add("CS" + i);
    }

    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, classes, 1, 20);

    assertThat(query.getClasses()).hasSize(500);
    assertThat(query.isClassesEmpty()).isFalse();
}

@Test
void shouldRejectMoreThanFiveHundredNormalizedClasses() {
    List<String> classes = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
        classes.add("CS" + i);
    }

    assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2026", null, classes, 1, 20))
            .isInstanceOf(ValidationException.class)
            .hasMessage("classes 不合法");
}

@Test
void shouldApplyClassLimitAfterTrimBlankAndDeduplication() {
    List<String> classes = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
        classes.add("CS2201");
    }

    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, classes, 1, 20);

    assertThat(query.getClasses()).containsExactly("CS2201");
}

@Test
void shouldRejectOverlongGradeAndClassValues() {
    String overlong = "A".repeat(257);

    assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2026", overlong, null, 1, 20))
            .isInstanceOf(ValidationException.class)
            .hasMessage("grade 不合法");
    assertThatThrownBy(() -> new UnsubmittedFinalRecordQuery("2025-2026", null, List.of(overlong), 1, 20))
            .isInstanceOf(ValidationException.class)
            .hasMessage("classes 不合法");
}
```

- [ ] **Step 4: Implement `UnsubmittedFinalRecordQuery`**

Create `UnsubmittedFinalRecordQuery`:

D-11 keeps `grade` and `classes` as exact-match filter strings without comma splitting or case folding. They are bound through MyBatis parameters and naturally produce no matches when no `org_unit.unit_code` or `org_unit.unit_name` equals the value. Match the source spec: trim, drop blanks, de-duplicate `classes` while preserving order after controller merge (`classes` values first, then `classes[]` values), then reject more than `MAX_CLASSES = 500` normalized class values. Also reject any single normalized `grade` or `classes` value longer than 256 characters.

Do not add a separate calendar-year range such as 1990-2100 in D-11. The frozen contract only requires `yyyy-yyyy` with `end = start + 1`; values that pass that structural rule but have no data naturally return empty results.

```java
package edu.whut.eval.domain.finalrecord.query;

import edu.whut.eval.common.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class UnsubmittedFinalRecordQuery {

    private static final Pattern ACADEMIC_YEAR = Pattern.compile("^\\d{4}-\\d{4}$");
    private static final int MAX_CLASSES = 500;
    private static final int MAX_FILTER_VALUE_LENGTH = 256;

    private final String academicYear;
    private final String grade;
    private final List<String> classes;
    private final long pageNo;
    private final long pageSize;
    private final long offset;

    public UnsubmittedFinalRecordQuery(String academicYear, String grade, List<String> classes, long pageNo, long pageSize) {
        this.academicYear = normalizeAcademicYear(academicYear);
        this.grade = normalizeFilterValue(grade, "grade");
        this.classes = normalizeClasses(classes);
        this.pageNo = pageNo <= 0 ? 1 : pageNo;
        this.pageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        this.offset = checkedOffset(this.pageNo, this.pageSize);
    }

    private String normalizeAcademicYear(String value) {
        if (value == null) {
            throw new ValidationException("academicYear 不合法");
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || !ACADEMIC_YEAR.matcher(trimmed).matches()) {
            throw new ValidationException("academicYear 不合法");
        }
        try {
            int start = Integer.parseInt(trimmed.substring(0, 4));
            int end = Integer.parseInt(trimmed.substring(5, 9));
            if (end != start + 1) {
                throw new ValidationException("academicYear 不合法");
            }
        } catch (NumberFormatException ex) {
            throw new ValidationException("academicYear 不合法");
        }
        return trimmed;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed;
    }

    private String normalizeFilterValue(String value, String name) {
        String trimmed = normalizeOptional(value);
        if (trimmed != null && trimmed.length() > MAX_FILTER_VALUE_LENGTH) {
            throw new ValidationException(name + " 不合法");
        }
        return trimmed;
    }

    private List<String> normalizeClasses(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = normalizeFilterValue(value, "classes");
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        if (normalized.size() > MAX_CLASSES) {
            throw new ValidationException("classes 不合法");
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private long checkedOffset(long pageNo, long pageSize) {
        try {
            return Math.multiplyExact(Math.subtractExact(pageNo, 1), pageSize);
        } catch (ArithmeticException ex) {
            throw new ValidationException("pageNo 不合法");
        }
    }

    public String getAcademicYear() { return academicYear; }
    public String getGrade() { return grade; }
    public List<String> getClasses() { return classes; }
    public boolean isClassesEmpty() { return classes.isEmpty(); }
    public long getPageNo() { return pageNo; }
    public long getPageSize() { return pageSize; }
    public long getOffset() { return offset; }
}
```

- [ ] **Step 5: Run query-object tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=UnsubmittedFinalRecordQueryTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/UnsubmittedFinalRecordQuery.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/UnsubmittedFinalRecordQueryTest.java
git commit -m "feat: add unsubmitted final record query"
```

---

### Task 2: Application Service View Mapping

**Files:**
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/UnsubmittedStudentRow.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/UnsubmittedStudentView.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordQueryApplicationService.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Append tests to `FinalRecordQueryApplicationServiceTest`. Use the existing test fixture style in that file for constructing `UserAuthorizationContext`, mocking `UserAuthorizationContextAssembler`, and verifying repository calls. Add a helper row method if the file does not already have one:

```java
private UnsubmittedStudentRow unsubmittedRow(Long studentUserId, String userNo, String userName,
                                             String grade, String className, Instant lastUpdatedAt) {
    UnsubmittedStudentRow row = new UnsubmittedStudentRow();
    row.setStudentUserId(studentUserId);
    row.setUserNo(userNo);
    row.setUserName(userName);
    row.setGrade(grade);
    row.setClassName(className);
    row.setLastUpdatedAt(lastUpdatedAt);
    return row;
}
```

Add tests:

```java
@Test
void shouldPageUnsubmittedStudentsWithFixedStatusAndStringFallbacks() {
    UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
    when(userAuthorizationContextAssembler.requiredAuthorizationContext()).thenReturn(admin);
    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
    when(finalRecordQueryRepository.pageUnsubmittedStudents(any(), same(query)))
            .thenReturn(new PageResult<>(2, List.of(
                    unsubmittedRow(1001L, "S001", "Alice", "2022级", "CS2201", Instant.parse("2026-07-12T10:15:30.123Z")),
                    unsubmittedRow(1002L, null, null, null, null, null)
            )));

    PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.records()).containsExactly(
            new UnsubmittedStudentView(1001L, "S001", "Alice", "2022级", "CS2201", "UNSUBMITTED", "2026-07-12T10:15:30.123Z"),
            new UnsubmittedStudentView(1002L, "", "", "", "", "UNSUBMITTED", "")
    );
    ArgumentCaptor<FinalRecordAccessContext> captor = ArgumentCaptor.forClass(FinalRecordAccessContext.class);
    verify(finalRecordQueryRepository).pageUnsubmittedStudents(captor.capture(), same(query));
    assertThat(captor.getValue().getPermissionCode()).isEqualTo(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
}

@Test
void shouldRenderMissingDraftLastUpdatedAtAsEmptyString() {
    UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
    when(userAuthorizationContextAssembler.requiredAuthorizationContext()).thenReturn(admin);
    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
    when(finalRecordQueryRepository.pageUnsubmittedStudents(any(), same(query)))
            .thenReturn(new PageResult<>(1, List.of(
                    unsubmittedRow(1001L, "S001", "Alice", "2022级", "CS2201", null)
            )));

    PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

    assertThat(page.records()).containsExactly(
            new UnsubmittedStudentView(1001L, "S001", "Alice", "2022级", "CS2201", "UNSUBMITTED", "")
    );
}

@Test
void shouldRenderLastUpdatedAtWithInstantStringPrecision() {
    UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
    when(userAuthorizationContextAssembler.requiredAuthorizationContext()).thenReturn(admin);
    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
    when(finalRecordQueryRepository.pageUnsubmittedStudents(any(), same(query)))
            .thenReturn(new PageResult<>(2, List.of(
                    unsubmittedRow(1001L, "S001", "Alice", "2022级", "CS2201", Instant.parse("2026-07-12T10:15:30Z")),
                    unsubmittedRow(1002L, "S002", "Bob", "2022级", "CS2201", Instant.parse("2026-07-12T10:15:30.456Z"))
            )));

    PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

    assertThat(page.records()).containsExactly(
            new UnsubmittedStudentView(1001L, "S001", "Alice", "2022级", "CS2201", "UNSUBMITTED", "2026-07-12T10:15:30Z"),
            new UnsubmittedStudentView(1002L, "S002", "Bob", "2022级", "CS2201", "UNSUBMITTED", "2026-07-12T10:15:30.456Z")
    );
}

@Test
void shouldDenyUnsubmittedListWithoutScoreViewAssigned() {
    UserAuthorizationContext admin = adminWithAuthority("other.permission");
    when(userAuthorizationContextAssembler.requiredAuthorizationContext()).thenReturn(admin);

    assertThatThrownBy(() -> service.pageUnsubmittedStudents(
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)))
            .isInstanceOf(AccessDeniedAppException.class)
            .hasMessage("当前用户无未提交最终成绩名单查询权限");
    verify(finalRecordQueryRepository, never()).pageUnsubmittedStudents(any(), any());
}

@Test
void shouldReturnEmptyUnsubmittedPage() {
    UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
    when(userAuthorizationContextAssembler.requiredAuthorizationContext()).thenReturn(admin);
    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
    when(finalRecordQueryRepository.pageUnsubmittedStudents(any(), same(query)))
            .thenReturn(new PageResult<>(0, List.of()));

    PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

    assertThat(page.total()).isZero();
    assertThat(page.records()).isEmpty();
}
```

- [ ] **Step 2: Run service test to verify it fails**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=FinalRecordQueryApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compile failure because row/view types and repository method do not exist.

- [ ] **Step 3: Add row and view DTOs**

Create `UnsubmittedStudentRow`:

```java
package edu.whut.eval.application.finalrecord.query;

import java.time.Instant;

public class UnsubmittedStudentRow {

    private Long studentUserId;
    private String userNo;
    private String userName;
    private String grade;
    private String className;
    private Instant lastUpdatedAt;

    public Long getStudentUserId() { return studentUserId; }
    public void setStudentUserId(Long studentUserId) { this.studentUserId = studentUserId; }
    public String getUserNo() { return userNo; }
    public void setUserNo(String userNo) { this.userNo = userNo; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}
```

Create `UnsubmittedStudentView`:

```java
package edu.whut.eval.application.finalrecord.query;

public record UnsubmittedStudentView(
        Long studentUserId,
        String userNo,
        String userName,
        String grade,
        String className,
        String status,
        String lastUpdatedAt
) {
}
```

- [ ] **Step 4: Extend repository contract and service**

Add to `FinalRecordQueryRepository`:

```java
PageResult<UnsubmittedStudentRow> pageUnsubmittedStudents(FinalRecordAccessContext accessContext,
                                                          UnsubmittedFinalRecordQuery query);
```

Add imports for `UnsubmittedStudentRow` and `UnsubmittedFinalRecordQuery`.

Transaction note: keep `pageUnsubmittedStudents(...)` consistent with the existing `FinalRecordQueryApplicationService` query-method style in this repository. At plan time the current query methods in this class are plain service methods without `@Transactional(readOnly = true)`. Do not add a one-off read-only transaction annotation only to D-11; if the team decides to add read-only transactions, do it as a separate service-wide consistency refactor with tests for all query methods.

In `FinalRecordQueryApplicationService`, add imports and this method:

```java
import java.time.Instant;

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

Add helpers:

```java
private UnsubmittedStudentView toUnsubmittedView(UnsubmittedStudentRow row) {
    return new UnsubmittedStudentView(row.getStudentUserId(), valueOrEmpty(row.getUserNo()),
            valueOrEmpty(row.getUserName()), valueOrEmpty(row.getGrade()), valueOrEmpty(row.getClassName()),
            "UNSUBMITTED", formatLastUpdatedAt(row.getLastUpdatedAt()));
}

private String formatLastUpdatedAt(Instant value) {
    return value == null ? "" : value.toString();
}

private String valueOrEmpty(String value) {
    return value == null ? "" : value;
}
```

`lastUpdatedAt` is a UTC `Instant` contract at the service/API boundary. The repository row must expose `Instant` values from the mapper; the application service must not reinterpret them through `LocalDateTime`, `ZoneId.systemDefault()`, or a fixed millisecond formatter. `Instant.toString()` is intentional: it emits an ISO-8601 `Z` value and preserves the actual precision of the `Instant`, so tests must compare the exact strings produced by that method rather than forcing `.SSS` milliseconds.

- [ ] **Step 5: Run query-object and service tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=UnsubmittedFinalRecordQueryTest,FinalRecordQueryApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/UnsubmittedStudentRow.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/UnsubmittedStudentView.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/service/FinalRecordQueryApplicationService.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java
git commit -m "feat: map unsubmitted final record views"
```

---

### Task 3: Repository Roster SQL

**Files:**
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/SqlPredicateFragment.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`

- [ ] **Step 1: Expand the integration-test schema**

Open the existing `MybatisPlusFinalRecordQueryRepositoryIntegrationTest` fixture. Ensure the test schema has these columns before adding D-11 cases:

```sql
CREATE TABLE iam_user (
  id BIGINT PRIMARY KEY,
  user_no VARCHAR(64) NOT NULL,
  user_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL
);

CREATE TABLE org_unit (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT,
  unit_type VARCHAR(32) NOT NULL,
  unit_code VARCHAR(64) NOT NULL,
  unit_name VARCHAR(128) NOT NULL,
  path VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL
);

CREATE TABLE org_membership (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  org_unit_id BIGINT NOT NULL,
  membership_type VARCHAR(32) NOT NULL,
  is_primary TINYINT NOT NULL,
  status VARCHAR(32) NOT NULL
);
```

Also ensure the test `final_record` schema preserves the frozen D contract needed by D-11: `status VARCHAR(32) NOT NULL`, `updated_at DATETIME NOT NULL`, and a unique key on `(student_user_id, academic_year)`. Keep all existing Minimal D columns needed by submitted/confirmed list/detail tests. If the current fixture uses simplified `iam_user`, `org_unit`, `org_membership`, or `final_record` tables, replace only the test fixture setup and update old inserts to populate the required non-null columns.

Fixture migration rules for existing tests:

- Existing `iam_user` inserts must populate non-null `user_no`, `user_name`, and `status`; D-11 must not add or depend on an `iam_user.identity` column because the frozen A-group IAM schema does not provide one.
- Existing `org_unit` college roots must set `parent_id = NULL`, `unit_type = 'COLLEGE'`, a deterministic `unit_code`, the existing `unit_name`, the existing `path`, and `status = 'ACTIVE'`.
- Existing class `org_unit` rows used by old Minimal D submitted/confirmed tests must set `unit_type = 'CLASS'`, `status = 'ACTIVE'`, preserve the old `path`, and set `unit_code` to the last path segment or another explicit deterministic fixture code. If the old helper did not create grade rows, `parent_id` may remain `NULL` for those old rows; D-11-specific `seedRoster()` must create explicit grade parents.
- D-11 `seedRoster()` must create active `COLLEGE -> GRADE -> CLASS` paths with non-null `unit_code`, `unit_name`, `path`, and `status` so grade/classes filters and sort order are deterministic.
- Existing `org_membership` inserts must populate `membership_type = 'STUDENT'`, `is_primary = 1`, and `status = 'ACTIVE'` unless the test is explicitly about inactive, non-primary, or non-student membership exclusion.
- Existing `final_record` rows for submitted/confirmed tests must keep non-null status and `updated_at`; D-11 tests must also use only one `final_record` row per `(student_user_id, academic_year)` and one of the frozen statuses `DRAFT`, `SUBMITTED`, or `CONFIRMED`.
- After changing the fixture schema and old insert helpers, immediately run the existing repository integration test class before adding D-11 SQL so schema backfills do not silently weaken Minimal D coverage:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusFinalRecordQueryRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected at this point: existing Minimal D tests still PASS, or fail only because newly planned D-11 methods/tests have not been added yet. Do not proceed if old submitted/confirmed list/detail tests regress because of fixture migration.

- [ ] **Step 2: Write failing no-record, draft, and submitted/confirmed tests**

Add repository tests with a seed helper equivalent to:

```java
private void seedRoster() {
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2002, NULL, 'COLLEGE', 'CS', '计算机学院', '/WHUT/CS', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3001, 2002, 'GRADE', 'CS2022', '计算机2022级', '/WHUT/CS/CS2022', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4001, 3001, 'CLASS', 'CS2201', '计算机2201班', '/WHUT/CS/CS2022/CS2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4002, 3001, 'CLASS', 'CS2202', '计算机2202班', '/WHUT/CS/CS2022/CS2202', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1001, 'S001', 'Alice', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1002, 'S002', 'Bob', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1003, 'S003', 'Cindy', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5001, 1001, 4001, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5002, 1002, 4001, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5003, 1003, 4002, 'STUDENT', 1, 'ACTIVE')");
}

private void insertFinalRecord(Long id, Long studentUserId, String academicYear, String status, String updatedAt) {
    jdbcTemplate.update("""
            INSERT INTO final_record (id, student_user_id, academic_year, status,
                                      moral_total, intellectual_total, physical_total, labor_total, grand_total,
                                      submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0.00, 0.00, 0.00, 0.00, 0.00,
                    CASE WHEN ? = 'SUBMITTED' THEN CAST(? AS DATETIME) ELSE NULL END,
                    CASE WHEN ? = 'CONFIRMED' THEN CAST(? AS DATETIME) ELSE NULL END,
                    NULL, 1, CAST(? AS DATETIME), CAST(? AS DATETIME))
            """, id, studentUserId, academicYear, status,
            status, updatedAt, status, updatedAt, updatedAt, updatedAt);
}

private UnsubmittedStudentRow findRow(PageResult<UnsubmittedStudentRow> page, Long studentUserId) {
    return page.records().stream()
            .filter(row -> studentUserId.equals(row.getStudentUserId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing unsubmitted student row: " + studentUserId));
}
```

Add tests:

```java
@Test
void shouldIncludeCurrentRosterStudentsWithNoFinalRecord() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
    assertThat(page.records()).allSatisfy(row -> assertThat(row.getLastUpdatedAt()).isNull());
}

@Test
void shouldKeepDraftStudentsUnsubmittedAndExposeDraftUpdatedAt() {
    seedRoster();
    insertFinalRecord(11L, 1001L, "2025-2026", "DRAFT", "2026-07-12 10:15:30.123");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    UnsubmittedStudentRow alice = findRow(page, 1001L);
    assertThat(page.total()).isEqualTo(3);
    assertThat(alice.getLastUpdatedAt()).isEqualTo(Instant.parse("2026-07-12T10:15:30.123Z"));
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .contains(1001L);
    assertThat(page.records()).filteredOn(row -> row.getStudentUserId() == 1001L)
            .hasSize(1);
}

@Test
void shouldKeepDraftStudentsUnsubmittedWhenFilteredByClass() {
    seedRoster();
    insertFinalRecord(13L, 1001L, "2025-2026", "DRAFT", "2026-07-12 11:15:30.456");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2201"), 1, 20)
    );

    UnsubmittedStudentRow alice = findRow(page, 1001L);
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L);
    assertThat(alice.getLastUpdatedAt()).isEqualTo(Instant.parse("2026-07-12T11:15:30.456Z"));
}

@Test
void shouldExcludeSubmittedAndConfirmedStudents() {
    seedRoster();
    insertFinalRecord(21L, 1001L, "2025-2026", "SUBMITTED", "2026-07-12 10:15:30");
    insertFinalRecord(22L, 1002L, "2025-2026", "CONFIRMED", "2026-07-12 10:15:30");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
    assertThat(findRow(page, 1003L).getLastUpdatedAt()).isNull();
}

@Test
void shouldIsolateSubmittedConfirmedAndDraftRecordsByAcademicYear() {
    seedRoster();
    insertFinalRecord(25L, 1001L, "2024-2025", "SUBMITTED", "2026-07-12 10:15:30");
    insertFinalRecord(26L, 1002L, "2024-2025", "CONFIRMED", "2026-07-12 10:15:30");
    insertFinalRecord(27L, 1003L, "2024-2025", "DRAFT", "2026-07-12 12:15:30");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
    assertThat(page.records()).allSatisfy(row -> assertThat(row.getLastUpdatedAt()).isNull());
}

@Test
void shouldKeepCurrentYearDraftVisibleWhenOtherYearSubmittedExists() {
    seedRoster();
    insertFinalRecord(28L, 1001L, "2024-2025", "SUBMITTED", "2026-07-12 10:15:30");
    insertFinalRecord(29L, 1001L, "2025-2026", "DRAFT", "2026-07-12 13:15:30");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .contains(1001L);
    assertThat(findRow(page, 1001L).getLastUpdatedAt())
            .isEqualTo(Instant.parse("2026-07-12T13:15:30Z"));
}

@Test
void shouldExcludeCurrentYearSubmittedStudentsEvenWhenPreviousYearDraftExists() {
    seedRoster();
    insertFinalRecord(24L, 1001L, "2024-2025", "DRAFT", "2026-07-12 10:15:30");
    insertFinalRecord(25L, 1001L, "2025-2026", "SUBMITTED", "2026-07-12 11:15:30");
    insertFinalRecord(26L, 1002L, "2024-2025", "DRAFT", "2026-07-12 10:15:30");
    insertFinalRecord(27L, 1002L, "2025-2026", "CONFIRMED", "2026-07-12 11:15:30");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
}

@Test
void shouldExcludeInactiveUsersInactiveMembershipsAndNonPrimaryMemberships() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1004, 'S004', 'InactiveUser', 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1005, 'S005', 'InactiveMembership', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1006, 'S006', 'NonPrimary', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1008, 'T008', 'TeacherMembership', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5004, 1004, 4001, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4001, 'STUDENT', 1, 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4001, 'STUDENT', 0, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5008, 1008, 4001, 'TEACHER', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
}

@Test
void shouldExcludeInactiveClassOrgUnitsAndNonClassOrgUnits() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4014, 3001, 'CLASS', 'CS2299', '失效班级', '/WHUT/CS/CS2022/CS2299', 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4015, 3001, 'GRADE', 'NOT_CLASS', '非班级组织', '/WHUT/CS/CS2022/NOT_CLASS', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1014, 'S014', 'InactiveClass', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1015, 'S015', 'NonClassOrg', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5014, 1014, 4014, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5015, 1015, 4015, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L)
            .doesNotContain(1014L, 1015L);
}

@Test
void shouldKeepClassWithoutActiveGradeWhenGradeFilterAbsentAndExcludeWhenGradeRequested() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3002, 2002, 'GRADE', 'CS2023', '计算机2023级', '/WHUT/CS/CS2023', 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4004, 3002, 'CLASS', 'CS2301', '计算机2301班', '/WHUT/CS/CS2023/CS2301', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1004, 'S004', 'NoGrade', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5004, 1004, 4004, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> withoutGradeFilter = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(withoutGradeFilter.total()).isEqualTo(4);
    assertThat(withoutGradeFilter.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L, 1004L);
    assertThat(findRow(withoutGradeFilter, 1004L).getGrade()).isNull();

    PageResult<UnsubmittedStudentRow> withMatchingGradeFilter = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", null, 1, 20)
    );
    PageResult<UnsubmittedStudentRow> withInactiveGradeFilter = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2023", null, 1, 20)
    );

    assertThat(withMatchingGradeFilter.total()).isEqualTo(3);
    assertThat(withMatchingGradeFilter.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
    assertThat(withInactiveGradeFilter.total()).isZero();
    assertThat(withInactiveGradeFilter.records()).isEmpty();
}

@Test
void shouldReturnEmptyWhenGradeFilterDoesNotMatchAnyActiveVisibleGrade() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2099", null, 1, 20)
    );

    assertThat(page.total()).isZero();
    assertThat(page.records()).isEmpty();
}
```

Use helpers already present in the integration test for creating authorization contexts. If missing, create `accessContextWithOrgSubtree(Long orgUnitId)`, `accessContextWithOrgSubtrees(Long... orgUnitIds)`, `accessContextWithOrgUnit(Long orgUnitId)`, `accessContextWithOrgUnits(Long... orgUnitIds)`, `accessContextWithAllScope()`, `accessContextWithUnsupportedCategoryOnly()`, `accessContextWithEmptyGrantedScopes()`, `accessContextWithoutScoreViewAssigned()`, `accessContextWithOrgUnitAndOrgSubtree(Long orgUnitId, Long orgSubtreeRootId)`, and `accessContextWithOrgSubtreeAndUnsupportedCategory(Long orgSubtreeRootId)` by following the existing `AuthorizationScope` test construction style. The varargs helpers must produce one scope rule per provided id while preserving input order; they must not collapse same-type scope rules before the production `rosterScopeFragment(...)` code sees them.

- [ ] **Step 3: Write failing scope and filter tests**

Add focused tests:

```java
@Test
void shouldResolveOrgSubtreeUsingRealOrgPath() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2999, NULL, 'COLLEGE', 'CS2', '相似学院', '/WHUT/CS2', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4999, 2999, 'CLASS', 'CS2X', '相似班', '/WHUT/CS2/CS2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1099, 'S099', 'Similar', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5099, 1099, 4999, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
}

@Test
void shouldApplyOrgUnitAsExactClassOnly() {
    seedRoster();

    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgUnit(4001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)).records())
            .extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L);
    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgUnit(3001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)).records())
            .isEmpty();
}

@Test
void shouldApplyGradeAndClassesFiltersTogetherWithCaseSensitiveExactCodeOrNameMatches() {
    seedRoster();

    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", List.of("CS2201"), 1, 20)).records())
            .extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L);
    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "cs2022", List.of("cs2201"), 1, 20)).records())
            .isEmpty();
    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "计算机2022级", List.of("计算机2202班"), 1, 20)).records())
            .extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "计算机", null, 1, 20)).records())
            .as("grade must not use contains matching")
            .isEmpty();
    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS22"), 1, 20)).records())
            .as("classes must not use prefix matching")
            .isEmpty();
    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("2201"), 1, 20)).records())
            .as("classes must not use contains matching")
            .isEmpty();
}

@Test
void shouldUnionCodeAndNameMatchesWithoutDuplicatingStudents() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4005, 3001, 'CLASS', 'CS2205', 'CS2201', '/WHUT/CS/CS2022/CS2205', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1005, 'S005', 'NameMatchesCode', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4005, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> crossUnit = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2201"), 1, 20)
    );

    assertThat(crossUnit.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1005L);
    assertThat(crossUnit.total()).isEqualTo(3);

    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4006, 3001, 'CLASS', 'DUP2201', 'DUP2201', '/WHUT/CS/CS2022/DUP2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1006, 'S006', 'SameUnitBothSides', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4006, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> sameUnit = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("DUP2201"), 1, 20)
    );

    assertThat(sameUnit.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1006L);
    assertThat(sameUnit.total()).isEqualTo(1);
}

@Test
void shouldDeduplicateSameStudentWhenClassCodeAndNameMatchDifferentVisibleClasses() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4007, 3001, 'CLASS', 'DUAL_MATCH', '代码命中班', '/WHUT/CS/CS2022/DUAL_CODE', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4008, 3001, 'CLASS', 'OTHER_MATCH', 'DUAL_MATCH', '/WHUT/CS/CS2022/DUAL_NAME', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1007, 'S007', 'DualMatch', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1007, 4008, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (6000, 1007, 4007, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("DUAL_MATCH"), 1, 20)
    );

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1007L);
    assertThat(page.records()).filteredOn(row -> row.getStudentUserId() == 1007L)
            .hasSize(1);
    assertThat(findRow(page, 1007L).getClassName()).isEqualTo("DUAL_MATCH");
}

@Test
void shouldReturnEmptyPageForUnsupportedScopeOnly() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithUnsupportedCategoryOnly(),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isZero();
    assertThat(page.records()).isEmpty();
}

@Test
void shouldReturnEmptyPageForGrantedButEmptyScopes() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithEmptyGrantedScopes(),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isZero();
    assertThat(page.records()).isEmpty();
}

@Test
void shouldReturnEmptyPageForDeniedScopePredicateWhenRepositoryIsCalledDirectly() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithoutScoreViewAssigned(),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isZero();
    assertThat(page.records()).isEmpty();
}

@Test
void shouldReturnAllCurrentRosterRowsForAllScope() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithAllScope(),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
    assertThat(page.records()).allSatisfy(row -> assertThat(row.getLastUpdatedAt()).isNull());
}

@Test
void shouldUnionOrgUnitAndOrgSubtreeWithoutDuplicatingStudents() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgUnitAndOrgSubtree(4001L, 2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
}

@Test
void shouldUnionSameClassOrgUnitAndOrgSubtreeWithoutDuplicatingStudents() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgUnitAndOrgSubtree(4001L, 4001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L);
}

@Test
void shouldUnionMultipleOrgSubtreeRootsWithoutDuplicatingStudents() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3002, 2002, 'GRADE', 'CS2023', '计算机2023级', '/WHUT/CS/CS2023', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4003, 3002, 'CLASS', 'CS2301', '计算机2301班', '/WHUT/CS/CS2023/CS2301', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1004, 'S004', 'Dora', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5004, 1004, 4003, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> disjointRoots = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtrees(3001L, 3002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    PageResult<UnsubmittedStudentRow> overlappingRoots = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtrees(2002L, 3001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(disjointRoots.total()).isEqualTo(4);
    assertThat(disjointRoots.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L, 1004L);
    assertThat(overlappingRoots.total()).isEqualTo(4);
    assertThat(overlappingRoots.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L, 1004L)
            .doesNotHaveDuplicates();
}

@Test
void shouldUnionMultipleOrgUnitRulesWithoutDuplicatingStudents() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> disjointClasses = repository.pageUnsubmittedStudents(
            accessContextWithOrgUnits(4001L, 4002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    PageResult<UnsubmittedStudentRow> repeatedClass = repository.pageUnsubmittedStudents(
            accessContextWithOrgUnits(4001L, 4001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(disjointClasses.total()).isEqualTo(3);
    assertThat(disjointClasses.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
    assertThat(repeatedClass.total()).isEqualTo(2);
    assertThat(repeatedClass.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L)
            .doesNotHaveDuplicates();
}

@Test
void shouldIgnoreUnsupportedFragmentsWhenSupportedOrgSubtreeExists() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtreeAndUnsupportedCategory(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
}

@Test
void shouldAllowTopLevelOrgSubtreeRootPath() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (1000, NULL, 'SCHOOL', 'WHUT', '武汉理工大学', '/WHUT', 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(1000L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
}

@Test
void shouldAllowOrgSubtreeRootAtClassLevelOnlyForThatClass() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(4001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L)
            .doesNotContain(1003L);
}

@Test
void shouldAllowOrgSubtreeRootAtGradeLevelOnlyForThatGrade() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3002, 2002, 'GRADE', 'CS2023', '计算机2023级', '/WHUT/CS/CS2023', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4003, 3002, 'CLASS', 'CS2301', '计算机2301班', '/WHUT/CS/CS2023/CS2301', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1004, 'S004', 'Dora', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5004, 1004, 4003, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(3001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L)
            .doesNotContain(1004L);
}

@Test
void shouldRejectInactiveOrgSubtreeRootEvenWhenRootPathAndChildrenAreValid() {
    seedRoster();
    jdbcTemplate.update("UPDATE org_unit SET status = 'INACTIVE' WHERE id = 2002");
    jdbcTemplate.update("UPDATE org_unit SET status = 'INACTIVE' WHERE id = 3001");
    jdbcTemplate.update("UPDATE org_unit SET status = 'INACTIVE' WHERE id = 4001");

    PageResult<UnsubmittedStudentRow> inactiveCollegeRoot = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    PageResult<UnsubmittedStudentRow> inactiveGradeRoot = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(3001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    PageResult<UnsubmittedStudentRow> inactiveClassRoot = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(4001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(inactiveCollegeRoot.total()).isZero();
    assertThat(inactiveCollegeRoot.records()).isEmpty();
    assertThat(inactiveGradeRoot.total()).isZero();
    assertThat(inactiveGradeRoot.records()).isEmpty();
    assertThat(inactiveClassRoot.total()).isZero();
    assertThat(inactiveClassRoot.records()).isEmpty();
}

@Test
void shouldRejectMalformedOrgSubtreeRootAndClassPaths() {
    seedRoster();

    jdbcTemplate.update("UPDATE org_unit SET path = '' WHERE id = 2002");
    PageResult<UnsubmittedStudentRow> blankRootPath = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS' WHERE id = 2002");

    jdbcTemplate.update("UPDATE org_unit SET path = 'WHUT/CS' WHERE id = 2002");
    PageResult<UnsubmittedStudentRow> missingLeadingSlashRootPath = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS' WHERE id = 2002");

    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS/' WHERE id = 2002");
    PageResult<UnsubmittedStudentRow> trailingSlashRootPath = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS' WHERE id = 2002");

    jdbcTemplate.update("UPDATE org_unit SET path = '' WHERE id = 4001");
    PageResult<UnsubmittedStudentRow> blankClassPath = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS/CS2022/CS2201' WHERE id = 4001");

    jdbcTemplate.update("UPDATE org_unit SET path = 'WHUT/CS/CS2022/CS2201' WHERE id = 4001");
    PageResult<UnsubmittedStudentRow> missingLeadingSlashClassPath = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS/CS2022/CS2201' WHERE id = 4001");

    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS/CS2022/CS2201/' WHERE id = 4001");
    PageResult<UnsubmittedStudentRow> trailingSlashClassPath = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS/CS2022/CS2201' WHERE id = 4001");

    assertThat(blankRootPath.records()).isEmpty();
    assertThat(missingLeadingSlashRootPath.records()).isEmpty();
    assertThat(trailingSlashRootPath.records()).isEmpty();
    assertThat(blankClassPath.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
    assertThat(missingLeadingSlashClassPath.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
    assertThat(trailingSlashClassPath.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
}

@Test
void shouldRejectOrgSubtreePathsContainingLikeWildcards() {
    seedRoster();

    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/C_%' WHERE id = 2002");
    PageResult<UnsubmittedStudentRow> wildcardRootPath = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS' WHERE id = 2002");

    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS/CS2022/CS_201' WHERE id = 4001");
    PageResult<UnsubmittedStudentRow> underscoreClassPath = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    jdbcTemplate.update("UPDATE org_unit SET path = '/WHUT/CS/CS2022/CS2201' WHERE id = 4001");

    assertThat(wildcardRootPath.records()).isEmpty();
    assertThat(underscoreClassPath.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
}

```

- [ ] **Step 4: Write mandatory scope-parity, duplicate-membership, and pagination tests**

D-11 has a private `rosterScopeFragment(...)` translator. This is a mandatory verification point, not conditional on shared scope code changing: add tests proving D-11 roster visibility stays aligned with the existing whole-record final-record scope semantics for denied permission, `ALLOW_ALL`, `ORG_UNIT`, `ORG_SUBTREE`, unsupported/empty scopes, and similar-prefix paths. Use the same `FinalRecordAccessContext` helpers to query both the existing submitted/confirmed admin list and the new unsubmitted roster path against equivalent student/class fixtures.

Add the scope-parity test before the duplicate-membership tests:

```java
@Test
void shouldKeepD11PrivateScopeTranslatorAlignedWithSubmittedWholeRecordScopeSemantics() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2999, NULL, 'COLLEGE', 'CS2', '相似学院', '/WHUT/CS2', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4999, 2999, 'CLASS', 'CS2X', '相似班', '/WHUT/CS2/CS2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1099, 'S099', 'Similar', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5099, 1099, 4999, 'STUDENT', 1, 'ACTIVE')");
    insertFinalRecord(9101L, 1001L, "2024-2025", "SUBMITTED", "2026-07-12 10:00:00");
    insertFinalRecord(9102L, 1002L, "2024-2025", "SUBMITTED", "2026-07-12 10:01:00");
    insertFinalRecord(9103L, 1003L, "2024-2025", "SUBMITTED", "2026-07-12 10:02:00");
    insertFinalRecord(9199L, 1099L, "2024-2025", "SUBMITTED", "2026-07-12 10:03:00");

    assertScopeParity(accessContextWithAllScope());
    assertScopeParity(accessContextWithOrgUnit(4001L));
    assertScopeParity(accessContextWithOrgSubtree(2002L));
    assertScopeParity(accessContextWithOrgSubtree(3001L));
    assertScopeParity(accessContextWithOrgSubtree(4001L));
    assertScopeParity(accessContextWithOrgUnitAndOrgSubtree(4001L, 2002L));
    assertScopeParity(accessContextWithUnsupportedCategoryOnly());
    assertScopeParity(accessContextWithEmptyGrantedScopes());
    assertScopeParity(accessContextWithoutScoreViewAssigned());
}

private void assertScopeParity(FinalRecordAccessContext accessContext) {
    List<Long> submittedVisible = repository.pageAdminFinalRecords(
                    accessContext,
                    new FinalRecordPageQuery("2024-2025", null, null, null, 1, 100))
            .records().stream()
            .map(FinalRecordQueryRow::getStudentUserId)
            .toList();
    List<Long> d11Visible = repository.pageUnsubmittedStudents(
                    accessContext,
                    new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 100))
            .records().stream()
            .map(UnsubmittedStudentRow::getStudentUserId)
            .toList();

    assertThat(d11Visible).containsExactlyInAnyOrderElementsOf(submittedVisible);
}
```

Add tests:

```java
@Test
void shouldChooseLowestNumericVisibleMembershipIdAfterScopeAndFilters() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4003, 3001, 'CLASS', 'CS2200', '计算机2200班', '/WHUT/CS/CS2022/CS2200', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1001, 4003, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    UnsubmittedStudentRow alice = findRow(page, 1001L);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
    assertThat(page.records()).filteredOn(row -> row.getStudentUserId() == 1001L)
            .hasSize(1);
    assertThat(alice.getClassName()).isEqualTo("计算机2200班");
    assertThat(alice.getGrade()).isEqualTo("计算机2022级");
}

@Test
void shouldDisplayGradeAndClassFromSameLowestVisibleMembership() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3002, 2002, 'GRADE', 'CS2023', '计算机2023级', '/WHUT/CS/CS2023', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4004, 3002, 'CLASS', 'CS2301', '计算机2301班', '/WHUT/CS/CS2023/CS2301', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4005, 3001, 'CLASS', 'CS2205', '计算机2205班', '/WHUT/CS/CS2022/CS2205', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1001, 4004, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (6000, 1001, 4005, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    UnsubmittedStudentRow alice = findRow(page, 1001L);
    assertThat(page.records()).filteredOn(row -> row.getStudentUserId() == 1001L)
            .hasSize(1);
    assertThat(alice.getGrade()).isEqualTo("计算机2023级");
    assertThat(alice.getClassName()).isEqualTo("计算机2301班");
}

@Test
void shouldNotBorrowGradeFromHigherVisibleMembershipWhenLowestVisibleMembershipHasNoActiveGrade() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3002, 2002, 'GRADE', 'CS2023', '计算机2023级', '/WHUT/CS/CS2023', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3003, 2002, 'GRADE', 'CS2024', '计算机2024级', '/WHUT/CS/CS2024', 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4004, 3003, 'CLASS', 'CS2401', '计算机2401班', '/WHUT/CS/CS2024/CS2401', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4005, 3002, 'CLASS', 'CS2301', '计算机2301班', '/WHUT/CS/CS2023/CS2301', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1001, 4004, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (6000, 1001, 4005, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    UnsubmittedStudentRow alice = findRow(page, 1001L);
    assertThat(page.records()).filteredOn(row -> row.getStudentUserId() == 1001L)
            .hasSize(1);
    assertThat(alice.getClassName()).isEqualTo("计算机2401班");
    assertThat(alice.getGrade()).isNull();
}

@Test
void shouldIgnoreLowerMembershipIdOutsideScopeBeforePickingVisibleMembership() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2998, NULL, 'COLLEGE', 'MATH', '数学学院', '/WHUT/MATH', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4998, 2998, 'CLASS', 'MATH2201', '数学2201班', '/WHUT/MATH/MATH2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1001, 4998, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (6000, 1001, 4002, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2202"), 1, 20)
    );

    UnsubmittedStudentRow alice = findRow(page, 1001L);
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1003L);
    assertThat(alice.getClassName()).isEqualTo("计算机2202班");
}

@Test
void shouldKeepCountAndRowsConsistentWhenFiltersLeaveMultipleVisibleMembershipsForOneStudent() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4003, 3001, 'CLASS', 'CS2200', '计算机2200班', '/WHUT/CS/CS2022/CS2200', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4004, 3001, 'CLASS', 'CS2203', '计算机2203班', '/WHUT/CS/CS2022/CS2203', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1001, 4003, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (6000, 1001, 4002, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (7000, 1001, 4004, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", List.of("CS2200", "CS2203"), 1, 20)
    );

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .doesNotHaveDuplicates();
    assertThat(findRow(page, 1001L).getClassName()).isEqualTo("计算机2200班");
}

@Test
void shouldApplyGradeFilterBeforeCollapsingCrossGradeMembershipsForOneStudent() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3002, 2002, 'GRADE', 'CS2023', '计算机2023级', '/WHUT/CS/CS2023', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4004, 3002, 'CLASS', 'CS2301', '计算机2301班', '/WHUT/CS/CS2023/CS2301', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1001, 4004, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> noGradeFilter = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );
    PageResult<UnsubmittedStudentRow> cs2023Only = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2023", null, 1, 20)
    );
    PageResult<UnsubmittedStudentRow> cs2022Only = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", null, 1, 20)
    );

    assertThat(noGradeFilter.total()).isEqualTo(3);
    assertThat(findRow(noGradeFilter, 1001L).getGrade()).isEqualTo("计算机2023级");
    assertThat(findRow(noGradeFilter, 1001L).getClassName()).isEqualTo("计算机2301班");
    assertThat(cs2023Only.total()).isEqualTo(1);
    assertThat(cs2023Only.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L);
    assertThat(findRow(cs2023Only, 1001L).getGrade()).isEqualTo("计算机2023级");
    assertThat(findRow(cs2023Only, 1001L).getClassName()).isEqualTo("计算机2301班");
    assertThat(cs2022Only.total()).isEqualTo(3);
    assertThat(findRow(cs2022Only, 1001L).getGrade()).isEqualTo("计算机2022级");
    assertThat(findRow(cs2022Only, 1001L).getClassName()).isEqualTo("计算机2201班");
}

@Test
void shouldSelectSameLowestVisibleMembershipRegardlessOfClassesFilterOrder() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4003, 3001, 'CLASS', 'CS2200', '计算机2200班', '/WHUT/CS/CS2022/CS2200', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4004, 3001, 'CLASS', 'CS2203', '计算机2203班', '/WHUT/CS/CS2022/CS2203', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1001, 4003, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (7000, 1001, 4004, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> forward = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", List.of("CS2200", "CS2203"), 1, 20)
    );
    PageResult<UnsubmittedStudentRow> reverse = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", List.of("CS2203", "CS2200"), 1, 20)
    );

    assertThat(reverse.total()).isEqualTo(forward.total());
    assertThat(reverse.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactlyElementsOf(forward.records().stream().map(UnsubmittedStudentRow::getStudentUserId).toList());
    assertThat(findRow(forward, 1001L).getClassName()).isEqualTo("计算机2200班");
    assertThat(findRow(reverse, 1001L).getClassName()).isEqualTo("计算机2200班");
    assertThat(findRow(reverse, 1001L).getGrade()).isEqualTo(findRow(forward, 1001L).getGrade());
}

@Test
void shouldKeepCountAndPagedRowsConsistentWhenManyVisibleMembershipsCollapseToOneStudent() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4003, 3001, 'CLASS', 'CS2200', '计算机2200班', '/WHUT/CS/CS2022/CS2200', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4004, 3001, 'CLASS', 'CS2203', '计算机2203班', '/WHUT/CS/CS2022/CS2203', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (4000, 1001, 4003, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (6000, 1001, 4002, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (7000, 1001, 4004, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> firstPage = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", List.of("CS2200", "CS2203"), 1, 1)
    );
    PageResult<UnsubmittedStudentRow> secondPage = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", List.of("CS2200", "CS2203"), 2, 1)
    );

    assertThat(firstPage.total()).isEqualTo(1);
    assertThat(firstPage.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L);
    assertThat(secondPage.total()).isEqualTo(1);
    assertThat(secondPage.records()).isEmpty();
}

@Test
void shouldKeepCountAndSelectAlignedAcrossEligibilityMatrix() {
    seedRoster();
    insertFinalRecord(31L, 1002L, "2025-2026", "DRAFT", "2026-07-12 10:15:30");

    assertCountMatchesFullRecords(accessContextWithOrgUnit(4001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 100), 2);
    assertCountMatchesFullRecords(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 100), 3);
    assertCountMatchesFullRecords(accessContextWithAllScope(),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 100), 3);
    assertCountMatchesFullRecords(accessContextWithUnsupportedCategoryOnly(),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 100), 0);
    assertCountMatchesFullRecords(accessContextWithEmptyGrantedScopes(),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 100), 0);
    assertCountMatchesFullRecords(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", null, 1, 100), 3);
    assertCountMatchesFullRecords(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2201"), 1, 100), 2);
    assertCountMatchesFullRecords(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2022", List.of("CS2201"), 1, 100), 2);
}

private void assertCountMatchesFullRecords(FinalRecordAccessContext accessContext,
                                           UnsubmittedFinalRecordQuery query,
                                           long expectedTotal) {
    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(accessContext, query);
    assertThat(page.total()).isEqualTo(expectedTotal);
    assertThat(page.records()).hasSize(Math.toIntExact(expectedTotal));
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId).doesNotHaveDuplicates();
}

@Test
void shouldReturnEmptyPageWhenCountIsZeroAndFiltersCannotMatchVisibleRows() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "CS2099", List.of("NO_SUCH_CLASS"), 1, 20)
    );

    assertThat(page.total()).isZero();
    assertThat(page.records()).isEmpty();
}

@Test
void shouldPageInStableOrderWithoutDuplicatingStudents() {
    seedRoster();

    PageResult<UnsubmittedStudentRow> first = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 2)
    );
    PageResult<UnsubmittedStudentRow> second = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 2, 2)
    );
    PageResult<UnsubmittedStudentRow> outOfRange = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 99, 2)
    );

    assertThat(first.total()).isEqualTo(3);
    assertThat(second.total()).isEqualTo(3);
    assertThat(first.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L);
    assertThat(second.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
    assertThat(outOfRange.total()).isEqualTo(3);
    assertThat(outOfRange.records()).isEmpty();
}

@Test
void shouldUseUserIdAsFinalTieBreakerWhenStudentNumbersAreDuplicated() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1006, 'S099', 'DuplicateNoLow', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1007, 'S099', 'DuplicateNoHigh', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4001, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5007, 1007, 4001, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2201"), 1, 20)
    );
    PageResult<UnsubmittedStudentRow> firstPage = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2201"), 1, 3)
    );
    PageResult<UnsubmittedStudentRow> secondPage = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2201"), 2, 3)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1006L, 1007L);
    assertThat(firstPage.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1006L);
    assertThat(secondPage.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1007L);
}

@Test
void shouldSortByGradeClassStudentNumberAndUserIdWithStableTieBreakers() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3005, 2002, 'GRADE', 'CS2021', '计算机2021级', '/WHUT/CS/CS2021', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4005, 3005, 'CLASS', 'CS2101', '计算机2101班', '/WHUT/CS/CS2021/CS2101', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1005, 'S005', 'EarlierGrade', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1006, 'S099', 'DuplicateNoLow', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1007, 'S099', 'DuplicateNoHigh', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4005, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4001, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5007, 1007, 4001, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1005L, 1001L, 1002L, 1006L, 1007L, 1003L);
}

@Test
void shouldSortRowsWithActiveGradeBeforeRowsWithoutActiveGrade() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3002, 2002, 'GRADE', 'CS2023', '计算机2023级', '/WHUT/CS/CS2023', 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4003, 3002, 'CLASS', 'CS2301', '计算机2301班', '/WHUT/CS/CS2023/CS2301', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1005, 'S005', 'NoGradeLowNumber', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, status) VALUES (1006, 'S006', 'GradeAfterNoGradeNumber', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4003, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4001, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1006L, 1003L, 1005L);
    assertThat(page.records().subList(0, 4)).allSatisfy(row -> assertThat(row.getGrade()).isNotNull());
    assertThat(page.records().subList(4, 5)).allSatisfy(row -> assertThat(row.getGrade()).isNull());
}
```

- [ ] **Step 5: Run repository tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusFinalRecordQueryRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compile failure because D-11 repository/mapper methods do not exist, or assertion failures while SQL is still missing.

- [ ] **Step 6: Add mapper methods**

Add imports in `FinalRecordQueryMapper`:

```java
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentRow;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
```

Add methods:

```java
@SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildCountUnsubmittedStudents")
long countUnsubmittedStudents(@Param("scopeFragment") SqlPredicateFragment scopeFragment,
                              @Param("query") UnsubmittedFinalRecordQuery query);

@SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildSelectUnsubmittedStudents")
List<UnsubmittedStudentRow> selectUnsubmittedStudents(@Param("scopeFragment") SqlPredicateFragment scopeFragment,
                                                      @Param("query") UnsubmittedFinalRecordQuery query);
```

Add an import for `SqlPredicateFragment`. D-11 mapper methods must accept the typed scope fragment rather than separate raw `String`/`Map` parameters so callers cannot casually supply a raw SQL expression plus arbitrary parameter map. If the existing `FinalRecordQueryMapper` is already registered through `@Mapper` or repository-level `@MapperScan`, append these methods to the existing mapper interface and do not add duplicate mapper registration.

- [ ] **Step 7: Add D-11 SQL provider methods**

In `FinalRecordQuerySqlProvider`, add:

Count and select SQL must keep the same eligibility predicates: active user, active primary STUDENT membership, active class, scope expression, grade/classes filters, and the submitted/confirmed `NOT EXISTS` exclusion. This is a hard invariant: the count query's grouped visible-student subquery and the select query's inner `visible` subquery must keep equivalent FROM/JOIN/WHERE eligibility semantics for scope, grade, classes, final-record exclusion, membership status, user status, and student membership type. They may use different projection and grouping wrapper layers, as shown below, because count only needs one row per visible student while select first picks the lowest visible membership id. Do not add display-only joins, HAVING clauses, ordering, or row-shaping predicates to either eligibility subquery. If you add, delete, or change one eligibility predicate in either SQL, update the other SQL in the same task and add or adjust a count/select alignment assertion. Prefer extracting a small helper that renders the shared eligibility predicates from a small alias descriptor if the final implementation starts to drift from the snippets below.

The select `ORDER BY` puts rows with an active grade parent before rows whose selected class has no active grade parent, then orders by active grade code, class code, student number, and `u.id ASC`; the final user-id tie-breaker keeps pagination deterministic when two active students share the same student number. The outer `class_ou` / `grade_ou` type and status predicates duplicate inner eligibility checks defensively so future edits to the inner visible subquery cannot silently display inactive or wrong-type org units.

For `scopeExpression`, the placeholder `__D11_CLASS_ALIAS__` always represents the current visible class `org_unit` alias. In `buildCountUnsubmittedStudents(...)` it is replaced only with `class_ou`. In `buildSelectUnsubmittedStudents(...)` it is replaced only with the inner visible subquery alias `class_ou1`. It must never point at `grade_ou`, `grade_ou1`, the outer display `class_ou`, or caller-provided SQL. Grade and class request filters remain separate fixed expressions over `grade_ou1`/`class_ou1` inside select and `grade_ou`/`class_ou` inside count.

```java
public String buildCountUnsubmittedStudents(Map<String, Object> params) {
    String scopeExpression = scopeExpression(params, "class_ou");
    String classPredicate = classPredicate(params, "class_ou");
    return """
            SELECT COUNT(1)
            FROM (
              SELECT u.id AS student_user_id
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
               AND grade_ou.unit_type = 'GRADE'
               AND grade_ou.status = 'ACTIVE'
              WHERE u.status = 'ACTIVE'
                AND (%s)
                AND (#{query.grade} IS NULL OR %s OR %s)
                AND (%s)
                AND NOT EXISTS (
                  SELECT 1
                  FROM final_record submitted_fr
                  WHERE submitted_fr.student_user_id = u.id
                    AND submitted_fr.academic_year = #{query.academicYear}
                    AND submitted_fr.status IN ('SUBMITTED', 'CONFIRMED')
                )
              GROUP BY u.id
            ) visible_students
            """.formatted(scopeExpression,
                    caseSensitiveEquals("grade_ou.unit_code", "#{query.grade}"),
                    caseSensitiveEquals("grade_ou.unit_name", "#{query.grade}"),
                    classPredicate);
}

public String buildSelectUnsubmittedStudents(Map<String, Object> params) {
    String scopeExpression = scopeExpression(params, "class_ou1");
    String classPredicate = classPredicate(params, "class_ou1");
    return """
            SELECT
              u.id AS student_user_id,
              u.user_no AS user_no,
              u.user_name AS user_name,
              grade_ou.unit_name AS grade,
              class_ou.unit_name AS class_name,
              draft_fr.last_updated_at AS last_updated_at
            FROM (
              SELECT visible.user_id, MIN(visible.membership_id) AS membership_id
              FROM (
                SELECT om1.user_id, om1.id AS membership_id
                FROM org_membership om1
                JOIN iam_user u1
                  ON u1.id = om1.user_id
                 AND u1.status = 'ACTIVE'
                JOIN org_unit class_ou1
                  ON class_ou1.id = om1.org_unit_id
                 AND class_ou1.unit_type = 'CLASS'
                 AND class_ou1.status = 'ACTIVE'
                LEFT JOIN org_unit grade_ou1
                  ON grade_ou1.id = class_ou1.parent_id
                 AND grade_ou1.unit_type = 'GRADE'
                 AND grade_ou1.status = 'ACTIVE'
                WHERE om1.membership_type = 'STUDENT'
                  AND om1.is_primary = 1
                  AND om1.status = 'ACTIVE'
                  AND (%s)
                  AND (#{query.grade} IS NULL OR %s OR %s)
                  AND (%s)
                  AND NOT EXISTS (
                    SELECT 1
                    FROM final_record submitted_fr
                    WHERE submitted_fr.student_user_id = u1.id
                      AND submitted_fr.academic_year = #{query.academicYear}
                      AND submitted_fr.status IN ('SUBMITTED', 'CONFIRMED')
                  )
              ) visible
              GROUP BY visible.user_id
            ) picked_visible_om
            JOIN iam_user u
              ON u.id = picked_visible_om.user_id
            JOIN org_membership om
              ON om.id = picked_visible_om.membership_id
            JOIN org_unit class_ou
              ON class_ou.id = om.org_unit_id
             AND class_ou.unit_type = 'CLASS'
             AND class_ou.status = 'ACTIVE'
            LEFT JOIN org_unit grade_ou
              ON grade_ou.id = class_ou.parent_id
             AND grade_ou.unit_type = 'GRADE'
             AND grade_ou.status = 'ACTIVE'
            LEFT JOIN (
              SELECT student_user_id, updated_at AS last_updated_at
              FROM final_record
              WHERE academic_year = #{query.academicYear}
                AND status = 'DRAFT'
            ) draft_fr
              ON draft_fr.student_user_id = u.id
            ORDER BY CASE WHEN grade_ou.id IS NULL THEN 1 ELSE 0 END ASC,
                     grade_ou.unit_code ASC,
                     class_ou.unit_code ASC,
                     u.user_no ASC,
                     u.id ASC
            LIMIT #{query.pageSize} OFFSET #{query.offset}
            """.formatted(scopeExpression,
                    caseSensitiveEquals("grade_ou1.unit_code", "#{query.grade}"),
                    caseSensitiveEquals("grade_ou1.unit_name", "#{query.grade}"),
                    classPredicate);
}
```

Then add helper methods. The provider builds indexed MyBatis placeholders and never concatenates raw class values. `scopeExpression`, `caseSensitiveEquals(...)`, and `caseSensitiveIn(...)` may be injected into SQL strings only when they are built from fixed alias names plus MyBatis placeholders generated in this provider/repository; never pass raw request values, grade values, class values, or caller-provided SQL through these helpers. Use `CAST(... AS BINARY)` for filter equality because local H2 2.2.224 rejects MySQL's prefix `BINARY` operator, while H2 MySQL-mode and MySQL both support `CAST(expr AS BINARY)`. Sorting intentionally follows the configured column collation for `grade_ou.unit_code` and `class_ou.unit_code`; D-11 only requires deterministic ordering with `user_id ASC` as final tie-breaker, not bytewise sorting.

Before wiring the provider and repository, create `D11ScopeSqlShape` in `whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/D11ScopeSqlShape.java`. This class is the single source for D-11 scope SQL shape: the repository uses it to generate ORG_UNIT/ORG_SUBTREE fragments, and the provider uses it to validate the whitelist. Do not duplicate subtree SQL or whitelist regex bodies in `FinalRecordQuerySqlProvider` or `MybatisPlusFinalRecordQueryRepository`. `scopeExpression` is not a public extension point. For D-11, it must only come from `MybatisPlusFinalRecordQueryRepository.rosterScopeFragment(...)`; controller, service, query object, request fields, and external callers must never provide it. The mapper/provider methods remain package-internal infrastructure calls in practice: do not expose a repository API that accepts raw SQL fragments for D-11. `rosterScopeFragment(...)` must wrap every dynamic non-static expression in one outer pair of parentheses, including a single ORG_UNIT fragment and a single ORG_SUBTREE fragment, so the provider whitelist sees one consistent shape. Provider validation must normalize whitespace and then use full-expression matching against this closed whitelist: deny-all, allow-all, ORG_UNIT only, ORG_SUBTREE only, ORG_UNIT OR ORG_SUBTREE, and ORG_SUBTREE OR ORG_UNIT. Prefix matching is not allowed. The `root_ou.path IS NOT NULL` and class-path `IS NOT NULL` checks are defensive-in-depth guards and remain part of the generated SQL and whitelist even though the frozen A schema declares `org_unit.path NOT NULL`; the empty, leading slash, trailing slash, and `LIKE` wildcard character checks cover malformed but schema-valid path strings. D-11 treats `%` and `_` in subtree root/class paths as malformed and non-matching by requiring `LOCATE('%', path) = 0` and `LOCATE('_', path) = 0` before executing the `LIKE CONCAT(root_ou.path, '/%')` subtree comparison.

Provider helper contracts:

- `scopeExpression(Map<String, Object> params, String classAlias)` reads only `params.get("scopeFragment")`, treats missing/null/blank expression as defensive `1 = 0`, validates the original expression with `D11ScopeSqlShape.isAllowedScopeExpression(...)`, and replaces only `D11ScopeSqlShape.CLASS_ALIAS_PLACEHOLDER` with the fixed alias passed by the provider (`class_ou` or `class_ou1`). It must not read query filters or request values, must not accept a caller-supplied alias from outside the provider, and must not append raw SQL around the validated fragment.
- `classPredicate(Map<String, Object> params, String classAlias)` reads only `params.get("query")`; when the query is absent or `query.isClassesEmpty()` is true it returns the fixed predicate `TRUE`. Otherwise it returns one parenthesized predicate: `CAST(<alias>.unit_code AS BINARY) IN (...) OR CAST(<alias>.unit_name AS BINARY) IN (...)`, where each placeholder is generated as `CAST(#{query.classes[i]} AS BINARY)` from the normalized `UnsubmittedFinalRecordQuery.getClasses()` order.
- `caseSensitiveEquals(...)` and `caseSensitiveIn(...)` accept only provider-chosen column names and MyBatis placeholders generated in this provider. They are not generic SQL helpers and must not receive request strings.

```java
package edu.whut.eval.infra.security.sql;

import java.util.regex.Pattern;

public final class D11ScopeSqlShape {

    public static final String CLASS_ALIAS_PLACEHOLDER = "__D11_CLASS_ALIAS__";

    private D11ScopeSqlShape() {
    }

    public static String normalize(String expression) {
        return expression == null ? "" : expression.strip().replaceAll("\\s+", " ");
    }

    public static String orgUnitFragment(String inSql) {
        return CLASS_ALIAS_PLACEHOLDER + ".id IN (" + inSql + ")";
    }

    public static String orgSubtreeFragment(String inSql) {
        // Double percent signs are Java String.formatted() escapes; the emitted SQL uses single % wildcards.
        return """
                EXISTS (
                  SELECT 1
                  FROM org_unit root_ou
                  WHERE root_ou.id IN (%s)
                    AND root_ou.status = 'ACTIVE'
                    AND root_ou.path IS NOT NULL
                    AND root_ou.path <> ''
                    AND root_ou.path LIKE '/%%'
                    AND root_ou.path NOT LIKE '%%/'
                    AND LOCATE('%%', root_ou.path) = 0
                    AND LOCATE('_', root_ou.path) = 0
                    AND __D11_CLASS_ALIAS__.path IS NOT NULL
                    AND __D11_CLASS_ALIAS__.path <> ''
                    AND __D11_CLASS_ALIAS__.path LIKE '/%%'
                    AND __D11_CLASS_ALIAS__.path NOT LIKE '%%/'
                    AND LOCATE('%%', __D11_CLASS_ALIAS__.path) = 0
                    AND LOCATE('_', __D11_CLASS_ALIAS__.path) = 0
                    AND (
                      __D11_CLASS_ALIAS__.path = root_ou.path
                      OR __D11_CLASS_ALIAS__.path LIKE CONCAT(root_ou.path, '/%%')
                    )
                )
                """.formatted(inSql).strip();
    }

    public static boolean isAllowedScopeExpression(String normalized) {
        if ("1 = 0".equals(normalized) || "1 = 1".equals(normalized)) {
            return true;
        }
        String orgUnitFragmentPattern = "__D11_CLASS_ALIAS__\\.id IN \\(" + parameterListPattern("d11OrgUnit") + "\\)";
        String subtreeFragmentPattern = "EXISTS \\( SELECT 1 FROM org_unit root_ou WHERE root_ou\\.id IN \\("
                + parameterListPattern("d11Subtree")
                + "\\) AND root_ou\\.status = 'ACTIVE'"
                + " AND root_ou\\.path IS NOT NULL"
                + " AND root_ou\\.path <> ''"
                + " AND root_ou\\.path LIKE '/%'"
                + " AND root_ou\\.path NOT LIKE '%/'"
                + " AND LOCATE\\('%', root_ou\\.path\\) = 0"
                + " AND LOCATE\\('_', root_ou\\.path\\) = 0"
                + " AND __D11_CLASS_ALIAS__\\.path IS NOT NULL"
                + " AND __D11_CLASS_ALIAS__\\.path <> ''"
                + " AND __D11_CLASS_ALIAS__\\.path LIKE '/%'"
                + " AND __D11_CLASS_ALIAS__\\.path NOT LIKE '%/'"
                + " AND LOCATE\\('%', __D11_CLASS_ALIAS__\\.path\\) = 0"
                + " AND LOCATE\\('_', __D11_CLASS_ALIAS__\\.path\\) = 0"
                + " AND \\( __D11_CLASS_ALIAS__\\.path = root_ou\\.path"
                + " OR __D11_CLASS_ALIAS__\\.path LIKE CONCAT\\(root_ou\\.path, '/%'\\) \\) \\)";
        String orgUnitOnlyPattern = "\\( ?" + orgUnitFragmentPattern + " ?\\)";
        String subtreeOnlyPattern = "\\( ?" + subtreeFragmentPattern + " ?\\)";
        String orgThenSubtreePattern = "\\( ?" + orgUnitFragmentPattern + " OR " + subtreeFragmentPattern + " ?\\)";
        String subtreeThenOrgPattern = "\\( ?" + subtreeFragmentPattern + " OR " + orgUnitFragmentPattern + " ?\\)";
        return Pattern.matches(orgUnitOnlyPattern, normalized)
                || Pattern.matches(subtreeOnlyPattern, normalized)
                || Pattern.matches(orgThenSubtreePattern, normalized)
                || Pattern.matches(subtreeThenOrgPattern, normalized);
    }

    public static void assertGeneratedFragmentsSelfValidateForD11() {
        String singleSubtree = "(" + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}") + ")";
        String multiSubtree = "(" + orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}, #{scopeFragment.parameters.d11Subtree1}") + ")";
        if (!isAllowedScopeExpression(normalize(singleSubtree))
                || !isAllowedScopeExpression(normalize(multiSubtree))) {
            throw new IllegalStateException("Generated D-11 ORG_SUBTREE SQL must match whitelist");
        }
    }

    private static String parameterListPattern(String prefix) {
        String parameterPattern = "#\\{scopeFragment\\.parameters\\." + prefix + "\\d+\\}";
        return parameterPattern + "(, " + parameterPattern + ")*";
    }
}
```

```java
private String classPredicate(Map<String, Object> params, String classAlias) {
    UnsubmittedFinalRecordQuery query = (UnsubmittedFinalRecordQuery) params.get("query");
    if (query == null || query.isClassesEmpty()) {
        return "TRUE";
    }
    String castClassPlaceholders = castClassPlaceholders(query);
    return "(" + caseSensitiveIn(classAlias + ".unit_code", castClassPlaceholders)
            + " OR " + caseSensitiveIn(classAlias + ".unit_name", castClassPlaceholders) + ")";
}

private String castClassPlaceholders(UnsubmittedFinalRecordQuery query) {
    List<String> placeholders = new ArrayList<>();
    for (int i = 0; i < query.getClasses().size(); i++) {
        placeholders.add("CAST(#{query.classes[" + i + "]} AS BINARY)");
    }
    return String.join(", ", placeholders);
}

private String caseSensitiveEquals(String column, String placeholder) {
    return "CAST(" + column + " AS BINARY) = CAST(" + placeholder + " AS BINARY)";
}

private String caseSensitiveIn(String column, String castPlaceholders) {
    return "CAST(" + column + " AS BINARY) IN (" + castPlaceholders + ")";
}

private String scopeExpression(Map<String, Object> params, String classAlias) {
    SqlPredicateFragment fragment = (SqlPredicateFragment) params.get("scopeFragment");
    String expression = fragment == null ? null : fragment.getExpression();
    if (expression == null || expression.isBlank()) {
        return "1 = 0";
    }
    validateD11ScopeExpression(expression);
    // D-11 only replaces the fixed visible class org_unit alias placeholder.
    return expression.replace("__D11_CLASS_ALIAS__", classAlias);
}

private void validateD11ScopeExpression(String expression) {
    String normalized = D11ScopeSqlShape.normalize(expression);
    if (D11ScopeSqlShape.isAllowedScopeExpression(normalized)) {
        return;
    }
    throw new IllegalArgumentException("Unsafe D-11 scope expression");
}
```

Build the classes predicate outside the SQL template: when `query.isClassesEmpty()` is true, render the fixed predicate `TRUE`; otherwise render the two case-sensitive class code/name membership predicates. Add imports for `UnsubmittedFinalRecordQuery`, `D11ScopeSqlShape`, `SqlPredicateFragment`, `ArrayList`, and `List` if they are not already present in `FinalRecordQuerySqlProvider`. Keep `scopeExpression` limited to this mapper/provider plumbing plus the repository-internal D-11 builder. Do not add a controller, service, repository interface, DTO, request, or public helper parameter that accepts a raw SQL string for this value. Add a provider unit/integration test that passes a `SqlPredicateFragment` with expression `(__D11_CLASS_ALIAS__.id IN (#{scopeFragment.parameters.d11OrgUnit0})) OR 1 = 1` and asserts `buildCountUnsubmittedStudents(...)` throws `IllegalArgumentException`.

In `SqlPredicateFragment`, add a D-11-specific explicit true fragment while preserving the existing blank-expression `allowAll()` behavior used by existing translators:

```java
public static SqlPredicateFragment alwaysTrue() {
    return new SqlPredicateFragment("1 = 1", Map.of());
}
```

- [ ] **Step 8: Add D-11 scope-fragment builder in repository implementation**

In `MybatisPlusFinalRecordQueryRepository`, implement:

```java
@Override
public PageResult<UnsubmittedStudentRow> pageUnsubmittedStudents(FinalRecordAccessContext accessContext,
                                                                 UnsubmittedFinalRecordQuery query) {
    SqlPredicateFragment fragment = rosterScopeFragment(accessContext);
    long total = finalRecordQueryMapper.countUnsubmittedStudents(fragment, query);
    if (total == 0) {
        // Optimization only: correctness depends on count/select visible subqueries remaining isomorphic.
        return new PageResult<>(0, List.of());
    }
    List<UnsubmittedStudentRow> records = finalRecordQueryMapper.selectUnsubmittedStudents(
            fragment,
            query
    );
    return new PageResult<>(total, records);
}
```

Add `rosterScopeFragment(...)` using the same `AuthorizationScopeEvaluator` and `FinalRecordScopePredicateBuilder`, but translate to D-11 SQL directly instead of passing through `ApplicationScopeSqlTranslator`:

```java
private SqlPredicateFragment rosterScopeFragment(FinalRecordAccessContext accessContext) {
    UserAuthorizationContext authorizationContext = toAuthorizationContext(accessContext);
    AuthorizationScopeSet scopeSet = authorizationScopeEvaluator.evaluate(authorizationContext, accessContext.getPermissionCode());
    ApplicationScopePredicate predicate = finalRecordScopePredicateBuilder.buildForFinalRecord(authorizationContext, scopeSet);
    if (!predicate.isGranted()) {
        return SqlPredicateFragment.denyAll();
    }
    if (predicate.isAllowAll()) {
        return SqlPredicateFragment.alwaysTrue();
    }
    Map<String, Object> parameters = new LinkedHashMap<>();
    List<String> fragments = new ArrayList<>();
    List<Long> orgUnitIds = predicate.getClauses().stream()
            .filter(clause -> "ORG_UNIT".equals(clause.getScopeType()))
            .map(ApplicationScopeClause::getOrgUnitId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (!orgUnitIds.isEmpty()) {
        String inSql = bindList(parameters, "d11OrgUnit", orgUnitIds);
        fragments.add(D11ScopeSqlShape.orgUnitFragment(inSql));
    }
    List<Long> subtreeIds = predicate.getClauses().stream()
            .filter(clause -> "ORG_SUBTREE".equals(clause.getScopeType()))
            .map(ApplicationScopeClause::getOrgSubtreeRootId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (!subtreeIds.isEmpty()) {
        String inSql = bindList(parameters, "d11Subtree", subtreeIds);
        fragments.add(D11ScopeSqlShape.orgSubtreeFragment(inSql));
    }
    if (fragments.isEmpty()) {
        return new SqlPredicateFragment("1 = 0", Map.of());
    }
    return new SqlPredicateFragment("(" + String.join(" OR ", fragments) + ")", parameters);
}

private UserAuthorizationContext toAuthorizationContext(FinalRecordAccessContext accessContext) {
    return new UserAuthorizationContext(accessContext.getUserId(), accessContext.getUserNo(), accessContext.getUserName(),
            accessContext.getIdentity(), accessContext.getRoles(), accessContext.getAuthorities(), accessContext.getScopeRules());
}

private String bindList(Map<String, Object> parameters, String prefix, List<Long> values) {
    List<String> placeholders = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
        String key = prefix + i;
        parameters.put(key, values.get(i));
        placeholders.add("#{scopeFragment.parameters." + key + "}");
    }
    return String.join(", ", placeholders);
}
```

Use imports for `ApplicationScopeClause`, `D11ScopeSqlShape`, `ArrayList`, `LinkedHashMap`, `Map`, and `Objects`. Prefer `SqlPredicateFragment.alwaysTrue()` and `SqlPredicateFragment.denyAll()` for the static allow/deny branches; use the public constructor only for dynamic D-11 fragments that carry generated SQL and parameters. D-11 must not use an empty string to mean allow-all because `FinalRecordQuerySqlProvider.scopeExpression(...)` treats blank input as defensive deny-all. `SqlPredicateFragment.alwaysTrue()` must return expression `1 = 1` with an empty parameter map. Do not duplicate `D11ScopeSqlShape` strings to work around package access.

- [ ] **Step 9: Add provider scope-expression safety regression test**

Add these tests to `MybatisPlusFinalRecordQueryRepositoryIntegrationTest` so the D-11 SQL provider's raw-fragment defense is executable and tracked with the repository SQL work. The positive whitelist test must cover `denyAll`, `alwaysTrue`, single ORG_UNIT with one and multiple parameters, single ORG_SUBTREE, ORG_UNIT OR ORG_SUBTREE, and ORG_SUBTREE OR ORG_UNIT through both count and select provider entrypoints.

```java
@Test
void shouldRejectUnsafeD11ScopeExpressionFragments() {
    FinalRecordQuerySqlProvider provider = new FinalRecordQuerySqlProvider();
    Map<String, Object> params = new HashMap<>();
    params.put("scopeFragment", new SqlPredicateFragment(
            "(__D11_CLASS_ALIAS__.id IN (#{scopeFragment.parameters.d11OrgUnit0})) OR 1 = 1",
            Map.of("d11OrgUnit0", 4001L)
    ));
    params.put("query", new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20));

    assertThatThrownBy(() -> provider.buildCountUnsubmittedStudents(params))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsafe D-11 scope expression");
    assertThatThrownBy(() -> provider.buildSelectUnsubmittedStudents(params))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsafe D-11 scope expression");
}

@Test
void shouldAcceptOnlyWhitelistedD11ScopeExpressionShapes() {
    FinalRecordQuerySqlProvider provider = new FinalRecordQuerySqlProvider();

    D11ScopeSqlShape.assertGeneratedFragmentsSelfValidateForD11();
    assertProviderAccepts(provider, SqlPredicateFragment.denyAll(), "1 = 0");
    assertProviderAccepts(provider, SqlPredicateFragment.alwaysTrue(), "1 = 1");
    assertProviderAccepts(provider, new SqlPredicateFragment(
            "(" + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}") + ")",
            Map.of("d11OrgUnit0", 4001L)
    ), "class_ou.id IN");
    assertProviderAccepts(provider, new SqlPredicateFragment(
            "(" + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}, #{scopeFragment.parameters.d11OrgUnit1}") + ")",
            Map.of("d11OrgUnit0", 4001L, "d11OrgUnit1", 4002L)
    ), "class_ou.id IN");
    assertProviderAccepts(provider, new SqlPredicateFragment(
            "(" + D11ScopeSqlShape.orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}") + ")",
            Map.of("d11Subtree0", 2002L)
    ), "LOCATE('%', root_ou.path) = 0");
    assertProviderAccepts(provider, new SqlPredicateFragment(
            "(" + D11ScopeSqlShape.orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}, #{scopeFragment.parameters.d11Subtree1}") + ")",
            Map.of("d11Subtree0", 2002L, "d11Subtree1", 3001L)
    ), "root_ou.id IN");
    assertProviderAccepts(provider, new SqlPredicateFragment(
            "(" + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}")
                    + " OR " + D11ScopeSqlShape.orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}") + ")",
            Map.of("d11OrgUnit0", 4001L, "d11Subtree0", 2002L)
    ), " OR ");
    assertProviderAccepts(provider, new SqlPredicateFragment(
            "(" + D11ScopeSqlShape.orgSubtreeFragment("#{scopeFragment.parameters.d11Subtree0}")
                    + " OR " + D11ScopeSqlShape.orgUnitFragment("#{scopeFragment.parameters.d11OrgUnit0}") + ")",
            Map.of("d11Subtree0", 2002L, "d11OrgUnit0", 4001L)
    ), " OR ");
}

private void assertProviderAccepts(FinalRecordQuerySqlProvider provider,
                                   SqlPredicateFragment fragment,
                                   String expectedSql) {
    Map<String, Object> params = providerParams(fragment);
    assertThat(provider.buildCountUnsubmittedStudents(params)).contains(expectedSql);
    assertThat(provider.buildSelectUnsubmittedStudents(params)).contains(expectedSql);
}

private Map<String, Object> providerParams(SqlPredicateFragment fragment) {
    Map<String, Object> params = new HashMap<>();
    params.put("scopeFragment", fragment);
    params.put("query", new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20));
    return params;
}
```

Add imports for `FinalRecordQuerySqlProvider`, `D11ScopeSqlShape`, `SqlPredicateFragment`, `HashMap`, `Map`, `assertThat`, and `assertThatThrownBy` if the test file does not already have them.

- [ ] **Step 10: Run repository integration tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusFinalRecordQueryRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS. The provider must use `CAST(... AS BINARY)` for exact `grade` and `classes` comparisons so the same SQL path is deterministic on H2 MySQL-mode and MySQL. The ORG_SUBTREE path tests above also verify the `CONCAT(root_ou.path, '/%')` subtree predicate on the H2 MySQL-mode integration path used by this repository.

Before committing, run this local contract scan and inspect every hit:

```bash
rg -n "scopeExpression" whut-eval-infra whut-eval-application whut-eval-domain whut-eval-interfaces whut-eval-app/src/test
```

If `rg` is unavailable, run the fallback command:

```bash
grep -RInE "scopeExpression" whut-eval-infra whut-eval-application whut-eval-domain whut-eval-interfaces whut-eval-app/src/test
```

Expected: hits are limited to `FinalRecordQueryMapper`, `FinalRecordQuerySqlProvider`, `MybatisPlusFinalRecordQueryRepository`, and D-11 tests/plan references. There must be no public controller/service/repository-interface/query-object path that accepts caller-provided raw SQL for `scopeExpression`.

- [ ] **Step 11: Commit**

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/D11ScopeSqlShape.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/security/sql/SqlPredicateFragment.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java
git commit -m "feat: query unsubmitted final record roster"
```

---

### Task 4: Controller Route and Request Shape

**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java`

- [ ] **Step 1: Write failing controller tests**

Add controller tests:

Use Mockito imports for `any()`, `anyLong()`, `never()`, and `reset()` if the test file does not already import them. Use `org.hamcrest.Matchers.aMapWithSize` for the exact `PageResult` JSON field-count assertion.

```java
@Test
void shouldReturnUnsubmittedFinalRecordPage() throws Exception {
    when(queryApplicationService.pageUnsubmittedStudents(any()))
            .thenReturn(new PageResult<>(1, List.of(new UnsubmittedStudentView(
                    1001L, "S001", "Alice", "计算机2022级", "计算机2201班",
                    "UNSUBMITTED", "2026-07-12T10:15:30.123Z"))));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(aMapWithSize(2)))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].studentUserId").value(1001))
            .andExpect(jsonPath("$.data.records[0].status").value("UNSUBMITTED"))
            .andExpect(jsonPath("$.data.records[0].lastUpdatedAt").value("2026-07-12T10:15:30.123Z"))
            .andExpect(jsonPath("$.data.pages").doesNotExist())
            .andExpect(jsonPath("$.data.pageNum").doesNotExist())
            .andExpect(jsonPath("$.data.pageNo").doesNotExist())
            .andExpect(jsonPath("$.data.pageSize").doesNotExist());

    ArgumentCaptor<UnsubmittedFinalRecordQuery> captor = ArgumentCaptor.forClass(UnsubmittedFinalRecordQuery.class);
    verify(queryApplicationService).pageUnsubmittedStudents(captor.capture());
    assertThat(captor.getValue().getAcademicYear()).isEqualTo("2025-2026");
    assertThat(captor.getValue().getGrade()).isNull();
    assertThat(captor.getValue().getClasses()).isEmpty();
    assertThat(captor.getValue().getPageNo()).isEqualTo(1);
    assertThat(captor.getValue().getPageSize()).isEqualTo(20);
}

@Test
void shouldRouteStaticUnsubmittedPathBeforeRecordIdDetailPath() throws Exception {
    when(queryApplicationService.pageUnsubmittedStudents(any()))
            .thenReturn(new PageResult<>(1, List.of(new UnsubmittedStudentView(
                    1001L, "S001", "Alice", "计算机2022级", "计算机2201班",
                    "UNSUBMITTED", ""))));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].status").value("UNSUBMITTED"))
            .andExpect(jsonPath("$.data.record").doesNotExist())
            .andExpect(jsonPath("$.data.student").doesNotExist());

    ArgumentCaptor<UnsubmittedFinalRecordQuery> captor = ArgumentCaptor.forClass(UnsubmittedFinalRecordQuery.class);
    verify(queryApplicationService).pageUnsubmittedStudents(captor.capture());
    assertThat(captor.getValue().getAcademicYear()).isEqualTo("2025-2026");
    assertThat(captor.getValue().getPageNo()).isEqualTo(1);
    assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    verify(queryApplicationService, never()).getAdminFinalRecordDetail(anyLong());
}

@Test
void shouldAcceptRepeatedAndArrayStyleClassesButRejectRepeatedSingleValueParams() throws Exception {
    when(queryApplicationService.pageUnsubmittedStudents(any()))
            .thenReturn(new PageResult<>(0, List.of()));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("grade", "Grade,With,Comma")
                    .param("classes", "CS2201", "Class,With,Comma")
                    .param("classes[]", "CS2202")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isOk());

    ArgumentCaptor<UnsubmittedFinalRecordQuery> captor = ArgumentCaptor.forClass(UnsubmittedFinalRecordQuery.class);
    verify(queryApplicationService).pageUnsubmittedStudents(captor.capture());
    assertThat(captor.getValue().getGrade()).isEqualTo("Grade,With,Comma");
    assertThat(captor.getValue().getClasses()).containsExactly("CS2201", "Class,With,Comma", "CS2202");

    reset(queryApplicationService);
    when(queryApplicationService.pageUnsubmittedStudents(any()))
            .thenReturn(new PageResult<>(0, List.of()));
    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("classes[]", "CS2203", "CS2204")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isOk());
    verify(queryApplicationService).pageUnsubmittedStudents(captor.capture());
    assertThat(captor.getValue().getClasses()).containsExactly("CS2203", "CS2204");

    reset(queryApplicationService);
    when(queryApplicationService.pageUnsubmittedStudents(any()))
            .thenReturn(new PageResult<>(0, List.of()));
    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("classes", "CS2205", "CS2206")
                    .param("classes[]", " CS2205 ", "", "CS2207")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isOk());
    verify(queryApplicationService).pageUnsubmittedStudents(captor.capture());
    assertThat(captor.getValue().getClasses()).containsExactly("CS2205", "CS2206", "CS2207");

    reset(queryApplicationService);
    when(queryApplicationService.pageUnsubmittedStudents(any()))
            .thenReturn(new PageResult<>(0, List.of()));
    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("classes", "B", " A ")
                    .param("classes[]", "A", "C")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isOk());
    verify(queryApplicationService).pageUnsubmittedStudents(captor.capture());
    assertThat(captor.getValue().getClasses()).containsExactly("B", "A", "C");

    reset(queryApplicationService);
    when(queryApplicationService.pageUnsubmittedStudents(any()))
            .thenReturn(new PageResult<>(0, List.of()));
    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("classes", "A")
                    .param("classes[]", "B")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isOk());
    verify(queryApplicationService).pageUnsubmittedStudents(captor.capture());
    assertThat(captor.getValue().getClasses()).containsExactly("A", "B");

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026", "2026-2027")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("academicYear 不合法"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear[]", "2025-2026")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("grade[]", "CS2022")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("grade 不合法"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("grade", "CS2022", "CS2023")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("pageNo", "1", "2")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("pageNo 不合法"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("pageNo[]", "1")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("pageSize", "20", "50")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("pageSize 不合法"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("pageSize[]", "20")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));
}

@Test
void shouldValidateUnsubmittedAcademicYearClassesAndOverflowAtControllerBoundary() throws Exception {
    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("academicYear 不合法"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("grade", "CS2022")
                    .param("pageNo", "1")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2027")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2026-2025")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "   ")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    when(queryApplicationService.pageUnsubmittedStudents(any()))
            .thenReturn(new PageResult<>(0, List.of()));
    MockHttpServletRequestBuilder exactlyFiveHundredClasses = get("/api/admin/final-records/unsubmitted")
            .param("academicYear", "2025-2026")
            .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED)));
    for (int i = 0; i < 250; i++) {
        exactlyFiveHundredClasses.param("classes", "CS" + i);
    }
    for (int i = 250; i < 500; i++) {
        exactlyFiveHundredClasses.param("classes[]", "CS" + i);
    }
    mockMvc.perform(exactlyFiveHundredClasses)
            .andExpect(status().isOk());

    MockHttpServletRequestBuilder tooManyClasses = get("/api/admin/final-records/unsubmitted")
            .param("academicYear", "2025-2026")
            .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED)));
    for (int i = 0; i < 501; i++) {
        tooManyClasses.param("classes", "CS" + i);
    }
    mockMvc.perform(tooManyClasses)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    MockHttpServletRequestBuilder tooManyMergedClasses = get("/api/admin/final-records/unsubmitted")
            .param("academicYear", "2025-2026")
            .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED)));
    for (int i = 0; i < 250; i++) {
        tooManyMergedClasses.param("classes", "CS" + i);
    }
    for (int i = 250; i < 501; i++) {
        tooManyMergedClasses.param("classes[]", "CS" + i);
    }
    mockMvc.perform(tooManyMergedClasses)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("pageNo", String.valueOf(Long.MAX_VALUE))
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("pageNo", "abc")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("pageNo 不合法"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("pageSize", "abc")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("pageSize 不合法"));
}

```

Do not put the `score.view.assigned` authorization contract only in this standalone controller test file. Add the route security checks to the existing `FinalRecordSecurityIntegrationTest`, which already runs the real Spring Security and method-security configuration. Add imports for `AccessDeniedAppException`, `jsonPath`, and `never` if the file does not already have them:

```java
@Test
void shouldAllowUnsubmittedAdminListWithScoreViewAssigned() throws Exception {
    given(queryApplicationService.pageUnsubmittedStudents(any()))
            .willReturn(new PageResult<>(0, List.of()));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.view.assigned")))
            .andExpect(status().isOk());
}

@Test
void shouldRejectUnsubmittedAdminListWithoutScoreViewAssigned() throws Exception {
    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.confirm.assigned")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH-4030"));

    verify(queryApplicationService, never()).pageUnsubmittedStudents(any());
}

@Test
void shouldRejectUnsubmittedAdminListWithoutAuthentication() throws Exception {
    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026"))
            .andExpect(status().isUnauthorized());

    verify(queryApplicationService, never()).pageUnsubmittedStudents(any());
}

@Test
void shouldMapUnsubmittedServiceAccessDeniedToAuth4030() throws Exception {
    given(queryApplicationService.pageUnsubmittedStudents(any()))
            .willThrow(new AccessDeniedAppException("当前用户无未提交最终成绩名单查询权限"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithAuthorities(1010L, "score.view.assigned")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH-4030"));
}
```

Also update the existing `FinalRecordControllerSecurityAnnotationTest` so controller-level method security cannot be accidentally omitted while service-level checks still pass. This reflection test only locks the controller `@PreAuthorize` expression for `pageUnsubmittedFinalRecords -> SCORE_VIEW_ASSIGNED`; the real anonymous, missing-authority, and service-denied 401/403 behavior belongs in `FinalRecordSecurityIntegrationTest` above. Extend the admin expected map:

```java
@Test
void shouldRequireAdminFinalRecordAuthorities() {
    Map<String, String> expected = Map.of(
            "pageFinalRecords", SCORE_VIEW_ASSIGNED,
            "pageUnsubmittedFinalRecords", SCORE_VIEW_ASSIGNED,
            "getFinalRecord", SCORE_VIEW_ASSIGNED,
            "confirm", SCORE_CONFIRM_ASSIGNED
    );

    expected.forEach((methodName, expression) -> assertPreAuthorize(AdminFinalRecordController.class, methodName, expression));
}
```

- [ ] **Step 2: Run controller tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=AdminFinalRecordControllerWebMvcTest,FinalRecordSecurityIntegrationTest,FinalRecordControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: 404 or path-variable capture failure because `/unsubmitted` route does not exist, plus an annotation-test failure until `pageUnsubmittedFinalRecords` declares `@PreAuthorize`.

- [ ] **Step 3: Add controller route and helpers**

In `AdminFinalRecordController`, import:

```java
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentView;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
```

Add this route before `@GetMapping("/{recordId}")`:

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_VIEW_ASSIGNED)")
@GetMapping("/unsubmitted")
public ApiResponse<PageResult<UnsubmittedStudentView>> pageUnsubmittedFinalRecords(
        @RequestParam(required = false) String academicYear,
        @RequestParam(required = false) String grade,
        @RequestParam(required = false, name = "classes") List<String> classes,
        @RequestParam(required = false, name = "classes[]") List<String> arrayStyleClasses,
        @RequestParam(defaultValue = "1") String pageNo,
        @RequestParam(defaultValue = "20") String pageSize,
        HttpServletRequest request) {
    rejectMultiValueOrArrayStyleParameter(request, "academicYear");
    rejectMultiValueOrArrayStyleParameter(request, "grade");
    rejectMultiValueOrArrayStyleParameter(request, "pageNo");
    rejectMultiValueOrArrayStyleParameter(request, "pageSize");
    List<String> classFilters = mergeClassFilters(classes, arrayStyleClasses);
    return ApiResponse.success(queryApplicationService.pageUnsubmittedStudents(
            new UnsubmittedFinalRecordQuery(academicYear, grade, classFilters,
                    parseLongParameter("pageNo", pageNo),
                    parseLongParameter("pageSize", pageSize))
    ));
}
```

Add helpers:

```java
private void rejectMultiValueOrArrayStyleParameter(HttpServletRequest request, String name) {
    String[] values = request.getParameterValues(name);
    if (values != null && values.length > 1) {
        throw new ValidationException(name + " 不合法");
    }
    if (request.getParameterMap().containsKey(name + "[]")) {
        throw new ValidationException(name + " 不合法");
    }
}

private List<String> mergeClassFilters(List<String> classes, List<String> arrayStyleClasses) {
    List<String> merged = new ArrayList<>();
    if (classes != null) {
        merged.addAll(classes);
    }
    if (arrayStyleClasses != null) {
        merged.addAll(arrayStyleClasses);
    }
    return merged;
}

private long parseLongParameter(String name, String value) {
    try {
        return Long.parseLong(value);
    } catch (NumberFormatException ex) {
        throw new ValidationException(name + " 不合法");
    }
}
```

`mergeClassFilters(...)` only collects raw parameter values from both supported encodings. It must not trim, drop blanks, de-duplicate, enforce length, or enforce `MAX_CLASSES`; the merged list is immediately passed into `UnsubmittedFinalRecordQuery`, which performs the single source-of-truth normalization and validation after both `classes` and `classes[]` have been combined.

- [ ] **Step 4: Run controller and earlier tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=AdminFinalRecordControllerWebMvcTest,FinalRecordSecurityIntegrationTest,FinalRecordControllerSecurityAnnotationTest,UnsubmittedFinalRecordQueryTest,FinalRecordQueryApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java
git commit -m "feat: expose unsubmitted final record endpoint"
```

---

### Task 5: Regression and Full Verification

**Files:**
- Always inspect the final diff before verification.
- Modify only if D-11 changed shared scope translation, `FinalRecordScopePredicateBuilder`, or existing submitted/confirmed admin list/detail behavior:
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordScopePredicateBuilderTest.java`

- [ ] **Step 1: Inspect shared-scope impact**

Review the actual final diff, not only file names. Record the result of each check in the execution notes before continuing:

- `ApplicationScopeSqlTranslator` changed: add Step 2 regressions.
- `SqlPredicateFragment` changed: add Step 2 regressions and run `ScopeSqlTranslatorTest`, even if the visible change is a D-11-specific helper such as `alwaysTrue()`.
- `FinalRecordScopePredicateBuilder` output structure or clause mapping changed: add Step 2 regressions.
- Any public/shared method named with `scope`, `Scope`, `predicate`, `Predicate`, or org-path matching changed outside a D-11-only private helper: add Step 2 regressions.
- Existing `pageAdminFinalRecords(...)`, `buildSelectAdminFinalRecords(...)`, `buildCountAdminFinalRecords(...)`, `findAdminFinalRecordDetail(...)`, or submitted/confirmed list/detail SQL changed: add Step 2 regressions.
- Existing submitted/confirmed admin list/detail data needed by `FinalRecordAccessValidator` changed: add Step 2 regressions.
- Only new D-11 provider methods, `D11ScopeSqlShape`, and a D-11-only private `rosterScopeFragment(...)` changed, with no `SqlPredicateFragment` or shared scope translator changes: document that no shared-scope files or methods changed and continue to Step 3.

- [ ] **Step 2: Add shared-scope regression tests if required**

If shared scope behavior changed, add tests proving the existing submitted/confirmed list and detail paths still enforce real org-path scope, including negative similar-prefix cases:

```java
@Test
void shouldKeepSubmittedAdminListOrgSubtreeVisibilityOnRealOrgPaths() {
    seedSubmittedFinalRecordInClassPath("/WHUT/CS/CS2022/CS2201");
    seedSubmittedFinalRecordInClassPath("/WHUT/CS2/CS2201");

    PageResult<FinalRecordQueryRow> page = repository.pageAdminFinalRecords(
            accessContextWithOrgSubtree(2002L),
            new FinalRecordPageQuery("2025-2026", null, null, null, 1, 20)
    );

    assertThat(page.records()).extracting(FinalRecordQueryRow::getStudentUserId)
            .containsExactly(1001L);
}

@Test
void shouldKeepAdminDetailOrgSubtreeAccessOnRealOrgPaths() {
    FinalRecordQueryRow inScope = rowWithOrgPath(41001L, "/WHUT/CS/CS2022/CS2201");
    FinalRecordQueryRow outOfScope = rowWithOrgPath(41002L, "/WHUT/CS2/CS2201");
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(adminContextWithOrgSubtree(2002L));
    given(queryRepository.findAdminFinalRecordDetail(41001L)).willReturn(Optional.of(inScope));
    given(queryRepository.findAdminFinalRecordDetail(41002L)).willReturn(Optional.of(outOfScope));
    given(queryRepository.listAdminFinalRecordComponents(41001L)).willReturn(List.of());

    assertThat(service.getAdminFinalRecordDetail(41001L).record().finalRecordId()).isEqualTo(41001L);
    assertThatThrownBy(() -> service.getAdminFinalRecordDetail(41002L))
            .isInstanceOf(AccessDeniedAppException.class);
}
```

Keep these tests aligned with actual helper names and access-validation flow in the current test file. The detail regression must go through `FinalRecordQueryApplicationService.getAdminFinalRecordDetail(...)` or directly through `FinalRecordAccessValidator.requireAccess(...)`; `repository.findAdminFinalRecordDetail(...)` alone is an unscoped context fetch and is not sufficient. Do not relax existing `ORG_UNIT` exact-unit behavior.

If `ApplicationScopeSqlTranslator`, `SqlPredicateFragment`, or any other shared scope-SQL translator code changes outside a D-11-only helper, also update and run the existing shared translator regression suite:

```bash
mvn -pl whut-eval-app -am -Dtest=ScopeSqlTranslatorTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS. This command is mandatory whenever Step 1 marks shared scope translation as changed; passing D-11/final-record tests alone is not enough in that case. Record the command and result in the execution notes.

- [ ] **Step 3: Run focused D-11 tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=UnsubmittedFinalRecordQueryTest,FinalRecordQueryApplicationServiceTest,MybatisPlusFinalRecordQueryRepositoryIntegrationTest,AdminFinalRecordControllerWebMvcTest,FinalRecordSecurityIntegrationTest,FinalRecordControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 4: Run final-record regression tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest='*FinalRecord*Test' test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS. If Step 1 marked shared scope translation as changed, also run `mvn -pl whut-eval-app -am -Dtest=ScopeSqlTranslatorTest test -Dsurefire.failIfNoSpecifiedTests=false` here unless it was already run after Step 2 with the final shared-scope code. `ScopeSqlTranslatorTest` failures are in scope for D-11 when shared translator code changed.

- [ ] **Step 5: Run full test suite and compare with the pre-implementation baseline**

Run:

```bash
mvn test
```

Expected: PASS. Before comparing failures, check the Pre-Implementation Verification Baseline entry. If `Full Baseline Result` still says `PENDING` or `BLOCKED`, do not claim "zero new failures" for the full suite; report the final `mvn test` result, focused D-11 test result from Step 3, and final-record regression result from Step 4 as observed evidence. If a full baseline exists, compare this run with that recorded full baseline and confirm there are zero new failing tests. If only the focused fallback baseline exists, compare only D-11/final-record tests against that fallback; failures outside the focused set are observed state and must not be labeled new or pre-existing. D-11 added or modified tests must pass regardless of baseline availability. Existing failures that were already present in `whut-eval-domain`, `whut-eval-application`, `whut-eval-infra`, `whut-eval-interfaces`, or unrelated `whut-eval-app` tests may be treated as pre-existing only when they are named in the recorded full baseline and the final run shows zero new failing tests. In `whut-eval-app`, every final-record related test that was modified, added, or explicitly run by this plan is D-11-touched for verification purposes, including Task 3 mandatory D-11 private scope-parity tests, Step 2 shared-scope regressions, Step 3 focused D-11 tests, and Step 4 `*FinalRecord*Test` regressions; a failure in any of these D-11-touched tests fails D-11 verification even if the same run also contains unrelated pre-existing failures.

- [ ] **Step 6: Review diff for contract drift**

Run:

```bash
git diff --check
BASE_BRANCH="${BASE_BRANCH:-$(git show-ref --verify --quiet refs/heads/main && echo main || (git show-ref --verify --quiet refs/heads/master && echo master || true))}"
if [ -n "$BASE_BRANCH" ]; then
  git diff --stat "$BASE_BRANCH"...HEAD
else
  echo "BASE_BRANCH not found; skipping informational diff stat"
  echo "Optional: run git diff --stat HEAD~<D-11-commit-count>..HEAD if this checkout lacks main/master"
fi
rg -n "pageNum|pages|classId|className.*List|academicYear 不能为空|application_submission|application_fact" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
rg -n "status[[:space:]]+IN[[:space:]]*\([[:space:]]*'SUBMITTED'[[:space:]]*,[[:space:]]*'CONFIRMED'[[:space:]]*\)|status[[:space:]]*=[[:space:]]*'SUBMITTED'|status[[:space:]]*=[[:space:]]*'CONFIRMED'|submitted_fr\.status|final_record.*status" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
rg -n "LIKE[[:space:]]+'%{1,2}/[0-9]|LIKE[[:space:]]+CONCAT\('%{1,2}/',|org_unit_id.*path.*LIKE|path.*LIKE.*org_unit_id" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
rg -n "scopeExpression" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
```

Prefer the four `rg` scans above. If `rg` is unavailable in the execution environment, run the four fallback `grep -RInE` commands below as an equivalent substitute; execute one complete scan set, not both. These use POSIX ERE character classes such as `[[:space:]]` instead of `\s`, so their matching semantics align with the `rg` checks:

```bash
grep -RInE "pageNum|pages|classId|className.*List|academicYear 不能为空|application_submission|application_fact" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
grep -RInE "status[[:space:]]+IN[[:space:]]*\([[:space:]]*'SUBMITTED'[[:space:]]*,[[:space:]]*'CONFIRMED'[[:space:]]*\)|status[[:space:]]*=[[:space:]]*'SUBMITTED'|status[[:space:]]*=[[:space:]]*'CONFIRMED'|submitted_fr\.status|final_record.*status" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
grep -RInE "LIKE[[:space:]]+'%{1,2}/[0-9]|LIKE[[:space:]]+CONCAT\('%{1,2}/',|org_unit_id.*path.*LIKE|path.*LIKE.*org_unit_id" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
grep -RInE "scopeExpression" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
```

Expected:

- `git diff --check` prints no whitespace errors.
- The scope, status, numeric-path, and `scopeExpression` scans are mandatory. Run either the four `rg` commands or, when `rg` is unavailable, the four `grep -RInE` fallback commands over the same directories. Task 5 verification is incomplete unless one full scan set has been executed and every hit has been accepted or rejected using the rules below.
- The `scopeExpression` scan is mandatory. It may find the mapper/provider infrastructure and repository-internal builder, but must not find a D-11 public API, request object, controller, service, repository contract, or DTO that accepts a caller-provided raw SQL fragment.
- The scans pass only when they produce no new D-11 contract-drift hits in changed files. Any new hit fails the verification unless the execution notes name the file/line and explain why the hit is unrelated to D-11 contract drift.
- No `PageResult` metadata fields are added.
- Controller tests assert the D-11 `$.data` object has exactly `total` and `records`, with no `pages`, `pageNum`, `pageNo`, or `pageSize`.
- No D-11 request or response contract adds `classId`.
- No D-11 code uses application/import/export tables.
- No D-11 invalid academic-year path uses `academicYear 不能为空`.
- Treat the scans as checks on changed files/new hits, not a requirement for the entire repository to have zero historical matches.
- The status-predicate scan may find intentional D-11 `NOT EXISTS` exclusion of `SUBMITTED`/`CONFIRMED` records in the requested `academicYear`; it must not find D-11 code that requires returned unsubmitted roster rows themselves to have `SUBMITTED` or `CONFIRMED` status.
- The numeric-path anti-pattern check has zero new D-11 hits, or every hit is manually confirmed unrelated to D-11 `ORG_SUBTREE` SQL. Correct D-11 subtree matching must compare `class_ou.path` to `root_ou.path` with `CONCAT(root_ou.path, '/%')`, never numeric org ids embedded in path strings.

- [ ] **Step 7: Commit final fixes if any**

If Task 5 added regression tests or fixes:

```bash
git status --short
git add <all Task 5 files shown by git status, including any shared scope implementation files and regression tests>
git status --short
git commit -m "test: cover final record scope regressions"
```

If no files changed, do not create an empty commit. If `git status --short` still shows unstaged Task 5 files after `git add`, stop and stage the missing files before committing.

---

## Review Checklist

- `/api/admin/final-records/unsubmitted` is static and not captured by `/{recordId}`.
- Controller and service both require `score.view.assigned`.
- `academicYear` validation returns `ValidationException("academicYear 不合法")`.
- `classes` remains `List<String>` and accepts repeated `classes` plus array-style `classes[]`.
- `classes` and `classes[]` are merged before `UnsubmittedFinalRecordQuery` performs trim, blank dropping, de-duplication, length checks, and the normalized `MAX_CLASSES = 500` check.
- Controller tests prove raw `classes` values are merged before raw `classes[]` values, and de-duplication keeps the first normalized value after that merged order.
- Controller validation tests assert representative `$.message` values for invalid `academicYear`, `grade`, `pageNo`, and `pageSize`, not only `VAL-4001`.
- Commas inside `grade` and `classes` remain ordinary exact-match characters.
- `PageResult<T>` remains only `total` and `records`.
- Controller JSON tests lock `PageResult<T>` to exactly `total` and `records`.
- Roster SQL starts from active `iam_user`, active primary `org_membership`, and active class `org_unit`.
- Roster SQL requires `org_membership.membership_type = 'STUDENT'`; it does not depend on an `iam_user.identity` database column.
- Inactive `iam_user`, inactive `org_membership`, non-primary `org_membership`, and non-STUDENT membership types are excluded by repository tests.
- Inactive class `org_unit` rows and non-CLASS `org_unit` rows are excluded by repository tests.
- A missing final record and a single `DRAFT` record keep students unsubmitted; a `DRAFT` record contributes its non-null `updated_at`.
- Duplicate `final_record` rows for the same `(student_user_id, academic_year)`, nullable `final_record.status`, and nullable `final_record.updated_at` are excluded because they violate the frozen D SQL schema.
- `SUBMITTED` and `CONFIRMED` records exclude students.
- `lastUpdatedAt` is a UTC `Instant` at the mapper/service boundary and is rendered with `Instant.toString()` actual precision, or empty string.
- Classes without an active `GRADE` parent can appear without a grade filter, expose raw `grade = null`, and are excluded when a grade filter is present.
- Rows with an active `GRADE` parent sort before rows whose selected class has no active grade parent.
- When the lowest numeric visible membership's class has no active grade parent, display `grade = null` for that selected class even if a higher visible membership has an active grade.
- `ORG_UNIT` is exact class id only.
- `ORG_SUBTREE` resolves root `org_unit.path` and compares real code paths.
- `ORG_SUBTREE` rejects malformed but schema-valid root/class path strings, including empty path, missing leading `/`, trailing `/`, and embedded SQL `LIKE` wildcard characters `%` or `_`.
- D-11 private `rosterScopeFragment(...)` is covered by mandatory parity tests against existing submitted/confirmed whole-record scope visibility for denied permission, `ALLOW_ALL`, `ORG_UNIT`, `ORG_SUBTREE`, unsupported/empty scopes, and similar-prefix paths.
- `ORG_SUBTREE` may be rooted at any active org unit with a valid path, including GRADE and CLASS roots; a GRADE root exposes that grade's class descendants, and a CLASS root exposes that class only, not sibling classes.
- If `ORG_UNIT` and `ORG_SUBTREE` both grant the same class, visible students are still counted and returned once.
- Multiple same-type scope rules are covered: two `ORG_SUBTREE` roots and two `ORG_UNIT` rules both produce union visibility without duplicate students or whitelist rejection.
- Inactive `ORG_SUBTREE` roots at COLLEGE, GRADE, or CLASS level return an empty page even when their path and descendants are otherwise valid.
- Similar path prefixes such as `/WHUT/CS2` do not match `/WHUT/CS`.
- `D11ScopeSqlShape` is the single source for D-11 ORG_UNIT/ORG_SUBTREE SQL fragments and whitelist validation; repository/provider code and provider tests do not duplicate subtree SQL shape strings.
- `D11ScopeSqlShape.assertGeneratedFragmentsSelfValidateForD11()` proves generated single-root and multi-root ORG_SUBTREE SQL fragments pass the same whitelist used by the provider, including the `CONCAT(root_ou.path, '/%')` shape.
- Provider helper contracts are explicit: `scopeExpression(...)` only reads `scopeFragment`, validates the whitelist, and replaces the fixed class-alias placeholder; `classPredicate(...)` only reads `query` and generates code/name case-sensitive `IN` predicates.
- Duplicate active primary memberships collapse after scope and filters, selecting the lowest numeric visible membership id.
- Cross-grade duplicate memberships are filtered by `grade` before collapse, so count, row selection, and displayed grade/class all come from the visible membership set.
- Duplicate-membership repository tests assert both `records` and `total` so count and select deduplication stay aligned.
- Count and select SQL keep eligibility predicates aligned: active user, student membership, scope, grade/classes, and submitted/confirmed exclusion must change together.
- Grade/classes exact filters are case-sensitive, use code-or-name OR semantics, include distinct code/name matches, and do not duplicate a student when both sides match the same org unit.
- Reversing the request order of `classes` values does not change the selected display membership, returned student ids, total count, or grade/class display values.
- If the same student matches the same `classes` filter through one visible class code and another visible class name, the student appears once and displays the lowest numeric visible membership id.
- Sorting uses `user_id ASC` as the final tie-breaker when grade, class, and student number keys are equal, so pagination stays stable with duplicate student numbers.
- `pageNo` offset overflow is rejected.
- `pageNo` and `pageSize` intentionally remain `long` in `UnsubmittedFinalRecordQuery` so offset multiplication can detect overflow before mapper execution.
- Baseline notes belong in the execution notes for this plan, directly under the Pre-Implementation Verification Baseline section; do not bury baseline failures only in terminal scrollback or a separate handoff summary.
- A `PENDING` or `BLOCKED` Full Baseline blocks any full-suite "zero new failures" claim; it does not block reporting observed test results.
- Any modified, added, or explicitly run final-record related test under `whut-eval-app` is in D-11 verification scope; failures in shared-scope regressions or `*FinalRecord*Test` cannot be waived as outside the D-11 test package. Unrelated pre-existing failures are judged only by the recorded baseline and zero-new-failure comparison.
- If shared scope translation code changed, `ScopeSqlTranslatorTest` is mandatory verification and must pass; final-record-only regression commands are not sufficient.
- `SqlPredicateFragment` changes always count as shared scope translation impact for Task 5 verification, even when the visible addition is D-11-specific.
- No D-7, D-8, D-9, D-10, import, export, or frontend behavior is introduced.
