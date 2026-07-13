# D-9 CAS Activity Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build D-9 `POST /api/admin/imports/cas-activities` so authorized admins can synchronously import cultural and sports activity scores into draft final records.

**Architecture:** Add a D-9-specific import slice beside the existing D-7 mentor and D-8 lecture import code. Reuse D-8's controller/service/parser/repository/lock shape only where semantics match, while keeping D-9-specific item metadata, score cap, deterministic batch id, and SPORTS component persistence explicit.

**Tech Stack:** Java 21, Spring Boot 3, Spring MVC multipart, Spring Security `@PreAuthorize`, Spring transactions, MyBatis/MyBatis-Plus, Apache POI `WorkbookFactory` and `DataFormatter`, H2 MySQL-mode integration tests, MySQL `GET_LOCK` / `RELEASE_LOCK` for production batch serialization.

---

## Source Inputs

- Spec: `docs/superpowers/specs/2026-07-13-d9-cas-activity-import-design.md`
- Frozen D delivery contract: `docs/team-delivery/group-d-score-finalization-import-export.md`
- D schema/seed: `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
- E item seed shape: `docs/team-delivery/group-e-platform-governance-attachment-ai.safe-init.sql`
- Existing D-8 implementation:
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportRow.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportApplicationService.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelLectureImportParser.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisLectureImportRepository.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/LectureImportMapper.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/MySqlLectureImportBatchLock.java`
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
- Existing shared application/common types:
  - `whut-eval-application/src/main/java/edu/whut/eval/application/auth/service/UserAuthorizationContextAssembler.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java`
  - `whut-eval-domain/src/main/java/edu/whut/eval/domain/auth/model/UserAuthorizationContext.java`
  - `whut-eval-common/src/main/java/edu/whut/eval/common/exception/AccessDeniedAppException.java`
  - `whut-eval-common/src/main/java/edu/whut/eval/common/exception/FileStorageException.java`
  - `whut-eval-common/src/main/java/edu/whut/eval/common/exception/ValidationException.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/dataobject/FinalRecordDO.java`
- Existing D-8 tests:
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportParserTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisLectureImportRepositoryTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisLectureImportRepositoryIntegrationTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportBatchLockTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java`

## Spec Review Status

The D-9 spec has been reviewed through `review-loop` on clean `/tmp` snapshots because the real worktree's parent `AGENTS.md` causes reviewer timeouts. The latest blocking gate was:

- `loop-f749e019-004b-4bba-a8b4-a5432847b121`
- target digest: `sha256:874a5fcf85b1bc7ccb536c509b7ff275c0d0374cfbe6321a4cd3775c3610b4b6`
- result: `valid_reviewer_count = 2`, `blocking_issue_count = 0`, `deepseek` parse failed

Non-blocking P2/P3 boundary findings from that run were absorbed in `ec48ab2 docs: polish d9 activity spec edges`. Do not keep rerunning spec review indefinitely for new P2/P3 churn before planning; the SDD gate to enter planning is no P0/P1 findings, with actionable P2s either fixed or explicitly bounded.

## File Map

Create:

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportRow.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportFailedRow.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportResult.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportActivitiesCommand.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportApplicationService.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportBatchLock.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportParser.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportRepository.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportStudentTarget.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportedComponent.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportItemDefinition.java`
- `whut-eval-application/src/test/java/edu/whut/eval/application/finalrecord/importing/ActivityImportContractsCompileTest.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelActivityImportParser.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/MySqlActivityImportBatchLock.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ActivityImportMapper.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisActivityImportRepository.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportStudentTargetRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportedComponentRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportItemDefinitionRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityScoreCategoryTotalRow.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/ActivityImportFailedRowResponse.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/ActivityImportResultResponse.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportParserTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportApplicationServiceTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisActivityImportRepositoryTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisActivityImportRepositoryIntegrationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportBatchLockTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportApplicationContextSmokeTest.java`

Modify:

- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java`
- `docs/team-delivery/group-d-score-finalization-import-export.md` only after implementation passes, to mark D-9 implemented.

Do not modify:

- D-7 mentor import semantics.
- D-8 lecture public contracts.
- E-owned `evaluation_item` DDL or seed data unless an implementation test proves the D-9 smoke path cannot run without an existing SPORTS seed.
- B-9 candidate-source contracts.
- `docs/superpowers/plans/2026-07-12-d11-unsubmitted-final-records.md` in the main checkout; it is unrelated dirty work outside this worktree.

## Minimal D-9 Boundaries

Minimal D-9 is a synchronous import endpoint only. It validates activity metadata before mutation, parses a two-column participant workbook, resolves a canonical active SPORTS `evaluation_item`, inserts one `final_component_score` row per successful participant with `source_type = IMPORT` and deterministic `source_ref_id = activityBatchId`, updates draft final-record totals, and returns immediate failed-row receipts.

D-9 does not add an import batch table, async job, failure-file storage, activity catalog, attendance state, student candidate-source storage, frontend UI, D-10 export, or D-11 roster behavior.

Academic-year scope boundary: Minimal D-9 accepts any syntactically valid `YYYY-YYYY` academic year and writes final records for that requested year, but student target lookup and `score.import` scope checks intentionally use current active primary membership, matching D-8. D-9 does not implement historical organization membership or `heldAt`/academic-year-time-sliced authorization. This is an explicit MVP limitation, not an implementation omission.

---

### Task 1: Domain And Application Contracts

**Files:**

- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportRow.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportFailedRow.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportResult.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportActivitiesCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportParser.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportBatchLock.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportRepository.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportStudentTarget.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportedComponent.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportItemDefinition.java`
- Test: `whut-eval-application/src/test/java/edu/whut/eval/application/finalrecord/importing/ActivityImportContractsCompileTest.java`

