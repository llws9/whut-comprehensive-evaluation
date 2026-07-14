package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportFile;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportRow;
import edu.whut.eval.infra.finalrecord.exporting.PoiFinalScoreExportWorkbookWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.PaneInformation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoiFinalScoreExportWorkbookWriterTest {

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final List<String> HEADERS = List.of(
            "最终成绩ID", "学年", "学号", "姓名", "年级编码", "年级", "班级编码", "班级",
            "状态", "德育总分", "智育总分", "体育总分", "劳育总分", "总分", "提交时间", "确认时间"
    );

    private final PoiFinalScoreExportWorkbookWriter writer = new PoiFinalScoreExportWorkbookWriter();

    @Test
    void shouldWriteFinalScoreWorkbookContract() throws Exception {
        FinalScoreExportFile file = writer.write("2025-2026", List.of(fullRow(), sparseRow()));

        assertThat(file.contentType()).isEqualTo(XLSX_CONTENT_TYPE);
        assertThat(file.filename()).isEqualTo("final-scores-2025-2026.xlsx");

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = workbook.getSheet("final-scores");
            assertThat(sheet).isNotNull();
            PaneInformation paneInformation = sheet.getPaneInformation();
            assertThat(paneInformation).isNotNull();
            assertThat(paneInformation.isFreezePane()).isTrue();
            assertThat((int) paneInformation.getHorizontalSplitPosition()).isEqualTo(1);

            assertHeaders(sheet.getRow(0));
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertFullRow(sheet.getRow(1));
            assertSparseRow(sheet.getRow(2));
            assertNoFormulas(sheet);
            for (int column = 0; column < HEADERS.size(); column++) {
                assertThat(sheet.getColumnWidth(column)).isGreaterThan(8 * 256);
            }
            assertThat(sheet.getColumnWidth(14)).isGreaterThanOrEqualTo(20 * 256);
            assertThat(sheet.getColumnWidth(15)).isGreaterThanOrEqualTo(20 * 256);
        }
    }

    private void assertHeaders(Row row) {
        assertThat(row).isNotNull();
        for (int column = 0; column < HEADERS.size(); column++) {
            assertThat(row.getCell(column).getStringCellValue()).isEqualTo(HEADERS.get(column));
        }
    }

    private void assertFullRow(Row row) {
        assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
        assertThat(row.getCell(0).getStringCellValue()).isEqualTo("41001");
        assertThat(row.getCell(1).getStringCellValue()).isEqualTo("2025-2026");
        assertThat(row.getCell(2).getStringCellValue()).isEqualTo("2024305001");
        assertThat(row.getCell(3).getStringCellValue()).isEqualTo("张三");
        assertThat(row.getCell(4).getStringCellValue()).isEqualTo("CS2022");
        assertThat(row.getCell(5).getStringCellValue()).isEqualTo("2022级计算机");
        assertThat(row.getCell(6).getStringCellValue()).isEqualTo("CS2201");
        assertThat(row.getCell(7).getStringCellValue()).isEqualTo("计科一班");
        assertThat(row.getCell(8).getStringCellValue()).isEqualTo("SUBMITTED");
        assertNumericCell(row.getCell(9), 0.80d);
        assertNumericCell(row.getCell(10), 2.01d);
        assertNumericCell(row.getCell(11), 0.61d);
        assertNumericCell(row.getCell(12), 1.20d);
        assertNumericCell(row.getCell(13), 4.62d);
        assertThat(row.getCell(14).getStringCellValue()).isEqualTo("2026-07-07T12:00:00Z");
        assertThat(row.getCell(15).getStringCellValue()).isEqualTo("2026-07-08T01:02:03Z");
    }

    private void assertSparseRow(Row row) {
        assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
        assertThat(row.getCell(0).getStringCellValue()).isEqualTo("41002");
        assertThat(row.getCell(1).getStringCellValue()).isEqualTo("2025-2026");
        assertThat(row.getCell(2).getStringCellValue()).isEqualTo("2024305002");
        assertThat(row.getCell(3).getStringCellValue()).isEqualTo("李四");
        assertBlank(row.getCell(4));
        assertBlank(row.getCell(5));
        assertBlank(row.getCell(6));
        assertBlank(row.getCell(7));
        assertThat(row.getCell(8).getStringCellValue()).isEqualTo("CONFIRMED");
        assertBlank(row.getCell(9));
        assertBlank(row.getCell(10));
        assertBlank(row.getCell(11));
        assertBlank(row.getCell(12));
        assertBlank(row.getCell(13));
        assertBlank(row.getCell(14));
        assertBlank(row.getCell(15));
    }

    private void assertNumericCell(Cell cell, double expected) {
        assertThat(cell.getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(cell.getNumericCellValue()).isEqualTo(expected);
        assertThat(cell.getCellStyle().getDataFormatString()).isEqualTo("0.00");
    }

    private void assertBlank(Cell cell) {
        assertThat(cell.getCellType()).isEqualTo(CellType.BLANK);
    }

    private void assertNoFormulas(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
            }
        }
    }

    private FinalScoreExportRow fullRow() {
        return new FinalScoreExportRow(
                41001L,
                1001L,
                "2024305001",
                "张三",
                "CS2022",
                "2022级计算机",
                "CS2201",
                "计科一班",
                "2025-2026",
                "SUBMITTED",
                new BigDecimal("0.804"),
                new BigDecimal("2.005"),
                new BigDecimal("0.605"),
                new BigDecimal("1.20"),
                new BigDecimal("4.615"),
                Instant.parse("2026-07-07T12:00:00.987Z"),
                Instant.parse("2026-07-08T01:02:03.456Z")
        );
    }

    private FinalScoreExportRow sparseRow() {
        return new FinalScoreExportRow(
                41002L,
                1002L,
                "2024305002",
                "李四",
                null,
                null,
                null,
                null,
                "2025-2026",
                "CONFIRMED",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
