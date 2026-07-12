# D-8 Lecture Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build D-8 `POST /api/admin/imports/lectures` so authorized admins can synchronously import lecture attendance scores into draft final records.

**Architecture:** Add a D-8-specific import slice beside the existing D-7 mentor import code instead of extending D-7's upsert model. The interface layer reads multipart bytes and metadata, the application layer owns request/row validation, authorization, deterministic batch ids, duplicate-batch behavior, lock orchestration, and row ordering, while the infra layer owns POI parsing, MyBatis persistence, and the production MySQL named-lock adapter. Successful D-8 rows insert new `INTELLECTUAL/INTELLECTUAL_LECTURE` components with `source_type = IMPORT` and `source_ref_id = lectureBatchId`.

**Tech Stack:** Java 21, Spring Boot 3, Spring MVC multipart, Spring Security `@PreAuthorize`, Spring transactions, MyBatis/MyBatis-Plus, Apache POI `WorkbookFactory`, H2 MySQL-mode integration tests, MySQL `GET_LOCK` / `RELEASE_LOCK` for production batch serialization.

---

## Source Inputs

- Spec: `docs/superpowers/specs/2026-07-13-d8-lecture-import-design.md`
- Existing D-7 plan: `docs/superpowers/plans/2026-07-13-d7-mentor-score-import.md`
- Existing D-7 implementation:
  - `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
  - `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportApplicationService.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelMentorScoreImportParser.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisMentorScoreImportRepository.java`
  - `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/MentorScoreImportMapper.java`
- D schema: `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
- Frozen delivery doc: `docs/team-delivery/group-d-score-finalization-import-export.md`

## Spec Review Status

The D-8 spec passed a blocking review gate in `loop-f3d76838-0c22-46b3-abb7-b762df225c39` with `state = max_rounds_reached_non_blocking` and no P0/P1 findings. Later non-blocking boundary items were absorbed in commits `8031577` and `8e5d3d0`. A final rerun `loop-20d55383-9266-4b74-b6ca-bb2e5f086093` failed because all reviewer outputs were invalid or timed out; raw `doubao` output stated no P0/P1 findings, and its actionable boundary notes were absorbed.

Do not use the failed final rerun as a spec blocker. Do keep its tool failure path as evidence if later reporting asks why there is no clean final review-loop state after `8e5d3d0`.

## File Map

Create:

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportRow.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportFailedRow.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportResult.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportLecturesCommand.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportApplicationService.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportBatchLock.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportParser.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportRepository.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportStudentTarget.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportedComponent.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelLectureImportParser.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/MySqlLectureImportBatchLock.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/LectureImportMapper.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisLectureImportRepository.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureImportStudentTargetRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureImportedComponentRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureScoreCategoryTotalRow.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/LectureImportFailedRowResponse.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/LectureImportResultResponse.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportParserTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportApplicationServiceTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisLectureImportRepositoryIntegrationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportBatchLockTest.java`

Modify:

- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java`

Do not modify `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/config/FinalRecordApplicationConfiguration.java`; the production lock adapter is a `@Component`, and tests should use fakes or `@MockBean` at the test boundary.

Do not modify D-7 mentor import semantics except for shared controller constructor wiring and shared helper extraction that is strictly necessary.

## Minimal D-8 Boundaries

Minimal D-8 means the synchronous lecture-import MVP described by the frozen D-8 spec. It includes deterministic batch ids, batch serialization, row-level validation, current-organization scope checks, insert-only lecture components, and final-record total recalculation. It does not add batch deletion, patch-retry semantics, historical organization membership, asynchronous import jobs, or new row-level failure codes beyond the frozen failure table.

`studentNo` validation intentionally follows the frozen failure table: blank values fail with `STUDENT_NO_REQUIRED`; non-blank values, including unusually long or oddly formatted text, are looked up through parameterized `iam_user.user_no = ?` SQL and fail as `STUDENT_NOT_FOUND` when no active eligible student matches. The real IAM schema defines `iam_user.user_no` as `VARCHAR(64)`, but D-8 does not add a separate length/format failure code.

---

### Task 1: Domain And Application Contracts

**Files:**

- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportRow.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportFailedRow.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportResult.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportLecturesCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportParser.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportBatchLock.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportRepository.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportStudentTarget.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportedComponent.java`

- [ ] **Step 1: Add immutable domain result types**

Create `LectureImportRow`:

```java
package edu.whut.eval.domain.finalrecord.importing;

public record LectureImportRow(
        Long rowNo,
        String studentNo,
        String scoreValue,
        String displayText
) {
}
```

Create `LectureImportFailedRow`:

```java
package edu.whut.eval.domain.finalrecord.importing;

import java.util.Map;

public record LectureImportFailedRow(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
```

Create `LectureImportResult`:

```java
package edu.whut.eval.domain.finalrecord.importing;

import java.time.LocalDateTime;
import java.util.List;

public record LectureImportResult(
        String lectureBatchId,
        String title,
        LocalDateTime heldAt,
        String academicYear,
        long totalCount,
        long successCount,
        long failedCount,
        List<LectureImportFailedRow> failedRows
) {
}
```

- [ ] **Step 2: Add application command and ports**

Create `ImportLecturesCommand`:

```java
package edu.whut.eval.application.finalrecord.importing;

public record ImportLecturesCommand(
        byte[] fileContent,
        String title,
        String heldAt,
        String academicYear
) {
}
```

Create `LectureImportParser`:

```java
package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;

import java.util.List;

public interface LectureImportParser {
    List<LectureImportRow> parse(byte[] fileContent);
}
```

Create `LectureImportBatchLock`:

```java
package edu.whut.eval.application.finalrecord.importing;

import java.time.Duration;

public interface LectureImportBatchLock {
    boolean tryAcquire(String lectureBatchId, Duration timeout);

    void release(String lectureBatchId);
}
```

Create `LectureImportRepository`:

```java
package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;

import java.util.List;
import java.util.Optional;

public interface LectureImportRepository {
    boolean lectureBatchExists(String academicYear, String lectureBatchId);

    /**
     * The academicYear argument is reserved for historical-organization lookup; Minimal D-8 reads current membership only.
     */
    Optional<LectureImportStudentTarget> findTarget(String studentNo, String academicYear);

    Optional<String> findActiveOrgPath(Long orgUnitId);

    List<LectureImportFailedRow> insertLectureComponents(String academicYear,
                                                         String lectureBatchId,
                                                         List<LectureImportedComponent> components);
}
```

Create `LectureImportStudentTarget`:

```java
package edu.whut.eval.application.finalrecord.importing;

public record LectureImportStudentTarget(
        Long studentUserId,
        String studentNo,
        Long orgUnitId,
        String orgPath
) {
}
```

Create `LectureImportedComponent`:

```java
package edu.whut.eval.application.finalrecord.importing;

import java.math.BigDecimal;

public record LectureImportedComponent(
        Long rowNo,
        Long studentUserId,
        String studentNo,
        String scoreValueText,
        BigDecimal scoreValue,
        String displayText
) {
}
```

- [ ] **Step 3: Compile the contract slice**

Run:

```bash
mvn -pl whut-eval-application -am -DskipTests compile
```

Expected: compile passes. If it fails for missing imports or package names, fix the new files before continuing.

- [ ] **Step 4: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportRow.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportFailedRow.java \
  whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/LectureImportResult.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportLecturesCommand.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportParser.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportBatchLock.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportRepository.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportStudentTarget.java \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportedComponent.java
git commit -m "feat: add lecture import contracts"
```

---

### Task 2: Excel Lecture Parser

**Files:**

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelLectureImportParser.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportParserTest.java`

- [ ] **Step 1: Write parser tests**

Create `LectureImportParserTest` with these tests:

```java
package edu.whut.eval.app.finalrecord;

import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;
import edu.whut.eval.infra.finalrecord.importing.ExcelLectureImportParser;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LectureImportParserTest {

    private final ExcelLectureImportParser parser = new ExcelLectureImportParser();

    @Test
    void shouldParseValidRowsWithPhysicalRowNumbersAndTrimmedRawValues() throws Exception {
        byte[] workbook = xlsx(row(" 2022305001 ", " 0.50 ", " 签到 "));

        List<LectureImportRow> rows = parser.parse(workbook);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).rowNo()).isEqualTo(2L);
        assertThat(rows.get(0).studentNo()).isEqualTo("2022305001");
        assertThat(rows.get(0).scoreValue()).isEqualTo("0.50");
        assertThat(rows.get(0).displayText()).isEqualTo("签到");
    }

    @Test
    void shouldSkipBlankRowsAndIgnoreExtraColumns() throws Exception {
        byte[] workbook = xlsx(
                row(null, null, null, "ignored"),
                row("2022305002", "1.00", null, "ignored")
        );

        List<LectureImportRow> rows = parser.parse(workbook);

        assertThat(rows).extracting(LectureImportRow::rowNo).containsExactly(3L);
        assertThat(rows.get(0).displayText()).isNull();
    }

    @Test
    void shouldAcceptXlsAndXlsxWorkbookContent() throws Exception {
        assertThat(parser.parse(xlsx(row("2022305001", "1.00", "xlsx")))).hasSize(1);
        assertThat(parser.parse(xls(row("2022305001", "1.00", "xls")))).hasSize(1);
    }

    @Test
    void shouldRejectTooLargeBytesBeforeOpeningWorkbook() {
        byte[] bytes = new byte[(5 * 1024 * 1024) + 1];

        assertThatThrownBy(() -> parser.parse(bytes))
                .isInstanceOf(ValidationException.class)
                .hasMessage("讲座导入文件最多支持 5000 行且不超过 5MB");
    }

    @Test
    void shouldRejectMoreThan5000NonBlankRowsButAcceptExactly5000() throws Exception {
        String[][] fiveThousand = new String[5000][];
        for (int i = 0; i < fiveThousand.length; i++) {
            fiveThousand[i] = row("S" + i, "1.00", null);
        }
        assertThat(parser.parse(xlsx(fiveThousand))).hasSize(5000);

        String[][] fiveThousandOne = new String[5001][];
        for (int i = 0; i < fiveThousandOne.length; i++) {
            fiveThousandOne[i] = row("S" + i, "1.00", null);
        }
        assertThatThrownBy(() -> parser.parse(xlsx(fiveThousandOne)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("讲座导入文件最多支持 5000 行且不超过 5MB");
    }

    @Test
    void shouldRejectTemplateErrors() throws Exception {
        assertThatThrownBy(() -> parser.parse(noSheets()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：缺少工作表");
        assertThatThrownBy(() -> parser.parse(xlsxWithoutHeader()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：缺少表头");
        assertThatThrownBy(() -> parser.parse(xlsxWithHeaders("studentNo", "bad", "displayText")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：第2列表头应为 scoreValue");
        assertThatThrownBy(() -> parser.parse("studentNo,scoreValue,displayText\nS1,1.00,签到".getBytes()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：文件不可解析");
        assertThatThrownBy(() -> parser.parse("not excel".getBytes()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：文件不可解析");
    }

    private static String[] row(String studentNo, String scoreValue, String displayText, String... ignored) {
        String[] values = new String[3 + ignored.length];
        values[0] = studentNo;
        values[1] = scoreValue;
        values[2] = displayText;
        System.arraycopy(ignored, 0, values, 3, ignored.length);
        return values;
    }

    private static byte[] xlsx(String[]... rows) throws Exception {
        return workbookBytes(new XSSFWorkbook(), rows);
    }

    private static byte[] xls(String[]... rows) throws Exception {
        return workbookBytes(new HSSFWorkbook(), rows);
    }

    private static byte[] xlsxWithHeaders(String... headers) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet.createRow(0), headers);
            return bytes(workbook);
        }
    }

    private static byte[] xlsxWithoutHeader() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sheet1");
            return bytes(workbook);
        }
    }

    private static byte[] noSheets() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            return bytes(workbook);
        }
    }

    private static byte[] workbookBytes(Workbook workbook, String[]... rows) throws Exception {
        try (workbook) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet.createRow(0), "studentNo", "scoreValue", "displayText");
            for (int i = 0; i < rows.length; i++) {
                writeRow(sheet.createRow(i + 1), rows[i]);
            }
            return bytes(workbook);
        }
    }

    private static void writeRow(Row row, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                row.createCell(i).setCellValue(values[i]);
            }
        }
    }

    private static byte[] bytes(Workbook workbook) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        return output.toByteArray();
    }
}
```

- [ ] **Step 2: Run failing parser test**

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportParserTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `ExcelLectureImportParser` does not exist.

- [ ] **Step 3: Implement parser**

Create `ExcelLectureImportParser`:

```java
package edu.whut.eval.infra.finalrecord.importing;

