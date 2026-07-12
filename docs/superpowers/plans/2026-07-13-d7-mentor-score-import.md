# D-7 Mentor Score Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build D-7 `POST /api/admin/imports/mentor-scores` so authorized admins can synchronously import mentor/fixed score Excel rows into draft final records.

**Architecture:** Add a narrow import slice beside existing final-record command/query code. The interface layer reads multipart bytes, the application layer validates request and row semantics, the infra parser uses Apache POI, and the persistence layer owns active-student lookup, draft/component mutation, and total recalculation. D-7 writes only `DRAFT` final records and imported `final_component_score` rows; it never submits or confirms final records.

**Tech Stack:** Java 21, Spring Boot 3, Spring MVC multipart, Spring Security `@PreAuthorize`, MyBatis/MyBatis-Plus, H2 MySQL-mode integration tests, Apache POI `WorkbookFactory`.

---

## Source Inputs

- Spec: `docs/superpowers/specs/2026-07-13-d7-mentor-score-import-design.md`
- Frozen delivery doc: `docs/team-delivery/group-d-score-finalization-import-export.md`
- D safe-init SQL: `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
- Existing import pattern: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/iam/UserAdminController.java`
- Existing Excel parser pattern: `whut-eval-infra/src/main/java/edu/whut/eval/infra/iam/ExcelUserImportParser.java`
- Existing final-record persistence: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisPlusFinalRecordRepository.java`

## Review-Loop Note

Initial `/review-loop` produced one valid `gpt-5-4` reviewer result and then stalled because the `deepseek` and `doubao-seed-2-1-turbo` workers stayed in stale `running` state after their processes exited. The valid review found three spec issues:

- missing frozen row-level `failedRows` code/message mapping;
- missing `UPSERT` duplicate target-key behavior;
- ambiguous `STRICT_INSERT` pre-scan order.

Commit `dd6ad11` fixed those findings in the spec. A second limited reviewer run did not produce a final loop manifest in this CLI session, so implementation must preserve the fixed spec text and run direct verification after each task.

## File Map

Create:

- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/MentorScoreImportMode.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/MentorScoreImportRow.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/MentorScoreImportFailedRow.java`
- `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/MentorScoreImportResult.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportMentorScoresCommand.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportApplicationService.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportParser.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportRepository.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportStudentTarget.java`
- `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportedComponent.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelMentorScoreImportParser.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/MentorScoreImportMapper.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisMentorScoreImportRepository.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreImportStudentTargetRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreImportedComponentRow.java`
- `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreCategoryTotalRow.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/MentorScoreImportFailedRowResponse.java`
- `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/MentorScoreImportResultResponse.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MentorScoreImportParserTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MentorScoreImportApplicationServiceTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisMentorScoreImportRepositoryIntegrationTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`
- `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java`

Modify:

- `whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java`
- `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
- `docs/team-delivery/group-d-score-finalization-import-export.md`
- `docs/team-delivery/README.md`
- `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`

---

### Task 1: Seed And Permission Constant

**Files:**

- Modify: `whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java`
- Modify: `docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`

- [ ] **Step 1: Write failing permission and seed tests**

In `TeamDeliverySqlConsistencyTest`, extend existing D safe-init tests with `score.import`.

Add imports if needed:

```java
import edu.whut.eval.application.auth.AuthorizationPermissionCodes;
```

Add assertions inside `shouldInitializeFullSeedChainOnAGroupSchemaAndKeepSafeInitRerunnable()` after the existing `score.confirm.assigned` assertions:

```java
assertThat(AuthorizationPermissionCodes.SCORE_IMPORT).isEqualTo("score.import");
assertThat(countRows(connection, "iam_permission", "permission_code = 'score.import'")).isEqualTo(1);
assertThat(countRows(connection, "iam_role_permission", "role_id = 4003 AND permission_id = (SELECT id FROM iam_permission WHERE permission_code = 'score.import')")).isEqualTo(1);
assertThat(countRows(connection, "iam_role_permission", "role_id = 4004 AND permission_id = (SELECT id FROM iam_permission WHERE permission_code = 'score.import')")).isEqualTo(1);
assertThat(countRows(connection, "iam_scope_rule", "assignment_id = 7010 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002")).isEqualTo(1);
assertThat(countRows(connection, "iam_scope_rule", "assignment_id = 7011 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002")).isEqualTo(1);
```

Extend the D safe-init shape test near the existing created-at assertions:

```java
assertThat(sql).contains("SELECT 5024, 'score.import', '导入导师/固定成绩', 'score', 'ACTIVE', CURRENT_TIMESTAMP()");
assertThat(sql).contains("SELECT 6050, 4003, p.id, CURRENT_TIMESTAMP()");
assertThat(sql).contains("SELECT 6051, 4004, p.id, CURRENT_TIMESTAMP()");
assertThat(sql).contains("SELECT 8021, 7010, 'score.import', 'ORG_SUBTREE', 2002");
assertThat(sql).contains("SELECT 8022, 7011, 'score.import', 'ORG_SUBTREE', 2002");
```

Extend rerunnable natural-key tests with a preexisting `score.import` id:

```java
connection.createStatement().executeUpdate("""
        INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
        VALUES (9100, 'score.import', '已有导入权限', 'score', 'ACTIVE', CURRENT_TIMESTAMP())
        """);
executeStatements(connection, Files.readString(D_GROUP_SAFE_INIT_SQL));
assertThat(singleLong(connection, "SELECT id FROM iam_permission WHERE permission_code = 'score.import'"))
        .isEqualTo(9100L);
assertThat(countRows(connection, "iam_role_permission", "role_id = 4003 AND permission_id = 9100")).isEqualTo(1);
assertThat(countRows(connection, "iam_role_permission", "role_id = 4004 AND permission_id = 9100")).isEqualTo(1);
assertThat(countRows(connection, "iam_role_permission", "permission_id = 5024")).isEqualTo(0);
```

- [ ] **Step 2: Run failing test**

```bash
mvn -pl whut-eval-app -am -Dtest=TeamDeliverySqlConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because `AuthorizationPermissionCodes.SCORE_IMPORT` does not exist, or assertions fail because `score.import` is not seeded.

- [ ] **Step 3: Add permission constant**

In `AuthorizationPermissionCodes`, under score permissions:

```java
public static final String SCORE_IMPORT = "score.import";
```

- [ ] **Step 4: Extend D safe-init SQL**

Add `score.import` collision checks to the existing `d_seed_collision_guard` `INSERT ... SELECT 1 WHERE EXISTS` block:

```sql
   OR EXISTS (SELECT 1 FROM iam_permission WHERE id = 5024 AND permission_code <> 'score.import')
   OR EXISTS (
     SELECT 1 FROM iam_role_permission WHERE id = 6050 AND NOT EXISTS (
       SELECT 1 FROM iam_permission p WHERE p.permission_code = 'score.import' AND p.id = iam_role_permission.permission_id AND iam_role_permission.role_id = 4003
     )
   )
   OR EXISTS (
     SELECT 1 FROM iam_role_permission WHERE id = 6051 AND NOT EXISTS (
       SELECT 1 FROM iam_permission p WHERE p.permission_code = 'score.import' AND p.id = iam_role_permission.permission_id AND iam_role_permission.role_id = 4004
     )
   )
   OR EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = 8021 AND NOT (assignment_id = 7010 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002))
   OR EXISTS (SELECT 1 FROM iam_scope_rule WHERE id = 8022 AND NOT (assignment_id = 7011 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002))
```

Add inserts after the existing `score.confirm.assigned` seed block:

```sql
INSERT INTO iam_permission (id, permission_code, permission_name, permission_group, status, created_at)
SELECT 5024, 'score.import', '导入导师/固定成绩', 'score', 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (SELECT 1 FROM iam_permission WHERE permission_code = 'score.import');

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6050, 4003, p.id, CURRENT_TIMESTAMP()
FROM iam_permission p
WHERE p.permission_code = 'score.import'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = 4003 AND rp.permission_id = p.id
  );

INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT 6051, 4004, p.id, CURRENT_TIMESTAMP()
FROM iam_permission p
WHERE p.permission_code = 'score.import'
  AND NOT EXISTS (
    SELECT 1 FROM iam_role_permission rp
    WHERE rp.role_id = 4004 AND rp.permission_id = p.id
  );

INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at)
SELECT 8021, 7010, 'score.import', 'ORG_SUBTREE', 2002, NULL, NULL, '{"scoreRole":"counselor"}', 80, 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (
  SELECT 1 FROM iam_scope_rule
  WHERE assignment_id = 7010 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002
);

INSERT INTO iam_scope_rule (id, assignment_id, permission_code, scope_type, org_unit_id, category_code, item_code, expression_json, priority, status, created_at)
SELECT 8022, 7011, 'score.import', 'ORG_SUBTREE', 2002, NULL, NULL, '{"scoreRole":"college_reviewer"}', 70, 'ACTIVE', CURRENT_TIMESTAMP()
WHERE NOT EXISTS (
  SELECT 1 FROM iam_scope_rule
  WHERE assignment_id = 7011 AND permission_code = 'score.import' AND scope_type = 'ORG_SUBTREE' AND org_unit_id = 2002
);
```

- [ ] **Step 5: Verify task**

```bash
mvn -pl whut-eval-app -am -Dtest=TeamDeliverySqlConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: `TeamDeliverySqlConsistencyTest` passes and `git diff --check` has no output.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/auth/AuthorizationPermissionCodes.java \
  docs/team-delivery/group-d-score-finalization-import-export.safe-init.sql \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java
git commit -m "feat: seed score import permission"
```

---

### Task 2: Parser And Domain Result Types

**Files:**

- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/MentorScoreImportMode.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/MentorScoreImportRow.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/MentorScoreImportFailedRow.java`
- Create: `whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing/MentorScoreImportResult.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportParser.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelMentorScoreImportParser.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MentorScoreImportParserTest.java`

- [ ] **Step 1: Write failing parser tests**

Create `MentorScoreImportParserTest` with workbook helpers and these tests:

```java
package edu.whut.eval.app.finalrecord;

import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportRow;
import edu.whut.eval.infra.finalrecord.importing.ExcelMentorScoreImportParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentorScoreImportParserTest {

    private final ExcelMentorScoreImportParser parser = new ExcelMentorScoreImportParser();

    @Test
    void shouldParseValidWorkbookRowsWithExcelRowNumbers() throws Exception {
        byte[] workbook = workbook(row("S1001", "MORAL", "MORAL_HONOR", "1.25", "导师评分", "mentor-001"));

        List<MentorScoreImportRow> rows = parser.parse(workbook);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).rowNo()).isEqualTo(2L);
        assertThat(rows.get(0).studentNo()).isEqualTo("S1001");
        assertThat(rows.get(0).scoreValue()).isEqualTo("1.25");
    }

    @Test
    void shouldSkipBlankRows() throws Exception {
        byte[] workbook = workbook(row(null, null, null, null, null, null), row("S1002", "LABOR", "LABOR_SERVICE", "2", null, null));

        List<MentorScoreImportRow> rows = parser.parse(workbook);

        assertThat(rows).extracting(MentorScoreImportRow::rowNo).containsExactly(3L);
    }

    @Test
    void shouldRejectMissingHeader() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sheet1");
            byte[] bytes = bytes(workbook);

            assertThatThrownBy(() -> parser.parse(bytes))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("导入模板错误：缺少表头");
        }
    }

    @Test
    void shouldRejectHeaderMismatch() throws Exception {
        byte[] workbook = workbookWithHeaders(List.of("studentNo", "bad", "itemCode", "scoreValue", "displayText", "sourceRefId"));

        assertThatThrownBy(() -> parser.parse(workbook))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("导入模板错误：第2列表头应为 categoryCode");
    }

    @Test
    void shouldRejectUnreadableBytes() {
        assertThatThrownBy(() -> parser.parse("not excel".getBytes()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：文件不可解析");
    }

    private static String[] row(String studentNo, String categoryCode, String itemCode, String scoreValue, String displayText, String sourceRefId) {
        return new String[]{studentNo, categoryCode, itemCode, scoreValue, displayText, sourceRefId};
    }

    private static byte[] workbook(String[]... rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet.createRow(0), "studentNo", "categoryCode", "itemCode", "scoreValue", "displayText", "sourceRefId");
            for (int i = 0; i < rows.length; i++) {
                writeRow(sheet.createRow(i + 1), rows[i]);
            }
            return bytes(workbook);
        }
    }

    private static byte[] workbookWithHeaders(List<String> headers) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet.createRow(0), headers.toArray(String[]::new));
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

