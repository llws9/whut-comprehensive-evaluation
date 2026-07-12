# D-11 Unsubmitted Final Records Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `GET /api/admin/final-records/unsubmitted` so authorized admins can page current active in-scope students who have not submitted or confirmed final records for an academic year.

**Scope lock:** This D-11 plan implements only the frozen unsubmitted list route above. It does not add Excel export, `classId`, `pageNum`, `pages`, or frontend behavior. Request fields are `academicYear`, optional `grade`, repeated `classes`, `pageNo`, and `pageSize`; response pagination remains `PageResult<T>` with only `total` and `records`.

**Architecture:** Add a D-11 query path beside the existing Minimal D final-record list, but do not overload the submitted/confirmed list SQL. The new path starts from active IAM roster membership, applies whole-record organization scope to the selected class org, and excludes the whole student if that student has any `SUBMITTED` or `CONFIRMED` final record in the requested academic year. `DRAFT`, unknown, and `NULL` statuses still mean the student is unsubmitted, but only `DRAFT` records contribute `lastUpdatedAt` via `MAX(updated_at)`. Sort rows by grade nulls-last, grade code, class nulls-last, class code, student-number nulls-last, student number, then user id; this plan intentionally extends the design SQL with student-number nulls-last ordering to keep dirty `user_no = NULL` pagination stable. If a student has multiple visible primary class memberships after scope and filters, return the student once and display the class plus the active grade parent from the lowest numeric visible membership id; if that class has no active grade parent, display grade as null/empty rather than borrowing a grade from another membership.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Security method annotations, MyBatis provider SQL, H2 MySQL-mode integration tests, JUnit 5, AssertJ, Mockito.

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
- Existing final-record query tests:
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java`

## File Structure

### Domain

- Create `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/UnsubmittedFinalRecordQuery.java`
  - Validates `academicYear`.
  - Normalizes `grade`, `classes`, `pageNo`, and `pageSize`.
  - Exposes `classesEmpty` and checked `offset`.

### Application Layer

- Create `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/query/UnsubmittedStudentRow.java`
  - Mapper row with nullable database values.
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

### Interfaces

- Modify `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java`
  - Adds `/unsubmitted` route before `/{recordId}`.
  - Rejects multi-value `academicYear` and `grade`.
  - Merges repeated `classes` and array-style `classes[]`.

### Tests

- Create or modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/UnsubmittedFinalRecordQueryTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordQueryApplicationServiceTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
- Modify `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java`

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
    }

    @Test
    void shouldNormalizePageSizeLikeExistingFinalRecordPageQuery() {
        assertThat(new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 0).getPageSize()).isEqualTo(20);
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

D-11 keeps `grade` and `classes` as exact-match filter strings without comma splitting or case folding. They are bound through MyBatis parameters and naturally produce no matches when no `org_unit.unit_code` or `org_unit.unit_name` equals the value. To cap request size, keep `MAX_CLASSES = 500` and reject any single normalized `grade` or `classes` value longer than 256 characters.

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
void shouldRenderNullLastUpdatedAtAsEmptyString() {
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
void shouldRenderLastUpdatedAtWithFixedUtcMillisecondPrecision() {
    UserAuthorizationContext admin = adminWithAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED);
    when(userAuthorizationContextAssembler.requiredAuthorizationContext()).thenReturn(admin);
    UnsubmittedFinalRecordQuery query = new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20);
    when(finalRecordQueryRepository.pageUnsubmittedStudents(any(), same(query)))
            .thenReturn(new PageResult<>(1, List.of(
                    unsubmittedRow(1001L, "S001", "Alice", "2022级", "CS2201", Instant.parse("2026-07-12T10:15:30Z"))
            )));

    PageResult<UnsubmittedStudentView> page = service.pageUnsubmittedStudents(query);

    assertThat(page.records()).containsExactly(
            new UnsubmittedStudentView(1001L, "S001", "Alice", "2022级", "CS2201", "UNSUBMITTED", "2026-07-12T10:15:30.000Z")
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

In `FinalRecordQueryApplicationService`, add imports and this method:

```java
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
private static final DateTimeFormatter UNSUBMITTED_LAST_UPDATED_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

private UnsubmittedStudentView toUnsubmittedView(UnsubmittedStudentRow row) {
    return new UnsubmittedStudentView(row.getStudentUserId(), valueOrEmpty(row.getUserNo()),
            valueOrEmpty(row.getUserName()), valueOrEmpty(row.getGrade()), valueOrEmpty(row.getClassName()),
            "UNSUBMITTED", formatLastUpdatedAt(row.getLastUpdatedAt()));
}