import edu.whut.eval.application.finalrecord.importing.LectureImportParser;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;
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
public class ExcelLectureImportParser implements LectureImportParser {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final List<String> REQUIRED_HEADERS = List.of("studentNo", "scoreValue", "displayText");

    @Override
    public List<LectureImportRow> parse(byte[] fileContent) {
        if (fileContent != null && fileContent.length > MAX_BYTES) {
            throw new ValidationException("讲座导入文件最多支持 5000 行且不超过 5MB");
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

            List<LectureImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String studentNo = cellValue(row.getCell(0), formatter);
                String scoreValue = cellValue(row.getCell(1), formatter);
                String displayText = cellValue(row.getCell(2), formatter);
                if (isBlank(studentNo) && isBlank(scoreValue) && isBlank(displayText)) {
                    continue;
                }
                rows.add(new LectureImportRow(i + 1L, studentNo, scoreValue, displayText));
                if (rows.size() > MAX_ROWS) {
                    throw new ValidationException("讲座导入文件最多支持 5000 行且不超过 5MB");
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

- [ ] **Step 4: Verify parser**

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportParserTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: parser test passes and `git diff --check` has no output.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelLectureImportParser.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportParserTest.java
git commit -m "feat: parse lecture import workbooks"
```

---

### Task 3: Application Service Validation, Batch Id, And Row Semantics

**Files:**

- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportApplicationService.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportApplicationServiceTest.java`

- [ ] **Step 1: Write service tests for request validation and batch id**

Create `LectureImportApplicationServiceTest` with a fake lock and mocked ports. Start with these tests:

```java
package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.importing.ImportLecturesCommand;
import edu.whut.eval.application.finalrecord.importing.LectureImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.LectureImportBatchLock;
import edu.whut.eval.application.finalrecord.importing.LectureImportParser;
import edu.whut.eval.application.finalrecord.importing.LectureImportRepository;
import edu.whut.eval.application.finalrecord.importing.LectureImportStudentTarget;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.LectureImportResult;
import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LectureImportApplicationServiceTest {

    private final UserAuthorizationContextAssembler authorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
    private final LectureImportParser parser = mock(LectureImportParser.class);
    private final LectureImportRepository repository = mock(LectureImportRepository.class);
    private final RecordingLock lock = new RecordingLock();
    private final TransactionOperations transactionOperations = action -> action.doInTransaction(null);
    private final LectureImportApplicationService service =
            new LectureImportApplicationService(authorizationContextAssembler, parser, repository, lock, transactionOperations);

    @Test
    void shouldRejectInvalidRequestParametersBeforeParsing() {
        assertThatThrownBy(() -> service.importLectures(new ImportLecturesCommand(new byte[0], "讲座", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("上传文件不能为空");
        assertThatThrownBy(() -> service.importLectures(command(" ", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("title 不能为空");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "bad", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("heldAt 格式非法");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2027")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2024")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "9999-0000")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("academicYear 不合法");
        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30Z", "2025-2026")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("heldAt 格式非法");
    }

    @Test
    void shouldGenerateDeterministicBatchIdAndNormalizedMetadata() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());

        LectureImportResult result = service.importLectures(command(" 学院学术讲座 ", "2026-05-18T14:30:00.123", " 2025-2026 "));

        assertThat(result.lectureBatchId()).startsWith("LECTURE-20252026-20260518143000-");
        assertThat(result.lectureBatchId()).hasSize(44);
        assertThat(result.title()).isEqualTo("学院学术讲座");
        assertThat(result.heldAt().toString()).isEqualTo("2026-05-18T14:30");
        assertThat(result.academicYear()).isEqualTo("2025-2026");
    }

    @Test
    void shouldAllowRetryWhenHeaderOnlyImportLeavesNoBatchMarker() {
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());

        LectureImportResult first = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));
        LectureImportResult retry = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(first.totalCount()).isZero();
        assertThat(first.successCount()).isZero();
        assertThat(first.failedCount()).isZero();
        assertThat(retry.totalCount()).isZero();
        assertThat(lock.releases.get()).isEqualTo(2);
        verify(repository, never()).insertLectureComponents(any(), any(), any());
        verify(repository, times(2)).lectureBatchExists(eq("2025-2026"), eq(first.lectureBatchId()));
    }

    @Test
    void shouldRejectMissingScoreImportAuthority() {
        given(authorizationContextAssembler.requiredAuthorizationContext())
                .willReturn(new UserAuthorizationContext(1L, "admin", "Admin", "teacher", Set.of(), Set.of(), List.of()));

        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessage("当前用户无导入权限");
    }

    @Test
    void shouldRejectWhenBatchLockCannotBeAcquired() {
        lock.available = false;
        given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
        given(parser.parse(any())).willReturn(List.of());

        assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("同一讲座批次正在导入，请稍后重试");
        assertThat(lock.releases.get()).isZero();
    }

    private ImportLecturesCommand command(String title, String heldAt, String academicYear) {
        return new ImportLecturesCommand(new byte[]{1}, title, heldAt, academicYear);
    }

    private UserAuthorizationContext scopedAdmin() {
        return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("COUNSELOR"), Set.of("score.import"), List.of(
                new IamScopeRule(7010L, "score.import", "ORG_SUBTREE", 2002L, null, null, null, 80, "ACTIVE")
        ));
    }

    private static class RecordingLock implements LectureImportBatchLock {
        private boolean available = true;
        private final AtomicInteger releases = new AtomicInteger();

        @Override
        public boolean tryAcquire(String lectureBatchId, Duration timeout) {
            return available;
        }

        @Override
        public void release(String lectureBatchId) {
            releases.incrementAndGet();
        }
    }
}
```

- [ ] **Step 2: Run failing service tests**

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `LectureImportApplicationService` does not exist.

- [ ] **Step 3: Implement request validation and batch id skeleton**

Create `LectureImportApplicationService` with these constants and methods. The later steps will fill row validation and repository calls.

```java
package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.LectureImportResult;
import edu.whut.eval.domain.finalrecord.importing.LectureImportRow;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LectureImportApplicationService {

    private static final Pattern ACADEMIC_YEAR_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");
    private static final Pattern STRICT_DECIMAL_PATTERN = Pattern.compile("^[0-9]+(\\.[0-9]+)?$");
    private static final BigDecimal MAX_SCORE = new BigDecimal("99999999.99");
    private static final Duration BATCH_LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final String CATEGORY_CODE = "INTELLECTUAL";
    private static final String ITEM_CODE = "INTELLECTUAL_LECTURE";

    private final UserAuthorizationContextAssembler authorizationContextAssembler;
    private final LectureImportParser parser;
    private final LectureImportRepository repository;
    private final LectureImportBatchLock batchLock;
    private final TransactionOperations transactionOperations;

    public LectureImportApplicationService(UserAuthorizationContextAssembler authorizationContextAssembler,
                                           LectureImportParser parser,
                                           LectureImportRepository repository,
                                           LectureImportBatchLock batchLock,
                                           TransactionOperations transactionOperations) {
        this.authorizationContextAssembler = authorizationContextAssembler;
        this.parser = parser;
        this.repository = repository;
        this.batchLock = batchLock;
        this.transactionOperations = transactionOperations;
    }

    public LectureImportResult importLectures(ImportLecturesCommand command) {
        NormalizedRequest request = normalize(command);
        UserAuthorizationContext context = authorizationContextAssembler.requiredAuthorizationContext();
        if (!context.hasAuthority(AuthorizationPermissionCodes.SCORE_IMPORT)) {
            throw new AccessDeniedAppException("当前用户无导入权限");
        }
        String lectureBatchId = lectureBatchId(request);
        List<LectureImportRow> rows = parser.parse(command.fileContent());
        PreparedLectureRows preparedRows = prepareRows(rows);

        return transactionOperations.execute(status -> importPreparedRows(request, lectureBatchId, context, preparedRows));
    }

    private LectureImportResult importPreparedRows(NormalizedRequest request,
                                                   String lectureBatchId,
                                                   UserAuthorizationContext context,
                                                   PreparedLectureRows preparedRows) {
        boolean acquired = batchLock.tryAcquire(lectureBatchId, BATCH_LOCK_TIMEOUT);
        if (!acquired) {
            throw new ConflictException("同一讲座批次正在导入，请稍后重试");
        }
        boolean releaseRegistered = registerBatchLockRelease(lectureBatchId);
        try {
            if (repository.lectureBatchExists(request.academicYear(), lectureBatchId)) {
                throw new ConflictException("同一讲座批次已导入");
            }
            return processRows(request, lectureBatchId, context, preparedRows);
        } finally {
            if (!releaseRegistered) {
                batchLock.release(lectureBatchId);
            }
        }
    }

    private boolean registerBatchLockRelease(String lectureBatchId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                batchLock.release(lectureBatchId);
            }
        });
        return true;
    }

    private LectureImportResult processRows(NormalizedRequest request,
                                            String lectureBatchId,
                                            UserAuthorizationContext context,
                                            PreparedLectureRows preparedRows) {
        return new LectureImportResult(lectureBatchId, request.title(), request.heldAt(), request.academicYear(),
                preparedRows.totalCount(), 0, preparedRows.failedRows().size(), preparedRows.failedRows());
    }

    private PreparedLectureRows prepareRows(List<LectureImportRow> rows) {
        return new PreparedLectureRows(rows.size(), List.of(), rows);
    }

    private NormalizedRequest normalize(ImportLecturesCommand command) {
        if (command.fileContent() == null || command.fileContent().length == 0) {
            throw new ValidationException("上传文件不能为空");
        }
        String title = normalizeTitle(command.title());
        LocalDateTime heldAt = normalizeHeldAt(command.heldAt());
        String academicYear = normalizeAcademicYear(command.academicYear());
        return new NormalizedRequest(title, heldAt, academicYear);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("title 不能为空");
        }
        String value = title.trim();
        if (value.codePointCount(0, value.length()) > 255) {
            throw new ValidationException("title 长度不能超过 255");
        }
        return value;
    }

    private LocalDateTime normalizeHeldAt(String heldAt) {
        if (heldAt == null || heldAt.isBlank()) {
            throw new ValidationException("heldAt 格式非法");
        }
        try {
            return LocalDateTime.parse(heldAt.trim()).withNano(0);
        } catch (DateTimeParseException exception) {
            throw new ValidationException("heldAt 格式非法");
        }
    }

    private String normalizeAcademicYear(String academicYear) {
        if (academicYear == null) {
            throw new ValidationException("academicYear 不合法");
        }
        String value = academicYear.trim();
        Matcher matcher = ACADEMIC_YEAR_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new ValidationException("academicYear 不合法");
        }
        int start = Integer.parseInt(matcher.group(1));
        int end = Integer.parseInt(matcher.group(2));
        if (end != start + 1) {
            throw new ValidationException("academicYear 不合法");
        }
        return value;
    }

    private String lectureBatchId(NormalizedRequest request) {
        String year = request.academicYear().replace("-", "");
        String held = request.heldAt().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String hashInput = request.academicYear() + "|" + held + "|" + request.title();
        return "LECTURE-" + year + "-" + held + "-" + sha256Prefix(hashInput);
    }

    private String sha256Prefix(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.substring(0, 12);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record NormalizedRequest(String title, LocalDateTime heldAt, String academicYear) {
    }

    private record PreparedLectureRows(long totalCount,
                                       List<LectureImportFailedRow> failedRows,
                                       List<LectureImportRow> fieldValidRows) {
    }
}
```

- [ ] **Step 4: Verify request validation slice**

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: tests added so far pass.

Do not commit if later tests in this class have already been added and are failing; finish the relevant step first.

---

### Task 4: Application Row Validation, Auth Scope, Duplicate Rows, And Lock Release

**Files:**

- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportApplicationService.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportApplicationServiceTest.java`

- [ ] **Step 1: Add row behavior tests**

Extend `LectureImportApplicationServiceTest` with these tests:

```java
@Test
void shouldCollectFieldFailuresInFrozenOrderAndRawValueShape() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(
            new LectureImportRow(2L, null, null, "x"),
            new LectureImportRow(3L, "S0", null, "x"),
            new LectureImportRow(4L, "S1", "99999999.999", "x"),
            new LectureImportRow(5L, "S2", "1.234", "x"),
            new LectureImportRow(6L, "S3", "1.230", "x"),
            new LectureImportRow(7L, "S4", "1.00", "一".repeat(1001))
    ));

    LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

    assertThat(result.failedRows()).extracting("code")
            .containsExactly("STUDENT_NO_REQUIRED", "SCORE_VALUE_REQUIRED", "SCORE_VALUE_OUT_OF_RANGE", "SCORE_VALUE_SCALE_INVALID", "SCORE_VALUE_SCALE_INVALID", "DISPLAY_TEXT_TOO_LONG");
    assertThat(result.failedRows().get(0).rawValue()).containsOnlyKeys("studentNo", "scoreValue", "displayText");
}

@Test
void shouldValidateTitleLengthCodePointBoundaries() {
    String validTitle = "一".repeat(255);
    String tooLongTitle = "一".repeat(256);
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(new LectureImportRow(2L, "S1", "1.00", "讲座")));
    given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
    given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
    given(repository.insertLectureComponents(eq("2025-2026"), any(), any())).willReturn(List.of());

    LectureImportResult result = service.importLectures(command(validTitle, "2026-05-18T14:30", "2025-2026"));

    assertThat(result.successCount()).isEqualTo(1);
    assertThatThrownBy(() -> service.importLectures(command(tooLongTitle, "2026-05-18T14:30", "2025-2026")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("title 长度不能超过 255");
}

@Test
void shouldTreatZeroUpperBoundAndShortScaleScoresAsValidAndDefaultDisplayText() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(
            new LectureImportRow(2L, "S1", "0", null),
            new LectureImportRow(3L, "S2", "0.0", ""),
            new LectureImportRow(4L, "S3", "1", "一".repeat(1000)),
            new LectureImportRow(5L, "S4", "1.5", "讲座"),
            new LectureImportRow(6L, "S5", "99999999.99", "")
    ));
    given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
    given(repository.findTarget(eq("S2"), eq("2025-2026"))).willReturn(Optional.of(target(1002L, "/WHUT/CS/CS2022/CS2202")));
    given(repository.findTarget(eq("S3"), eq("2025-2026"))).willReturn(Optional.of(target(1003L, "/WHUT/CS/CS2022/CS2203")));
    given(repository.findTarget(eq("S4"), eq("2025-2026"))).willReturn(Optional.of(target(1004L, "/WHUT/CS/CS2022/CS2204")));
    given(repository.findTarget(eq("S5"), eq("2025-2026"))).willReturn(Optional.of(target(1005L, "/WHUT/CS/CS2022/CS2205")));
    given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
    given(repository.insertLectureComponents(eq("2025-2026"), any(), any())).willReturn(List.of());

    LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

    assertThat(result.successCount()).isEqualTo(5);
    verify(repository).insertLectureComponents(eq("2025-2026"), any(), org.mockito.ArgumentMatchers.argThat(components ->
            components.size() == 5
                    && components.get(0).scoreValue().compareTo(new BigDecimal("0.00")) == 0
                    && components.get(1).scoreValue().compareTo(new BigDecimal("0.00")) == 0
                    && components.get(2).scoreValue().compareTo(new BigDecimal("1.00")) == 0
                    && components.get(3).scoreValue().compareTo(new BigDecimal("1.50")) == 0
                    && components.get(0).displayText().equals("讲座 讲座签到")
                    && components.get(1).displayText().equals("讲座 讲座签到")
                    && components.get(2).displayText().equals("一".repeat(1000))
                    && components.get(4).displayText().equals("讲座 讲座签到")));
}

@Test
void shouldRejectNonStrictDecimalFormats() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(
            new LectureImportRow(2L, "S1", "-1", null),
            new LectureImportRow(3L, "S2", "1,234.56", null),
            new LectureImportRow(4L, "S3", "50%", null),
            new LectureImportRow(5L, "S4", "5E-1", null)
    ));

    LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

    assertThat(result.successCount()).isZero();
    assertThat(result.failedRows()).extracting("code")
            .containsExactly("SCORE_VALUE_INVALID", "SCORE_VALUE_INVALID", "SCORE_VALUE_INVALID", "SCORE_VALUE_INVALID");
}