- [ ] **Step 2: Run failing parser tests**

```bash
mvn -pl whut-eval-app -am -Dtest=MentorScoreImportParserTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because parser and row types do not exist.

- [ ] **Step 3: Add domain records and parser interface**

`MentorScoreImportMode.java`:

```java
package edu.whut.eval.domain.finalrecord.importing;

public enum MentorScoreImportMode {
    UPSERT,
    STRICT_INSERT
}
```

`MentorScoreImportRow.java`:

```java
package edu.whut.eval.domain.finalrecord.importing;

public record MentorScoreImportRow(
        Long rowNo,
        String studentNo,
        String categoryCode,
        String itemCode,
        String scoreValue,
        String displayText,
        String sourceRefId
) {
}
```

`MentorScoreImportFailedRow.java`:

```java
package edu.whut.eval.domain.finalrecord.importing;

import java.util.Map;

public record MentorScoreImportFailedRow(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
```

`MentorScoreImportResult.java`:

```java
package edu.whut.eval.domain.finalrecord.importing;

import java.time.Instant;
import java.util.List;

public record MentorScoreImportResult(
        String importBatchId,
        long totalCount,
        long successCount,
        long failedCount,
        List<MentorScoreImportFailedRow> failedRows,
        Instant processedAt
) {
}
```

`MentorScoreImportParser.java`:

```java
package edu.whut.eval.application.finalrecord.importing;

import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportRow;

import java.util.List;

public interface MentorScoreImportParser {
    List<MentorScoreImportRow> parse(byte[] fileContent);
}
```

- [ ] **Step 4: Implement Excel parser**

`ExcelMentorScoreImportParser` follows `ExcelUserImportParser`, with required headers:

```java
private static final List<String> REQUIRED_HEADERS = List.of(
        "studentNo", "categoryCode", "itemCode", "scoreValue", "displayText", "sourceRefId"
);
```

Parsing rules:

- use `WorkbookFactory.create(new ByteArrayInputStream(fileContent))`;
- `Sheet sheet = workbook.getSheetAt(0)`;
- throw `new ValidationException("导入模板错误：缺少表头")` when header row is null;
- for each header index, throw `new ValidationException("导入模板错误：第" + (i + 1) + "列表头应为 " + expected)`;
- skip rows where all six parsed values are blank;
- create `MentorScoreImportRow(i + 1L, studentNo, categoryCode, itemCode, scoreValue, displayText, sourceRefId)`;
- catch non-validation exceptions and throw `new ValidationException("导入模板错误：文件不可解析")`.

- [ ] **Step 5: Verify task**

```bash
mvn -pl whut-eval-app -am -Dtest=MentorScoreImportParserTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: parser tests pass and diff check is clean.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-domain/src/main/java/edu/whut/eval/domain/finalrecord/importing \
  whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportParser.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/finalrecord/importing/ExcelMentorScoreImportParser.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MentorScoreImportParserTest.java
git commit -m "feat: parse mentor score import workbooks"
```

---

### Task 3: Application Service Validation And Orchestration

**Files:**

- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/ImportMentorScoresCommand.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportApplicationService.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportRepository.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportStudentTarget.java`
- Create: `whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing/MentorScoreImportedComponent.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MentorScoreImportApplicationServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Create `MentorScoreImportApplicationServiceTest` with imports from the current codebase:

```java
import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.auth.model.UserAuthorizationContext;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportResult;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportRow;
import edu.whut.eval.domain.iam.model.IamScopeRule;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
```

Use mock collaborators:

```java
private final UserAuthorizationContextAssembler authorizationContextAssembler = mock(UserAuthorizationContextAssembler.class);
private final MentorScoreImportParser parser = mock(MentorScoreImportParser.class);
private final MentorScoreImportRepository repository = mock(MentorScoreImportRepository.class);
private final MentorScoreImportApplicationService service =
        new MentorScoreImportApplicationService(authorizationContextAssembler, parser, repository);
```

Use row helper:

```java
private MentorScoreImportRow row(long rowNo, String studentNo, String categoryCode, String itemCode, String scoreValue) {
    return new MentorScoreImportRow(rowNo, studentNo, categoryCode, itemCode, scoreValue, "导师评分", "source-" + rowNo);
}
```

Use scoped admin:

```java
private UserAuthorizationContext scopedAdmin() {
    return new UserAuthorizationContext(1010L, "T1010", "Counselor", "teacher", Set.of("COUNSELOR"),
            Set.of("score.import"),
            List.of(new IamScopeRule(7010L, "score.import", "ORG_SUBTREE", 2002L, null, null, null, 80, "ACTIVE")));
}
```

Use target:

```java
private MentorScoreImportStudentTarget target(String status) {
    return new MentorScoreImportStudentTarget(1001L, "S1001", 2010L, "/WHUT/CS/CS2022/CS2201", status);
}
```

Add exact behavior tests:

```java
@Test
void shouldRejectInvalidAcademicYear() {
    assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025", "UPSERT")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("academicYear 不合法");
}

@Test
void shouldRejectInvalidImportMode() {
    assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "MERGE")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("importMode 仅允许 UPSERT 或 STRICT_INSERT");
}

@Test
void shouldReturnFirstFieldFailurePerRow() {
    given(authorizationContextAssembler.currentAuthorizationContext()).willReturn(Optional.of(scopedAdmin()));
    given(parser.parse(any())).willReturn(List.of(row(2, "", "BAD", "", "abc")));

    MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.successCount()).isZero();
    assertThat(result.failedRows()).hasSize(1);
    assertThat(result.failedRows().get(0).code()).isEqualTo("STUDENT_NO_REQUIRED");
    assertThat(result.failedRows().get(0).message()).isEqualTo("studentNo 不能为空");
    assertThat(result.failedRows().get(0).rawValue()).containsEntry("categoryCode", "BAD");
}

