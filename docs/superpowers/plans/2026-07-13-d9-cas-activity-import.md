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
- Existing D-8 tests:
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportParserTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportApplicationServiceTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisLectureImportRepositoryTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisLectureImportRepositoryIntegrationTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportBatchLockTest.java`
  - `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`

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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityImportContractsCompileTest {

    @Test
    void contractsShouldExposeD9Shapes() {
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

        assertThat(row.rowNo()).isEqualTo(2L);
        assertThat(result.failedRows()).containsExactly(failure);
    }

    @Test
    void portsShouldCompile() {
        ActivityImportParser parser = fileContent -> List.of(new ActivityImportRow(2L, "2022305001", null));
        ActivityImportBatchLock lock = new ActivityImportBatchLock() {
            @Override
            public boolean tryAcquire(String activityBatchId, Duration timeout) {
                return true;
            }

            @Override
            public void release(String activityBatchId) {
            }
        };
        ActivityImportRepository repository = new ActivityImportRepository() {
            @Override
            public Optional<ActivityImportItemDefinition> findActiveSportsItem(String itemCode) {
                return Optional.empty();
            }

            @Override
            public boolean activityBatchExists(String academicYear, String categoryCode, String itemCode, String activityBatchId) {
                return false;
            }

            @Override
            public Optional<ActivityImportStudentTarget> findTarget(String studentNo, String academicYear) {
                return Optional.empty();
            }

            @Override
            public Optional<String> findActiveOrgPath(Long orgUnitId) {
                return Optional.empty();
            }

            @Override
            public List<ActivityImportFailedRow> insertActivityComponents(String academicYear,
                                                                          List<ActivityImportedComponent> components) {
                return List.of();
            }
        };

        assertThat(parser.parse(new byte[]{1})).hasSize(1);
        assertThat(lock.tryAcquire("batch", Duration.ofSeconds(1))).isTrue();
        assertThat(repository.findActiveSportsItem("SPORTS_COMPETITION")).isEmpty();
    }
}
```

- [ ] **Step 2: Run the contract test and verify it fails**

Run:

```bash
mvn -pl whut-eval-application -Dtest=ActivityImportContractsCompileTest test -Dsurefire.failIfNoSpecifiedTests=false
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
mvn -pl whut-eval-application -Dtest=ActivityImportContractsCompileTest test -Dsurefire.failIfNoSpecifiedTests=false
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

Use helper methods from the D-8 parser test, adjusted to two columns. Header-only and blank data rows must not count toward `totalCount`.

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
- partial success rejects same-batch retry when repository duplicate check is true;
- successful rows use normalized request title as persisted display text when row `displayText` is blank, while failed-row raw values still return blank as `null`;
- row-level failures for blank `studentNo`, over-64 `studentNo`, long `displayText`, duplicate student, missing target, out of scope, locked final record;
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

- `normalize(command)` validates request parameters in the spec order.
- `normalizeTitle` rejects null/blank title and title values over 255 Unicode code points after Java `String.trim()`.
- `normalizeItemCode` rejects null/blank itemCode and itemCode values over 64 Unicode code points after Java `String.trim()`.
- `normalizeAcademicYear` requires `^(\\d{4})-(\\d{4})$` and rejects values whose end year is not start year + 1.
- `scoreValue` uses `STRICT_DECIMAL_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]+)?$")`, parses to `BigDecimal`, and stores scale 2 via `setScale(2, RoundingMode.HALF_UP)` only after scale validation.
- `heldAt` parses through `LocalDateTime.parse(heldAt.trim())`, rejects date-only and date-hour strings by parse failure, and uses `.withNano(0)`.
- `itemCode` lookup calls `repository.findActiveSportsItem(trimmedItemCode)` after request syntax validation.
- `activityBatchId` hash input uses `len:value` segments joined by `|`.
- Lock orchestration follows D-8: enter `transactionOperations.execute(...)`, acquire lock, register release through `TransactionSynchronizationManager` when synchronization is active, run authoritative duplicate check, process rows, and release in `finally` if synchronization registration did not happen.
- Duplicate check calls `repository.activityBatchExists(academicYear, "SPORTS", canonicalItemCode, activityBatchId)`.
- Field-valid rows are sorted by `studentUserId ASC`, then `rowNo ASC` before persistence.
- For each successful row, `displayText` is `row.displayText().trim()` when present, otherwise the normalized request `title`.
- `canAccess` copies D-8 current-org scope semantics and uses real org paths for `ORG_SUBTREE`.
- `rawValue` contains exactly `studentNo` and `displayText`, with blank values as `null`.

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