@Test
void shouldCollectDuplicateStudentAfterFieldValidation() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(
            new LectureImportRow(2L, "S1", "bad", null),
            new LectureImportRow(3L, "S1", "1.00", null),
            new LectureImportRow(4L, "S1", "2.00", null)
    ));
    given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
    given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
    given(repository.insertLectureComponents(eq("2025-2026"), any(), any())).willReturn(List.of());

    LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

    assertThat(result.failedRows()).extracting("rowNo").containsExactly(2L, 4L);
    assertThat(result.failedRows()).extracting("code").containsExactly("SCORE_VALUE_INVALID", "DUPLICATE_STUDENT");
    assertThat(result.failedRows()).extracting("message").containsExactly("scoreValue 必须是数字", "同一讲座批次中学生重复");
    assertThat(result.successCount()).isEqualTo(1);
}

@Test
void shouldConsumeDuplicateKeyAfterStudentLookupFailure() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(
            new LectureImportRow(2L, "S404", "1.00", null),
            new LectureImportRow(3L, "S404", "2.00", null)
    ));
    given(repository.findTarget(eq("S404"), eq("2025-2026"))).willReturn(Optional.empty());

    LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

    assertThat(result.failedRows()).extracting("rowNo").containsExactly(2L, 3L);
    assertThat(result.failedRows()).extracting("code").containsExactly("STUDENT_NOT_FOUND", "DUPLICATE_STUDENT");
}

