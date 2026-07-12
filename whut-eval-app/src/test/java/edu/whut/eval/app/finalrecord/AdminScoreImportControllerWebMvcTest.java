package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.importing.ImportMentorScoresCommand;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.MentorScoreImportResult;
import edu.whut.eval.interfaces.admin.AdminScoreImportController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminScoreImportController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = AdminScoreImportControllerWebMvcTest.TestApplication.class)
@Import({
        AdminScoreImportController.class,
        GlobalExceptionHandler.class
})
class AdminScoreImportControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MentorScoreImportApplicationService importApplicationService;

    @Test
    void shouldImportMentorScoresAndReturnResultShape() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "mentor.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
        given(importApplicationService.importMentorScores(any(ImportMentorScoresCommand.class)))
                .willReturn(new MentorScoreImportResult(
                        "D7-batch",
                        2,
                        1,
                        1,
                        List.of(new MentorScoreImportFailedRow(3L, "OUT_OF_SCOPE", "当前用户无权导入该学生成绩", Map.of("studentNo", "S1002"))),
                        Instant.parse("2026-07-13T01:00:00Z")
                ));

        mockMvc.perform(multipart("/api/admin/imports/mentor-scores")
                        .file(file)
                        .param("academicYear", "2025-2026")
                        .param("importMode", "STRICT_INSERT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.importBatchId").value("D7-batch"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.failedRows[0].rowNo").value(3))
                .andExpect(jsonPath("$.data.failedRows[0].code").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.data.failedRows[0].message").value("当前用户无权导入该学生成绩"))
                .andExpect(jsonPath("$.data.failedRows[0].rawValue.studentNo").value("S1002"))
                .andExpect(jsonPath("$.data.processedAt").value("2026-07-13T01:00:00Z"));

        verify(importApplicationService).importMentorScores(argThat(command ->
                new String(command.fileContent()).equals("excel")
                        && "2025-2026".equals(command.academicYear())
                        && "STRICT_INSERT".equals(command.importMode())
        ));
    }

    @Test
    void shouldReturn400WhenFileIsEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/api/admin/imports/mentor-scores")
                        .file(file)
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));
    }

    @Test
    void shouldReturn400WhenImportModeIsInvalid() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "mentor.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());

        mockMvc.perform(multipart("/api/admin/imports/mentor-scores")
                        .file(file)
                        .param("academicYear", "2025-2026")
                        .param("importMode", "MERGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("importMode 仅允许 UPSERT 或 STRICT_INSERT"));
    }

    @Test
    void shouldReturn409WhenServiceReportsConflict() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "mentor.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
        given(importApplicationService.importMentorScores(any(ImportMentorScoresCommand.class)))
                .willThrow(new ConflictException("STRICT_INSERT 模式不允许覆盖"));

        mockMvc.perform(multipart("/api/admin/imports/mentor-scores")
                        .file(file)
                        .param("academicYear", "2025-2026")
                        .param("importMode", "STRICT_INSERT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BIZ-4090"))
                .andExpect(jsonPath("$.message").value("STRICT_INSERT 模式不允许覆盖"));
    }

    @Test
    void shouldReturn503WhenFileProcessingFails() throws Exception {
        MockMultipartFile file = new FailingMockMultipartFile();

        mockMvc.perform(multipart("/api/admin/imports/mentor-scores")
                        .file(file)
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EXT-5033"))
                .andExpect(jsonPath("$.message").value("文件处理失败，请稍后重试"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    static class FailingMockMultipartFile extends MockMultipartFile {

        FailingMockMultipartFile() {
            super("file", "mentor.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
        }

        @Override
        public byte[] getBytes() {
            throw new FileStorageException("文件处理失败，请稍后重试");
        }
    }
}
