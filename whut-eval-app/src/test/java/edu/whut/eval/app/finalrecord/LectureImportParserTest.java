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
import java.nio.charset.StandardCharsets;
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
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：文件不可解析");
        assertThatThrownBy(() -> parser.parse(noSheets()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：缺少工作表");
        assertThatThrownBy(() -> parser.parse(xlsxWithoutHeader()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：缺少表头");
        assertThatThrownBy(() -> parser.parse(xlsxWithHeaders("studentNo", "bad", "displayText")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：第2列表头应为 scoreValue");
        assertThatThrownBy(() -> parser.parse("studentNo,scoreValue,displayText\nS1,1.00,签到".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：文件不可解析");
        assertThatThrownBy(() -> parser.parse("not excel".getBytes(StandardCharsets.UTF_8)))
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
