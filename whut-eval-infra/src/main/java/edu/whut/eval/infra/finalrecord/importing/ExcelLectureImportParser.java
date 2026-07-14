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

    private static final long MAX_LECTURE_IMPORT_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 5000;
    private static final List<String> REQUIRED_HEADERS = List.of("studentNo", "scoreValue", "displayText");

    @Override
    public List<LectureImportRow> parse(byte[] fileContent) {
        if (fileContent == null) {
            throw new ValidationException("导入模板错误：文件不可解析");
        }
        if (fileContent.length > MAX_LECTURE_IMPORT_BYTES) {
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