@Test
void shouldAllowRetryWhenAllRowsFailBeforePersistenceLeavesNoBatchMarker() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(
            new LectureImportRow(2L, "S404", "1.00", null)
    ));
    given(repository.findTarget(eq("S404"), eq("2025-2026"))).willReturn(Optional.empty());

    LectureImportResult first = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));
    LectureImportResult retry = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

    assertThat(first.successCount()).isZero();
    assertThat(first.failedRows()).extracting("code").containsExactly("STUDENT_NOT_FOUND");
    assertThat(retry.successCount()).isZero();
    verify(repository, never()).insertLectureComponents(any(), any(), any());
    verify(repository, times(2)).lectureBatchExists(eq("2025-2026"), eq(first.lectureBatchId()));
}

@Test
void shouldCollectStudentScopeAndLockFailuresAndSortFailedRowsByRowNo() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(
            new LectureImportRow(4L, "S4", "1.00", null),
            new LectureImportRow(2L, "S2", "1.00", null),
            new LectureImportRow(3L, "S3", "1.00", null)
    ));
    given(repository.findTarget(eq("S2"), eq("2025-2026"))).willReturn(Optional.empty());
    given(repository.findTarget(eq("S3"), eq("2025-2026"))).willReturn(Optional.of(target(1003L, "/WHUT/ME/ME2022/ME2201")));
    given(repository.findTarget(eq("S4"), eq("2025-2026"))).willReturn(Optional.of(target(1004L, "/WHUT/CS/CS2022/CS2204")));
    given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
    given(repository.insertLectureComponents(eq("2025-2026"), any(), org.mockito.ArgumentMatchers.argThat(components -> components.size() == 1)))
            .willReturn(List.of(new LectureImportFailedRow(
                    4L,
                    "FINAL_RECORD_LOCKED",
                    "已提交或已确认的最终成绩不允许导入覆盖",
                    raw("S4", "1.00", null)
            )));

    LectureImportResult result = service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

    assertThat(result.failedRows()).extracting("rowNo").containsExactly(2L, 3L, 4L);
    assertThat(result.failedRows()).extracting("code").containsExactly("STUDENT_NOT_FOUND", "OUT_OF_SCOPE", "FINAL_RECORD_LOCKED");
}

@Test
void shouldReleaseAcquiredLockOnDuplicateConflict() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of());
    given(repository.lectureBatchExists(eq("2025-2026"), any())).willReturn(true);

    assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")))
            .isInstanceOf(ConflictException.class)
            .hasMessage("同一讲座批次已导入");
    assertThat(lock.releases.get()).isEqualTo(1);
}

@Test
void shouldReleaseAcquiredLockOnPersistenceFailure() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of(new LectureImportRow(2L, "S1", "1.00", null)));
    given(repository.findTarget(eq("S1"), eq("2025-2026"))).willReturn(Optional.of(target(1001L, "/WHUT/CS/CS2022/CS2201")));
    given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));
    doThrow(new ConflictException("最终成绩状态已变更，请刷新后重试"))
            .when(repository).insertLectureComponents(eq("2025-2026"), any(), any());

    assertThatThrownBy(() -> service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026")))
            .isInstanceOf(ConflictException.class)
            .hasMessage("最终成绩状态已变更，请刷新后重试");
    assertThat(lock.releases.get()).isEqualTo(1);
}

@Test
void shouldDeferLockReleaseUntilTransactionCompletionWhenSynchronizationIsActive() {
    given(authorizationContextAssembler.requiredAuthorizationContext()).willReturn(scopedAdmin());
    given(parser.parse(any())).willReturn(List.of());

    TransactionSynchronizationManager.initSynchronization();
    try {
        service.importLectures(command("讲座", "2026-05-18T14:30", "2025-2026"));

        assertThat(lock.releases.get()).isZero();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertThat(lock.releases.get()).isEqualTo(1);
    } finally {
        TransactionSynchronizationManager.clearSynchronization();
    }
}

private LectureImportStudentTarget target(Long studentUserId, String orgPath) {
    return new LectureImportStudentTarget(studentUserId, "S" + studentUserId, 2010L, orgPath);
}

private Map<String, String> raw(String studentNo, String scoreValue, String displayText) {
    Map<String, String> raw = new LinkedHashMap<>();
    raw.put("studentNo", studentNo);
    raw.put("scoreValue", scoreValue);
    raw.put("displayText", displayText);
    return raw;
}
```

- [ ] **Step 2: Run failing row tests**

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: several tests fail because `processRows` still returns all failures and does not call repository.

- [ ] **Step 3: Implement row preparation and transactional row processing**

Replace `prepareRows` and `processRows`, then add the helper methods below. `prepareRows` runs before the transaction and handles field validation plus duplicate detection in workbook order. `processRows` runs inside `transactionOperations` after the batch lock is acquired and handles student lookup, scope matching, final-record mutation, and row-failure merging.

```java
private PreparedLectureRows prepareRows(List<LectureImportRow> rows) {
    List<LectureImportFailedRow> failedRows = new ArrayList<>();
    List<FieldValidLectureRow> fieldValidRows = new ArrayList<>();
    Set<String> seenStudentNos = new LinkedHashSet<>();

    for (LectureImportRow row : rows) {
        Optional<LectureImportFailedRow> fieldFailure = validateFields(row);
        if (fieldFailure.isPresent()) {
            failedRows.add(fieldFailure.get());
            continue;
        }

        String studentNo = row.studentNo().trim();
        if (!seenStudentNos.add(studentNo)) {
            failedRows.add(failed(row, "DUPLICATE_STUDENT", "同一讲座批次中学生重复"));
            continue;
        }
        // validateFields has already rejected scale > 2; this only pads values such as "1" and "1.5".
        BigDecimal score = new BigDecimal(row.scoreValue().trim()).setScale(2, RoundingMode.HALF_UP);
        fieldValidRows.add(new FieldValidLectureRow(row, studentNo, row.scoreValue().trim(), score));
    }

    return new PreparedLectureRows(rows.size(), failedRows, fieldValidRows);
}

private LectureImportResult processRows(NormalizedRequest request,
                                        String lectureBatchId,
                                        UserAuthorizationContext context,
                                        PreparedLectureRows preparedRows) {
    List<LectureImportFailedRow> failedRows = new ArrayList<>(preparedRows.failedRows());
    List<ResolvedLectureRow> resolvedRows = new ArrayList<>();
    Map<Long, Optional<String>> orgPathCache = new HashMap<>();

    for (FieldValidLectureRow candidate : preparedRows.fieldValidRows()) {
        LectureImportRow row = candidate.row();
        Optional<LectureImportStudentTarget> target = repository.findTarget(candidate.studentNo(), request.academicYear());
        if (target.isEmpty()) {
            failedRows.add(failed(row, "STUDENT_NOT_FOUND", "studentNo 对应学生不存在或未启用"));
            continue;
        }
        if (!canAccess(context, target.get(), orgPathCache)) {
            failedRows.add(failed(row, "OUT_OF_SCOPE", "当前用户无权导入该学生讲座成绩"));
            continue;
        }

        String displayText = isBlank(row.displayText()) ? request.title() + " 讲座签到" : row.displayText().trim();
        resolvedRows.add(new ResolvedLectureRow(row.rowNo(), target.get().studentUserId(), candidate.studentNo(), candidate.scoreValueText(), candidate.scoreValue(), displayText));
    }

    resolvedRows.sort(Comparator.comparing(ResolvedLectureRow::studentUserId).thenComparing(ResolvedLectureRow::rowNo));
    List<LectureImportedComponent> components = resolvedRows.stream()
            .map(row -> new LectureImportedComponent(row.rowNo(), row.studentUserId(), row.studentNo(), row.scoreValueText(), row.scoreValue(), row.displayText()))
            .toList();
    if (!components.isEmpty()) {
        failedRows.addAll(repository.insertLectureComponents(request.academicYear(), lectureBatchId, components));
    }

    failedRows.sort(Comparator.comparing(LectureImportFailedRow::rowNo));
    long totalCount = preparedRows.totalCount();
    long failedCount = failedRows.size();
    return new LectureImportResult(
            lectureBatchId,
            request.title(),
            request.heldAt(),
            request.academicYear(),
            totalCount,
            totalCount - failedCount,
            failedCount,
            List.copyOf(failedRows)
    );
}
```

Add these helpers in `LectureImportApplicationService`:

```java
private Optional<LectureImportFailedRow> validateFields(LectureImportRow row) {
    if (isBlank(row.studentNo())) {
        return Optional.of(failed(row, "STUDENT_NO_REQUIRED", "studentNo 不能为空"));
    }
    // D-8 does not add a studentNo length/format failure code beyond the frozen spec.
    // Non-matching long or unusual values are handled by the parameterized user_no lookup as STUDENT_NOT_FOUND.
    if (isBlank(row.scoreValue())) {
        return Optional.of(failed(row, "SCORE_VALUE_REQUIRED", "scoreValue 不能为空"));
    }
    String value = row.scoreValue().trim();
    if (!STRICT_DECIMAL_PATTERN.matcher(value).matches()) {
        return Optional.of(failed(row, "SCORE_VALUE_INVALID", "scoreValue 必须是数字"));
    }
    BigDecimal score = new BigDecimal(value);
    if (score.compareTo(MAX_SCORE) > 0) {
        return Optional.of(failed(row, "SCORE_VALUE_OUT_OF_RANGE", "scoreValue 必须在 0 到 99999999.99 之间"));
    }
    if (score.scale() > 2) {
        return Optional.of(failed(row, "SCORE_VALUE_SCALE_INVALID", "scoreValue 最多保留 2 位小数"));
    }
    if (row.displayText() != null && row.displayText().codePointCount(0, row.displayText().length()) > 1000) {
        return Optional.of(failed(row, "DISPLAY_TEXT_TOO_LONG", "displayText 长度不能超过 1000"));
    }
    return Optional.empty();
}