- [ ] **Step 1: Write the failing compile contract test**

Create `ActivityImportContractsCompileTest`:

```java
package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportResult;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportRow;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ActivityImportContractsCompileTest {
    private ActivityImportContractsCompileTest() {
    }

    static ActivityImportResult constructContracts(ActivityImportParser parser,
                                                   ActivityImportBatchLock lock,
                                                   ActivityImportRepository repository) {
        ActivityImportRow row = new ActivityImportRow(2L, "2022305001", "签到");
        ActivityImportFailedRow failure = new ActivityImportFailedRow(
                2L,
                "STUDENT_NOT_FOUND",
                "studentNo 对应学生不存在或未启用",
                Map.of("studentNo", "2022305001", "displayText", "签到")
        );
        ActivityImportResult result = new ActivityImportResult(
                "ACTIVITY-20252026-20260518143000-ABCDEF123456",
                "校运会志愿服务",
                "SPORTS_COMPETITION",
                new BigDecimal("0.50"),
                1,
                0,
                1,
                List.of(failure)
        );
        ActivityImportedComponent component = new ActivityImportedComponent(
                row.rowNo(),
                1001L,
                row.studentNo(),
                result.itemCode(),
                "SPORTS",
                new BigDecimal("0.50"),
                row.displayText(),
                row.displayText(),
                result.activityBatchId()
        );

        List<ActivityImportRow> parsedRows = parser.parse(new byte[]{1});
        boolean locked = lock.tryAcquire(result.activityBatchId(), Duration.ofSeconds(1));
        Optional<ActivityImportItemDefinition> item = repository.findActiveSportsItem(result.itemCode());
        Optional<ActivityImportStudentTarget> target = repository.findTarget(row.studentNo(), "2025-2026");
        Optional<String> orgPath = repository.findActiveOrgPath(target.map(ActivityImportStudentTarget::orgUnitId).orElse(0L));
        boolean exists = repository.activityBatchExists("2025-2026", "SPORTS", result.itemCode(), result.activityBatchId());
        List<ActivityImportFailedRow> repositoryFailures = repository.insertActivityComponents(
                "2025-2026",
                List.of(component)
        );
        lock.release(result.activityBatchId());

        long totalCount = parsedRows.size()
                + (locked ? 0 : 1)
                + item.stream().count()
                + target.stream().count()
                + orgPath.stream().count()
                + (exists ? 1 : 0);
        return new ActivityImportResult(
                result.activityBatchId(),
                result.title(),
                result.itemCode(),
                result.scoreValue(),
                totalCount,
                result.successCount(),
                repositoryFailures.size() + failure.rowNo(),
                repositoryFailures
        );
    }
}
```

- [ ] **Step 2: Run the contract test and verify it fails**

Run:

```bash
mvn -pl whut-eval-application test-compile
```

Expected: compilation fails because the `ActivityImport*` domain records and ports do not exist.

- [ ] **Step 3: Add the domain records**

Create `ActivityImportRow`:

```java
package edu.whut.eval.domain.finalrecord.importing;

public record ActivityImportRow(
        Long rowNo,
        String studentNo,
        String displayText
) {
}
```

Create `ActivityImportFailedRow`:

```java
package edu.whut.eval.domain.finalrecord.importing;

import java.util.Map;

public record ActivityImportFailedRow(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
```

Create `ActivityImportResult`:

```java
package edu.whut.eval.domain.finalrecord.importing;

import java.math.BigDecimal;
import java.util.List;

public record ActivityImportResult(
        String activityBatchId,
        String title,
        String itemCode,
        BigDecimal scoreValue,
        long totalCount,
        long successCount,
        long failedCount,
        List<ActivityImportFailedRow> failedRows
) {
}
```

- [ ] **Step 4: Add the command, value records, and ports**

Create `ImportActivitiesCommand`:

```java
package edu.whut.eval.application.finalrecord.importing;

public record ImportActivitiesCommand(
        byte[] fileContent,
        String title,
        String itemCode,
        String scoreValue,
        String heldAt,
        String academicYear
) {
}
```

Create `ActivityImportParser`:

```java
package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.ActivityImportRow;

import java.util.List;

public interface ActivityImportParser {
    List<ActivityImportRow> parse(byte[] fileContent);
}
```

Create `ActivityImportBatchLock`:

```java
package edu.whut.eval.application.finalrecord.importing;

import java.time.Duration;

public interface ActivityImportBatchLock {
    boolean tryAcquire(String activityBatchId, Duration timeout);

    void release(String activityBatchId);
}
```

Lock port contract:

- `tryAcquire(...) == true`: lock acquired.
- `tryAcquire(...) == false`: lock was not acquired because the same exact activity batch is already running; the service maps this to `409 / BIZ-4090 / 同一活动批次正在导入，请稍后重试`.
- Storage-unavailable paths, including MySQL `GET_LOCK` returning `NULL` or unexpected SQL errors, must not return `false`; the adapter throws a Spring `DataAccessException` so the existing data-access handler maps it to `500 / SYS-5000 / 数据访问异常，请稍后重试`.

Create `ActivityImportStudentTarget`:

```java
package edu.whut.eval.application.finalrecord.importing;

public record ActivityImportStudentTarget(
        Long studentUserId,
        String studentNo,
        Long orgUnitId,
        String orgPath
) {
}
```

Create `ActivityImportItemDefinition`:

```java
package edu.whut.eval.application.finalrecord.importing;

import java.math.BigDecimal;

public record ActivityImportItemDefinition(
        String itemCode,
        String categoryCode,
        BigDecimal maxPoints,
        boolean allowOverflow
) {
}
```

Create `ActivityImportedComponent`:

```java
package edu.whut.eval.application.finalrecord.importing;

import java.math.BigDecimal;

public record ActivityImportedComponent(
        Long rowNo,
        Long studentUserId,
        String studentNo,
        String canonicalItemCode,
        String categoryCode,
        BigDecimal scoreValue,
        String rawDisplayText,
        String displayText,
        String activityBatchId
) {
}
```

Create `ActivityImportRepository`:

```java
package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.ActivityImportFailedRow;

import java.util.List;
import java.util.Optional;

public interface ActivityImportRepository {
    Optional<ActivityImportItemDefinition> findActiveSportsItem(String itemCode);

    boolean activityBatchExists(String academicYear, String categoryCode, String itemCode, String activityBatchId);

    Optional<ActivityImportStudentTarget> findTarget(String studentNo, String academicYear);

    Optional<String> findActiveOrgPath(Long orgUnitId);

    List<ActivityImportFailedRow> insertActivityComponents(String academicYear, List<ActivityImportedComponent> components);
}
```

- [ ] **Step 5: Run the contract test and commit**

Run:

```bash
mvn -pl whut-eval-application test-compile
```

Expected: pass.

Commit:

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportRow.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportFailedRow.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/ActivityImportResult.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportActivitiesCommand.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportParser.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportBatchLock.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportRepository.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportStudentTarget.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportedComponent.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportItemDefinition.java \
  whut-eval-application/src/test/java/edu/whut/eval/application/finalrecord/importing/ActivityImportContractsCompileTest.java
git commit -m "feat: add activity import contracts"
```

### Task 2: Excel Activity Parser

**Files:**

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelActivityImportParser.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportParserTest.java`

- [ ] **Step 1: Write parser tests**

Create `ActivityImportParserTest` by copying the style of `LectureImportParserTest`, then use these D-9-specific assertions:

```java
@Test
void shouldParseActivityWorkbook() {
    byte[] workbook = workbook("studentNo", "displayText",
            row("2022305001", "志愿服务签到"),
            row("2022305002", ""));

    List<ActivityImportRow> rows = parser.parse(workbook);

    assertThat(rows).containsExactly(
            new ActivityImportRow(2L, "2022305001", "志愿服务签到"),
            new ActivityImportRow(3L, "2022305002", null)
    );
}

@Test
void shouldAcceptHeaderOnlyWorkbook() {
    byte[] workbook = workbook("studentNo", "displayText");

    assertThat(parser.parse(workbook)).isEmpty();
}

@Test
void shouldRejectHeaderMismatch() {
    byte[] workbook = workbook("学号", "displayText", row("2022305001", "签到"));

    assertThatThrownBy(() -> parser.parse(workbook))
            .isInstanceOf(ValidationException.class)
            .hasMessage("导入模板错误：第1列表头应为 studentNo");
}

@Test
void shouldIgnoreExtraColumnsBeyondDisplayText() {
    byte[] workbook = workbook("studentNo", "displayText", "ignored",
            row("2022305001", "签到", "备注不会导入"));

    assertThat(parser.parse(workbook)).containsExactly(
            new ActivityImportRow(2L, "2022305001", "签到")
    );
}

@Test
void shouldRejectOversizedBytesBeforeOpeningWorkbook() {
    byte[] bytes = new byte[5 * 1024 * 1024 + 1];

    assertThatThrownBy(() -> parser.parse(bytes))
            .isInstanceOf(ValidationException.class)
            .hasMessage("文体活动导入文件最多支持 5000 行且不超过 5MB");
}

@Test
void shouldReadFormulaCellWithoutEvaluator() {
    byte[] workbook = workbookWithFormulaDisplayText();

    List<ActivityImportRow> rows = parser.parse(workbook);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).displayText()).isNotBlank();
}
```

Use helper methods from the D-8 parser test, adjusted to two required columns plus optional ignored extras. Header-only and blank data rows must not count toward `totalCount`.

- [ ] **Step 2: Run parser tests and verify failure**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ActivityImportParserTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `ExcelActivityImportParser` is missing.

- [ ] **Step 3: Implement `ExcelActivityImportParser`**

Create `ExcelActivityImportParser`:

```java
package edu.whut.eval.infra.finalrecord.importing;

import edu.whut.eval.application.finalrecord.importing.ActivityImportParser;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExcelActivityImportParser implements ActivityImportParser {

    private static final long MAX_ACTIVITY_IMPORT_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final List<String> REQUIRED_HEADERS = List.of("studentNo", "displayText");

    @Override
    public List<ActivityImportRow> parse(byte[] fileContent) {
        if (fileContent == null) {
            throw new ValidationException("导入模板错误：文件不可解析");
        }
        if (fileContent.length > MAX_ACTIVITY_IMPORT_BYTES) {
            throw new ValidationException("文体活动导入文件最多支持 5000 行且不超过 5MB");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileContent))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ValidationException("导入模板错误：缺少工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new ValidationException("导入模板错误：缺少表头");
            }
            validateHeaders(header, formatter);

            List<ActivityImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String studentNo = cellValue(row.getCell(0), formatter);
                String displayText = cellValue(row.getCell(1), formatter);
                if (isBlank(studentNo) && isBlank(displayText)) {
                    continue;
                }
                rows.add(new ActivityImportRow(i + 1L, studentNo, displayText));
                if (rows.size() > MAX_ROWS) {
                    throw new ValidationException("文体活动导入文件最多支持 5000 行且不超过 5MB");
                }
            }
            return rows;
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ValidationException("导入模板错误：文件不可解析");
        }
    }

    private void validateHeaders(Row header, DataFormatter formatter) {
        for (int i = 0; i < REQUIRED_HEADERS.size(); i++) {
            String actual = cellValue(header.getCell(i), formatter);
            String expected = REQUIRED_HEADERS.get(i);
            if (!expected.equals(actual)) {
                throw new ValidationException("导入模板错误：第" + (i + 1) + "列表头应为 " + expected);
            }
        }
    }

    private String cellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

- [ ] **Step 4: Run parser tests and commit**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ActivityImportParserTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: pass.

Commit:

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelActivityImportParser.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportParserTest.java
git commit -m "feat: parse activity import workbooks"
```