private String formatLastUpdatedAt(Instant value) {
    return value == null ? "" : UNSUBMITTED_LAST_UPDATED_FORMATTER.format(value);
}

private String valueOrEmpty(String value) {
    return value == null ? "" : value;
}
```

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
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`

- [ ] **Step 1: Expand the integration-test schema**

Open the existing `MybatisPlusFinalRecordQueryRepositoryIntegrationTest` fixture. Ensure the test schema has these columns before adding D-11 cases:

```sql
CREATE TABLE iam_user (
  id BIGINT PRIMARY KEY,
  user_no VARCHAR(64),
  user_name VARCHAR(128),
  identity VARCHAR(32),
  status VARCHAR(32)
);

CREATE TABLE org_unit (
  id BIGINT PRIMARY KEY,
  parent_id BIGINT,
  unit_type VARCHAR(32),
  unit_code VARCHAR(64),
  unit_name VARCHAR(128),
  path VARCHAR(512),
  status VARCHAR(32)
);

CREATE TABLE org_membership (
  id BIGINT PRIMARY KEY,
  user_id BIGINT,
  org_unit_id BIGINT,
  membership_type VARCHAR(32),
  is_primary TINYINT,
  status VARCHAR(32)
);
```

Also ensure the test `final_record.status` and `final_record.updated_at` columns accept `NULL`, because D-11 explicitly covers legacy dirty rows with null status and DRAFT rows whose `updated_at` is null. Keep all existing Minimal D columns needed by submitted/confirmed list/detail tests. If the current fixture uses simplified `org_unit` or `org_membership` tables, replace only the test fixture setup and update old inserts to populate the new columns.

- [ ] **Step 2: Write failing no-record, draft, and submitted/confirmed tests**

Add repository tests with a seed helper equivalent to:

```java
private void seedRoster() {
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2002, NULL, 'COLLEGE', 'CS', '计算机学院', '/WHUT/CS', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3001, 2002, 'GRADE', 'CS2022', '计算机2022级', '/WHUT/CS/CS2022', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4001, 3001, 'CLASS', 'CS2201', '计算机2201班', '/WHUT/CS/CS2022/CS2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4002, 3001, 'CLASS', 'CS2202', '计算机2202班', '/WHUT/CS/CS2022/CS2202', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1001, 'S001', 'Alice', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1002, 'S002', 'Bob', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1003, 'S003', 'Cindy', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5001, 1001, 4001, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5002, 1002, 4001, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5003, 1003, 4002, 'STUDENT', 1, 'ACTIVE')");
}

private void insertFinalRecord(Long id, Long studentUserId, String academicYear, String status, String updatedAt) {
    String timestampExpression = updatedAt == null ? "NULL" : "CAST(? AS DATETIME)";
    jdbcTemplate.update("""
            INSERT INTO final_record (id, student_user_id, academic_year, status,
                                      moral_total, intellectual_total, physical_total, labor_total, grand_total,
                                      submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0.00, 0.00, 0.00, 0.00, 0.00,
                    CASE WHEN ? = 'SUBMITTED' THEN %s ELSE NULL END,
                    CASE WHEN ? = 'CONFIRMED' THEN %s ELSE NULL END,
                    NULL, 1, CURRENT_TIMESTAMP(), %s)
            """.formatted(timestampExpression, timestampExpression, timestampExpression),
            bindNullableTimestamp(id, studentUserId, academicYear, status, updatedAt));
}

private Object[] bindNullableTimestamp(Long id, Long studentUserId, String academicYear, String status, String updatedAt) {
    if (updatedAt == null) {
        return new Object[]{id, studentUserId, academicYear, status, status, status};
    }
    return new Object[]{id, studentUserId, academicYear, status, status, updatedAt, status, updatedAt, updatedAt};
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
void shouldKeepDraftStudentsUnsubmittedAndExposeMaxDraftUpdatedAt() {
    seedRoster();
    insertFinalRecord(11L, 1001L, "2025-2026", "DRAFT", "2026-07-12 10:15:30.123");
    insertFinalRecord(12L, 1001L, "2025-2026", "DRAFT", "2026-07-12 11:15:30.456");
    insertFinalRecord(13L, 1001L, "2025-2026", "UNKNOWN", "2026-07-12 12:15:30.789");
    insertFinalRecord(14L, 1001L, "2025-2026", null, "2026-07-12 13:15:30.789");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    UnsubmittedStudentRow alice = findRow(page, 1001L);
    assertThat(page.total()).isEqualTo(3);
    assertThat(alice.getLastUpdatedAt()).isEqualTo(Instant.parse("2026-07-12T11:15:30.456Z"));
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .contains(1001L);
    assertThat(page.records()).filteredOn(row -> row.getStudentUserId() == 1001L)
            .hasSize(1);
}

@Test
void shouldKeepDraftStudentsWithNullUpdatedAtAndExposeNullLastUpdatedAt() {
    seedRoster();
    insertFinalRecord(13L, 1001L, "2025-2026", "DRAFT", null);

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2201"), 1, 20)
    );

    UnsubmittedStudentRow alice = findRow(page, 1001L);
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L);
    assertThat(alice.getLastUpdatedAt()).isNull();
}

@Test
void shouldExcludeSubmittedAndConfirmedStudents() {
    seedRoster();
    insertFinalRecord(21L, 1001L, "2025-2026", "SUBMITTED", "2026-07-12 10:15:30");
    insertFinalRecord(22L, 1002L, "2025-2026", "CONFIRMED", "2026-07-12 10:15:30");
    insertFinalRecord(23L, 1003L, "2025-2026", "UNKNOWN", "2026-07-12 10:15:30");

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
    insertFinalRecord(28L, 1003L, "2024-2025", "UNKNOWN", "2026-07-12 13:15:30");
    insertFinalRecord(29L, 1003L, "2024-2025", null, "2026-07-12 14:15:30");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
    assertThat(findRow(page, 1003L).getLastUpdatedAt()).isNull();
}

@Test
void shouldTreatNullFinalRecordStatusAsUnsubmittedWithoutLastUpdatedAtContribution() {
    seedRoster();
    insertFinalRecord(24L, 1003L, "2025-2026", null, "2026-07-12 10:15:30");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2202"), 1, 20)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
    assertThat(findRow(page, 1003L).getLastUpdatedAt()).isNull();
}

@Test
void shouldExcludeStudentsThatHaveDraftAndSubmittedOrConfirmedRecords() {
    seedRoster();
    insertFinalRecord(31L, 1001L, "2025-2026", "DRAFT", "2026-07-12 10:15:30");
    insertFinalRecord(32L, 1001L, "2025-2026", "SUBMITTED", "2026-07-12 11:15:30");
    insertFinalRecord(33L, 1002L, "2025-2026", "DRAFT", "2026-07-12 10:15:30");
    insertFinalRecord(34L, 1002L, "2025-2026", "CONFIRMED", "2026-07-12 11:15:30");

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
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1004, 'S004', 'InactiveUser', 'STUDENT', 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1005, 'S005', 'InactiveMembership', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1006, 'S006', 'NonPrimary', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1007, 'T007', 'TeacherIdentity', 'TEACHER', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1008, 'T008', 'TeacherMembership', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5004, 1004, 4001, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4001, 'STUDENT', 1, 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4001, 'STUDENT', 0, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5007, 1007, 4001, 'STUDENT', 1, 'ACTIVE')");
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
void shouldKeepClassWithoutActiveGradeWhenGradeFilterAbsentAndExcludeWhenGradeRequested() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3002, 2002, 'GRADE', 'CS2023', '计算机2023级', '/WHUT/CS/CS2023', 'INACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4004, 3002, 'CLASS', 'CS2301', '计算机2301班', '/WHUT/CS/CS2023/CS2301', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1004, 'S004', 'NoGrade', 'STUDENT', 'ACTIVE')");
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

Use helpers already present in the integration test for creating authorization contexts. If missing, create `accessContextWithOrgSubtree(Long orgUnitId)`, `accessContextWithOrgUnit(Long orgUnitId)`, `accessContextWithAllScope()`, `accessContextWithUnsupportedCategoryOnly()`, `accessContextWithEmptyGrantedScopes()`, `accessContextWithOrgUnitAndOrgSubtree(Long orgUnitId, Long orgSubtreeRootId)`, and `accessContextWithOrgSubtreeAndUnsupportedCategory(Long orgSubtreeRootId)` by following the existing `AuthorizationScope` test construction style.

- [ ] **Step 3: Write failing scope and filter tests**

Add focused tests:

```java
@Test
void shouldResolveOrgSubtreeUsingRealOrgPath() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2999, NULL, 'COLLEGE', 'CS2', '相似学院', '/WHUT/CS2', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4999, 2999, 'CLASS', 'CS2X', '相似班', '/WHUT/CS2/CS2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1099, 'S099', 'Similar', 'STUDENT', 'ACTIVE')");
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
void shouldApplyOrgUnitExactClassEvenWhenClassPathIsNull() {
    seedRoster();
    jdbcTemplate.update("UPDATE org_unit SET path = NULL WHERE id = 4001");

    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgUnit(4001L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)).records())
            .extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L);
    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)).records())
            .extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1003L);
}