private LectureImportFailedRow failed(LectureImportRow row, String code, String message) {
    Map<String, String> raw = new LinkedHashMap<>();
    raw.put("studentNo", row.studentNo());
    raw.put("scoreValue", row.scoreValue());
    raw.put("displayText", row.displayText());
    return new LectureImportFailedRow(row.rowNo(), code, message, raw);
}

private boolean canAccess(UserAuthorizationContext context,
                          LectureImportStudentTarget target,
                          Map<Long, Optional<String>> orgPathCache) {
    for (IamScopeRule rule : context.findScopeRulesByPermissionCode(AuthorizationPermissionCodes.SCORE_IMPORT)) {
        if (!"ACTIVE".equals(rule.status())) {
            continue;
        }
        if ("ALL".equals(rule.scopeType())) {
            return true;
        }
        if ("ORG_UNIT".equals(rule.scopeType()) && rule.orgUnitId() != null && rule.orgUnitId().equals(target.orgUnitId())) {
            return true;
        }
        if ("ORG_SUBTREE".equals(rule.scopeType()) && matchesOrgSubtree(rule.orgUnitId(), target.orgPath(), orgPathCache)) {
            return true;
        }
    }
    return false;
}

private boolean matchesOrgSubtree(Long rootOrgUnitId,
                                  String targetPath,
                                  Map<Long, Optional<String>> orgPathCache) {
    if (rootOrgUnitId == null || isBlank(targetPath)) {
        return false;
    }
    Optional<String> rootPath = orgPathCache.computeIfAbsent(rootOrgUnitId, repository::findActiveOrgPath);
    if (rootPath.isEmpty() || isBlank(rootPath.get())) {
        return false;
    }
    String normalizedRootPath = trimTrailingSlash(rootPath.get().trim());
    String normalizedTargetPath = trimTrailingSlash(targetPath.trim());
    return normalizedTargetPath.equals(normalizedRootPath)
            || normalizedTargetPath.startsWith(normalizedRootPath + "/");
}

private String trimTrailingSlash(String path) {
    if (path.length() > 1 && path.endsWith("/")) {
        return path.substring(0, path.length() - 1);
    }
    return path;
}

private boolean isBlank(String value) {
    return value == null || value.isBlank();
}

private record PreparedLectureRows(
        long totalCount,
        List<LectureImportFailedRow> failedRows,
        List<FieldValidLectureRow> fieldValidRows
) {
}

private record FieldValidLectureRow(
        LectureImportRow row,
        String studentNo,
        String scoreValueText,
        BigDecimal scoreValue
) {
}

private record ResolvedLectureRow(
        Long rowNo,
        Long studentUserId,
        String studentNo,
        String scoreValueText,
        BigDecimal scoreValue,
        String displayText
) {
}
```

- [ ] **Step 4: Verify service behavior**

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: service tests pass.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportApplicationService.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportApplicationServiceTest.java
git commit -m "feat: validate lecture import rows"
```

---

### Task 5: Persistence Mapper And Repository

**Files:**

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/LectureImportMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisLectureImportRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureImportStudentTargetRow.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureImportedComponentRow.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureScoreCategoryTotalRow.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisLectureImportRepositoryIntegrationTest.java`

- [ ] **Step 1: Write repository integration tests**

Create `MybatisLectureImportRepositoryIntegrationTest` by copying the Spring/H2 setup and helper style from `MybatisMentorScoreImportRepositoryIntegrationTest`, then applying the D-8 differences below. The copied H2 schema must include:

- `iam_user`, `org_unit`, and `org_membership` with explicit `org_membership.id BIGINT PRIMARY KEY` so tests can verify smallest `org_membership.id` selection.
- `final_record` with `UNIQUE KEY uk_final_record_student_year (student_user_id, academic_year)`.
- `final_component_score` without a uniqueness constraint on `category_code`, `item_code`, `source_type`, or `source_ref_id`.

Define these helpers in the test file:

```java
private void insertOrgUnit(Long id, String unitName, String path, String status)
private void insertStudent(Long id, String userNo, String status)
private void insertMembership(Long id, Long userId, Long orgUnitId, boolean primary, String status)
private Long insertDraftRecord(Long studentUserId, String academicYear)
private Long insertFinalRecord(Long studentUserId, String academicYear, String status)
private void insertComponent(Long finalRecordId, String categoryCode, String itemCode, String scoreValue, String sourceType, String sourceRefId)
private static LectureImportedComponent component(Long rowNo, Long studentUserId, String studentNo, String scoreValue, String displayText)
```

Required tests:

```java
@Test
void shouldFindActiveStudentTargetWithoutIdentityAndResolveSmallestPrimaryMembershipId() {
    insertStudent(1004L, "S1004", "ACTIVE");
    insertMembership(9002L, 1004L, 2010L, true, "ACTIVE");
    insertMembership(9001L, 1004L, 3010L, true, "ACTIVE");

    Optional<LectureImportStudentTarget> target = repository.findTarget("S1004", "2025-2026");

    assertThat(target).isPresent();
    assertThat(target.get().orgUnitId()).isEqualTo(3010L);
}

@Test
void shouldFindOnlyActiveOrgPathForScopeRoot() {
    insertOrgUnit(4001L, "临时学院", "/WHUT/TEMP", "INACTIVE");

    assertThat(repository.findActiveOrgPath(2002L)).contains("/WHUT/CS");
    assertThat(repository.findActiveOrgPath(4001L)).isEmpty();
    assertThat(repository.findActiveOrgPath(9999L)).isEmpty();
}

@Test
void shouldDetectExistingLectureBatchByAcademicYearAndSourceRefId() {
    Long recordId = insertDraftRecord(1001L, "2025-2026");
    insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_LECTURE", "1.00", "IMPORT", "LECTURE-20252026-20260518143000-ABCDEF123456");

    assertThat(repository.lectureBatchExists("2025-2026", "LECTURE-20252026-20260518143000-ABCDEF123456")).isTrue();
    assertThat(repository.lectureBatchExists("2026-2027", "LECTURE-20252026-20260518143000-ABCDEF123456")).isFalse();
}

@Test
void shouldInsertLectureComponentsWithoutOverwritingPreviousLectureBatches() {
    repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-AAAABBBBCCCC", List.of(
            component(2L, 1001L, "S1001", "1.25", "讲座A"),
            component(3L, 1001L, "S1001", "2.00", "讲座A second batch row should not happen in service")
    ));

    Long recordId = jdbcTemplate.queryForObject("SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'", Long.class);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score WHERE final_record_id = ?", Long.class, recordId)).isEqualTo(2L);
    assertThat(jdbcTemplate.queryForObject("SELECT intellectual_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
            .isEqualByComparingTo("3.25");
    assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(2L);
}

@Test
void shouldRecalculateTotalsAfterEachSuccessfulLectureRowAndIncrementVersion() {
    Long recordId = insertDraftRecord(1001L, "2025-2026");
    // Test fixture inserts a pre-existing application component directly and does not change final_record.version.
    insertComponent(recordId, "INTELLECTUAL", "INTELLECTUAL_APPLICATION", "2.00", "APPLICATION", "app-1");

    repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-EXISTING0001", List.of(
            component(2L, 1001L, "S1001", "1.25", "讲座A"),
            component(3L, 1001L, "S1001", "2.00", "讲座B")
    ));

    assertThat(jdbcTemplate.queryForObject("SELECT intellectual_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
            .isEqualByComparingTo("5.25");
    assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(2L);
}

@Test
void shouldReturnFinalRecordLockedFailureAndLeaveNoComponentForSubmittedRecord() {
    insertFinalRecord(1001L, "2025-2026", "SUBMITTED");

    List<LectureImportFailedRow> failures = repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-LOCKED000001", List.of(
            component(2L, 1001L, "S1001", "1.00", "讲座")
    ));

    assertThat(failures).extracting("code").containsExactly("FINAL_RECORD_LOCKED");
    assertThat(failures.get(0).rawValue()).containsEntry("studentNo", "S1001");
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score", Long.class)).isZero();
}

@Test
void shouldReturnFinalRecordLockedFailureAndLeaveNoComponentForConfirmedRecord() {
    insertFinalRecord(1001L, "2025-2026", "CONFIRMED");

    List<LectureImportFailedRow> failures = repository.insertLectureComponents("2025-2026", "LECTURE-20252026-20260518143000-LOCKED000002", List.of(
            component(2L, 1001L, "S1001", "1.00", "讲座")
    ));

    assertThat(failures).extracting("code").containsExactly("FINAL_RECORD_LOCKED");
    assertThat(failures.get(0).rawValue()).containsEntry("studentNo", "S1001");
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score", Long.class)).isZero();
}
```

Do not add a unique key to `final_component_score`; the real D safe-init schema does not define one.

- [ ] **Step 2: Run failing repository tests**

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisLectureImportRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because mapper/repository classes do not exist.

- [ ] **Step 3: Implement mapper**

Create `LectureImportMapper` with SQL adapted from `MentorScoreImportMapper`:

```java
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
LectureImportStudentTargetRow selectTarget(@Param("studentNo") String studentNo);

@Select("SELECT path FROM org_unit WHERE id = #{orgUnitId} AND status = 'ACTIVE'")
String selectActiveOrgPath(@Param("orgUnitId") Long orgUnitId);

