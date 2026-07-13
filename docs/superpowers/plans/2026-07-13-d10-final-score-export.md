# D-10 Final Score Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build D-10 `GET /api/admin/exports/final-scores` so authorized admins can synchronously export scoped final-score totals as an `.xlsx` workbook.

**Architecture:** Add a narrow D-10 export slice beside the existing final-record query flow. The application layer owns request normalization, authorization, no-data handling, row-cap enforcement, and the workbook writer port; infra owns MyBatis export SQL and Apache POI workbook generation; the interface layer owns raw HTTP parameter-shape validation and file response headers.

**Tech Stack:** Java 21, Spring Boot 3, Spring MVC, Spring Security `@PreAuthorize`, MyBatis/MyBatis-Plus SQL providers, Apache POI `XSSFWorkbook`, H2 MySQL-mode integration tests, existing global `BaseAppException` mapping.

---

## Source Inputs

- Spec: `docs/superpowers/specs/2026-07-13-d10-final-score-export-design.md`
- Spec review-loop gate: `loop-966c7665-65d0-4286-8030-e9452f993250`, state `converged`, `p2_fingerprints = []`, `deepseek` timed out; remaining issues are P3 style only.
- D safe-init SQL: `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
- A IAM schema/seed: `docs/team-delivery/group-a-identity-user-admin.sql`
- Current final-record query code:
  - `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java`
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/FinalRecordAccessContext.java`
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/query/FinalRecordPageQuery.java`
- Existing controller/test patterns:
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalRecordController.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalRecordControllerWebMvcTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/iam/GroupAIdentitySqlSeedConsistencyTest.java`

## File Map

Create:

- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportQuery.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportRow.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportFile.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportWorkbookWriter.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportApplicationService.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportGenerationException.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/exporting/PoiFinalScoreExportWorkbookWriter.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalScoreExportController.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportQueryTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportApplicationServiceTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/PoiFinalScoreExportWorkbookWriterTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalScoreExportControllerWebMvcTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalScoreExportControllerSecurityAnnotationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportApplicationContextSmokeTest.java`

Modify:

- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/GroupAIdentitySqlSeedConsistencyTest.java`
- `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`

Do not modify:

- D-7/D-8/D-9 import behavior.
- D-11 unsubmitted roster plan or code.
- Main checkout dirty file `docs/superpowers/plans/2026-07-12-d11-unsubmitted-final-records.md`.
- Existing `/api/admin/final-records` JSON list/detail response contracts except shared repository internals needed for D-10.

## Implementation Tasks

### Task 1: Export Query And File Contracts

**Files:**

- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportQuery.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportRow.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportFile.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportWorkbookWriter.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportGenerationException.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportQueryTest.java`

- [ ] **Step 1: Write failing query normalization tests**

Create `FinalScoreExportQueryTest` with cases for academic-year validation, exact uppercase status validation, blank grade normalization, mixed repeated/comma `classes`, blank-only `classes`, semantic validation priority, and `501` normalized classes tokens:

```java
package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportQuery;
import edu.whut.eval.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalScoreExportQueryTest {

    @Test
    void shouldNormalizeValidQuery() {
        FinalScoreExportQuery query = new FinalScoreExportQuery(
                " 2025-2026 ",
                " ",
                "  ",
                List.of("CS2201, CS2202", "CS2202", "CS2203,, ")
        );

        assertThat(query.academicYear()).isEqualTo("2025-2026");
        assertThat(query.status()).isNull();
        assertThat(query.grade()).isNull();
        assertThat(query.classes()).containsExactly("CS2201", "CS2202", "CS2203");
    }

    @Test
    void shouldRejectInvalidAcademicYearBeforeOtherSemanticErrors() {
        List<String> tooManyClasses = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            tooManyClasses.add("C" + index);
        }

        assertThatThrownBy(() -> new FinalScoreExportQuery("2025/2026", "draft", "CS2022", tooManyClasses))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
    }

    @Test
    void shouldRejectNonConsecutiveAcademicYear() {
        assertThatThrownBy(() -> new FinalScoreExportQuery("2025-2027", null, null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
    }

    @Test
    void shouldRejectLowercaseAndDraftStatus() {
        assertThatThrownBy(() -> new FinalScoreExportQuery("2025-2026", "submitted", null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 SUBMITTED 或 CONFIRMED");
        assertThatThrownBy(() -> new FinalScoreExportQuery("2025-2026", "DRAFT", null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("status 仅允许 SUBMITTED 或 CONFIRMED");
    }

    @Test
    void shouldTreatBlankClassesAsAbsentAndReturnImmutableList() {
        FinalScoreExportQuery missing = new FinalScoreExportQuery("2025-2026", null, null, null);
        FinalScoreExportQuery blank = new FinalScoreExportQuery("2025-2026", null, null, List.of(",,", " "));

        assertThat(blank.classes()).isEmpty();
        assertThat(blank.classes()).isEqualTo(missing.classes());
        assertThatThrownBy(() -> blank.classes().add("CS2201"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectMoreThanFiveHundredNormalizedClassTokens() {
        List<String> raw = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            raw.add("CS" + index);
        }

        assertThatThrownBy(() -> new FinalScoreExportQuery("2025-2026", null, null, raw))
                .isInstanceOf(ValidationException.class)
                .hasMessage("classes 参数过多");
    }
}
```

- [ ] **Step 2: Run the failing query tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=FinalScoreExportQueryTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `FinalScoreExportQuery` does not exist.

- [ ] **Step 3: Implement query/file contracts**

Create `FinalScoreExportQuery` as a Java record. Use `List.copyOf(...)` for immutable classes and validate in the order `academicYear`, `status`, `grade`, `classes`.

Create `FinalScoreExportRow` as an immutable record with the exact fields from the spec. Use nullable object references for grade/class/timestamps and totals so the writer can be null-safe.

Create `FinalScoreExportFile` with defensive copies:

```java
package edu.whut.eval.application.finalrecord.exporting;

import java.util.Arrays;

public final class FinalScoreExportFile {
    private final String filename;
    private final String contentType;
    private final byte[] content;

    public FinalScoreExportFile(String filename, String contentType, byte[] content) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    public String filename() {
        return filename;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
```

Create `FinalScoreExportWorkbookWriter`:

```java
package edu.whut.eval.application.finalrecord.exporting;

import java.util.List;

public interface FinalScoreExportWorkbookWriter {
    FinalScoreExportFile write(String academicYear, List<FinalScoreExportRow> rows);
}
```

Create `FinalScoreExportGenerationException` extending `BaseAppException` with `CommonErrorCode.FILE_STORAGE_FAILED` and message constructor.

- [ ] **Step 4: Run query tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=FinalScoreExportQueryTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: `FinalScoreExportQueryTest` passes.

- [ ] **Step 5: Commit contracts**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting \
        whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportQueryTest.java
git commit -m "feat: add final score export contracts"
```

### Task 2: Application Service Orchestration

**Files:**

- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting/FinalScoreExportApplicationService.java`
- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportApplicationServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create tests that verify:

- the service requires `score.export.assigned`;
- it passes `MAX_SYNC_EXPORT_ROWS + 1` to the repository;
- an empty row list throws `ResourceNotFoundException("无匹配导出数据")`;
- `MAX_SYNC_EXPORT_ROWS + 1` rows throw `FinalScoreExportGenerationException("Excel 生成失败")` before writer invocation;
- writer runtime failures are wrapped as `FinalScoreExportGenerationException`;
- exactly `MAX_SYNC_EXPORT_ROWS` rows are passed to the writer;
- two calls with the same query call the repository twice, documenting current-snapshot semantics.

Use Mockito as in `FinalRecordQueryApplicationServiceTest`. Build row lists with a helper that references `FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS`, not numeric literals.

- [ ] **Step 2: Run failing service tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=FinalScoreExportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because the service and repository export method do not exist.

- [ ] **Step 3: Add repository port method**

Modify `FinalRecordQueryRepository`:

```java
List<FinalScoreExportRow> listAdminFinalScoreExportRows(FinalRecordAccessContext accessContext,
                                                        FinalScoreExportQuery query,
                                                        int limit);
```

Import `FinalScoreExportQuery` and `FinalScoreExportRow`.

- [ ] **Step 4: Implement service**

`FinalScoreExportApplicationService` constructor dependencies:

- `UserAuthorizationContextAssembler`
- `FinalRecordQueryRepository`
- `FinalScoreExportWorkbookWriter`

Implementation behavior:

1. Load `UserAuthorizationContext`.
2. Verify `AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED` is present; otherwise throw `AccessDeniedAppException("当前用户无最终成绩导出权限")`.
3. Build `FinalRecordAccessContext` with permission code `AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED`.
4. Call `listAdminFinalScoreExportRows(accessContext, query, MAX_SYNC_EXPORT_ROWS + 1)`.
5. Empty rows throw `ResourceNotFoundException("无匹配导出数据")`.
6. More than `MAX_SYNC_EXPORT_ROWS` rows log row-cap context and throw `FinalScoreExportGenerationException("Excel 生成失败")` before writer call.
7. Call writer and return `FinalScoreExportFile`.
8. Catch `FinalScoreExportGenerationException` and rethrow; catch other `RuntimeException` from writer, log writer context, and wrap with `FinalScoreExportGenerationException("Excel 生成失败")`.

- [ ] **Step 5: Run service tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=FinalScoreExportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: service tests pass.

- [ ] **Step 6: Commit service**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/exporting \
        whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportApplicationServiceTest.java
git commit -m "feat: add final score export service"
```

### Task 3: POI Workbook Writer

**Files:**

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/exporting/PoiFinalScoreExportWorkbookWriter.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/PoiFinalScoreExportWorkbookWriterTest.java`

- [ ] **Step 1: Write failing workbook tests**

Create `PoiFinalScoreExportWorkbookWriterTest` that opens the returned bytes with `XSSFWorkbook` and asserts:

- content type is `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`;
- filename is `final-scores-2025-2026.xlsx`;
- one sheet named `final-scores`;
- first row is frozen;
- header row exactly matches A-P;
- data region row count equals input size;
- A-P values and cell types for two representative rows, including a row with null grade/class/timestamps and null totals;
- `finalRecordId` is a string cell;
- totals are numeric cells at scale 2 for non-null totals and blank cells for null totals;
- timestamps are UTC `Instant` text truncated to seconds;
- no formulas exist;
- every column width is greater than `8 * 256`, and timestamp columns O/P are at least `20 * 256`.

- [ ] **Step 2: Run failing workbook tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=PoiFinalScoreExportWorkbookWriterTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `PoiFinalScoreExportWorkbookWriter` does not exist.

- [ ] **Step 3: Implement POI writer**

Implementation notes:

- Annotate with `@Component`.
- Use `XSSFWorkbook` and `ByteArrayOutputStream`.
- Build a bold header style and a numeric `0.00` style.
- Write string cells with `CellType.STRING`.
- Use `row.finalRecordId().toString()` for column A.
- Use `BigDecimal.setScale(2, RoundingMode.HALF_UP).doubleValue()` for totals.
- Use blank cells for null totals and null grade/class/timestamp fields.
- Truncate timestamps with `instant.truncatedTo(ChronoUnit.SECONDS).toString()`.
- Set fixed widths after writing rows; do not depend on POI auto-size requiring fonts.
- Wrap `IOException` or POI runtime failures in `FinalScoreExportGenerationException("Excel 生成失败")`.

- [ ] **Step 4: Run workbook tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=PoiFinalScoreExportWorkbookWriterTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: workbook tests pass.

- [ ] **Step 5: Commit writer**

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/exporting/PoiFinalScoreExportWorkbookWriter.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/PoiFinalScoreExportWorkbookWriterTest.java
git commit -m "feat: add final score workbook writer"
```

### Task 4: Repository Export SQL

**Files:**

- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java`
- Modify: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java`

- [ ] **Step 1: Extend H2 schema in repository integration test**

Update the test schema so `org_unit` includes `unit_code`, `unit_type`, and `status`, matching D-10 export joins. Insert a college, grade, and class tree with paths like `/WHUT/CS/CS2022/CS2201`.

- [ ] **Step 2: Add failing export repository tests**

Add integration tests covering:

- requested `academicYear` filters out otherwise matching rows from other academic years;
- default status exports only `SUBMITTED` and `CONFIRMED`, excluding `DRAFT`;
- explicit `SUBMITTED` and explicit `CONFIRMED`;
- grade/class exact case-sensitive filters with lowercase/mixed-case negative checks;
- repeated class tokens already normalized by query object;
- ambiguous grade code/name and class code/name matches;
- token matching both `classCode` and `className` on the same row returns one final record;
- no active primary membership and non-CLASS primary membership remain visible only to `ALL` scope with blank grade/class fields;
- ORG_SUBTREE callers cannot see no-derived-class rows;
- multiple active primary memberships use the smallest `org_membership.id` and return one row;
- `studentUserNo` and `studentUserName` come from `iam_user`;
- deterministic ordering `gradeCode NULLS LAST`, `classCode NULLS LAST`, `studentUserNo`, `finalRecordId`;
- small local `limit` values apply after filters/scope/order;
- `500` normalized classes tokens executes in H2 MySQL-mode;
- unsupported-scope-only and no active export scope rules return empty lists.

- [ ] **Step 3: Run failing repository tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusFinalRecordQueryRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation or assertion failures because export repository methods are not implemented.

- [ ] **Step 4: Implement repository adapter method**

In `MybatisPlusFinalRecordQueryRepository`, reuse the existing private `scopeFragment(accessContext)` and call a new mapper method:

```java
return finalRecordQueryMapper.selectAdminFinalScoreExportRows(
        fragment.getExpression(),
        fragment.getParameters(),
        query,
        limit
);
```

- [ ] **Step 5: Implement mapper and SQL provider**

Add mapper method:

```java
@SelectProvider(type = FinalRecordQuerySqlProvider.class, method = "buildSelectAdminFinalScoreExportRows")
List<FinalScoreExportRow> selectAdminFinalScoreExportRows(@Param("expression") String expression,
                                                          @Param("parameters") Map<String, Object> parameters,
                                                          @Param("query") FinalScoreExportQuery query,
                                                          @Param("limit") int limit);
```

Provider requirements:

- base `final_record fr`;
- `INNER JOIN iam_user u ON u.id = fr.student_user_id`;
- `LEFT OUTER JOIN org_membership om` with the smallest active primary membership id per user;
- `LEFT OUTER JOIN org_unit class_ou` with `unit_type = 'CLASS' AND status = 'ACTIVE'`;
- `LEFT OUTER JOIN org_unit grade_ou` with `unit_type = 'GRADE' AND status = 'ACTIVE'`;
- replace translated scope aliases onto `fr.student_user_id`, `class_ou.id`, `class_ou.path`, and harmless status replacements for category/item unsupported-scope behavior;
- use case-sensitive comparisons for grade/class, following the existing local helper style or adding a small provider helper that works on H2 and MySQL;
- always filter `fr.academic_year = #{query.academicYear}`;
- default absent status to `fr.status IN ('SUBMITTED', 'CONFIRMED')`, explicit status to `fr.status = #{query.status}`;
- order with portable null-last expressions, then `u.user_no`, then `fr.id`;
- `LIMIT #{limit}`;
- select columns matching `FinalScoreExportRow` constructor/property names.

- [ ] **Step 6: Run repository tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisPlusFinalRecordQueryRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: repository integration tests pass, including existing final-record list/detail tests.

- [ ] **Step 7: Commit repository SQL**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/repository/FinalRecordQueryRepository.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordQueryRepository.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQueryMapper.java \
        whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/FinalRecordQuerySqlProvider.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisPlusFinalRecordQueryRepositoryIntegrationTest.java
git commit -m "feat: add final score export repository query"
```

### Task 5: HTTP Controller And Security

**Files:**

- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalScoreExportController.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalScoreExportControllerWebMvcTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalScoreExportControllerSecurityAnnotationTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java`

- [ ] **Step 1: Write failing controller security test**

Assert:

- class-level `@RequestMapping("/api/admin/exports")`;
- method `exportFinalScores(...)` has `@GetMapping("/final-scores")`;
- method `@PreAuthorize` value equals `hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_EXPORT_ASSIGNED)`;
- `AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED` equals `score.export.assigned`.

- [ ] **Step 2: Write failing WebMvc tests**

Use `@WebMvcTest(controllers = AdminFinalScoreExportController.class)` and mock `FinalScoreExportApplicationService`. Cover:

- successful response status, xlsx content type, attachment filename, and workbook bytes;
- raw `classes=A,B&classes=B,C` results in service query classes `[A, B, C]`;
- `pageNo`, blank `pageNo`, repeated `pageNo`, `pageSize`, blank `pageSize`, repeated `pageSize` fail with `导出接口不支持分页参数`;
- repeated `academicYear`, `status`, or `grade` fail with `导出接口不支持重复单值参数`;
- invalid `academicYear` fails through the real endpoint with `400 / VAL-4001 / academicYear 不合法`;
- invalid lowercase or `DRAFT` `status` fails through the real endpoint with `400 / VAL-4001 / status 仅允许 SUBMITTED 或 CONFIRMED`;
- more than `500` normalized `classes` tokens fail through the real endpoint with `400 / VAL-4001 / classes 参数过多`;
- unknown query parameter is ignored;
- service `ResourceNotFoundException("无匹配导出数据")` maps to `404 / RES-4040`;
- service `FinalScoreExportGenerationException("Excel 生成失败")` maps to `503 / EXT-5033`;
- unauthenticated request is rejected with filters enabled without freezing a new response body;
- authenticated user without `SCORE_EXPORT_ASSIGNED` receives `403`.

- [ ] **Step 3: Run failing controller tests**

Run:

```bash
mvn -pl whut-eval-app,whut-eval-interfaces -am -Dtest=AdminFinalScoreExportControllerWebMvcTest,AdminFinalScoreExportControllerSecurityAnnotationTest,FinalRecordControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because the controller does not exist.

- [ ] **Step 4: Implement controller**

Controller method shape:

```java
@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_EXPORT_ASSIGNED)")
@GetMapping("/final-scores")
public ResponseEntity<byte[]> exportFinalScores(HttpServletRequest request) { ... }
```

Implementation requirements:

- inspect `request.getParameterMap()` before constructing `FinalScoreExportQuery`;
- reject `pageNo`/`pageSize` presence first;
- reject repeated `academicYear`, `status`, and `grade`;
- read `classes` via `request.getParameterValues("classes")` and pass raw values unchanged;
- construct `FinalScoreExportQuery`;
- call service;
- copy filename/content type/content bytes to `ResponseEntity<byte[]>`;
- use `Content-Disposition: attachment; filename="..."`;
- do not wrap file bytes in `ApiResponse`.

- [ ] **Step 5: Run controller tests**

Run:

```bash
mvn -pl whut-eval-app,whut-eval-interfaces -am -Dtest=AdminFinalScoreExportControllerWebMvcTest,AdminFinalScoreExportControllerSecurityAnnotationTest,FinalRecordControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: controller tests pass.

- [ ] **Step 6: Commit controller**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminFinalScoreExportController.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalScoreExportControllerWebMvcTest.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminFinalScoreExportControllerSecurityAnnotationTest.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalRecordControllerSecurityAnnotationTest.java
git commit -m "feat: add final score export controller"
```

### Task 6: Safe-Init Scope Rules

**Files:**

- Modify: `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/GroupAIdentitySqlSeedConsistencyTest.java`

- [ ] **Step 1: Write failing SQL consistency tests**

Add assertions for D-10 `iam_scope_rule` rows `8023`, `8024`, `8025`:

- `assignment_id`, `permission_code`, `scope_type`, `org_unit_id`, `category_code`, `item_code`, `expression_json`, `priority`, `status`;
- `created_at` appears in the insert column list;
- rerun is a no-op success;
- unrelated reserved-id collision raises duplicate-key/database error;
- middle-id collision at `8024` aborts before `8023` and `8025` are inserted;
- guard table pattern uses `CREATE TEMPORARY TABLE IF NOT EXISTS d_seed_collision_guard`, `DELETE`, seed row `1`, and duplicate-key collision checks before conditional inserts.

- [ ] **Step 2: Run failing SQL tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=TeamDeliverySqlConsistencyTest,GroupAIdentitySqlSeedConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: D-10 scope assertions fail because safe-init rows are not present or guard semantics are incomplete.

- [ ] **Step 3: Implement safe-init SQL**

Patch `group-d-score-finalization-import-export.safe-init.sql`:

- run all `8023`/`8024`/`8025` guard checks before any export scope insert;
- compare nullable columns with `IS NULL`;
- compare JSON via compact strings after whitespace normalization;
- insert rows conditionally with `WHERE NOT EXISTS`;
- include column list `id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at`;
- use compact JSON literals:
  - `{"scoreRole":"counselor"}`
  - `{"scoreRole":"college_reviewer"}`
  - `{"superAdmin":true}`

- [ ] **Step 4: Run SQL tests**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=TeamDeliverySqlConsistencyTest,GroupAIdentitySqlSeedConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: SQL consistency tests pass.

- [ ] **Step 5: Commit SQL**

```bash
git add docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java \
        whut-eval-app/src/test/java/edu/whut/eval/app/iam/GroupAIdentitySqlSeedConsistencyTest.java
git commit -m "feat: seed final score export scopes"
```

### Task 7: Wiring Smoke And Focused Verification

**Files:**

- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportApplicationContextSmokeTest.java`

- [ ] **Step 1: Write failing production-scan context smoke test**

Create a focused Spring context test that uses component scanning or the same app-level configuration pattern as existing smoke tests to discover beans through the production package layout. Do not directly `@Import(PoiFinalScoreExportWorkbookWriter.class)` in the test, because that would bypass the wiring behavior being verified. Assert the context contains:

- `FinalScoreExportApplicationService`;
- `FinalScoreExportWorkbookWriter`;
- `PoiFinalScoreExportWorkbookWriter`;
- `AdminFinalScoreExportController`.

- [ ] **Step 2: Run failing smoke test**

Run:

```bash
mvn -pl whut-eval-app,whut-eval-interfaces -am -Dtest=FinalScoreExportApplicationContextSmokeTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: failure if component scanning or test configuration is missing required beans.

- [ ] **Step 3: Fix production wiring only if the smoke test proves it is needed**

If the POI writer is not discovered, fix the production wiring in a module that can legally see the infra implementation, such as an app-level test/application configuration or an infra-owned configuration class already scanned by the app. Do not add a broad scan root, and do not add an `@Bean` for `PoiFinalScoreExportWorkbookWriter` inside `whut-eval-application`, because that module must not depend on infra classes.

- [ ] **Step 4: Run focused D-10 verification**

Run:

```bash
mvn -pl whut-eval-app,whut-eval-interfaces -am -Dtest=FinalScoreExport*Test,*FinalScoreExportWorkbookWriterTest,AdminFinalScoreExportControllerWebMvcTest,AdminFinalScoreExportControllerSecurityAnnotationTest,FinalRecordControllerSecurityAnnotationTest,MybatisPlusFinalRecordQueryRepositoryIntegrationTest,TeamDeliverySqlConsistencyTest,GroupAIdentitySqlSeedConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: all focused D-10 tests pass and output includes `AdminFinalScoreExportControllerWebMvcTest` plus `AdminFinalScoreExportControllerSecurityAnnotationTest`.

- [ ] **Step 5: Run D-7/D-8/D-9 import regressions**

Run:

```bash
mvn -pl whut-eval-application -am test-compile
mvn -pl whut-eval-app -am -Dtest=ActivityImportParserTest,ActivityImportApplicationServiceTest,MybatisActivityImportRepositoryTest,MybatisActivityImportRepositoryIntegrationTest,ActivityImportBatchLockTest,ActivityImportApplicationContextSmokeTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest,LectureImportParserTest,LectureImportApplicationServiceTest,MybatisLectureImportRepositoryTest,MybatisLectureImportRepositoryIntegrationTest,LectureImportBatchLockTest,LectureImportApplicationContextSmokeTest,MentorScoreImportParserTest,MentorScoreImportApplicationServiceTest,MybatisMentorScoreImportRepositoryTest,MybatisMentorScoreImportRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: import regression suites pass.

- [ ] **Step 6: Commit final wiring**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportApplicationContextSmokeTest.java
git commit -m "test: verify final score export wiring"
```

If Step 3 required a real wiring change, add only the production configuration file that changed; do not stage unrelated configuration files.

## Self-Review Checklist

- Spec coverage: Tasks 1-7 cover query normalization, raw HTTP shape validation, export authorization, repository filtering/scope/order/limit, workbook A-P mapping, safe-init rules, and verification commands.
- Type consistency: repository method uses `FinalRecordAccessContext`, `FinalScoreExportQuery`, `FinalScoreExportRow`, and `int limit` consistently across application, infra repository, mapper, and tests.
- TDD order: every implementation task starts with a failing test and a targeted Maven command.
- Placeholder scan: this plan contains no `TBD`, no open implementation choices, and no "write tests for the above" placeholder steps.

## Execution Notes

- Keep commits task-sized; do not squash while implementing.
- If a focused command fails because a test class name changed, update the D-10 focused command in the spec and this plan in the same commit as the test rename.
- If review-loop reports worker timeout with no P1/P2 from valid reviewers, record it as tool coverage warning and continue.
