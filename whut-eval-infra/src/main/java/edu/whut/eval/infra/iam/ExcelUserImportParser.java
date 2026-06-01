package edu.whut.eval.infra.iam;

import edu.whut.eval.application.iam.query.UserImportRowView;
import edu.whut.eval.application.iam.service.UserImportParser;
import edu.whut.eval.common.exception.ValidationException;
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
public class ExcelUserImportParser implements UserImportParser {

    private static final List<String> REQUIRED_HEADERS = List.of("userNo", "userName", "password", "email", "phone");

    @Override
    public List<UserImportRowView> parse(byte[] fileContent) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileContent))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row header = sheet.getRow(0);
            if (header == null) {
                throw new ValidationException("导入模板错误：缺少表头");
            }
            validateHeaders(header, formatter);

            List<UserImportRowView> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String userNo = cellValue(row.getCell(0), formatter);
                String userName = cellValue(row.getCell(1), formatter);
                String password = cellValue(row.getCell(2), formatter);
                String email = cellValue(row.getCell(3), formatter);
                String phone = cellValue(row.getCell(4), formatter);

                if (isBlank(userNo) && isBlank(userName) && isBlank(password) && isBlank(email) && isBlank(phone)) {
                    continue;
                }

                rows.add(new UserImportRowView(i + 1L, userNo, userName, password, email, phone));
            }
            return rows;
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
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