### Task 3: Application Service Validation And Batch Semantics

**Files:**

- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportApplicationService.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportApplicationServiceTest.java`

- [ ] **Step 1: Write service tests first**

Create `ActivityImportApplicationServiceTest` with fakes for `ActivityImportParser`, `ActivityImportRepository`, `ActivityImportBatchLock`, `TransactionOperations`, and `UserAuthorizationContextAssembler`. Cover:

- missing/empty file, title, itemCode, scoreValue, heldAt, and academicYear messages;
- title length > 255, itemCode length > 64, and academicYear values that do not match `YYYY-YYYY` or whose end year is not start year + 1;
- strict `scoreValue` ordering: non-decimal, `>99999999.99`, scale > 2, item cap when `allowOverflow = false`;
- active SPORTS item required, invalid cap metadata exposed by repository as `Optional.empty()`;
- deterministic `activityBatchId` format `^ACTIVITY-[0-9]{8}-[0-9]{14}-[0-9A-F]{12}$`;
- length-prefixed hash input by asserting titles containing `|` and `:` generate distinct ids;
- canonical item code used in response, duplicate detection, and persisted components;
- header-only import returns zero counts and releases lock;
- partial success rejects same-batch retry when repository duplicate check is true with `409 / BIZ-4090 / 同一活动批次已导入`;
- batch lock timeout (`tryAcquire == false`) maps to `409 / BIZ-4090 / 同一活动批次正在导入，请稍后重试`, while lock adapter `DataAccessException` propagates to the existing database-access failure handler;
- successful rows use normalized request title as persisted display text when row `displayText` is blank, while failed-row raw values still return blank as `null`;
- duplicate `studentNo` handling: field-invalid rows do not consume a duplicate key; the first field-valid row consumes `studentNo.trim()` even if later target lookup, scope, or final-record lock processing fails; later field-valid rows with the same normalized key fail with `DUPLICATE_STUDENT`;
- row-level failure code/message table: blank `studentNo` uses `STUDENT_NO_REQUIRED` / `studentNo 不能为空`; over-64 `studentNo` and missing/inactive student target both use `STUDENT_NOT_FOUND` / `studentNo 对应学生不存在或未启用`; over-1000 `displayText` uses `DISPLAY_TEXT_TOO_LONG` / `displayText 长度不能超过 1000`; duplicate student uses `DUPLICATE_STUDENT` / `同一活动批次中学生重复`; out of scope uses `OUT_OF_SCOPE` / `当前用户无权导入该学生文体活动成绩`; locked final record uses `FINAL_RECORD_LOCKED` / `已提交或已确认的最终成绩不允许导入覆盖`;
- row-level failures for blank `studentNo`, over-64 `studentNo`, over-1000-code-point `displayText` after trim, duplicate student, missing target, out of scope, locked final record;
- `failedRows` are sorted by original Excel `rowNo ASC` before building the response, regardless of whether the failure was produced during field validation, lookup/scope checks, or repository locked-record processing.
- `totalCount` is the number of non-blank parsed data rows and equals `successCount + failedCount`; `failedCount` includes every row-level failure category.
- unsupported or empty scope grants no rows;
- defensive service-level missing authority maps through `AccessDeniedAppException`;
- lock release happens through transaction `afterCompletion` when synchronization is active and through a `finally` fallback when synchronization is inactive.

Use test method names such as:

```java
@Test
void shouldGenerateDeterministicLengthPrefixedBatchId()

@Test
void shouldRejectScoreValueUsingFrozenOrder()

@Test
void shouldReturnHeaderOnlyZeroSuccessWithoutPersistedMarker()

