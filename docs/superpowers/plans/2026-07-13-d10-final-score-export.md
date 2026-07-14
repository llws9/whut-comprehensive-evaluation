# D-10 Final Score Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build D-10 `GET /api/admin/exports/final-scores` so authorized admins can synchronously export scoped final-score totals as an `.xlsx` workbook.

**Frozen D-10 export contract:** D-10 is an HTTP synchronous download endpoint, not a server-side batch file job. The service must not write a workbook to a local output path or persistent export directory. The POI writer returns bytes in memory with filename `final-scores-{academicYear}.xlsx`, and the controller streams those bytes with `Content-Disposition: attachment; filename="final-scores-{academicYear}.xlsx"`. If the scoped query has no matching rows, the endpoint returns `404 / RES-4040 / 无匹配导出数据`; it must not return an empty workbook.

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
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportHttpIntegrationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportApplicationContextSmokeTest.java`

Modify:

- `whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java` (verify `SCORE_EXPORT_ASSIGNED`; add only if absent)
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

Pre-implementation verification already found the permission constant present in this branch:

```java
public static final String SCORE_EXPORT_ASSIGNED = "score.export.assigned";
```

Task 2 and Task 5 still include explicit checks for this constant. If an implementation checkout lacks it, add exactly that constant to `AuthorizationPermissionCodes`; do not create a duplicate.

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
    void shouldRejectMissingOrBlankAcademicYear() {
        assertThatThrownBy(() -> new FinalScoreExportQuery(null, null, null, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> new FinalScoreExportQuery("   ", null, null, List.of()))
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

Create `FinalScoreExportQuery` as a Java record:

```java
public record FinalScoreExportQuery(String academicYear,
                                    String status,
                                    String grade,
                                    List<String> classes) {
    public FinalScoreExportQuery {
        // validate and normalize all fields here
    }
}
```

Use `List.copyOf(...)` for immutable classes and process fields in this order: trim and validate `academicYear`, trim `status`, convert blank `status` to `null`, validate non-null `status`, trim `grade`, convert blank `grade` to `null`, then normalize and cap `classes`. Add a query test that passes `" CS2022 "` as `grade` and asserts `query.grade()` is `"CS2022"`. The `classes` constructor argument is the raw servlet parameter list: each string may contain comma-separated tokens. Normalize `classes` by iterating raw values in order, splitting each raw value by comma, trimming tokens, dropping blank tokens, de-duplicating by first appearance, preserving first-seen order, and storing an immutable list. The allowed normalized token count is at most `500`; `500` is valid, `501` or more throws `ValidationException("classes 参数过多")`.

Create `FinalScoreExportRow` as an immutable record with the exact fields and order below. Use nullable object references for grade/class/timestamps and totals so the writer can be null-safe.

```java
public record FinalScoreExportRow(
        Long finalRecordId,
        Long studentUserId,
        String studentUserNo,
        String studentUserName,
        String gradeCode,
        String gradeName,
        String classCode,
        String className,
        String academicYear,
        String status,
        BigDecimal moralTotal,
        BigDecimal intellectualTotal,
        BigDecimal physicalTotal,
        BigDecimal laborTotal,
        BigDecimal grandTotal,
        Instant submittedAt,
        Instant confirmedAt
) {
}
```

The workbook writer maps this row to columns A-P as: A `finalRecordId`, B `academicYear`, C `studentUserNo`, D `studentUserName`, E `gradeCode`, F `gradeName`, G `classCode`, H `className`, I `status`, J `moralTotal`, K `intellectualTotal`, L `physicalTotal`, M `laborTotal`, N `grandTotal`, O `submittedAt`, P `confirmedAt`. `studentUserId` remains part of the export row contract for service/repository completeness and joins, but D-10's frozen A-P workbook layout does not add a column for it.

Create `FinalScoreExportFile` with defensive copies:

```java
package edu.whut.eval.application.finalrecord.exporting;

import java.util.Arrays;
import java.util.Objects;

public final class FinalScoreExportFile {
    private final String filename;
    private final String contentType;
    private final byte[] content;