@Select("""
        SELECT COUNT(1)
        FROM final_component_score fcs
        JOIN final_record fr ON fr.id = fcs.final_record_id
        WHERE fr.academic_year = #{academicYear}
          AND fcs.category_code = 'INTELLECTUAL'
          AND fcs.item_code = 'INTELLECTUAL_LECTURE'
          AND fcs.source_type = 'IMPORT'
          AND fcs.source_ref_id = #{lectureBatchId}
        """)
long countLectureBatchComponents(@Param("academicYear") String academicYear,
                                 @Param("lectureBatchId") String lectureBatchId);

@Select("SELECT id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at FROM final_record WHERE student_user_id = #{studentUserId} AND academic_year = #{academicYear} FOR UPDATE")
FinalRecordDO selectFinalRecordForUpdate(@Param("studentUserId") Long studentUserId,
                                         @Param("academicYear") String academicYear);

@Insert("INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at) VALUES (#{studentUserId}, #{academicYear}, #{status}, #{moralTotal}, #{intellectualTotal}, #{physicalTotal}, #{laborTotal}, #{grandTotal}, #{submittedAt}, #{confirmedAt}, #{confirmComment}, #{version}, #{createdAt}, #{updatedAt})")
@Options(useGeneratedKeys = true, keyProperty = "id")
int insertDraft(FinalRecordDO record);

@Insert("INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at) VALUES (#{finalRecordId}, #{categoryCode}, #{itemCode}, #{scoreValue}, #{displayText}, 'IMPORT', #{sourceRefId}, #{createdAt})")
int insertLectureComponent(LectureImportedComponentRow component);

@Select("SELECT category_code AS categoryCode, COALESCE(SUM(score_value), 0) AS scoreValue FROM final_component_score WHERE final_record_id = #{finalRecordId} GROUP BY category_code")
List<LectureScoreCategoryTotalRow> selectTotals(@Param("finalRecordId") Long finalRecordId);

@Update("UPDATE final_record SET moral_total = #{moral}, intellectual_total = #{intellectual}, physical_total = #{physical}, labor_total = #{labor}, grand_total = #{grand}, updated_at = #{updatedAt}, version = version + 1 WHERE id = #{finalRecordId} AND status = 'DRAFT'")
int updateTotals(@Param("finalRecordId") Long finalRecordId,
                 @Param("moral") BigDecimal moral,
                 @Param("intellectual") BigDecimal intellectual,
                 @Param("physical") BigDecimal physical,
                 @Param("labor") BigDecimal labor,
                 @Param("grand") BigDecimal grand,
                 @Param("updatedAt") LocalDateTime updatedAt);
```

The `insertDraft` SQL must bind the `confirm_comment` column with `#{confirmComment}`. Do not write a literal `confirm_comment` token in the `VALUES` list.

`findTarget(String studentNo, String academicYear)` keeps the `academicYear` argument to match the D-7 port shape and to make later historical-organization lookup changes binary-local to the infra adapter. Minimal D-8 intentionally does not use the argument in the SQL because the current schema has only current `org_membership` state.

Create the row classes used by the mapper:

```java
package edu.whut.eval.infra.persistence.repository.row;

public class LectureImportStudentTargetRow {
    private Long studentUserId;
    private String studentNo;
    private Long orgUnitId;
    private String orgPath;
    // getters and setters
}
```

```java
package edu.whut.eval.infra.persistence.repository.row;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LectureImportedComponentRow {
    private Long finalRecordId;
    private String categoryCode;
    private String itemCode;
    private BigDecimal scoreValue;
    private String displayText;
    private String sourceRefId;
    private LocalDateTime createdAt;
    // getters and setters
}
```

```java
package edu.whut.eval.infra.persistence.repository.row;

import java.math.BigDecimal;

public class LectureScoreCategoryTotalRow {
    private String categoryCode;
    private BigDecimal scoreValue;
    // getters and setters
}
```

- [ ] **Step 4: Implement repository**

Create `MybatisLectureImportRepository`:

```java
package edu.whut.eval.infra.persistence.repository;

import edu.whut.eval.application.finalrecord.importing.LectureImportRepository;
import edu.whut.eval.application.finalrecord.importing.LectureImportStudentTarget;
import edu.whut.eval.application.finalrecord.importing.LectureImportedComponent;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.infra.persistence.dataobject.FinalRecordDO;
import edu.whut.eval.infra.persistence.mapper.LectureImportMapper;
import edu.whut.eval.infra.persistence.repository.row.LectureImportStudentTargetRow;
import edu.whut.eval.infra.persistence.repository.row.LectureImportedComponentRow;
import edu.whut.eval.infra.persistence.repository.row.LectureScoreCategoryTotalRow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MybatisLectureImportRepository implements LectureImportRepository {

    private static final String CATEGORY_CODE = "INTELLECTUAL";
    private static final String ITEM_CODE = "INTELLECTUAL_LECTURE";

    private final LectureImportMapper mapper;

    public MybatisLectureImportRepository(LectureImportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean lectureBatchExists(String academicYear, String lectureBatchId) {
        return mapper.countLectureBatchComponents(academicYear, lectureBatchId) > 0;
    }

    @Override
    public Optional<LectureImportStudentTarget> findTarget(String studentNo, String academicYear) {
        return Optional.ofNullable(mapper.selectTarget(studentNo))
                .map(this::toTarget);
    }

    @Override
    public Optional<String> findActiveOrgPath(Long orgUnitId) {
        return Optional.ofNullable(mapper.selectActiveOrgPath(orgUnitId));
    }

    @Override
    @Transactional
    public List<LectureImportFailedRow> insertLectureComponents(String academicYear,
                                                                String lectureBatchId,
                                                                List<LectureImportedComponent> components) {
        List<LectureImportFailedRow> failures = new ArrayList<>();
        for (LectureImportedComponent component : components) {
            FinalRecordDO record = mapper.selectFinalRecordForUpdate(component.studentUserId(), academicYear);
            if (record == null) {
                record = insertOrReloadDraft(academicYear, component);
            }
            if (!"DRAFT".equals(record.getStatus())) {
                failures.add(lockedFailure(component));
                continue;
            }

            LocalDateTime now = LocalDateTime.now();
            mapper.insertLectureComponent(toComponentRow(record.getId(), lectureBatchId, component, now));
            updateTotals(record.getId(), now);
        }
        return List.copyOf(failures);
    }

    private LectureImportStudentTarget toTarget(LectureImportStudentTargetRow row) {
        return new LectureImportStudentTarget(
                row.getStudentUserId(),
                row.getStudentNo(),
                row.getOrgUnitId(),
                row.getOrgPath()
        );
    }

    private FinalRecordDO insertOrReloadDraft(String academicYear, LectureImportedComponent component) {
        FinalRecordDO record = newDraftRecord(academicYear, component.studentUserId());
        try {
            mapper.insertDraft(record);
            return record;
        } catch (DataIntegrityViolationException exception) {
            FinalRecordDO concurrentRecord = mapper.selectFinalRecordForUpdate(component.studentUserId(), academicYear);
            if (concurrentRecord == null) {
                throw new ConflictException("最终成绩保存后读取失败");
            }
            return concurrentRecord;
        }
    }

    private FinalRecordDO newDraftRecord(String academicYear, Long studentUserId) {
        LocalDateTime now = LocalDateTime.now();
        FinalRecordDO record = new FinalRecordDO();
        record.setStudentUserId(studentUserId);
        record.setAcademicYear(academicYear);
        record.setStatus("DRAFT");
        record.setMoralTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        record.setIntellectualTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        record.setPhysicalTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        record.setLaborTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        record.setGrandTotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        record.setSubmittedAt(null);
        record.setConfirmedAt(null);
        record.setConfirmComment(null);
        record.setVersion(0L);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private LectureImportedComponentRow toComponentRow(Long finalRecordId,
                                                       String lectureBatchId,
                                                       LectureImportedComponent component,
                                                       LocalDateTime now) {
        LectureImportedComponentRow row = new LectureImportedComponentRow();
        row.setFinalRecordId(finalRecordId);
        row.setCategoryCode(CATEGORY_CODE);
        row.setItemCode(ITEM_CODE);
        row.setScoreValue(component.scoreValue().setScale(2, RoundingMode.HALF_UP));
        row.setDisplayText(component.displayText());
        row.setSourceRefId(lectureBatchId);
        row.setCreatedAt(now);
        return row;
    }

    private LectureImportFailedRow lockedFailure(LectureImportedComponent component) {
        Map<String, String> rawValue = new LinkedHashMap<>();
        rawValue.put("studentNo", component.studentNo());
        rawValue.put("scoreValue", component.scoreValueText());
        rawValue.put("displayText", component.displayText());
        return new LectureImportFailedRow(
                component.rowNo(),
                "FINAL_RECORD_LOCKED",
                "已提交或已确认的最终成绩不允许导入覆盖",
                rawValue
        );
    }

    private void updateTotals(Long finalRecordId, LocalDateTime updatedAt) {
        BigDecimal moral = BigDecimal.ZERO;
        BigDecimal intellectual = BigDecimal.ZERO;
        BigDecimal physical = BigDecimal.ZERO;
        BigDecimal labor = BigDecimal.ZERO;

        for (LectureScoreCategoryTotalRow total : mapper.selectTotals(finalRecordId)) {
            BigDecimal value = scale(total.getScoreValue());
            switch (total.getCategoryCode()) {
                case "MORAL" -> moral = value;
                case "INTELLECTUAL" -> intellectual = value;
                case "SPORTS" -> physical = value;
                case "LABOR" -> labor = value;
                default -> throw new ConflictException("unsupported final record category: " + total.getCategoryCode());
            }
        }

        BigDecimal grand = scale(moral.add(intellectual).add(physical).add(labor));
        int updated = mapper.updateTotals(finalRecordId, scale(moral), scale(intellectual), scale(physical), scale(labor), grand, updatedAt);
        if (updated == 0) {
            throw new ConflictException("最终成绩状态已变更，请刷新后重试");
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
```

