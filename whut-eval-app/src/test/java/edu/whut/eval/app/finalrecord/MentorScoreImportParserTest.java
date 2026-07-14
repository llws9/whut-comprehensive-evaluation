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
        assertThat(rows.get(0).categoryCode()).isEqualTo("MORAL");
        assertThat(rows.get(0).itemCode()).isEqualTo("MORAL_HONOR");
        assertThat(rows.get(0).scoreValue()).isEqualTo("1.25");
        assertThat(rows.get(0).displayText()).isEqualTo("导师评分");
        assertThat(rows.get(0).sourceRefId()).isEqualTo("mentor-001");
    }

    @Test
    void shouldSkipBlankRows() throws Exception {
        byte[] workbook = workbook(
                row(null, null, null, null, null, null),
                row("S1002", "LABOR", "LABOR_SERVICE", "2", null, null)
        );

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
        byte[] workbook = workbookWithHeaders(List.of(
                "studentNo", "bad", "itemCode", "scoreValue", "displayText", "sourceRefId"
        ));

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

    private static String[] row(String studentNo,
                                String categoryCode,
                                String itemCode,
                                String scoreValue,
                                String displayText,
                                String sourceRefId) {
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