    public FinalScoreExportFile(String filename, String contentType, byte[] content) {
        this.filename = Objects.requireNonNull(filename, "filename must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
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

Create `FinalScoreExportGenerationException` extending `BaseAppException` with `CommonErrorCode.FILE_STORAGE_FAILED`, a message constructor, and a `(String message, Throwable cause)` constructor that preserves the original writer failure as `getCause()`.

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
- it captures the `FinalRecordAccessContext` passed to `FinalRecordQueryRepository.listAdminFinalScoreExportRows(...)` and asserts `captured.getPermissionCode()` equals `AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED`, proving D-10 uses `score.export.assigned` and not `score.view.assigned`;
- it passes `MAX_SYNC_EXPORT_ROWS + 1` to the repository;
- an empty row list throws `ResourceNotFoundException("无匹配导出数据")`;
- `MAX_SYNC_EXPORT_ROWS + 1` rows throw `FinalScoreExportGenerationException("Excel 生成失败")` before writer invocation;
- writer runtime failures are wrapped as `FinalScoreExportGenerationException`;
- row-cap overflow and writer runtime failure preserve the same public exception type/message but emit distinct log branches: row-cap logs academic year, normalized filters, returned row count, and `MAX_SYNC_EXPORT_ROWS`; writer failure logs academic year, row count, and original exception type/message;
- exactly `MAX_SYNC_EXPORT_ROWS` rows are passed to the writer;
- two calls with the same query call the repository twice, documenting current-snapshot semantics.

Use Mockito as in `FinalRecordQueryApplicationServiceTest`. Build row lists with a helper that references `FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS`, not numeric literals. Include an `ArgumentCaptor<FinalRecordAccessContext>` assertion like:

```java
ArgumentCaptor<FinalRecordAccessContext> accessContextCaptor =
        ArgumentCaptor.forClass(FinalRecordAccessContext.class);
verify(repository).listAdminFinalScoreExportRows(
        accessContextCaptor.capture(),
        same(query),
        eq(FinalScoreExportApplicationService.MAX_SYNC_EXPORT_ROWS + 1)
);
assertThat(accessContextCaptor.getValue().getPermissionCode())
        .isEqualTo(AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED);
```

Use Spring Boot's existing `OutputCaptureExtension` / `CapturedOutput` test support, as already used in `AuthControllerWebMvcTest`, to assert the two internal log branches. The row-cap test should assert a stable event name such as `final-score-export.row-cap-exceeded` plus the academic year, normalized `status`, normalized `grade`, normalized class list, returned row count, and `MAX_SYNC_EXPORT_ROWS`. The writer-failure test should assert a different stable event name such as `final-score-export.workbook-writer-failed` plus academic year, row count, and the original exception type/message.

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

Annotate `FinalScoreExportApplicationService` with `@Service`.

Public method signature:

```java
public FinalScoreExportFile export(FinalScoreExportQuery query)
```

Define the synchronous row cap as one public service-owned constant and use it everywhere in the application-service implementation:

```java
public static final int MAX_SYNC_EXPORT_ROWS = 20_000;
```

Use `MAX_SYNC_EXPORT_ROWS + 1` only as the repository probe limit. Do not introduce separate hard-coded `20_000` or `20_001` values in service code or service tests.

Row-cap overflow intentionally reuses `FinalScoreExportGenerationException("Excel 生成失败")`, mapping to the frozen public `503 / EXT-5033 / Excel 生成失败` response. Do not replace this with a 4xx validation or payload-too-large response in D-10; the distinction between row-cap overflow and workbook failure is internal logging only.

Implementation behavior:

1. Load `UserAuthorizationContext`.
2. Verify `AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED` is present; otherwise throw `AccessDeniedAppException("当前用户无最终成绩导出权限")`.
3. Build `FinalRecordAccessContext` with permission code `AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED`.
4. Call `listAdminFinalScoreExportRows(accessContext, query, MAX_SYNC_EXPORT_ROWS + 1)`.
5. Empty rows throw `ResourceNotFoundException("无匹配导出数据")`.
6. More than `MAX_SYNC_EXPORT_ROWS` rows log row-cap context and throw `FinalScoreExportGenerationException("Excel 生成失败")` before writer call.
7. Call writer and return `FinalScoreExportFile`.
8. Limit the writer-failure `try/catch` block to the writer invocation in Step 7 only. The row-cap exception thrown in Step 6 must be outside that catch block, must be logged only as `final-score-export.row-cap-exceeded`, and must not also be logged as writer failure. Inside the writer-only catch block, catch `FinalScoreExportGenerationException`, log writer-failure context using `exception.getCause()` when present or the exception itself otherwise, then rethrow the same exception; catch other `RuntimeException` from writer, log writer context using that original exception, and wrap with `FinalScoreExportGenerationException("Excel 生成失败", exception)`. This keeps the writer free to wrap POI/IO failures while still letting the service emit the required writer-failure log branch with original exception type/message.

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
- header row exactly matches these A-P literal values: `最终成绩ID`, `学年`, `学号`, `姓名`, `年级编码`, `年级`, `班级编码`, `班级`, `状态`, `德育总分`, `智育总分`, `体育总分`, `劳育总分`, `总分`, `提交时间`, `确认时间`;
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
- Write the fixed filename `final-scores-{academicYear}.xlsx` into `FinalScoreExportFile`; the controller only forwards this filename into `Content-Disposition`.
- Write string cells with `CellType.STRING`.
- Use `row.finalRecordId().toString()` for column A.
- After writing the header row, call `sheet.createFreezePane(0, 1)` to freeze the first row.
- For non-null totals, use `BigDecimal.setScale(2, RoundingMode.HALF_UP).doubleValue()`; for null totals, write blank cells.
- Use blank cells for null grade/class/timestamp fields.
- Truncate timestamps with `instant.truncatedTo(ChronoUnit.SECONDS).toString()`.
- Set fixed widths after writing rows; do not depend on POI auto-size requiring fonts.
- Wrap `IOException` or POI runtime failures in `FinalScoreExportGenerationException("Excel 生成失败", exception)`, preserving the original exception as the cause for service-layer logging.

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
- those same no-derived-class rows are excluded even for `ALL` scope when `grade` or `classes` filters are present;
- ORG_SUBTREE callers cannot see no-derived-class rows;
- multiple active primary memberships use the smallest `org_membership.id` and return one row;
- `studentUserNo` and `studentUserName` come from `iam_user`;
- deterministic ordering `gradeCode NULLS LAST`, `classCode NULLS LAST`, `studentUserNo`, `finalRecordId`;
- ordering tests must include tied fixtures: at least two export rows with identical `gradeCode` and `classCode` but different `studentUserNo` values to prove `studentUserNo ASC`, and at least two export rows tied on `gradeCode`, `classCode`, and `studentUserNo` but with different `finalRecordId` values to prove `finalRecordId ASC`;
- small local `limit` values apply after filters/scope/order;
- `500` normalized classes tokens is the valid upper boundary and executes in H2 MySQL-mode without error; in a known fixture it should return the same matching row count as the equivalent single-token class filter. `501` normalized tokens is rejected by `FinalScoreExportQuery` before repository execution;
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
- `LEFT OUTER JOIN org_membership om ON om.user_id = fr.student_user_id AND om.status = 'ACTIVE' AND om.is_primary = 1 AND om.id = (SELECT MIN(om2.id) FROM org_membership om2 WHERE om2.user_id = fr.student_user_id AND om2.status = 'ACTIVE' AND om2.is_primary = 1)`;
- `LEFT OUTER JOIN org_unit class_ou ON class_ou.id = om.org_unit_id AND class_ou.unit_type = 'CLASS' AND class_ou.status = 'ACTIVE'`;
- `LEFT OUTER JOIN org_unit grade_ou ON grade_ou.id = class_ou.parent_id AND grade_ou.unit_type = 'GRADE' AND grade_ou.status = 'ACTIVE'`;
- keep all join-key, `status`, `is_primary`, and `unit_type` predicates above inside the `LEFT OUTER JOIN ... ON` clauses. Do not move them into `WHERE`, because rows with no derived class must remain available to `ALL` scope when no grade/classes filters are present;
- repository integration data must include at least two potentially confusable organization branches, for example two active `GRADE` rows and two active `CLASS` rows with overlapping names/codes in different branches, so an incorrect join key such as joining `grade_ou` by path text or joining `class_ou` without `class_ou.id = om.org_unit_id` fails deterministically;
- replace only the final-record scope aliases emitted by `FinalRecordScopePredicateBuilder`: `applicant_user_id` -> `fr.student_user_id`, `org_unit_id` -> `class_ou.id`, and `org_path` -> `class_ou.path`;
- do not replace `category_code`, `item_code`, or unrelated aliases with `fr.status` for D-10 export. The final-record scope builder emits only `ORG_UNIT`/`ORG_SUBTREE` clauses for this resource; unsupported or empty predicates must continue through the existing deny/empty-result semantics from `SqlPredicateFragment.denyAll()` / `1 = 0`;
- implement case-sensitive grade/class predicates with explicit helpers in `FinalRecordQuerySqlProvider`:

```java
private String caseSensitiveEquals(String column, String parameter) {
    return "CAST(" + column + " AS BINARY) = CAST(" + parameter + " AS BINARY)";
}

private String caseSensitiveIn(String column, String collectionExpression) {
    return "CAST(" + column + " AS BINARY) IN (" + collectionExpression + ")";
}
```

For class tokens, build `collectionExpression` only from MyBatis parameter placeholders, for example `CAST(#{query.classes[0]} AS BINARY)`, `CAST(#{query.classes[1]} AS BINARY)`, and so on for the normalized query size, then pass it to `caseSensitiveIn(...)`. Do not concatenate any raw class token value into the SQL string; the only dynamic string assembly is the trusted placeholder index sequence derived from `query.classes().size()`. MyBatis provider SQL still receives the final SQL string with `#{...}` placeholders and binds the actual list values. Add a repository test with a class token containing quote/comma-like punctuation to prove values remain parameter-bound and do not break SQL syntax. The H2/MySQL-mode verification for this plan showed `CAST(column AS BINARY)` executes and is case-sensitive; do not use MySQL-only `BINARY column` syntax or `COLLATE`, because the existing H2 path rejects them.
- when `query.grade` is present, append `(` + `caseSensitiveEquals("grade_ou.unit_code", "#{query.grade}")` + ` OR ` + `caseSensitiveEquals("grade_ou.unit_name", "#{query.grade}")` + `)` in the `WHERE` clause;
- when `query.classes` is non-empty, append `(` + `caseSensitiveIn("class_ou.unit_code", collectionExpression)` + ` OR ` + `caseSensitiveIn("class_ou.unit_name", collectionExpression)` + `)` in the `WHERE` clause;
- grade and classes predicates must be added to `WHERE`, not to any `LEFT OUTER JOIN ... ON` condition. This ensures rows with no derived grade/class are excluded whenever grade/classes filters are present, while still preserving those rows for `ALL` scope when no grade/classes filters are present;
- always filter `fr.academic_year = #{query.academicYear}`;
- default absent status to `fr.status IN ('SUBMITTED', 'CONFIRMED')`, explicit status to `fr.status = #{query.status}`;
- order exactly by `CASE WHEN grade_ou.unit_code IS NULL THEN 1 ELSE 0 END`, then `grade_ou.unit_code`, then `CASE WHEN class_ou.unit_code IS NULL THEN 1 ELSE 0 END`, then `class_ou.unit_code`, then `u.user_no`, then `fr.id`;
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
- `AuthorizationPermissionCodes.SCORE_EXPORT_ASSIGNED` exists and equals `score.export.assigned`. The current branch already has this constant; if a future implementation checkout does not, add exactly `public static final String SCORE_EXPORT_ASSIGNED = "score.export.assigned";` to `AuthorizationPermissionCodes`.

- [ ] **Step 2: Write failing WebMvc tests**

Use `@WebMvcTest(controllers = AdminFinalScoreExportController.class)` and mock `FinalScoreExportApplicationService`. Cover:

- successful response status, xlsx content type, attachment filename, and workbook bytes;
- raw `classes=A,B&classes=B,C` results in service query classes `[A, B, C]`;
- `pageNo`, blank `pageNo`, repeated `pageNo`, `pageSize`, blank `pageSize`, repeated `pageSize` fail with `400 / VAL-4001 / 导出接口不支持分页参数`;
- repeated `academicYear`, `status`, or `grade` fail with `400 / VAL-4001 / 导出接口不支持重复单值参数`;
- a combined invalid request containing both a pagination parameter and repeated single-value parameters, for example `pageNo=1&academicYear=2025-2026&academicYear=2026-2027`, fails with `400 / VAL-4001 / 导出接口不支持分页参数`, proving pagination-parameter rejection has priority;
- invalid `academicYear` fails through the real endpoint with `400 / VAL-4001 / academicYear 不合法`;
- invalid lowercase or `DRAFT` `status` fails through the real endpoint with `400 / VAL-4001 / status 仅允许 SUBMITTED 或 CONFIRMED`;
- `501` or more normalized `classes` tokens fail through the real endpoint with `400 / VAL-4001 / classes 参数过多`;
- unknown query parameter is ignored;
- service `ResourceNotFoundException("无匹配导出数据")` maps to `404 / RES-4040`;
- unknown `grade` or unknown `classes` values are accepted as filters, not rejected as request errors; when they produce no repository rows, the endpoint returns `404 / RES-4040 / 无匹配导出数据`, not `400 / VAL-4001`;
- service `FinalScoreExportGenerationException("Excel 生成失败")` maps to `503 / EXT-5033`;
- unauthenticated request is rejected by the existing Spring Security filter chain; D-10 must not introduce a new 401/403 response contract beyond the global security layer behavior;
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
- read `classes` via `request.getParameterValues("classes")`. If it returns `null`, pass `null` to `FinalScoreExportQuery`; otherwise convert the returned raw `String[]` to a `List<String>` preserving array order and pass that raw list unchanged into `new FinalScoreExportQuery(academicYear, status, grade, rawClasses)`;
- construct `FinalScoreExportQuery`. The query object owns splitting/trimming/deduping `classes` and throwing `ValidationException("classes 参数过多")` when the normalized token count is `501` or more; the controller must not duplicate the 500-token validation or pre-normalize class tokens;
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
- exact expected values:

| id | assignment_id | permission_code | scope_type | org_unit_id | category_code | item_code | expression_json | priority | status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `8023` | `7010` | `score.export.assigned` | `ORG_SUBTREE` | `2002` | `NULL` | `NULL` | `{"scoreRole":"counselor"}` | `80` | `ACTIVE` |
| `8024` | `7011` | `score.export.assigned` | `ORG_SUBTREE` | `2002` | `NULL` | `NULL` | `{"scoreRole":"college_reviewer"}` | `70` | `ACTIVE` |
| `8025` | `7012` | `score.export.assigned` | `ALL` | `NULL` | `NULL` | `NULL` | `{"superAdmin":true}` | `1000` | `ACTIVE` |

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
- compare JSON via compact strings after whitespace normalization by removing ordinary spaces, tabs, line feeds, and carriage returns before comparing with the fixed compact literals;
- guard comparisons intentionally exclude `created_at`; compare only `assignment_id`, `permission_code`, `scope_type`, `org_unit_id`, `category_code`, `item_code`, `expression_json`, `priority`, and `status`, because clean reruns may preserve the original timestamp;
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

### Task 7: HTTP Export Integration

**Files:**

- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportHttpIntegrationTest.java`

- [ ] **Step 1: Write failing full-chain HTTP integration test**

Create a Spring integration test that uses the real production beans for controller, application service, repository, SQL provider, and workbook writer with an H2 MySQL-mode database. Use `MockMvc` against `GET /api/admin/exports/final-scores`, then open the response bytes with `XSSFWorkbook`.

Minimum assertions:

- request `academicYear=2025-2026&classes=CS2201` with an authenticated admin authority `score.export.assigned`;
- the test authorization context has an active export `ALL` or matching `ORG_SUBTREE` scope rule;
- seeded H2 rows include one matching `SUBMITTED` final record and one non-matching row filtered by class or year;
- HTTP status is `200`;
- `Content-Type` is xlsx;
- `Content-Disposition` is an attachment filename ending in `final-scores-2025-2026.xlsx`;
- workbook sheet `final-scores` exists;
- header row contains the frozen A-P literal labels;
- first data row contains the expected final record id, academic year, student number, student name, class code, status, and total cells from the seeded database;
- assert column A `finalRecordId` is a `CellType.STRING` cell whose value equals the seeded final record id string, matching the POI writer contract that uses `row.finalRecordId().toString()`.

Use the same production-facing security-context adapter pattern as `FinalRecordSecurityIntegrationTest` / `SecurityProbeControllerWebMvcTest` instead of a direct mocked `UserAuthorizationContextAssembler`:

- import the JWT security slice used by those tests: `SecurityConfiguration`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`, `JwtAuthenticationFilter`, `JwtTokenResolver`, `JwtClaimsParser`, `JwtClaimsToCurrentUserMapper`, `SecurityContextCurrentUserProvider`, `SecurityContextUserAuthorizationContextAssembler`, and `JwtConfigurationValidator`;
- set the same `infra.security.jwt.*` test properties and create a valid Bearer access token with user id, user no/name, identity, roles, authorities, session id, and token type claims;
- mock only `UserAuthorizationContextLoader` and `AccessSessionService` from the security boundary. The loader must return a `UserAuthorizationContext` built from the `UserAuthorizationContextLoadRequest`, with authority `score.export.assigned` and an active export `ALL` or matching `ORG_SUBTREE` `IamScopeRule`;
- call MockMvc with `Authorization: Bearer <token>` so `JwtAuthenticationFilter` creates `CurrentUser`, `SecurityContextCurrentUserProvider` reads it from `SecurityContextHolder`, and `SecurityContextUserAuthorizationContextAssembler` is the `UserAuthorizationContextAssembler` seen by `FinalScoreExportApplicationService`;
- do not use `@AutoConfigureMockMvc(addFilters = false)` for this full-chain HTTP test unless the test also proves the service still receives its authorization context through `SecurityContextUserAuthorizationContextAssembler`.

The test must not mock `FinalScoreExportApplicationService`, `FinalRecordQueryRepository`, or `FinalScoreExportWorkbookWriter`.

- [ ] **Step 2: Run failing full-chain integration test**

Run:

```bash
mvn -pl whut-eval-app,whut-eval-interfaces -am -Dtest=FinalScoreExportHttpIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: fails until the controller, service, repository SQL, safe-init-related test schema, and workbook writer are all implemented and wired.

- [ ] **Step 3: Make the integration test pass without test-only production substitutions**

If the full-chain test fails because a production bean is missing, fix production `src/main` wiring as described in Task 8. If it fails because seed/test schema data is incomplete, add only the minimum H2 setup needed by this test. Do not mock the service/repository/writer to make this test pass.

- [ ] **Step 4: Commit HTTP integration test**

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/FinalScoreExportHttpIntegrationTest.java
git commit -m "test: cover final score export http integration"
```

If Step 3 required a production wiring change, include only that production file in this commit.

### Task 8: Wiring Smoke And Focused Verification

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

If the POI writer or controller is not discovered, fix only production `src/main` wiring in a module that can legally see the infra/interface implementation, such as an infra-owned or app-owned production configuration class already scanned by the app. Do not use `@TestConfiguration`, test-scope configuration, test-only `@Import`, or direct test imports to make the smoke test pass. Do not add a broad scan root, and do not add an `@Bean` for `PoiFinalScoreExportWorkbookWriter` inside `whut-eval-application`, because that module must not depend on infra classes.

- [ ] **Step 4: Run focused D-10 verification**

Run:

```bash
mvn -pl whut-eval-app,whut-eval-interfaces -am -Dtest=FinalScoreExportQueryTest,FinalScoreExportApplicationServiceTest,PoiFinalScoreExportWorkbookWriterTest,FinalScoreExportHttpIntegrationTest,FinalScoreExportApplicationContextSmokeTest,AdminFinalScoreExportControllerWebMvcTest,AdminFinalScoreExportControllerSecurityAnnotationTest,FinalRecordControllerSecurityAnnotationTest,MybatisPlusFinalRecordQueryRepositoryIntegrationTest,TeamDeliverySqlConsistencyTest,GroupAIdentitySqlSeedConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
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

- Spec coverage: Tasks 1-8 cover query normalization, raw HTTP shape validation, export authorization, repository filtering/scope/order/limit, workbook A-P mapping, safe-init rules, full-chain HTTP-to-xlsx integration, and verification commands.
- Type consistency: repository method uses `FinalRecordAccessContext`, `FinalScoreExportQuery`, `FinalScoreExportRow`, and `int limit` consistently across application, infra repository, mapper, and tests.
- TDD order: every implementation task starts with a failing test and a targeted Maven command.
- Placeholder scan: this plan contains no `TBD`, no open implementation choices, and no "write tests for the above" placeholder steps.

## Execution Notes

- Keep commits task-sized; do not squash while implementing.
- If a focused command fails because a test class name changed, update the D-10 focused command in the spec and this plan in the same commit as the test rename.
- If review-loop reports worker timeout with no P1/P2 from valid reviewers, record it as tool coverage warning and continue.