@Test
void shouldReturnStudentNotFoundForMissingEligibleStudent() {
    given(authorizationContextAssembler.currentAuthorizationContext()).willReturn(Optional.of(scopedAdmin()));
    given(parser.parse(any())).willReturn(List.of(row(2, "S404", "MORAL", "MORAL_HONOR", "1.00")));
    given(repository.findTarget("S404", "2025-2026")).willReturn(Optional.empty());

    MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

    assertThat(result.failedRows()).extracting("code").containsExactly("STUDENT_NOT_FOUND");
    verify(repository, times(0)).upsertDraftComponent(any(), any());
}

@Test
void shouldReturnOutOfScopeWhenOrgPathIsOutsideRealSubtree() {
    given(authorizationContextAssembler.currentAuthorizationContext()).willReturn(Optional.of(scopedAdmin()));
    given(parser.parse(any())).willReturn(List.of(row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00")));
    given(repository.findTarget("S1001", "2025-2026"))
            .willReturn(Optional.of(new MentorScoreImportStudentTarget(1001L, "S1001", 3010L, "/WHUT/ME/ME2022/ME2201", null)));
    given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));

    MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

    assertThat(result.failedRows()).extracting("code").containsExactly("OUT_OF_SCOPE");
}

@Test
void shouldReturnLockedWhenFinalRecordIsSubmittedOrConfirmed() {
    given(authorizationContextAssembler.currentAuthorizationContext()).willReturn(Optional.of(scopedAdmin()));
    given(parser.parse(any())).willReturn(List.of(row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00")));
    given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target("SUBMITTED")));
    given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));

    MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

    assertThat(result.failedRows()).extracting("code").containsExactly("FINAL_RECORD_LOCKED");
    verify(repository, times(0)).upsertDraftComponent(any(), any());
}

@Test
void shouldApplyUpsertDuplicateWorkbookTargetsInRowOrder() {
    given(authorizationContextAssembler.currentAuthorizationContext()).willReturn(Optional.of(scopedAdmin()));
    given(parser.parse(any())).willReturn(List.of(
            row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00"),
            row(3, "S1001", "MORAL", "MORAL_HONOR", "2.00")));
    given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(null)));
    given(repository.findActiveOrgPath(2002L)).willReturn(Optional.of("/WHUT/CS"));

    MentorScoreImportResult result = service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "UPSERT"));

    assertThat(result.successCount()).isEqualTo(2);
    verify(repository).upsertDraftComponent(
            org.mockito.ArgumentMatchers.argThat(component -> component.rowNo().equals(2L) && component.scoreValue().compareTo(new BigDecimal("1.00")) == 0),
            org.mockito.ArgumentMatchers.startsWith("D7-"));
    verify(repository).upsertDraftComponent(
            org.mockito.ArgumentMatchers.argThat(component -> component.rowNo().equals(3L) && component.scoreValue().compareTo(new BigDecimal("2.00")) == 0),
            org.mockito.ArgumentMatchers.startsWith("D7-"));
}

@Test
void shouldRejectStrictInsertWorkbookDuplicateBeforeMutation() {
    given(authorizationContextAssembler.currentAuthorizationContext()).willReturn(Optional.of(scopedAdmin()));
    given(parser.parse(any())).willReturn(List.of(
            row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00"),
            row(3, "S1001", "MORAL", "MORAL_HONOR", "2.00")));
    given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(null)));

    assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "STRICT_INSERT")))
            .isInstanceOf(ConflictException.class)
            .hasMessage("STRICT_INSERT 模式不允许覆盖");
    verify(repository, times(0)).upsertDraftComponent(any(), any());
}

@Test
void shouldRejectStrictInsertDatabaseDuplicateBeforeMutation() {
    given(authorizationContextAssembler.currentAuthorizationContext()).willReturn(Optional.of(scopedAdmin()));
    given(parser.parse(any())).willReturn(List.of(row(2, "S1001", "MORAL", "MORAL_HONOR", "1.00")));
    given(repository.findTarget("S1001", "2025-2026")).willReturn(Optional.of(target(null)));
    given(repository.importedComponentExists(1001L, "2025-2026", "MORAL", "MORAL_HONOR")).willReturn(true);

    assertThatThrownBy(() -> service.importMentorScores(new ImportMentorScoresCommand(new byte[]{1}, "2025-2026", "STRICT_INSERT")))
            .isInstanceOf(ConflictException.class)
            .hasMessage("STRICT_INSERT 模式不允许覆盖");
}

@Test
void shouldDeclareTransactionalBoundary() throws Exception {
    assertThat(MentorScoreImportApplicationService.class
            .getMethod("importMentorScores", ImportMentorScoresCommand.class)
            .getAnnotation(Transactional.class)).isNotNull();
}
```

- [ ] **Step 2: Run failing service tests**

```bash
mvn -pl whut-eval-app -am -Dtest=MentorScoreImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because service and repository contracts do not exist.

- [ ] **Step 3: Add command and repository contracts**

`ImportMentorScoresCommand`:

```java
package edu.whut.eval.application.finalrecord.importing;

public record ImportMentorScoresCommand(
        byte[] fileContent,
        String academicYear,
        String importMode
) {
}
```

`MentorScoreImportStudentTarget`:

```java
package edu.whut.eval.application.finalrecord.importing;

public record MentorScoreImportStudentTarget(
        Long studentUserId,
        String studentNo,
        Long orgUnitId,
        String orgPath,
        String finalRecordStatus
) {
}
```

`MentorScoreImportedComponent`:

```java
package edu.whut.eval.application.finalrecord.importing;

import java.math.BigDecimal;

public record MentorScoreImportedComponent(
        Long rowNo,
        Long studentUserId,
        String academicYear,
        String categoryCode,
        String itemCode,
        BigDecimal scoreValue,
        String displayText,
        String sourceRefId
) {
}
```

`MentorScoreImportRepository`:

```java
package edu.whut.eval.application.finalrecord.importing;

import java.util.Optional;

public interface MentorScoreImportRepository {
    Optional<MentorScoreImportStudentTarget> findTarget(String studentNo, String academicYear);
    Optional<String> findActiveOrgPath(Long orgUnitId);
    boolean importedComponentExists(Long studentUserId, String academicYear, String categoryCode, String itemCode);
    void upsertDraftComponent(MentorScoreImportedComponent component, String importBatchId);
}
```

- [ ] **Step 4: Implement service**

Service requirements:

- normalize `importMode` to `UPSERT` default;
- validate `academicYear` with regex and consecutive years;
- call `authorizationContextAssembler.requiredAuthorizationContext()`;
- fail with `AccessDeniedAppException("当前用户无导入权限")` if context lacks `score.import`;
- parse rows;
- generate batch id as `D7-<yyyyMMddTHHmmssSSSZ>-<6 uppercase hex chars>`;
- validate field failures using the spec mapping and first-match rule;
- resolve eligible students for field-valid rows;
- pre-scan `STRICT_INSERT` eligible rows for workbook duplicate target keys and database duplicates;
- use real org-path scope matching for `ALL`, `ORG_UNIT`, and `ORG_SUBTREE`;
- skip unsupported scopes;
- for row-level failures, append `MentorScoreImportFailedRow`;
- for successes, call `repository.upsertDraftComponent(...)`;
- return `MentorScoreImportResult(batchId, totalCount, successCount, failedRows.size(), failedRows, processedAt)`.

Keep helpers private and focused:

- `validateAcademicYear(String academicYear)`;
- `parseMode(String importMode)`;
- `validateFields(MentorScoreImportRow row)`;
- `rawValue(MentorScoreImportRow row)`;
- `isInScope(UserAuthorizationContext context, MentorScoreImportStudentTarget target)`;
- `matchesOrgSubtree(String rootPath, String targetPath)`.

Use `edu.whut.eval.domain.auth.model.UserAuthorizationContext` and `edu.whut.eval.domain.iam.model.IamScopeRule`. For `ORG_SUBTREE`, resolve the scope root path with `repository.findActiveOrgPath(rule.orgUnitId())` and match by segment-safe prefix: exact path match is allowed, and subtree match requires `targetPath.startsWith(rootPath + "/")`. `/WHUT/CS2` must not match root `/WHUT/CS`.

- [ ] **Step 5: Verify task**

```bash
mvn -pl whut-eval-app -am -Dtest=MentorScoreImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: service tests pass and diff check is clean.

- [ ] **Step 6: Commit**

```bash
git add whut-eval-application/src/main/java/edu/whut/eval/application/finalrecord/importing \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MentorScoreImportApplicationServiceTest.java
git commit -m "feat: add mentor score import service"
```

---

### Task 4: Persistence And SQL Scope

**Files:**

- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/MentorScoreImportMapper.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisMentorScoreImportRepository.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreImportStudentTargetRow.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreImportedComponentRow.java`
- Create: `whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreCategoryTotalRow.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisMentorScoreImportRepositoryIntegrationTest.java`

- [ ] **Step 1: Write failing repository integration tests**

Use H2 MySQL mode and a local schema with:

- `iam_user` without any `identity` column;
- `org_unit`;
- `org_membership`;
- `final_record`;
- `final_component_score`.

Create the test with the same Spring/MyBatis test shape as `MybatisPlusFinalRecordRepositoryIntegrationTest`:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MybatisMentorScoreImportRepositoryIntegrationTest.TestConfig.class)
class MybatisMentorScoreImportRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MentorScoreImportRepository repository;

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_component_score");
        jdbcTemplate.execute("DROP TABLE IF EXISTS final_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_membership");
        jdbcTemplate.execute("DROP TABLE IF EXISTS org_unit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS iam_user");
        jdbcTemplate.execute("CREATE TABLE iam_user (id BIGINT PRIMARY KEY, user_no VARCHAR(64), user_name VARCHAR(128), status VARCHAR(32))");
        jdbcTemplate.execute("CREATE TABLE org_unit (id BIGINT PRIMARY KEY, unit_name VARCHAR(128), path VARCHAR(512), status VARCHAR(32))");
        jdbcTemplate.execute("""
                CREATE TABLE org_membership (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  org_unit_id BIGINT NOT NULL,
                  membership_type VARCHAR(32) NOT NULL,
                  is_primary TINYINT(1) NOT NULL DEFAULT 0,
                  status VARCHAR(32) NOT NULL)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE final_record (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  student_user_id BIGINT NOT NULL,
                  academic_year VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  moral_total DECIMAL(10,2) NOT NULL,
                  intellectual_total DECIMAL(10,2) NOT NULL,
                  physical_total DECIMAL(10,2) NOT NULL,
                  labor_total DECIMAL(10,2) NOT NULL,
                  grand_total DECIMAL(10,2) NOT NULL,
                  submitted_at DATETIME NULL,
                  confirmed_at DATETIME NULL,
                  confirm_comment VARCHAR(1000) NULL,
                  version BIGINT NOT NULL,
                  created_at DATETIME NOT NULL,
                  updated_at DATETIME NOT NULL,
                  UNIQUE KEY uk_final_record_student_year (student_user_id, academic_year))
                """);
        jdbcTemplate.execute("""
                CREATE TABLE final_component_score (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  final_record_id BIGINT NOT NULL,
                  category_code VARCHAR(64) NOT NULL,
                  item_code VARCHAR(64) NOT NULL,
                  score_value DECIMAL(10,2) NOT NULL,
                  display_text VARCHAR(1000) NULL,
                  source_type VARCHAR(32) NOT NULL,
                  source_ref_id VARCHAR(64) NULL,
                  created_at DATETIME NOT NULL)
                """);
        insertOrgUnit(2002L, "计算机与人工智能学院", "/WHUT/CS", "ACTIVE");
        insertOrgUnit(2010L, "计科一班", "/WHUT/CS/CS2022/CS2201", "ACTIVE");
        insertOrgUnit(3010L, "机械一班", "/WHUT/ME/ME2022/ME2201", "ACTIVE");
        insertStudent(1001L, "S1001", "张三", "ACTIVE", 2010L, true, "ACTIVE");
        insertStudent(1002L, "S1002", "李四", "DISABLED", 2010L, true, "ACTIVE");
        insertStudent(1003L, "S1003", "王五", "ACTIVE", 2010L, true, "DISABLED");
    }
```

Add exact test cases:

```java
@Test
void shouldFindActiveStudentTargetWithoutIamUserIdentityColumn() {
    Optional<MentorScoreImportStudentTarget> target = repository.findTarget("S1001", "2025-2026");

    assertThat(target).isPresent();
    assertThat(target.get().studentUserId()).isEqualTo(1001L);
    assertThat(target.get().orgUnitId()).isEqualTo(2010L);
    assertThat(target.get().orgPath()).isEqualTo("/WHUT/CS/CS2022/CS2201");
    assertThat(target.get().finalRecordStatus()).isNull();
}