@Test
void shouldFilterGradeAndClassesByCaseSensitiveExactCodeOrNameIntersection() {
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
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1005, 'S005', 'NameMatchesCode', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4005, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> crossUnit = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("CS2201"), 1, 20)
    );

    assertThat(crossUnit.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1005L);

    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4006, 3001, 'CLASS', 'DUP2201', 'DUP2201', '/WHUT/CS/CS2022/DUP2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1006, 'S006', 'SameUnitBothSides', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4006, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> sameUnit = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("DUP2201"), 1, 20)
    );

    assertThat(sameUnit.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1006L);
}

@Test
void shouldMatchDirtyNullUnitCodesThroughExactUnitNames() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3005, 2002, 'GRADE', NULL, '无代码年级', '/WHUT/CS/NO_CODE_GRADE', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4005, 3001, 'CLASS', NULL, '无代码班', '/WHUT/CS/CS2022/NO_CODE_CLASS', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4006, 3005, 'CLASS', 'NC2201', '无代码年级一班', '/WHUT/CS/NO_CODE_GRADE/NC2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1005, 'S005', 'NullClassCode', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1006, 'S006', 'NullGradeCode', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4005, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4006, 'STUDENT', 1, 'ACTIVE')");

    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, List.of("无代码班"), 1, 20)).records())
            .extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1005L);
    assertThat(repository.pageUnsubmittedStudents(accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", "无代码年级", null, 1, 20)).records())
            .extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1006L);
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
void shouldRejectMalformedOrgSubtreeRootPaths() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2010, NULL, 'COLLEGE', 'BAD_NULL', '空值路径学院', NULL, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2011, NULL, 'COLLEGE', 'BAD_BLANK', '空路径学院', '', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2012, NULL, 'COLLEGE', 'BAD_PREFIX', '缺少前缀学院', 'WHUT/CS', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (2013, NULL, 'COLLEGE', 'BAD_TRAILING', '尾斜杠学院', '/WHUT/CS/', 'ACTIVE')");

    for (Long rootId : List.of(2010L, 2011L, 2012L, 2013L)) {
        PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
                accessContextWithOrgSubtree(rootId),
                new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
        );
        assertThat(page.total()).isZero();
        assertThat(page.records()).isEmpty();
    }
}

@Test
void shouldRejectMalformedClassPathsForOrgSubtreeMatching() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4010, 3001, 'CLASS', 'BAD_NULL', '空值路径班', NULL, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4011, 3001, 'CLASS', 'BAD_BLANK', '空路径班', '', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4012, 3001, 'CLASS', 'BAD_PREFIX', '缺少前缀班', 'WHUT/CS/CS2022/BAD_PREFIX', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4013, 3001, 'CLASS', 'BAD_TRAILING', '尾斜杠班', '/WHUT/CS/CS2022/BAD_TRAILING/', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1010, 'S010', 'BadNull', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1011, 'S011', 'BadBlank', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1012, 'S012', 'BadPrefix', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1013, 'S013', 'BadTrailing', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5010, 1010, 4010, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5011, 1011, 4011, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5012, 1012, 4012, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5013, 1013, 4013, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1003L);
}
```

- [ ] **Step 4: Write failing duplicate-membership and pagination tests**

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
void shouldSortNullUnitCodesAfterNonNullCodesWithStableTieBreakers() {
    seedRoster();
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (3005, 2002, 'GRADE', NULL, '无代码年级', '/WHUT/CS/NO_CODE_GRADE', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4005, 3001, 'CLASS', NULL, '无代码班', '/WHUT/CS/CS2022/NO_CODE_CLASS', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_unit (id, parent_id, unit_type, unit_code, unit_name, path, status) VALUES (4006, 3005, 'CLASS', 'NC2201', '无代码年级一班', '/WHUT/CS/NO_CODE_GRADE/NC2201', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1005, 'S005', 'NullClassCode', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1006, 'S006', 'NullGradeCode', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO iam_user (id, user_no, user_name, identity, status) VALUES (1007, NULL, 'NullUserNo', 'STUDENT', 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5005, 1005, 4005, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5006, 1006, 4006, 'STUDENT', 1, 'ACTIVE')");
    jdbcTemplate.update("INSERT INTO org_membership (id, user_id, org_unit_id, membership_type, is_primary, status) VALUES (5007, 1007, 4001, 'STUDENT', 1, 'ACTIVE')");

    PageResult<UnsubmittedStudentRow> page = repository.pageUnsubmittedStudents(
            accessContextWithOrgSubtree(2002L),
            new UnsubmittedFinalRecordQuery("2025-2026", null, null, 1, 20)
    );

    assertThat(page.records()).extracting(UnsubmittedStudentRow::getStudentUserId)
            .containsExactly(1001L, 1002L, 1007L, 1003L, 1005L, 1006L);
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
long countUnsubmittedStudents(@Param("scopeExpression") String scopeExpression,
                              @Param("scopeParameters") Map<String, Object> scopeParameters,
                              @Param("query") UnsubmittedFinalRecordQuery query);

@SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildSelectUnsubmittedStudents")
List<UnsubmittedStudentRow> selectUnsubmittedStudents(@Param("scopeExpression") String scopeExpression,
                                                      @Param("scopeParameters") Map<String, Object> scopeParameters,
                                                      @Param("query") UnsubmittedFinalRecordQuery query);
```