@Test
void shouldUseCanonicalItemCodeForResponseDuplicateCheckAndPersistence()
```

- [ ] **Step 2: Run service tests and verify failure**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ActivityImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `ActivityImportApplicationService` is missing.

- [ ] **Step 3: Implement service using D-8 shape with D-9 differences**

Implement `ActivityImportApplicationService` with these concrete rules:

- `normalize(command)` validates request parameters in this order: file, title, itemCode, scoreValue, heldAt, academicYear.
- `normalizeTitle` rejects null/blank title and title values over 255 Unicode code points after Java `String.trim()`.
- `normalizeItemCode` rejects null/blank itemCode and itemCode values over 64 Unicode code points after Java `String.trim()`.
- `normalizeAcademicYear` requires `^(\\d{4})-(\\d{4})$` and rejects values whose end year is not start year + 1.
- Inject `UserAuthorizationContextAssembler`, call `requiredAuthorizationContext()`, and defensively require `AuthorizationPermissionCodes.SCORE_IMPORT`; missing authority throws `AccessDeniedAppException("当前用户无导入权限")`.
- `scoreValue` validation order is: trim and reject blank or non-`STRICT_DECIMAL_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]+)?$")`, parse to `BigDecimal`, reject values `> 99999999.99`, reject scale greater than 2, then reject values above item `maxPoints` when `allowOverflow = false`. Store scale 2 via `setScale(2, RoundingMode.HALF_UP)` only after scale validation.
- `heldAt` parses ISO-8601 datetime strings through `LocalDateTime.parse(heldAt.trim())` such as `2026-05-18T14:30:00`, rejects date-only and date-hour strings by parse failure, and uses `.withNano(0)`.
- `itemCode` lookup calls `repository.findActiveSportsItem(trimmedItemCode)` after request syntax validation.
- `activityBatchId` format is `ACTIVITY-` + `academicYear` without hyphen + `-` + `heldAt` formatted `yyyyMMddHHmmss` + `-` + the first 12 uppercase hexadecimal characters of SHA-256 over `hashInput(request, held)`.
- `hashInput(request, held)` uses length-prefixed segments joined by `|` in this exact order: normalized `title`, canonical `itemCode`, scale-2 plain-string `scoreValue`, normalized `academicYear`, and `held` formatted as `yyyyMMddHHmmss`. Each segment is `codePointCount:value`. Do not use delimiter-only concatenation.
- Lock orchestration follows D-8: enter `transactionOperations.execute(...)`, acquire lock on the request transaction owner connection, map `tryAcquire == false` to `ConflictException("同一活动批次正在导入，请稍后重试")`, let `DataAccessException` from the lock adapter propagate, register release through `TransactionSynchronizationManager` when synchronization is active, run authoritative duplicate check, process rows, and release on the same transaction owner connection in `afterCompletion` or in `finally` if synchronization registration did not happen.
- Duplicate check calls `repository.activityBatchExists(academicYear, "SPORTS", canonicalItemCode, activityBatchId)` and maps a positive result to `ConflictException("同一活动批次已导入")`.
- Duplicate-student detection runs after `validateFields(row)` passes. It uses `studentNo.trim()` as the key, first field-valid row wins and consumes the key, field-invalid rows do not consume the key, and later field-valid duplicates fail even when the first field-valid row later fails lookup, scope, or locked-record processing.
- Field-valid rows are sorted by `studentUserId ASC`, then `rowNo ASC` before persistence.
- For each successful row, `displayText` is `row.displayText().trim()` when present, otherwise the normalized request `title`.
- `validateFields(row)` covers only row field validation: blank `studentNo` with `STUDENT_NO_REQUIRED`, `studentNo` over 64 Unicode code points after trim with `STUDENT_NOT_FOUND`, and `displayText` over 1000 Unicode code points after trim with `DISPLAY_TEXT_TOO_LONG`.
- `canAccess` copies D-8 current-org scope semantics and uses real org paths for `ORG_SUBTREE`. It supports `GLOBAL`, `ORG_UNIT`, and `ORG_SUBTREE`; unsupported or empty scopes grant no rows. For each target, use `orgPathCache.computeIfAbsent(target.orgUnitId(), repository::findActiveOrgPath)` so missing/inactive orgs fail closed. `ORG_UNIT` matches the target `orgUnitId`; `ORG_SUBTREE` matches when the cached target org path equals the scoped org path or starts with scoped org path plus `/`.
- `rawValue` contains exactly `studentNo` and `displayText`, with blank values as `null`.

Use this private request shape:

```java
private record NormalizedRequest(
        String title,
        String itemCode,
        BigDecimal scoreValue,
        LocalDateTime heldAt,
        String academicYear
) {
}
```

Important helper signatures:

```java
private String activityBatchId(NormalizedRequest request)
private String hashInput(NormalizedRequest request, String held)
private String encodeHashPart(String value)
private Optional<ActivityImportFailedRow> validateFields(ActivityImportRow row)
private ActivityImportFailedRow failed(ActivityImportRow row, String code, String message)
private boolean canAccess(UserAuthorizationContext context, ActivityImportStudentTarget target, Map<Long, Optional<String>> orgPathCache)
```

- [ ] **Step 4: Run service tests and commit**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ActivityImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: pass.

Commit:

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ActivityImportApplicationService.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportApplicationServiceTest.java
git commit -m "feat: validate activity import requests"
```

### Task 4: MyBatis Repository And Item Metadata

**Files:**

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ActivityImportMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisActivityImportRepository.java`
- Create: row DTOs listed in File Map.
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisActivityImportRepositoryTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisActivityImportRepositoryIntegrationTest.java`

- [ ] **Step 1: Write repository tests**

Use D-8 repository tests as the base and add D-9-specific cases:

- `findActiveSportsItem` returns only active `category_code = 'SPORTS'` rows;
- null, malformed, non-object, missing-field, wrong-type, negative/out-of-bound `cap_rule_json` returns `Optional.empty()`;
- valid `{"maxPoints": 1.5, "allowOverflow": false}` maps to `ActivityImportItemDefinition`;
- target lookup requires active user, active primary membership, and active org;
- `findActiveOrgPath` returns active org path and returns `Optional.empty()` for missing or inactive org units;
- duplicate-batch check filters by academic year, `SPORTS`, canonical item code, `IMPORT`, and `activityBatchId`;
- missing final record creates DRAFT and inserts SPORTS component;
- existing DRAFT inserts and updates totals/version;
- SUBMITTED/CONFIRMED returns `FINAL_RECORD_LOCKED`;
- two different activity batches for same student/item create two components;
- unexpected totals update failure rolls back inserted components in integration test.

- [ ] **Step 2: Run repository tests and verify failure**

Run:

```bash
mvn -pl whut-eval-app -Dtest=MybatisActivityImportRepositoryTest,MybatisActivityImportRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because mapper/repository files are missing.

- [ ] **Step 3: Implement mapper**

Create the four row DTO classes first. Use ordinary Java classes with private fields plus getters/setters, matching the existing D-8 row DTO style.

Create `ActivityImportStudentTargetRow` with:

```java
private Long studentUserId;
private String studentNo;
private Long orgUnitId;
private String orgPath;
```

Create `ActivityImportedComponentRow` with:

```java
private Long finalRecordId;
private String categoryCode;
private String itemCode;
private BigDecimal scoreValue;
private String displayText;
private String sourceRefId;
private LocalDateTime createdAt;
```

