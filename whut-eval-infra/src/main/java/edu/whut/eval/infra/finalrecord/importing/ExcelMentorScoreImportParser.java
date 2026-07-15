package edu.whut.eval.infra.finalrecord.importing;

import edu.whut.eval.application.finalrecord.importing.MentorScoreImportParser;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportRow;
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
public class ExcelMentorScoreImportParser implements MentorScoreImportParser {

    private static final long MAX_MENTOR_SCORE_IMPORT_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 10000;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "studentNo", "categoryCode", "itemCode", "scoreValue", "displayText", "sourceRefId"
    );

    @Override
    public List<MentorScoreImportRow> parse(byte[] fileContent) {
        if (fileContent == null) {
            throw new ValidationException("导入模板错误：文件不可解析");
        }
        if (fileContent.length > MAX_MENTOR_SCORE_IMPORT_BYTES) {
            throw new ValidationException("导师评分导入文件不超过 10MB");
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

            List<MentorScoreImportRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String studentNo = cellValue(row.getCell(0), formatter);
                String categoryCode = cellValue(row.getCell(1), formatter);
                String itemCode = cellValue(row.getCell(2), formatter);
                String scoreValue = cellValue(row.getCell(3), formatter);
                String displayText = cellValue(row.getCell(4), formatter);
                String sourceRefId = cellValue(row.getCell(5), formatter);

                if (isBlank(studentNo) && isBlank(categoryCode) && isBlank(itemCode)
                        && isBlank(scoreValue) && isBlank(displayText) && isBlank(sourceRefId)) {
                    continue;
                }

                rows.add(new MentorScoreImportRow(
                        i + 1L,
                        studentNo,
                        categoryCode,
                        itemCode,
                        scoreValue,
                        displayText,
                        sourceRefId
                ));
                if (rows.size() > MAX_ROWS) {
                    throw new ValidationException("导师评分导入文件最多支持 10000 行且不超过 10MB");
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