- [ ] **Step 7: Add D-11 SQL provider methods**

In `FinalRecordQuerySqlProvider`, add:

Count and select SQL must keep the same eligibility predicates: active user, `u.identity = 'STUDENT'`, active primary STUDENT membership, active class, scope expression, grade/classes filters, and the submitted/confirmed `NOT EXISTS` exclusion. This is a hard invariant: the count query's grouped visible-student subquery and the select query's inner `visible` subquery must keep equivalent FROM/JOIN/WHERE eligibility semantics for scope, grade, classes, final-record exclusion, membership status, user status, and user identity. They may use different projection and grouping wrapper layers, as shown below, because count only needs one row per visible student while select first picks the lowest visible membership id. Do not add display-only joins, HAVING clauses, ordering, or row-shaping predicates to either eligibility subquery. If you add, delete, or change one eligibility predicate in either SQL, update the other SQL in the same task and add or adjust a count/select alignment assertion. Prefer extracting a small helper that renders the shared eligibility predicates from a small alias descriptor if the final implementation starts to drift from the snippets below.

The select `ORDER BY` intentionally adds `CASE WHEN u.user_no IS NULL THEN 1 ELSE 0 END` before `u.user_no ASC`; this extends the design SQL to make dirty `user_no = NULL` data sort after numbered students consistently on H2 and MySQL. The outer `class_ou` / `grade_ou` type and status predicates duplicate inner eligibility checks defensively so future edits to the inner visible subquery cannot silently display inactive or wrong-type org units.

For `scopeExpression`, the placeholder `__D11_CLASS_ALIAS__` always represents the current visible class `org_unit` alias. In `buildCountUnsubmittedStudents(...)` it is replaced only with `class_ou`. In `buildSelectUnsubmittedStudents(...)` it is replaced only with the inner visible subquery alias `class_ou1`. It must never point at `grade_ou`, `grade_ou1`, the outer display `class_ou`, or caller-provided SQL. Grade and class request filters remain separate fixed expressions over `grade_ou1`/`class_ou1` inside select and `grade_ou`/`class_ou` inside count.

```java
public String buildCountUnsubmittedStudents(Map<String, Object> params) {
    String scopeExpression = scopeExpression(params, "class_ou");
    String castClassPlaceholders = castClassPlaceholders(params);
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
                AND u.identity = 'STUDENT'
                AND (%s)
                AND (#{query.grade} IS NULL OR %s OR %s)
                AND (#{query.classesEmpty} = TRUE OR %s OR %s)
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
                    caseSensitiveIn("class_ou.unit_code", castClassPlaceholders),
                    caseSensitiveIn("class_ou.unit_name", castClassPlaceholders));
}

public String buildSelectUnsubmittedStudents(Map<String, Object> params) {
    String scopeExpression = scopeExpression(params, "class_ou1");
    String castClassPlaceholders = castClassPlaceholders(params);
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
                 AND u1.identity = 'STUDENT'
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
                  AND (#{query.classesEmpty} = TRUE OR %s OR %s)
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
              SELECT student_user_id, MAX(updated_at) AS last_updated_at
              FROM final_record
              WHERE academic_year = #{query.academicYear}
                AND status = 'DRAFT'
              GROUP BY student_user_id
            ) draft_fr
              ON draft_fr.student_user_id = u.id
            ORDER BY CASE WHEN grade_ou.unit_code IS NULL THEN 1 ELSE 0 END ASC,
                     grade_ou.unit_code ASC,
                     CASE WHEN class_ou.unit_code IS NULL THEN 1 ELSE 0 END ASC,
                     class_ou.unit_code ASC,
                     CASE WHEN u.user_no IS NULL THEN 1 ELSE 0 END ASC,
                     u.user_no ASC,
                     u.id ASC
            LIMIT #{query.pageSize} OFFSET #{query.offset}
            """.formatted(scopeExpression,
                    caseSensitiveEquals("grade_ou1.unit_code", "#{query.grade}"),
                    caseSensitiveEquals("grade_ou1.unit_name", "#{query.grade}"),
                    caseSensitiveIn("class_ou1.unit_code", castClassPlaceholders),
                    caseSensitiveIn("class_ou1.unit_name", castClassPlaceholders));
}
```