Create `ActivityImportItemDefinitionRow` with:

```java
private String itemCode;
private String categoryCode;
private String capRuleJson;
```

Create `ActivityScoreCategoryTotalRow` with:

```java
private String categoryCode;
private BigDecimal scoreValue;
```

Then create `ActivityImportMapper` with methods equivalent to D-8 plus item lookup. Include MyBatis annotation imports for `@Select`, `@Insert`, `@Options`, `@Update`, and `@Param`; use `@Param` for every SQL-bound scalar argument:

```java
@Mapper
public interface ActivityImportMapper {
    @Select("""
            SELECT item_code AS itemCode,
                   category_code AS categoryCode,
                   cap_rule_json AS capRuleJson
            FROM evaluation_item
            WHERE item_code = #{itemCode}
              AND category_code = 'SPORTS'
              AND status = 'ACTIVE'
            """)
    ActivityImportItemDefinitionRow selectActiveSportsItem(@Param("itemCode") String itemCode);

    @Select("""
            SELECT COUNT(1)
            FROM final_component_score fcs
            JOIN final_record fr ON fr.id = fcs.final_record_id
            WHERE fr.academic_year = #{academicYear}
              AND fcs.category_code = #{categoryCode}
              AND fcs.item_code = #{itemCode}
              AND fcs.source_type = 'IMPORT'
              AND fcs.source_ref_id = #{activityBatchId}
            """)
    long countActivityBatchComponents(@Param("academicYear") String academicYear,
                                      @Param("categoryCode") String categoryCode,
                                      @Param("itemCode") String itemCode,
                                      @Param("activityBatchId") String activityBatchId);

    @Select("""
            SELECT u.id AS student_user_id,
                   u.user_no AS student_no,
                   ou.id AS org_unit_id,
                   ou.path AS org_path
            FROM iam_user u
            JOIN org_membership om ON om.user_id = u.id AND om.status = 'ACTIVE' AND om.is_primary = 1
            JOIN org_unit ou ON ou.id = om.org_unit_id AND ou.status = 'ACTIVE'
            WHERE u.user_no = #{studentNo}
              AND u.status = 'ACTIVE'
            ORDER BY om.id ASC
            LIMIT 1
            """)
    ActivityImportStudentTargetRow selectTarget(@Param("studentNo") String studentNo);

    @Select("SELECT path FROM org_unit WHERE id = #{orgUnitId} AND status = 'ACTIVE'")
    String selectActiveOrgPath(@Param("orgUnitId") Long orgUnitId);

    @Select("SELECT id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at FROM final_record WHERE student_user_id = #{studentUserId} AND academic_year = #{academicYear} FOR UPDATE")
    FinalRecordDO selectFinalRecordForUpdate(@Param("studentUserId") Long studentUserId,
                                             @Param("academicYear") String academicYear);

    @Insert("INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at) VALUES (#{studentUserId}, #{academicYear}, #{status}, #{moralTotal}, #{intellectualTotal}, #{physicalTotal}, #{laborTotal}, #{grandTotal}, #{submittedAt}, #{confirmedAt}, #{confirmComment}, #{version}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDraft(FinalRecordDO record);

    @Insert("INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at) VALUES (#{finalRecordId}, #{categoryCode}, #{itemCode}, #{scoreValue}, #{displayText}, 'IMPORT', #{sourceRefId}, #{createdAt})")
    int insertActivityComponent(ActivityImportedComponentRow row);

    @Select("SELECT category_code AS categoryCode, COALESCE(SUM(score_value), 0) AS scoreValue FROM final_component_score WHERE final_record_id = #{finalRecordId} GROUP BY category_code")
    List<ActivityScoreCategoryTotalRow> selectTotals(@Param("finalRecordId") Long finalRecordId);

    @Update("UPDATE final_record SET moral_total = #{moral}, intellectual_total = #{intellectual}, physical_total = #{physical}, labor_total = #{labor}, grand_total = #{grand}, updated_at = #{updatedAt}, version = version + 1 WHERE id = #{finalRecordId} AND status = 'DRAFT'")
    int updateTotals(@Param("finalRecordId") Long finalRecordId,
                     @Param("moral") BigDecimal moral,
                     @Param("intellectual") BigDecimal intellectual,
                     @Param("physical") BigDecimal physical,
                     @Param("labor") BigDecimal labor,
                     @Param("grand") BigDecimal grand,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
```

SQL requirements:

- `selectActiveSportsItem`: `WHERE item_code = #{itemCode} AND category_code = 'SPORTS' AND status = 'ACTIVE'`.
- `countActivityBatchComponents`: `FROM final_component_score fcs JOIN final_record fr ON fr.id = fcs.final_record_id`, filter `fr.academic_year`, `fcs.category_code`, `fcs.item_code`, `fcs.source_type = 'IMPORT'`, `fcs.source_ref_id`.
- `selectTarget`: active `iam_user`, active primary `org_membership`, active `org_unit`, deterministic `ORDER BY om.id ASC LIMIT 1`.
- `ActivityImportRepository.findTarget(String studentNo, String academicYear)` keeps the `academicYear` argument for parity with D-7/D-8 and a future historical-membership lookup. Minimal D-9 mapper `selectTarget(String studentNo)` intentionally ignores academic year and reads current primary active membership only.
- `selectFinalRecordForUpdate`: lock by `(student_user_id, academic_year)`.
- `insertActivityComponent` is intentionally single-row. Its SQL hard-codes `source_type = 'IMPORT'`; `ActivityImportedComponentRow` does not carry a `sourceType` field. `MybatisActivityImportRepository.insertActivityComponents(...)` loops over sorted components inside one transaction, matching D-8's per-row lock, insert, and total recalculation semantics.
- `updateTotals` must increment `version` in SQL using `version = version + 1`, not by requiring a caller-provided version argument. Every multi-argument mapper method must use `@Param` names that exactly match SQL bind expressions.