Notes for this implementation:

- `findTarget` maps `LectureImportStudentTargetRow`.
- `lectureBatchExists` returns `count > 0`.
- `insertLectureComponents` returns `List<LectureImportFailedRow>` and loops over already sorted components. For each component, it locks `final_record`, inserts or reloads a DRAFT row if missing, records `FINAL_RECORD_LOCKED` as a row-level failure for non-DRAFT records, inserts a new lecture component, and immediately recalculates totals while the row is still locked.
- The locked-row failure raw value uses `component.studentNo()` so the response preserves the frozen `rawValue.studentNo` contract from the workbook, not the internal user id.
- Recalculate and update totals after every successful lecture component insertion, not once per distinct `final_record_id`. This preserves the frozen D-8 contract that `final_record.version` increments for every successful row mutation, including multiple successful lecture rows for the same student in one repository call.
- If any `updateTotals` returns 0 after a DRAFT lock and component insert, throw `ConflictException("最终成绩状态已变更，请刷新后重试")` so the outer transaction rolls back. This path is a persistence consistency failure, not a row-level validation failure, so it must not be represented in the returned failure list.
- A partial-success batch is intentionally not idempotent in Minimal D-8: if any component already exists for `lectureBatchId`, `lectureBatchExists` makes a later same-batch request return 409. Operators must change `title` or `heldAt` to create a new deterministic batch id for a retry; D-8 does not add batch deletion or patch-retry semantics.

- [ ] **Step 5: Verify repository**

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisLectureImportRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: repository tests pass and formatting check has no output.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/LectureImportRepository.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/LectureImportMapper.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisLectureImportRepository.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureImportStudentTargetRow.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureImportedComponentRow.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/LectureScoreCategoryTotalRow.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisLectureImportRepositoryIntegrationTest.java
git commit -m "feat: persist lecture import components"
```

---

### Task 6: Batch Lock Adapters

**Files:**

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/MySqlLectureImportBatchLock.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportBatchLockTest.java`

- [ ] **Step 1: Write lock adapter tests**

Create `LectureImportBatchLockTest` with a mocked `JdbcTemplate`:

```java
class LectureImportBatchLockTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MySqlLectureImportBatchLock lock = new MySqlLectureImportBatchLock(jdbcTemplate);

    @Test
    void shouldAcquireAndReleaseNamedLock() {
        given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), eq("D8_LECTURE:batch"), eq(30)))
                .willReturn(1);

        assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isTrue();

        lock.release("batch");
        verify(jdbcTemplate).queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, "D8_LECTURE:batch");
    }

    @Test
    void shouldReturnFalseWhenNamedLockTimesOut() {
        given(jdbcTemplate.queryForObject(eq("SELECT GET_LOCK(?, ?)"), eq(Integer.class), eq("D8_LECTURE:batch"), eq(30)))
                .willReturn(0);

        assertThat(lock.tryAcquire("batch", Duration.ofSeconds(30))).isFalse();
    }
}
```

- [ ] **Step 2: Run failing lock tests**

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportBatchLockTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `MySqlLectureImportBatchLock` does not exist.

- [ ] **Step 3: Implement MySQL lock adapter**

Create:

```java
package edu.whut.eval.infra.finalrecord.importing;

import edu.whut.eval.application.finalrecord.importing.LectureImportBatchLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MySqlLectureImportBatchLock implements LectureImportBatchLock {

    private static final String PREFIX = "D8_LECTURE:";

    private final JdbcTemplate jdbcTemplate;

    public MySqlLectureImportBatchLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryAcquire(String lectureBatchId, Duration timeout) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                lockName(lectureBatchId),
                Math.toIntExact(timeout.toSeconds())
        );
        return result != null && result == 1;
    }

    @Override
    public void release(String lectureBatchId) {
        jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName(lectureBatchId));
    }

    private String lockName(String lectureBatchId) {
        return PREFIX + lectureBatchId;
    }
}
```

Production uses the active transaction-bound connection through Spring's `JdbcTemplate` / transaction synchronization. Do not call `dataSource.getConnection()` directly.

- [ ] **Step 4: Verify lock adapter**

```bash
mvn -pl whut-eval-app -am -Dtest=LectureImportBatchLockTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: lock tests pass.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/MySqlLectureImportBatchLock.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/LectureImportBatchLockTest.java
git commit -m "feat: add lecture import batch lock"
```

---

### Task 7: Controller And Response Surface

**Files:**

- Modify: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/LectureImportResultResponse.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/LectureImportFailedRowResponse.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java`

- [ ] **Step 1: Add MVC tests**

Extend `AdminScoreImportControllerWebMvcTest`:

Add imports for `ImportLecturesCommand`, `LectureImportApplicationService`, `LectureImportFailedRow`, `LectureImportResult`, `ValidationException`, `LocalDateTime`, and `LinkedHashMap`. Existing D-7 imports for `ConflictException`, `FileStorageException`, `MockMultipartFile`, `List`, `Map`, and MVC matchers can be reused.

```java
@MockBean
private LectureImportApplicationService lectureImportApplicationService;

@Test
void shouldImportLecturesAndReturnResultShape() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
    Map<String, String> rawValue = new LinkedHashMap<>();
    rawValue.put("studentNo", "S1002");
    rawValue.put("scoreValue", "1.00");
    rawValue.put("displayText", null);
    given(lectureImportApplicationService.importLectures(any(ImportLecturesCommand.class)))
            .willReturn(new LectureImportResult(
                    "LECTURE-20252026-20260518143000-ABCDEF123456",
                    "学院学术讲座",
                    LocalDateTime.parse("2026-05-18T14:30:00"),
                    "2025-2026",
                    2,
                    1,
                    1,
                    List.of(new LectureImportFailedRow(3L, "OUT_OF_SCOPE", "当前用户无权导入该学生讲座成绩", rawValue))
            ));

    mockMvc.perform(multipart("/api/admin/imports/lectures")
                    .file(file)
                    .param("title", "学院学术讲座")
                    .param("heldAt", "2026-05-18T14:30")
                    .param("academicYear", "2025-2026"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lectureBatchId").value("LECTURE-20252026-20260518143000-ABCDEF123456"))
            .andExpect(jsonPath("$.data.title").value("学院学术讲座"))
            .andExpect(jsonPath("$.data.heldAt").value("2026-05-18T14:30:00"))
            .andExpect(jsonPath("$.data.academicYear").value("2025-2026"))
            .andExpect(jsonPath("$.data.failedRows[0].rawValue.scoreValue").value("1.00"));

    verify(lectureImportApplicationService).importLectures(argThat(command ->
            new String(command.fileContent()).equals("excel")
                    && "学院学术讲座".equals(command.title())
                    && "2026-05-18T14:30".equals(command.heldAt())
                    && "2025-2026".equals(command.academicYear())
    ));
}

@Test
void shouldReturn400WhenLectureTitleMissing() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());

    mockMvc.perform(multipart("/api/admin/imports/lectures")
                    .file(file)
                    .param("title", " ")
                    .param("heldAt", "2026-05-18T14:30")
                    .param("academicYear", "2025-2026"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("title 不能为空"));
}

@Test
void shouldReturn400WhenLectureFileIsEmpty() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

    mockMvc.perform(multipart("/api/admin/imports/lectures")
                    .file(file)
                    .param("title", "学院学术讲座")
                    .param("heldAt", "2026-05-18T14:30")
                    .param("academicYear", "2025-2026"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("上传文件不能为空"));
}

@Test
void shouldReturn400WhenLectureHeldAtInvalid() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
    given(lectureImportApplicationService.importLectures(any(ImportLecturesCommand.class)))
            .willThrow(new ValidationException("heldAt 格式非法"));

    mockMvc.perform(multipart("/api/admin/imports/lectures")
                    .file(file)
                    .param("title", "学院学术讲座")
                    .param("heldAt", "2026-05-18T14:30Z")
                    .param("academicYear", "2025-2026"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("VAL-4001"))
            .andExpect(jsonPath("$.message").value("heldAt 格式非法"));
}

@Test
void shouldReturn200WhenLectureWorkbookHasOnlyHeader() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
    given(lectureImportApplicationService.importLectures(any(ImportLecturesCommand.class)))
            .willReturn(new LectureImportResult(
                    "LECTURE-20252026-20260518143000-ABCDEF123456",
                    "学院学术讲座",
                    LocalDateTime.parse("2026-05-18T14:30:00"),
                    "2025-2026",
                    0,
                    0,
                    0,
                    List.of()
            ));

    mockMvc.perform(multipart("/api/admin/imports/lectures")
                    .file(file)
                    .param("title", "学院学术讲座")
                    .param("heldAt", "2026-05-18T14:30")
                    .param("academicYear", "2025-2026"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(0))
            .andExpect(jsonPath("$.data.successCount").value(0))
            .andExpect(jsonPath("$.data.failedCount").value(0))
            .andExpect(jsonPath("$.data.failedRows").isEmpty());
}

@Test
void shouldReturn409WhenLectureServiceReportsDuplicateBatch() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
    given(lectureImportApplicationService.importLectures(any(ImportLecturesCommand.class)))
            .willThrow(new ConflictException("同一讲座批次已导入"));

    mockMvc.perform(multipart("/api/admin/imports/lectures")
                    .file(file)
                    .param("title", "学院学术讲座")
                    .param("heldAt", "2026-05-18T14:30")
                    .param("academicYear", "2025-2026"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("BIZ-4090"))
            .andExpect(jsonPath("$.message").value("同一讲座批次已导入"));
}