Then add helper methods. The helper must never output `IN ()`; when classes are empty it outputs `NULL` because the `classesEmpty` branch disables the `IN` predicate. The provider builds indexed MyBatis placeholders and never concatenates raw class values. `scopeExpression`, `caseSensitiveEquals(...)`, and `caseSensitiveIn(...)` may be injected into SQL strings only when they are built from fixed alias names plus MyBatis placeholders generated in this provider/repository; never pass raw request values, grade values, class values, or caller-provided SQL through these helpers. Use `CAST(... AS BINARY)` for case-sensitive comparisons because local H2 2.2.224 rejects MySQL's prefix `BINARY` operator, while H2 MySQL-mode and MySQL both support `CAST(expr AS BINARY)`:

```java
private String castClassPlaceholders(Map<String, Object> params) {
    UnsubmittedFinalRecordQuery query = (UnsubmittedFinalRecordQuery) params.get("query");
    if (query == null || query.isClassesEmpty()) {
        // Defensive placeholder only; query.classesEmpty short-circuits the IN predicates.
        return "NULL";
    }
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
    String expression = (String) params.get("scopeExpression");
    if (expression == null || expression.isBlank()) {
        return "1 = 0";
    }
    // D-11 only replaces the fixed visible class org_unit alias placeholder.
    return expression.replace("__D11_CLASS_ALIAS__", classAlias);
}
```

Add imports for `UnsubmittedFinalRecordQuery`, `ArrayList`, and `List` if they are not already present in `FinalRecordQuerySqlProvider`.

- [ ] **Step 8: Add D-11 scope-fragment builder in repository implementation**

In `MybatisPlusFinalRecordQueryRepository`, implement:

```java
@Override
public PageResult<UnsubmittedStudentRow> pageUnsubmittedStudents(FinalRecordAccessContext accessContext,
                                                                 UnsubmittedFinalRecordQuery query) {
    SqlPredicateFragment fragment = rosterScopeFragment(accessContext);
    long total = finalRecordQueryMapper.countUnsubmittedStudents(fragment.getExpression(), fragment.getParameters(), query);
    if (total == 0) {
        // Optimization only: correctness depends on count/select visible subqueries remaining isomorphic.
        return new PageResult<>(0, List.of());
    }
    List<UnsubmittedStudentRow> records = finalRecordQueryMapper.selectUnsubmittedStudents(
            fragment.getExpression(),
            fragment.getParameters(),
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
        return SqlPredicateFragment.allowAll();
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
        fragments.add("__D11_CLASS_ALIAS__.id IN (" + inSql + ")");
    }
    List<Long> subtreeIds = predicate.getClauses().stream()
            .filter(clause -> "ORG_SUBTREE".equals(clause.getScopeType()))
            .map(ApplicationScopeClause::getOrgSubtreeRootId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (!subtreeIds.isEmpty()) {
        String inSql = bindList(parameters, "d11Subtree", subtreeIds);
        // Double percent signs are Java String.formatted() escapes; the emitted SQL uses single % wildcards.
        fragments.add("""
                EXISTS (
                  SELECT 1
                  FROM org_unit root_ou
                  WHERE root_ou.id IN (%s)
                    AND root_ou.status = 'ACTIVE'
                    AND root_ou.path IS NOT NULL
                    AND root_ou.path <> ''
                    AND root_ou.path LIKE '/%%'
                    AND root_ou.path NOT LIKE '%%/'
                    AND __D11_CLASS_ALIAS__.path IS NOT NULL
                    AND __D11_CLASS_ALIAS__.path <> ''
                    AND __D11_CLASS_ALIAS__.path LIKE '/%%'
                    AND __D11_CLASS_ALIAS__.path NOT LIKE '%%/'
                    AND (
                      __D11_CLASS_ALIAS__.path = root_ou.path
                      OR __D11_CLASS_ALIAS__.path LIKE CONCAT(root_ou.path, '/%%')
                    )
                )
                """.formatted(inSql));
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
        placeholders.add("#{scopeParameters." + key + "}");
    }
    return String.join(", ", placeholders);
}
```