- [ ] **Step 4: Implement repository**

Implement `MybatisActivityImportRepository` by adapting `MybatisLectureImportRepository`:

- category and item come from `ActivityImportedComponent`, not constants.
- `findActiveSportsItem` parses `cap_rule_json` with Jackson `ObjectMapper`.
- invalid cap JSON returns `Optional.empty()` and therefore surfaces through the same `404 / RES-4040 / 对应项目定义不存在` request failure as an unavailable item.
- `insertActivityComponents` is `@Transactional`, creates/reloads DRAFT final records, inserts rows, and updates totals after each successful insert.
- `toComponentRow(...)` sets `categoryCode`, `itemCode`, `scoreValue`, `displayText`, `sourceRefId = activityBatchId`, and `createdAt`; `source_type = 'IMPORT'` is hard-coded by the mapper insert SQL.
- locked records return `ActivityImportFailedRow` with raw values exactly `studentNo` and `displayText`.
- duplicate final-record insert handling uses SQLState `23000` or MySQL error code `1062`.

- [ ] **Step 5: Run repository tests and commit**

Run:

```bash
mvn -pl whut-eval-app -Dtest=MybatisActivityImportRepositoryTest,MybatisActivityImportRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: pass.

Commit:

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/ActivityImportMapper.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisActivityImportRepository.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportStudentTargetRow.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportedComponentRow.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityImportItemDefinitionRow.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/ActivityScoreCategoryTotalRow.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisActivityImportRepositoryTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisActivityImportRepositoryIntegrationTest.java
git commit -m "feat: persist activity import components"
```

### Task 5: MySQL Batch Lock

**Files:**

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/MySqlActivityImportBatchLock.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportBatchLockTest.java`

- [ ] **Step 1: Write lock tests**

Create tests mirroring `LectureImportBatchLockTest`:

```java
@Test
void shouldAcquireAndReleaseSameNamedLock() {
    given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class),
            eq("D9_ACTIVITY:batch"), eq(30))).willReturn(1);
    given(jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "D9_ACTIVITY:batch"))
            .willReturn(1);

    assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isTrue();

    lock.release("batch");
    verify(jdbcTemplate).queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "D9_ACTIVITY:batch");
}

@Test
void shouldReturnFalseWhenNamedLockTimesOut() {
    given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class),
            eq("D9_ACTIVITY:batch"), eq(30))).willReturn(0);

    assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isFalse();
}

@Test
void shouldThrowDataAccessExceptionWhenNamedLockReturnsNull() {
    given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class),
            eq("D9_ACTIVITY:batch"), eq(30))).willReturn(null);

    assertThatThrownBy(() -> lock.tryAcquire("batch", Duration.ofSeconds(30)))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("GET_LOCK returned NULL");
}

@Test
void shouldThrowDataAccessExceptionWhenReleaseReturnsZeroOrNull() {
    given(jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "D9_ACTIVITY:batch"))
            .willReturn(0, (Integer) null);

    assertThatThrownBy(() -> lock.release("batch"))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("RELEASE_LOCK did not release activity import batch");
    assertThatThrownBy(() -> lock.release("batch"))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("RELEASE_LOCK did not release activity import batch");
}
```

- [ ] **Step 2: Implement lock adapter**

Create `MySqlActivityImportBatchLock`:

```java
package edu.whut.eval.infra.finalrecord.importing;

import edu.whut.eval.application.finalrecord.importing.ActivityImportBatchLock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MySqlActivityImportBatchLock implements ActivityImportBatchLock {

    private static final String PREFIX = "D9_ACTIVITY:";

    private final JdbcTemplate jdbcTemplate;

    public MySqlActivityImportBatchLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryAcquire(String activityBatchId, Duration timeout) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                lockName(activityBatchId),
                Math.toIntExact(timeout.toSeconds())
        );
        if (result == null) {
            throw new DataAccessResourceFailureException("GET_LOCK returned NULL for activity import batch");
        }
        return result == 1;
    }

    @Override
    public void release(String activityBatchId) {
        Integer result = jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName(activityBatchId));
        if (result == null || result != 1) {
            throw new DataAccessResourceFailureException("RELEASE_LOCK did not release activity import batch");
        }
    }

    private String lockName(String activityBatchId) {
        return PREFIX + activityBatchId;
    }
}
```

- [ ] **Step 3: Run lock tests and commit**

Run:

```bash
mvn -pl whut-eval-app -Dtest=ActivityImportBatchLockTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: pass.

Commit:

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/MySqlActivityImportBatchLock.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportBatchLockTest.java
git commit -m "feat: add activity import batch lock"
```

### Task 6: HTTP Controller And Response DTOs

**Files:**

- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/ActivityImportFailedRowResponse.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/ActivityImportResultResponse.java`
- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`
- Test: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java`

- [ ] **Step 1: Write MVC and security tests**

Add tests for:

- route `POST /api/admin/imports/cas-activities`;
- missing `file/title/itemCode/scoreValue/heldAt/academicYear`;
- title longer than 255 Unicode code points, itemCode longer than 64 Unicode code points, invalid scoreValue variants, invalid heldAt variants, and invalid academicYear variants return the frozen `VAL-4001` messages;
- unsupported, blank, missing, and extensionless filenames rejected before service invocation;
- file > 5 MB rejected before `getBytes()`;
- multipart read failure maps to `503 / EXT-5033 / 文件处理失败，请稍后重试`;
- successful response fields exactly `activityBatchId`, `title`, `itemCode`, `scoreValue`, `totalCount`, `successCount`, `failedCount`, `failedRows`;
- failed row `rawValue` contains exactly `studentNo` and `displayText`;
- security annotation requires `score.import`.