Then create `ActivityImportMapper` with methods equivalent to D-8 plus item lookup:

```java
@Mapper
public interface ActivityImportMapper {
    ActivityImportItemDefinitionRow selectActiveSportsItem(String itemCode);

    long countActivityBatchComponents(String academicYear, String categoryCode, String itemCode, String activityBatchId);

    ActivityImportStudentTargetRow selectTarget(String studentNo);

    String selectActiveOrgPath(Long orgUnitId);

    FinalRecordDO selectFinalRecordForUpdate(Long studentUserId, String academicYear);

    int insertDraft(FinalRecordDO record);

    int insertActivityComponent(ActivityImportedComponentRow row);

    List<ActivityScoreCategoryTotalRow> selectTotals(Long finalRecordId);

    @Update("UPDATE final_record SET moral_total = #{moral}, intellectual_total = #{intellectual}, physical_total = #{physical}, labor_total = #{labor}, grand_total = #{grand}, updated_at = #{updatedAt}, version = version + 1 WHERE id = #{finalRecordId} AND status = 'DRAFT'")
    int updateTotals(Long finalRecordId,
                     BigDecimal moralTotal,
                     BigDecimal intellectualTotal,
                     BigDecimal physicalTotal,
                     BigDecimal laborTotal,
                     BigDecimal grandTotal,
                     LocalDateTime updatedAt);
}
```

SQL requirements:

- `selectActiveSportsItem`: `WHERE item_code = #{itemCode} AND category_code = 'SPORTS' AND status = 'ACTIVE'`.
- `countActivityBatchComponents`: join `final_record fr ON fr.id = fcs.final_record_id`, filter `fr.academic_year`, `fcs.category_code`, `fcs.item_code`, `fcs.source_type = 'IMPORT'`, `fcs.source_ref_id`.
- `selectTarget`: active `iam_user`, active primary `org_membership`, active `org_unit`, deterministic `ORDER BY om.id ASC LIMIT 1`.
- `ActivityImportRepository.findTarget(String studentNo, String academicYear)` keeps the `academicYear` argument for parity with D-7/D-8 and a future historical-membership lookup. Minimal D-9 mapper `selectTarget(String studentNo)` intentionally ignores academic year and reads current primary active membership only.
- `selectFinalRecordForUpdate`: lock by `(student_user_id, academic_year)`.
- `insertActivityComponent` is intentionally single-row. `MybatisActivityImportRepository.insertActivityComponents(...)` loops over sorted components inside one transaction, matching D-8's per-row lock, insert, and total recalculation semantics.
- `updateTotals` must increment `version` in SQL using `version = version + 1`, not by requiring a caller-provided version argument.

- [ ] **Step 4: Implement repository**

Implement `MybatisActivityImportRepository` by adapting `MybatisLectureImportRepository`:

- category and item come from `ActivityImportedComponent`, not constants.
- `findActiveSportsItem` parses `cap_rule_json` with Jackson `ObjectMapper`.
- invalid cap JSON returns `Optional.empty()`.
- `insertActivityComponents` is `@Transactional`, creates/reloads DRAFT final records, inserts rows, and updates totals after each successful insert.
- `toComponentRow(...)` sets `categoryCode`, `itemCode`, `scoreValue`, `displayText`, `sourceType = 'IMPORT'` through SQL, `sourceRefId = activityBatchId`, and `createdAt`.
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
void shouldReturnFalseWhenNamedLockReturnsNull() {
    given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class),
            eq("D9_ACTIVITY:batch"), eq(30))).willReturn(null);

    assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isFalse();
}
```

- [ ] **Step 2: Implement lock adapter**

Create `MySqlActivityImportBatchLock`:

```java
package edu.whut.eval.infra.finalrecord.importing;

import edu.whut.eval.application.finalrecord.importing.ActivityImportBatchLock;
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
        return result != null && result == 1;
    }

    @Override
    public void release(String activityBatchId) {
        jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName(activityBatchId));
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
- validate request params in frozen order before byte reading where possible: file presence/size/filename, title blank/length, itemCode blank/length, scoreValue blank/syntax/range/scale, heldAt blank/parse shape, academicYear blank/regex/year increment;
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