@Test
void shouldExcludeInactiveUserAndInactivePrimaryMembership() {
    assertThat(repository.findTarget("S1002", "2025-2026")).isEmpty();
    assertThat(repository.findTarget("S1003", "2025-2026")).isEmpty();
}

@Test
void shouldFindActiveOrgPathForScopeRoot() {
    assertThat(repository.findActiveOrgPath(2002L)).contains("/WHUT/CS");
    assertThat(repository.findActiveOrgPath(9999L)).isEmpty();
}

@Test
void shouldInsertDraftRecordAndImportedComponent() {
    repository.upsertDraftComponent(component("MORAL", "MORAL_HONOR", "1.25", "mentor-001"), "D7-batch");

    Long recordId = jdbcTemplate.queryForObject("SELECT id FROM final_record WHERE student_user_id = 1001 AND academic_year = '2025-2026'", Long.class);
    assertThat(recordId).isNotNull();
    assertThat(jdbcTemplate.queryForObject("SELECT status FROM final_record WHERE id = ?", String.class, recordId)).isEqualTo("DRAFT");
    assertThat(jdbcTemplate.queryForObject("SELECT moral_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
            .isEqualByComparingTo("1.25");
    assertThat(jdbcTemplate.queryForObject("SELECT grand_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
            .isEqualByComparingTo("1.25");
    assertThat(jdbcTemplate.queryForObject("SELECT version FROM final_record WHERE id = ?", Long.class, recordId)).isEqualTo(1L);
    assertThat(jdbcTemplate.queryForObject("SELECT source_type FROM final_component_score WHERE final_record_id = ?", String.class, recordId))
            .isEqualTo("IMPORT");
}

@Test
void shouldUpdateOnlyImportedComponentAndPreserveApplicationComponent() {
    Long recordId = insertDraftRecord(1001L, "2025-2026");
    insertComponent(recordId, "MORAL", "MORAL_HONOR", "3.00", "APPLICATION", "app-1");
    insertComponent(recordId, "MORAL", "MORAL_HONOR", "1.00", "IMPORT", "old-import");

    repository.upsertDraftComponent(component("MORAL", "MORAL_HONOR", "2.00", "new-import"), "D7-batch");

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM final_component_score WHERE final_record_id = ? AND source_type = 'APPLICATION'", Long.class, recordId))
            .isEqualTo(1L);
    assertThat(jdbcTemplate.queryForObject("SELECT score_value FROM final_component_score WHERE final_record_id = ? AND source_type = 'IMPORT'", BigDecimal.class, recordId))
            .isEqualByComparingTo("2.00");
    assertThat(jdbcTemplate.queryForObject("SELECT moral_total FROM final_record WHERE id = ?", BigDecimal.class, recordId))
            .isEqualByComparingTo("5.00");
}
```

Use helper methods `insertOrgUnit`, `insertStudent`, `insertDraftRecord`, `insertComponent`, and `component(...)` in the test file. `component(...)` returns:

```java
private static MentorScoreImportedComponent component(String categoryCode, String itemCode, String scoreValue, String sourceRefId) {
    return new MentorScoreImportedComponent(2L, 1001L, "2025-2026", categoryCode, itemCode,
            new BigDecimal(scoreValue), "导师评分", sourceRefId);
}
```

- [ ] **Step 2: Run failing repository tests**

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisMentorScoreImportRepositoryIntegrationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because mapper/repository do not exist.

- [ ] **Step 3: Add mapper rows**

`MentorScoreImportStudentTargetRow` fields:

```java
package edu.whut.eval.infra.persistence.repository.row;

public class MentorScoreImportStudentTargetRow {
private Long studentUserId;
private String studentNo;
private Long orgUnitId;
private String orgPath;
private String finalRecordStatus;

    // Generate standard getters and setters for every field.
}
```

`MentorScoreImportedComponentRow` fields:

```java
package edu.whut.eval.infra.persistence.repository.row;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MentorScoreImportedComponentRow {
private Long id;
private Long finalRecordId;
private String categoryCode;
private String itemCode;
private BigDecimal scoreValue;
private String displayText;
private String sourceRefId;
private LocalDateTime createdAt;

    // Generate standard getters and setters for every field.
}
```

`MentorScoreCategoryTotalRow` fields:

```java
package edu.whut.eval.infra.persistence.repository.row;

import java.math.BigDecimal;

public class MentorScoreCategoryTotalRow {
private String categoryCode;
private BigDecimal scoreValue;

    // Generate standard getters and setters for every field.
}
```

- [ ] **Step 4: Add mapper methods**

`MentorScoreImportMapper` methods:

```java
@Select("""
        SELECT u.id AS student_user_id,
               u.user_no AS student_no,
               ou.id AS org_unit_id,
               ou.path AS org_path,
               fr.status AS final_record_status
        FROM iam_user u
        JOIN org_membership om ON om.user_id = u.id AND om.status = 'ACTIVE' AND om.is_primary = 1
        JOIN org_unit ou ON ou.id = om.org_unit_id AND ou.status = 'ACTIVE'
        LEFT JOIN final_record fr ON fr.student_user_id = u.id AND fr.academic_year = #{academicYear}
        WHERE u.user_no = #{studentNo}
          AND u.status = 'ACTIVE'
        LIMIT 1
        """)
MentorScoreImportStudentTargetRow selectTarget(@Param("studentNo") String studentNo,
                                               @Param("academicYear") String academicYear);
```

Add:

- `@Select("SELECT path FROM org_unit WHERE id = #{orgUnitId} AND status = 'ACTIVE'") String selectActiveOrgPath(@Param("orgUnitId") Long orgUnitId);`
- `@Select("SELECT id, student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at FROM final_record WHERE student_user_id = #{studentUserId} AND academic_year = #{academicYear} FOR UPDATE") FinalRecordDO selectFinalRecordForUpdate(@Param("studentUserId") Long studentUserId, @Param("academicYear") String academicYear);`
- `@Insert("INSERT INTO final_record (student_user_id, academic_year, status, moral_total, intellectual_total, physical_total, labor_total, grand_total, submitted_at, confirmed_at, confirm_comment, version, created_at, updated_at) VALUES (#{studentUserId}, #{academicYear}, #{status}, #{moralTotal}, #{intellectualTotal}, #{physicalTotal}, #{laborTotal}, #{grandTotal}, #{submittedAt}, #{confirmedAt}, #{confirmComment}, #{version}, #{createdAt}, #{updatedAt})") @Options(useGeneratedKeys = true, keyProperty = "id") int insertDraft(FinalRecordDO record);`
- `@Select("SELECT id, final_record_id, category_code, item_code, score_value, display_text, source_ref_id, created_at FROM final_component_score WHERE final_record_id = #{finalRecordId} AND category_code = #{categoryCode} AND item_code = #{itemCode} AND source_type = 'IMPORT' LIMIT 1") MentorScoreImportedComponentRow selectImportedComponent(@Param("finalRecordId") Long finalRecordId, @Param("categoryCode") String categoryCode, @Param("itemCode") String itemCode);`
- `@Insert("INSERT INTO final_component_score (final_record_id, category_code, item_code, score_value, display_text, source_type, source_ref_id, created_at) VALUES (#{finalRecordId}, #{categoryCode}, #{itemCode}, #{scoreValue}, #{displayText}, 'IMPORT', #{sourceRefId}, #{createdAt})") int insertImportedComponent(MentorScoreImportedComponentRow component);`
- `@Update("UPDATE final_component_score SET score_value = #{scoreValue}, display_text = #{displayText}, source_ref_id = #{sourceRefId}, created_at = #{createdAt} WHERE id = #{id} AND source_type = 'IMPORT'") int updateImportedComponent(MentorScoreImportedComponentRow component);`
- `@Select("SELECT category_code AS categoryCode, COALESCE(SUM(score_value), 0) AS scoreValue FROM final_component_score WHERE final_record_id = #{finalRecordId} GROUP BY category_code") List<MentorScoreCategoryTotalRow> selectTotals(@Param("finalRecordId") Long finalRecordId);`
- `@Update("UPDATE final_record SET moral_total = #{moral}, intellectual_total = #{intellectual}, physical_total = #{physical}, labor_total = #{labor}, grand_total = #{grand}, updated_at = #{updatedAt}, version = version + 1 WHERE id = #{finalRecordId} AND status = 'DRAFT'") int updateTotals(@Param("finalRecordId") Long finalRecordId, @Param("moral") BigDecimal moral, @Param("intellectual") BigDecimal intellectual, @Param("physical") BigDecimal physical, @Param("labor") BigDecimal labor, @Param("grand") BigDecimal grand, @Param("updatedAt") LocalDateTime updatedAt);`

Use `source_type = 'IMPORT'` in imported component queries.

- [ ] **Step 5: Implement repository**

`MybatisMentorScoreImportRepository`:

- maps row objects into application records;
- `findTarget(...)` delegates to mapper;
- `findActiveOrgPath(...)` returns optional root path;
- `importedComponentExists(...)` finds the target final record and imported component;
- `upsertDraftComponent(...)` opens/reuses a draft final record, inserts or updates the imported component, then recalculates totals.

Implementation rules:

- new draft has status `DRAFT`, zero totals, version `0`, null submit/confirm fields, and `created_at = updated_at = now`;
- new component has `source_type = 'IMPORT'`, `source_ref_id` from row/default, and `created_at = now`;
- update component sets replacement values and current timestamp;
- total recalculation sums all current components by category and defaults missing categories to zero;
- final-record version increments by one during total update.

- [ ] **Step 6: Verify task**

```bash
mvn -pl whut-eval-app -am -Dtest=MybatisMentorScoreImportRepositoryIntegrationTest,MentorScoreImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: repository and service tests pass.

- [ ] **Step 7: Commit**

```bash
git add whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/mapper/MentorScoreImportMapper.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/MybatisMentorScoreImportRepository.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreImportStudentTargetRow.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreImportedComponentRow.java \
  whut-eval-infra/src/main/java/edu/whut/eval/infra/persistence/repository/row/MentorScoreCategoryTotalRow.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/MybatisMentorScoreImportRepositoryIntegrationTest.java
git commit -m "feat: persist mentor score imports"
```

---

### Task 5: HTTP Controller And Response DTOs

**Files:**

- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/MentorScoreImportFailedRowResponse.java`
- Create: `whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/MentorScoreImportResultResponse.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java`
- Create: `whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java`

- [ ] **Step 1: Write failing MVC tests**

Test cases:

- multipart `POST /api/admin/imports/mentor-scores` returns result shape;
- empty file returns `400 VAL-4001`;
- invalid mode returns `400 VAL-4001`;
- service `ConflictException` returns `409 BIZ-4090`;
- service `FileStorageException("文件处理失败，请稍后重试")` returns `503 EXT-5033`;
- response has `importBatchId`, counts, `failedRows[0].rowNo/code/message/rawValue`, and `processedAt`.

Use `standaloneSetup(new AdminScoreImportController(service)).setControllerAdvice(new GlobalExceptionHandler())`.

- [ ] **Step 2: Write failing security annotation test**

Reflect `AdminScoreImportController#importMentorScores(MultipartFile, String, String)` and assert:

```java
assertThat(preAuthorize.value()).isEqualTo(
        "hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)"
);
assertThat(AuthorizationPermissionCodes.SCORE_IMPORT).isEqualTo("score.import");
```

- [ ] **Step 3: Run failing controller tests**

```bash
mvn -pl whut-eval-app -am -Dtest=AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compilation fails because controller/DTOs do not exist.

- [ ] **Step 4: Add response DTOs**

`MentorScoreImportFailedRowResponse`:

```java
package edu.whut.eval.interfaces.admin.response;

import java.util.Map;

public record MentorScoreImportFailedRowResponse(
        Long rowNo,
        String code,
        String message,
        Map<String, String> rawValue
) {
}
```

`MentorScoreImportResultResponse`:

```java
package edu.whut.eval.interfaces.admin.response;

import java.util.List;

public record MentorScoreImportResultResponse(
        String importBatchId,
        long totalCount,
        long successCount,
        long failedCount,
        List<MentorScoreImportFailedRowResponse> failedRows,
        String processedAt
) {
}
```

- [ ] **Step 5: Add controller**

`AdminScoreImportController`:

```java
@RestController
@Validated
@RequestMapping("/api/admin/imports")
public class AdminScoreImportController {

    private final MentorScoreImportApplicationService importApplicationService;

    public AdminScoreImportController(MentorScoreImportApplicationService importApplicationService) {
        this.importApplicationService = importApplicationService;
    }

    @PreAuthorize("hasAuthority(T(edu.whut.eval.application.auth.AuthorizationPermissionCodes).SCORE_IMPORT)")
    @PostMapping(value = "/mentor-scores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MentorScoreImportResultResponse> importMentorScores(@RequestParam("file") MultipartFile file,
                                                                           @RequestParam("academicYear") String academicYear,
                                                                           @RequestParam(value = "importMode", defaultValue = "UPSERT") String importMode) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("上传文件不能为空");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new FileStorageException("文件处理失败，请稍后重试", exception);
        }
        MentorScoreImportResult result = importApplicationService.importMentorScores(
                new ImportMentorScoresCommand(bytes, academicYear, importMode)
        );
        return ApiResponse.success(toResponse(result));
    }
}
```

Mapping rules:

- `processedAt` uses `result.processedAt().toString()`;
- failed rows map field-for-field;
- no pagination wrapper is used.

- [ ] **Step 6: Verify task**

```bash
mvn -pl whut-eval-app -am -Dtest=AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest,MentorScoreImportApplicationServiceTest test -Dsurefire.failIfNoSpecifiedTests=false
git diff --check
```

Expected: controller and service tests pass.

- [ ] **Step 7: Commit**

```bash
git add whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/AdminScoreImportController.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/MentorScoreImportFailedRowResponse.java \
  whut-eval-interfaces/src/main/java/edu/whut/eval/interfaces/admin/response/MentorScoreImportResultResponse.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerWebMvcTest.java \
  whut-eval-app/src/test/java/edu/whut/eval/app/finalrecord/AdminScoreImportControllerSecurityAnnotationTest.java
git commit -m "feat: expose mentor score import endpoint"
```

---

### Task 6: Delivery Docs And Final Verification

**Files:**

- Modify: `docs/team-delivery/group-d-score-finalization-import-export.md`
- Modify: `docs/team-delivery/README.md`
- Modify: `whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java`

- [ ] **Step 1: Write failing doc consistency test**

Update `TeamDeliverySqlConsistencyTest` doc assertions so D-7 is no longer expected as deferred while D-8 to D-11 remain deferred:

```java
assertThat(doc).contains("D-8 / D-9 / D-10 / D-11");
assertThat(doc).contains("D-7 当前已实现");
assertThat(doc).contains("自动 smoke gate 不覆盖 D-8 至 D-11");
```

Remove or update assertions that require `"D-7 / D-8 / D-9 / D-10 / D-11"` to remain deferred as a single group.

- [ ] **Step 2: Run failing doc test**

```bash
mvn -pl whut-eval-app -am -Dtest=TeamDeliverySqlConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: doc assertion fails until docs are updated.

- [ ] **Step 3: Update delivery docs**

In `group-d-score-finalization-import-export.md`:

- change top note to say D-7 is implemented in Java and D-8 to D-11 remain `DEFERRED_AFTER_MINIMAL_D`;
- table row D-7 status becomes `IMPLEMENTED`;
- keep D-8, D-9, D-10, D-11 deferred;
- add a short D-7 implementation note pointing to `AdminScoreImportController`.

In `docs/team-delivery/README.md`, keep the definition of `DEFERRED_AFTER_MINIMAL_D`, but do not state that D-7 remains deferred.

- [ ] **Step 4: Run focused verification**

```bash
mvn -pl whut-eval-app -am -Dtest=MentorScoreImportParserTest,MentorScoreImportApplicationServiceTest,MybatisMentorScoreImportRepositoryIntegrationTest,AdminScoreImportControllerWebMvcTest,AdminScoreImportControllerSecurityAnnotationTest,TeamDeliverySqlConsistencyTest test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: all focused D-7 tests pass.

- [ ] **Step 5: Run broader regression**

```bash
mvn -pl whut-eval-app -am -Dtest='*FinalRecord*Test,*Iam*Test,*UserAdmin*Test,TeamDeliverySqlConsistencyTest' test -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: final-record, IAM, user-admin, and delivery SQL tests pass.

- [ ] **Step 6: Run full verification**

```bash
git diff --check
mvn test
```

Expected: diff check has no output and full Maven test suite passes.

- [ ] **Step 7: Commit docs and verification evidence**

```bash
git add docs/team-delivery/group-d-score-finalization-import-export.md \
  docs/team-delivery/README.md \
  whut-eval-app/src/test/java/edu/whut/eval/app/iam/TeamDeliverySqlConsistencyTest.java
git commit -m "docs: mark D-7 import implemented"
```

---

## Final Branch Workflow

After all implementation commits:

1. Re-run final verification:

```bash
git status --short --branch
git diff --check
mvn test
```

2. Run code review-loop or the closest available readonly review path. If `review-loop` still stalls, capture:

- `loop doctor` output;
- the valid reviewer artifact path;
- stale worker states from `.trae-orch/capabilities/.../manifest.json`.

3. Fix any confirmed P0/P1/P2 findings.

4. Merge to `main` only after D-7 branch is clean and verified:

```bash
git switch main
git merge --ff-only feature/d7-mentor-score-import
mvn test
git push origin main
```

5. Do not merge the existing D-11 fork before D-8, D-9, and D-10 state is resolved according to the dependency order.

## Acceptance Checklist

- `score.import` constant and safe-init seed exist and are rerunnable.
- `POST /api/admin/imports/mentor-scores` is exposed with `SCORE_IMPORT` authorization.
- Excel parser enforces frozen headers and skips blank rows.
- Row-level validation returns deterministic `failedRows`.
- `UPSERT` and `STRICT_INSERT` behavior matches the spec.
- Active-student lookup does not depend on `iam_user.identity`.
- Scope checks use real org paths.
- Only draft final records are mutated.
- Totals and versions update after successful imports.
- D-8, D-9, D-10, and D-11 behavior is not introduced by this branch.