- [ ] **Step 2: Add response DTO records**

Create `ActivityImportFailedRowResponse`:

```java
package edu.whut.eval.interfaces.admin.response;

import java.util.Map;

public record ActivityImportFailedRowResponse(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
```

Create `ActivityImportResultResponse`:

```java
package edu.whut.eval.interfaces.admin.response;

import java.math.BigDecimal;
import java.util.List;

public record ActivityImportResultResponse(
        String activityBatchId,
        String title,
        String itemCode,
        BigDecimal scoreValue,
        long totalCount,
        long successCount,
        long failedCount,
        List<ActivityImportFailedRowResponse> failedRows
) {
}
```

- [ ] **Step 3: Add controller endpoint**

Modify `AdminScoreImportController`:

- inject `ActivityImportApplicationService`;
- add `MAX_ACTIVITY_IMPORT_BYTES = 5L * 1024 * 1024`;
- add `importActivities(...)` with `@PostMapping(value = "/cas-activities", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`;
- controller validates only file-layer conditions before byte reading where possible: file presence, file size, and original filename/extension. Pass title, itemCode, scoreValue, heldAt, and academicYear to `ActivityImportApplicationService`, whose `normalize(command)` owns the frozen business-parameter validation order and messages;
- validate original filename using shared helper that rejects null, blank, extensionless, `.xlsm`, `.csv`, and text;
- wrap `IOException` from `getBytes()` in `FileStorageException("文件处理失败，请稍后重试", exception)`;
- map `ActivityImportResult` to `ActivityImportResultResponse`.

- [ ] **Step 4: Run MVC/security tests and commit**

Run:

```bash
mvn -pl whut-eval-app -Dtest=AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: pass.

Commit:

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/ActivityImportFailedRowResponse.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/ActivityImportResultResponse.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java
git commit -m "feat: expose activity import endpoint"
```

### Task 7: Spring Wiring, Regression, And Delivery Status

**Files:**

- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportApplicationContextSmokeTest.java`
- Modify: `docs/team-delivery/group-d-score-finalization-import-export.md`

- [ ] **Step 1: Add Spring context smoke test**

Create `ActivityImportApplicationContextSmokeTest` using the same pattern as `LectureImportApplicationContextSmokeTest`. Mock `ActivityImportBatchLock` so H2 context startup does not execute MySQL named-lock SQL. Assert these beans exist:

```java
@Autowired AdminScoreImportController controller;
@Autowired ActivityImportApplicationService service;
@Autowired ActivityImportParser parser;
@Autowired ActivityImportRepository repository;
@Autowired ActivityImportMapper mapper;
@MockBean ActivityImportBatchLock batchLock;
```

- [ ] **Step 2: Run focused D-9 and D-7/D-8 regression suite**

Run:

```bash
mvn -pl whut-eval-app -am -Dtest=ActivityImportParserTest,ActivityImportApplicationServiceTest,MybatisActivityImportRepositoryTest,MybatisActivityImportRepositoryIntegrationTest,ActivityImportBatchLockTest,ActivityImportApplicationContextSmokeTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest,LectureImportParserTest,LectureImportApplicationServiceTest,MybatisLectureImportRepositoryTest,MybatisLectureImportRepositoryIntegrationTest,LectureImportBatchLockTest,LectureImportApplicationContextSmokeTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: all targeted tests pass.

- [ ] **Step 3: Update delivery doc after tests pass**

Modify `docs/team-delivery/group-d-score-finalization-import-export.md` only after implementation tests pass. Update D-9 status from deferred/planned to implemented in the same style used for D-7/D-8. Do not mark D-10 or D-11 as implemented.

- [ ] **Step 4: Commit final implementation state**

Run:

```bash
git diff --check
git status --short
```

Expected: only intended D-9 files are modified.

Commit:

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/ActivityImportApplicationContextSmokeTest.java \
  docs/team-delivery/group-d-score-finalization-import-export.md
git commit -m "test: cover activity import wiring"
```

## Final Verification

After all tasks are complete, run:

```bash
mvn -pl whut-eval-app -am -Dtest=ActivityImportParserTest,ActivityImportApplicationServiceTest,MybatisActivityImportRepositoryTest,MybatisActivityImportRepositoryIntegrationTest,ActivityImportBatchLockTest,ActivityImportApplicationContextSmokeTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest,LectureImportParserTest,LectureImportApplicationServiceTest,MybatisLectureImportRepositoryTest,MybatisLectureImportRepositoryIntegrationTest,LectureImportBatchLockTest,LectureImportApplicationContextSmokeTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Then run:

```bash
git diff --check
git status --short
```

Expected:

- targeted D-9 and D-7/D-8 regression tests pass;
- no whitespace errors;
- no generated `.trae-orch` files staged;
- only intentional implementation/docs files differ from `origin/main`.

## Plan Self-Review

Spec coverage:

- HTTP route, permission, multipart params, response shape, request errors, row errors, Excel parser, deterministic batch id, item metadata, cap rules, transaction/lock ordering, row-level scope, persistence, and regression requirements are mapped to Tasks 1 through 7.
- Out-of-scope items remain excluded: D-10, D-11, B-9 candidate storage, import history, async jobs, failure files, frontend UI.

Unresolved-marker scan:

- Scan passed; no unresolved markers are intentionally present.
- Each task has concrete files, tests, commands, and commit points.

Type consistency:

- Domain and application types consistently use `ActivityImport*`.
- Public route uses `/api/admin/imports/cas-activities`.
- Response uses `activityBatchId`, canonical `itemCode`, numeric `scoreValue`, counts, and `failedRows`.
