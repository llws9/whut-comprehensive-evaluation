package edu.whut.eval.app.finalrecord;

import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.importing.ActivityImportRow;
import edu.whut.eval.infra.finalrecord.importing.ExcelActivityImportParser;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
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

class ActivityImportParserTest {

    private final ExcelActivityImportParser parser = new ExcelActivityImportParser();

    @Test
    void shouldParseActivityWorkbook() throws Exception {
        byte[] workbook = xlsx(
                row(" 2022305001 ", " 志愿服务签到 "),
                row("2022305002", ""));

        List<ActivityImportRow> rows = parser.parse(workbook);

        assertThat(rows).containsExactly(
                new ActivityImportRow(2L, "2022305001", "志愿服务签到"),
                new ActivityImportRow(3L, "2022305002", null)
        );
    }

    @Test
    void shouldAcceptHeaderOnlyWorkbook() throws Exception {
        byte[] workbook = xlsx();

        assertThat(parser.parse(workbook)).isEmpty();
    }

    @Test
    void shouldRejectHeaderMismatch() throws Exception {
        byte[] workbook = xlsxWithHeaders("学号", "displayText");

        assertThatThrownBy(() -> parser.parse(workbook))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：第1列表头应为 studentNo");
    }

    @Test
    void shouldIgnoreExtraColumnsBeyondDisplayText() throws Exception {
        byte[] workbook = xlsx(row("2022305001", "签到", "备注不会导入"));

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
    void shouldRejectMoreThanFiveThousandNonBlankRows() throws Exception {
        String[][] rows = new String[5001][];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = row("S" + i, "签到");
        }

        assertThatThrownBy(() -> parser.parse(xlsx(rows)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("文体活动导入文件最多支持 5000 行且不超过 5MB");
    }

    @Test
    void shouldNotCountBlankRowsTowardRowLimit() throws Exception {
        String[][] rows = new String[5020][];
        for (int i = 0; i < 5000; i++) {
            rows[i] = row("S" + i, "签到");
        }
        for (int i = 5000; i < rows.length; i++) {
            rows[i] = row(null, null);
        }

        assertThat(parser.parse(xlsx(rows))).hasSize(5000);
    }

    @Test
    void shouldRejectMissingSheetHeaderAndUnreadableWorkbook() throws Exception {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：文件不可解析");
        assertThatThrownBy(() -> parser.parse(noSheets()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：缺少工作表");
        assertThatThrownBy(() -> parser.parse(xlsxWithoutHeader()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：缺少表头");
        assertThatThrownBy(() -> parser.parse("not excel".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("导入模板错误：文件不可解析");
    }

    @Test
    void shouldParseXlsWorkbookContent() throws Exception {
        byte[] workbook = xls(row("2022305001", "校运会志愿服务"));

        assertThat(parser.parse(workbook)).containsExactly(
                new ActivityImportRow(2L, "2022305001", "校运会志愿服务")
        );
    }

    @Test
    void shouldReadFormulaCellWithoutEvaluator() throws Exception {
        byte[] workbook = workbookWithFormulaDisplayText();

        List<ActivityImportRow> rows = parser.parse(workbook);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).displayText()).isNotBlank();
    }

    private static String[] row(String studentNo, String displayText, String... ignored) {
        String[] values = new String[2 + ignored.length];
        values[0] = studentNo;
        values[1] = displayText;
        System.arraycopy(ignored, 0, values, 2, ignored.length);
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

    private static byte[] workbookWithFormulaDisplayText() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet.createRow(0), "studentNo", "displayText");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("2022305001");
            Cell formula = row.createCell(1);
            formula.setCellFormula("\"志愿\"&\"服务\"");
            return bytes(workbook);
        }
    }

    private static byte[] workbookBytes(Workbook workbook, String[]... rows) throws Exception {
        try (workbook) {
            Sheet sheet = workbook.createSheet("Sheet1");
            writeRow(sheet.createRow(0), "studentNo", "displayText");
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
