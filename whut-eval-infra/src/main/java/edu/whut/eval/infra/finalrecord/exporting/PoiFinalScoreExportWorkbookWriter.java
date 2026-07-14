package edu.whut.eval.infra.finalrecord.exporting;

import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportFile;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportGenerationException;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportRow;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportWorkbookWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class PoiFinalScoreExportWorkbookWriter implements FinalScoreExportWorkbookWriter {

    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] HEADERS = {
            "最终成绩ID", "学年", "学号", "姓名", "年级编码", "年级", "班级编码", "班级",
            "状态", "德育总分", "智育总分", "体育总分", "劳育总分", "总分", "提交时间", "确认时间"
    };

    @Override
    public FinalScoreExportFile write(String academicYear, List<FinalScoreExportRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("final-scores");
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle numericStyle = numericStyle(workbook);
            writeHeader(sheet, headerStyle);
            int rowIndex = 1;
            for (FinalScoreExportRow row : rows) {
                writeDataRow(sheet.createRow(rowIndex++), row, numericStyle);
            }
            sheet.createFreezePane(0, 1);
            applyColumnWidths(sheet);
            workbook.write(output);
            return new FinalScoreExportFile("final-scores-" + academicYear + ".xlsx", CONTENT_TYPE, output.toByteArray());
        } catch (IOException | RuntimeException exception) {
            throw new FinalScoreExportGenerationException("Excel 生成失败", exception);
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle numericStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle) {
        Row row = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = row.createCell(column, CellType.STRING);
            cell.setCellValue(HEADERS[column]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeDataRow(Row excelRow, FinalScoreExportRow row, CellStyle numericStyle) {
        writeString(excelRow, 0, row.finalRecordId() == null ? null : row.finalRecordId().toString());
        writeString(excelRow, 1, row.academicYear());
        writeString(excelRow, 2, row.studentUserNo());
        writeString(excelRow, 3, row.studentUserName());
        writeString(excelRow, 4, row.gradeCode());
        writeString(excelRow, 5, row.gradeName());
        writeString(excelRow, 6, row.classCode());
        writeString(excelRow, 7, row.className());
        writeString(excelRow, 8, row.status());
        writeNumber(excelRow, 9, row.moralTotal(), numericStyle);
        writeNumber(excelRow, 10, row.intellectualTotal(), numericStyle);
        writeNumber(excelRow, 11, row.physicalTotal(), numericStyle);
        writeNumber(excelRow, 12, row.laborTotal(), numericStyle);
        writeNumber(excelRow, 13, row.grandTotal(), numericStyle);
        writeInstant(excelRow, 14, row.submittedAt());
        writeInstant(excelRow, 15, row.confirmedAt());
    }

    private void writeString(Row row, int column, String value) {
        Cell cell = row.createCell(column, value == null ? CellType.BLANK : CellType.STRING);
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    private void writeNumber(Row row, int column, BigDecimal value, CellStyle numericStyle) {
        Cell cell = row.createCell(column, value == null ? CellType.BLANK : CellType.NUMERIC);
        if (value != null) {
            cell.setCellStyle(numericStyle);
            cell.setCellValue(value.setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
    }

    private void writeInstant(Row row, int column, Instant value) {
        writeString(row, column, value == null ? null : value.truncatedTo(ChronoUnit.SECONDS).toString());
    }

    private void applyColumnWidths(Sheet sheet) {
        int[] widths = {14, 14, 16, 14, 14, 18, 14, 18, 12, 12, 12, 12, 12, 12, 24, 24};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
    }
}
