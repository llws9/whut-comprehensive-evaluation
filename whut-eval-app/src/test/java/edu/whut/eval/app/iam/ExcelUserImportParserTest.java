package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.query.UserImportRowView;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.infra.iam.ExcelUserImportParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelUserImportParserTest {

    private final ExcelUserImportParser parser = new ExcelUserImportParser();

    @Test
    void shouldParseRowsFromExcelTemplate() throws Exception {
        byte[] bytes = buildWorkbook(List.of(
                List.of("userNo", "userName", "password", "email", "phone"),
                List.of("2024305001", "王老师", "pwd123", "w@example.com", "13800000000"),
                List.of("2024305002", "李老师", "pwd234", "l@example.com", "13800001111")
        ));

        List<UserImportRowView> rows = parser.parse(bytes);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rowNo()).isEqualTo(2L);
        assertThat(rows.get(0).userNo()).isEqualTo("2024305001");
        assertThat(rows.get(1).rowNo()).isEqualTo(3L);
    }

    @Test
    void shouldThrowWhenHeaderInvalid() throws Exception {
        byte[] bytes = buildWorkbook(List.of(
                List.of("wrongNo", "userName", "password", "email", "phone"),
                List.of("2024305001", "王老师", "pwd123", "w@example.com", "13800000000")
        ));

        assertThrows(ValidationException.class, () -> parser.parse(bytes));
    }

    private byte[] buildWorkbook(List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("users");
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i);
                List<String> values = rows.get(i);
                for (int j = 0; j < values.size(); j++) {
                    row.createCell(j).setCellValue(values.get(j));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