@Test
void shouldReturn503WhenLectureMultipartReadFails() throws Exception {
    MockMultipartFile file = new FailingLectureMockMultipartFile();

    mockMvc.perform(multipart("/api/admin/imports/lectures")
                    .file(file)
                    .param("title", "学院学术讲座")
                    .param("heldAt", "2026-05-18T14:30")
                    .param("academicYear", "2025-2026"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("EXT-5033"))
            .andExpect(jsonPath("$.message").value("文件处理失败，请稍后重试"));
}

static class FailingLectureMockMultipartFile extends MockMultipartFile {

    FailingLectureMockMultipartFile() {
        super("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
    }

    @Override
    public byte[] getBytes() {
        throw new FileStorageException("文件处理失败，请稍后重试");
    }
}
```

Extend `AdminScoreImportControllerSecurityAnnotationTest`:

Add imports for `MediaType`, `PostMapping`, and `RequestMapping`.

```java
@Test
void shouldRequireScoreImportAuthorityForLectureImport() throws Exception {
    PreAuthorize preAuthorize = AdminScoreImportController.class
            .getMethod("importLectures", MultipartFile.class, String.class, String.class, String.class)
            .getAnnotation(PreAuthorize.class);

    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo(
            "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)"
    );
}

@Test
void shouldExposeLectureImportOnExactMultipartRoute() throws Exception {
    PostMapping postMapping = AdminScoreImportController.class
            .getMethod("importLectures", MultipartFile.class, String.class, String.class, String.class)
            .getAnnotation(PostMapping.class);

    assertThat(postMapping).isNotNull();
    assertThat(postMapping.value()).containsExactly("/lectures");
    assertThat(postMapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
    assertThat(AdminScoreImportController.class.getAnnotation(RequestMapping.class).value())
            .containsExactly("/api/admin/imports");
}
```

- [ ] **Step 2: Run failing MVC tests**

```bash
mvn -pl whut-eval-app -am -Dtest=AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because lecture endpoint and response DTOs do not exist.

- [ ] **Step 3: Implement response DTOs and controller method**

Create `LectureImportFailedRowResponse`:

```java
package edu.whut.eval.interfaces.admin.response;

import java.util.Map;

public record LectureImportFailedRowResponse(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
```

Create `LectureImportResultResponse`:

```java
package edu.whut.eval.interfaces.admin.response;

import java.util.List;

public record LectureImportResultResponse(
        String lectureBatchId,
        String title,
        String heldAt,
        String academicYear,
        long totalCount,
        long successCount,
        long failedCount,
        List<LectureImportFailedRowResponse> failedRows
) {
}
```

Modify `AdminScoreImportController` to keep a single constructor and inject both services:

```java
private static final long MAX_LECTURE_IMPORT_BYTES = 5L * 1024 * 1024;
private static final DateTimeFormatter LECTURE_RESPONSE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

private final MentorScoreImportApplicationService mentorScoreImportApplicationService;
private final LectureImportApplicationService lectureImportApplicationService;

public AdminScoreImportController(MentorScoreImportApplicationService mentorScoreImportApplicationService,
                                  LectureImportApplicationService lectureImportApplicationService) {
    this.mentorScoreImportApplicationService = mentorScoreImportApplicationService;
    this.lectureImportApplicationService = lectureImportApplicationService;
}

@PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)")
@PostMapping(value = "/lectures", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<LectureImportResultResponse> importLectures(
        @RequestParam("file") MultipartFile file,
        @RequestParam("title") String title,
        @RequestParam("heldAt") String heldAt,
        @RequestParam("academicYear") String academicYear) {
    if (file == null || file.isEmpty()) {
        throw new ValidationException("上传文件不能为空");
    }
    if (title == null || title.trim().isEmpty()) {
        throw new ValidationException("title 不能为空");
    }
    if (file.getSize() > MAX_LECTURE_IMPORT_BYTES) {
        throw new ValidationException("讲座导入文件最多支持 5000 行且不超过 5MB");
    }
    byte[] bytes;
    try {
        bytes = file.getBytes();
    } catch (IOException exception) {
        throw new FileStorageException("文件处理失败，请稍后重试", exception);
    }
    LectureImportResult result = lectureImportApplicationService.importLectures(
            new ImportLecturesCommand(bytes, title, heldAt, academicYear)
    );
    return ApiResponse.success(toLectureResponse(result));
}

private LectureImportResultResponse toLectureResponse(LectureImportResult result) {
    return new LectureImportResultResponse(
            result.lectureBatchId(),
            result.title(),
            result.heldAt().format(LECTURE_RESPONSE_TIME_FORMATTER),
            result.academicYear(),
            result.totalCount(),
            result.successCount(),
            result.failedCount(),
            result.failedRows().stream()
                    .map(row -> new LectureImportFailedRowResponse(row.rowNo(), row.code(), row.message(), row.rawValue()))
                    .toList()
    );
}
```

Use `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")` for `heldAt` response so whole seconds always render as `2026-05-18T14:30:00`.

- [ ] **Step 4: Verify controller**

```bash
mvn -pl whut-eval-app -am -Dtest=AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: MVC and annotation tests pass.

- [ ] **Step 5: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/LectureImportResultResponse.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/LectureImportFailedRowResponse.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java
git commit -m "feat: expose lecture import endpoint"
```

---

### Task 8: Integration, Regression, And Full Verification

**Files:**

- Modify: tests from Tasks 2-7 as needed.
- No production files unless a verification failure exposes a missing requirement.

- [ ] **Step 1: Add regression tests for core D-8 risks**

Add the focused tests below at the named layer:

- Parser: `rowNo` is worksheet physical row number when blank rows exist.
- Parser: missing header, CSV/text unreadable content, and unsupported workbook-like formats fail as template/unreadable errors.
- Service: non-strict decimal score formats (`-1`, `1,234.56`, `50%`, `5E-1`) fail with `SCORE_VALUE_INVALID`.
- Service: `scoreValue` variants `0`, `0.0`, `0.00`, `1`, and `1.5` are accepted and normalized to scale 2 without rounding.
- Service: `displayText` with exactly 1000 Unicode code points is accepted and 1001 is rejected.
- Service: `heldAt` rejects timezone offsets, accepts omitted seconds, and truncates fractional seconds.
- Service: `academicYear` rejects `2025-2027`, `2025-2024`, and `9999-0000`.
- Service: unsupported-only scope set returns `OUT_OF_SCOPE`; `ALL` scope allows import; multiple scopes use union semantics.
- Service: failedRows are sorted ascending by `rowNo` when failures originate from field validation, duplicate, student lookup, and scope paths.
- Service: header-only imports and all-failed zero-success imports acquire/release the batch lock, run the authoritative duplicate-batch check, do not call component persistence, and allow same-metadata retry.
- Repository: two distinct `lectureBatchId` values for the same student/year create two `INTELLECTUAL_LECTURE` components.
- Repository: duplicate-batch detection joins through `final_record.academic_year`.
- Repository: total recalculation persists scale 2 with `RoundingMode.HALF_UP` and increments `version` once per successful lecture row mutation, not once per distinct `final_record_id`.
- Service: H2/JVM test lock adapter serializes two same-`lectureBatchId` service calls; hold the first call at the fake lock boundary and assert the second call gets `ConflictException("同一讲座批次正在导入，请稍后重试")` before any repository mutation.
- Controller: empty lecture file maps to `400 / VAL-4001`, invalid `heldAt` maps to `400 / VAL-4001`, header-only result maps to `200` with zero counts, multipart read failure maps to `503 / EXT-5033`, service conflict `同一讲座批次已导入` maps to `409 / BIZ-4090`, and the route is exactly `POST /api/admin/imports/lectures` with `multipart/form-data` consumes.

- [ ] **Step 2: Run targeted D-8 suite**

```bash
mvn -pl whut-eval-app -am \
  -Dtest=LectureImportParserTest,LectureImportApplicationServiceTest,MybatisLectureImportRepositoryIntegrationTest,LectureImportBatchLockTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest \
  test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: all targeted D-8 tests pass.

- [ ] **Step 3: Run D-7 regression suite**

```bash
mvn -pl whut-eval-app -am \
  -Dtest=MentorScoreImportParserTest,MentorScoreImportApplicationServiceTest,MybatisMentorScoreImportRepositoryIntegrationTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest \
  test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: D-7 tests still pass after adding the D-8 endpoint to the shared controller.

- [ ] **Step 4: Run final record module regression slice**

```bash
mvn -pl whut-eval-app -am \
  -Dtest='*FinalRecord*Test,*ScoreImport*Test,*LectureImport*Test' \
  test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: final-record and import tests pass.

- [ ] **Step 5: Run formatting and full compile**

```bash
mvn -pl whut-eval-app -am test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: full app module test suite passes and `git diff --check` has no output.

- [ ] **Step 6: Commit verification-only test additions**

If Step 1 added tests after the previous commits, commit them:

```bash
git add whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord
git commit -m "test: cover lecture import regressions"
```

If no files changed, do not create an empty commit.

---

## Implementation Order

1. Task 1 must land first because every later slice depends on D-8 contracts.
2. Task 2 can run independently after Task 1 because parser tests do not require application service wiring.
3. Task 3 and Task 4 establish service behavior before persistence exists, using mocks/fakes.
4. Task 5 then implements the repository against the frozen schema and may require one coordinated service API adjustment for row-level `FINAL_RECORD_LOCKED`.
5. Task 6 adds production locking after the service lock contract exists.
6. Task 7 exposes HTTP and must preserve D-7 controller behavior.
7. Task 8 is the verification closure before code review-loop.

## Plan Self-Review Checklist

- Spec coverage:
  - HTTP route, request params, response shape: Task 7.
  - Excel template, limits, row numbers, raw values: Task 2 and Task 8.
  - Batch id, normalized metadata, duplicate-batch behavior: Task 3, Task 4, Task 5.
  - Batch lock release on all paths: Task 4 and Task 6.
  - Student eligibility and scope semantics: Task 4 and Task 5.
  - Insert-only component mutation and total recalculation: Task 5 and Task 8.
  - Controller/security/error mapping: Task 7.
- Placeholder scan: no `TBD`, no open-ended "add tests" without concrete target behavior.
- Type consistency: D-8 types use `LectureImport*`; D-7 `MentorScoreImport*` types remain D-7-only.