Use imports for `ApplicationScopeClause`, `ArrayList`, `LinkedHashMap`, `Map`, and `Objects`. Prefer `SqlPredicateFragment.allowAll()` and `SqlPredicateFragment.denyAll()` for the static allow/deny branches; use the public constructor only for dynamic D-11 fragments that carry generated SQL and parameters.

- [ ] **Step 9: Run repository integration tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusFinalRecordQueryRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS. The provider must use `CAST(... AS BINARY)` for exact `grade` and `classes` comparisons so the same SQL path is deterministic on H2 MySQL-mode and MySQL.

- [ ] **Step 10: Commit**

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java
git commit -m "feat: query unsubmitted final record roster"
```

---

### Task 4: Controller Route and Request Shape

**Files:**
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java`

- [ ] **Step 1: Write failing controller tests**

Add controller tests:

Use Mockito imports for `anyLong()` and `never()` if the test file does not already import them.

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
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].studentUserId").value(1001))
            .andExpect(jsonPath("$.data.records[0].status").value("UNSUBMITTED"))
            .andExpect(jsonPath("$.data.records[0].lastUpdatedAt").value("2026-07-12T10:15:30.123Z"));
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

    verify(queryApplicationService).pageUnsubmittedStudents(any());
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

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026", "2026-2027")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

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
            .andExpect(jsonPath("$.code").value("VAL-4001"));

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
            .andExpect(jsonPath("$.code").value("VAL-4001"));

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
            .andExpect(jsonPath("$.code").value("VAL-4001"));

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
            .andExpect(jsonPath("$.code").value("VAL-4001"));

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
                    .param("academicYear", "   ")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    MockHttpServletRequestBuilder tooManyClasses = get("/api/admin/final-records/unsubmitted")
            .param("academicYear", "2025-2026")
            .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED)));
    for (int i = 0; i < 501; i++) {
        tooManyClasses.param("classes", "CS" + i);
    }
    mockMvc.perform(tooManyClasses)
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
            .andExpect(jsonPath("$.code").value("VAL-4001"));

    mockMvc.perform(get("/api/admin/final-records/unsubmitted")
                    .param("academicYear", "2025-2026")
                    .param("pageSize", "abc")
                    .with(user("admin").authorities(new SimpleGrantedAuthority(AuthorizationPermissionCodes.SCORE_VIEW_ASSIGNED))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"));
}

```

Do not put the `score.view.assigned` authorization contract only in this standalone controller test file. Add the route security checks to the existing `FinalRecordSecurityIntegrationTest`, which already runs the real Spring Security and method-security configuration:

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
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run controller tests to verify they fail**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=AdminFinalRecordControllerWebMvcTest,FinalRecordSecurityIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: 404 or path-variable capture failure because `/unsubmitted` route does not exist.

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

- [ ] **Step 4: Run controller and earlier tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=AdminFinalRecordControllerWebMvcTest,FinalRecordSecurityIntegrationTest,UnsubmittedFinalRecordQueryTest,FinalRecordQueryApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordSecurityIntegrationTest.java
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
- `FinalRecordScopePredicateBuilder` output structure or clause mapping changed: add Step 2 regressions.
- Any public/shared method named with `scope`, `Scope`, `predicate`, `Predicate`, or org-path matching changed outside a D-11-only private helper: add Step 2 regressions.
- Existing `pageAdminFinalRecords(...)`, `buildSelectAdminFinalRecords(...)`, `buildCountAdminFinalRecords(...)`, `findAdminFinalRecordDetail(...)`, or submitted/confirmed list/detail SQL changed: add Step 2 regressions.
- Existing submitted/confirmed admin list/detail data needed by `FinalRecordAccessValidator` changed: add Step 2 regressions.
- Only new D-11 provider methods and a D-11-only private `rosterScopeFragment(...)` changed: document that no shared-scope files or methods changed and continue to Step 3.

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

- [ ] **Step 3: Run focused D-11 tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=UnsubmittedFinalRecordQueryTest,FinalRecordQueryApplicationServiceTest,MybatisPlusFinalRecordQueryRepositoryIntegrationTest,AdminFinalRecordControllerWebMvcTest,FinalRecordSecurityIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 4: Run final-record regression tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest='*FinalRecord*Test' test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS.

- [ ] **Step 5: Run full test suite with a baseline rule**

Run:

```bash
mvn test
```

Expected: PASS. If unrelated pre-existing failures exist, do not skip this step: capture the failure count and failing test names, verify the focused D-11 tests in Step 3 and final-record regressions in Step 4 still pass, then rerun `mvn test` after the D-11 changes and confirm there are zero new failing tests compared with the recorded baseline. Any new failure in a D-11-touched module fails verification.

- [ ] **Step 6: Review diff for contract drift**

Run:

```bash
git diff --check
git diff --stat main...HEAD
rg -n "pageNum|pages|classId|className.*List|academicYear 不能为空|application_submission|application_fact" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
rg -n "status\s+IN\s*\(\s*'SUBMITTED'\s*,\s*'CONFIRMED'\s*\)|status\s*=\s*'SUBMITTED'|status\s*=\s*'CONFIRMED'|submitted_fr\.status|final_record.*status" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
rg -n "LIKE\s+'%/[0-9]|LIKE\s+CONCAT\('%/',|org_unit_id.*path.*LIKE|path.*LIKE.*org_unit_id" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
```

If `rg` is unavailable in the execution environment, run the equivalent `grep -RInE` command with the regex in double quotes:

```bash
grep -RInE "pageNum|pages|classId|className.*List|academicYear 不能为空|application_submission|application_fact" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
grep -RInE "status\s+IN\s*\(\s*'SUBMITTED'\s*,\s*'CONFIRMED'\s*\)|status\s*=\s*'SUBMITTED'|status\s*=\s*'CONFIRMED'|submitted_fr\.status|final_record.*status" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
grep -RInE "LIKE\s+'%/[0-9]|LIKE\s+CONCAT\('%/',|org_unit_id.*path.*LIKE|path.*LIKE.*org_unit_id" \
  whut-eval-domain whut-eval-application whut-eval-infra whut-eval-interfaces whut-eval-app/src/test
```

Expected:

- `git diff --check` prints no whitespace errors.
- The scope, status, and numeric-path scans are mandatory. If `rg` is available, run the three `rg` commands above; if `rg` is unavailable, run the three `grep -RInE` fallback commands above over the same directories before claiming Task 5 verification is complete.
- The scans pass only when they produce no new D-11 contract-drift hits in changed files. Any new hit fails the verification unless the execution notes name the file/line and explain why the hit is unrelated to D-11 contract drift.
- No `PageResult` metadata fields are added.
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
- Commas inside `grade` and `classes` remain ordinary exact-match characters.
- `PageResult<T>` remains only `total` and `records`.
- Roster SQL starts from active `iam_user`, active primary `org_membership`, and active class `org_unit`.
- Roster SQL requires both `iam_user.identity = 'STUDENT'` and `org_membership.membership_type = 'STUDENT'`.
- Inactive `iam_user`, inactive `org_membership`, non-primary `org_membership`, non-STUDENT identities, and non-STUDENT membership types are excluded by repository tests.
- `DRAFT` records keep students unsubmitted, appear once per student, and only contribute `MAX(updated_at)`.
- A student with both `DRAFT` and `SUBMITTED` or `CONFIRMED` records for the same year is excluded.
- `SUBMITTED` and `CONFIRMED` records exclude students.
- Unknown and `NULL` final-record statuses are ignored and do not contribute to `lastUpdatedAt`.
- `lastUpdatedAt` is rendered as UTC `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` with fixed millisecond precision, or empty string.
- DRAFT rows with `updated_at = NULL` keep the student unsubmitted, expose raw `lastUpdatedAt = null`, and render API `lastUpdatedAt` as an empty string.
- Classes without an active `GRADE` parent can appear without a grade filter, expose raw `grade = null`, and are excluded when a grade filter is present.
- Rows without an active `GRADE` parent sort after rows with active grades.
- `ORG_UNIT` is exact class id only and does not depend on `org_unit.path` being non-null.
- `ORG_SUBTREE` resolves root `org_unit.path` and compares real code paths.
- Similar path prefixes such as `/WHUT/CS2` do not match `/WHUT/CS`.
- Duplicate active primary memberships collapse after scope and filters, selecting the lowest numeric visible membership id.
- Duplicate-membership repository tests assert both `records` and `total` so count and select deduplication stay aligned.
- Count and select SQL keep eligibility predicates aligned: identity, membership, scope, grade/classes, and submitted/confirmed exclusion must change together.
- Grade/classes exact filters are case-sensitive, use code-or-name OR semantics, include distinct code/name matches, and do not duplicate a student when both sides match the same org unit.
- Dirty `unit_code = NULL` rows can still match by exact `unit_name`.
- `pageNo` offset overflow is rejected.
- No D-7, D-8, D-9, D-10, import, export, or frontend behavior is introduced.
