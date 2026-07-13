package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.importing.ImportLecturesCommand;
import edu.whut.eval.application.finalrecord.importing.ImportMentorScoresCommand;
import edu.whut.eval.application.finalrecord.importing.LectureImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.MentorScoreImportApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.finalrecord.importing.LectureImportFailedRow;
import edu.whut.eval.domain.finalrecord.importing.LectureImportResult;
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

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @MockBean
    private LectureImportApplicationService lectureImportApplicationService;

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

    @Test
    void shouldImportLecturesAndReturnResultShape() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
        Map<String, String> rawValue = new LinkedHashMap<>();
        rawValue.put("studentNo", "S1002");
        rawValue.put("scoreValue", "1.00");
        rawValue.put("displayText", null);
        given(lectureImportApplicationService.importLectures(any(ImportLecturesCommand.class)))
                .willReturn(new LectureImportResult(
                        "LECTURE-20252026-20260518143000-ABCDEF123456",
                        "学院学术讲座",
                        LocalDateTime.parse("2026-05-18T14:30:00"),
                        "2025-2026",
                        2,
                        1,
                        1,
                        List.of(new LectureImportFailedRow(3L, "OUT_OF_SCOPE", "当前用户无权导入该学生讲座成绩", rawValue))
                ));

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lectureBatchId").value("LECTURE-20252026-20260518143000-ABCDEF123456"))
                .andExpect(jsonPath("$.data.title").value("学院学术讲座"))
                .andExpect(jsonPath("$.data.heldAt").value("2026-05-18T14:30:00"))
                .andExpect(jsonPath("$.data.academicYear").value("2025-2026"))
                .andExpect(jsonPath("$.data.failedRows[0].rawValue.scoreValue").value("1.00"));

        verify(lectureImportApplicationService).importLectures(argThat(command ->
                new String(command.fileContent()).equals("excel")
                        && "学院学术讲座".equals(command.title())
                        && "2026-05-18T14:30".equals(command.heldAt())
                        && "2025-2026".equals(command.academicYear())
        ));
    }

    @Test
    void shouldReturn400WhenLectureTitleMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", " ")
                        .param("heldAt", "2026-05-18T14:30")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("title 不能为空"));
    }

    @Test
    void shouldReturn400WhenLectureTitleParameterIsMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("heldAt", "2026-05-18T14:30")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("title 不能为空"));

        verifyNoInteractions(lectureImportApplicationService);
    }

    @Test
    void shouldReturn400WhenLectureFileIsEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));
    }

    @Test
    void shouldReturn400WhenLectureFilePartIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("上传文件不能为空"));

        verifyNoInteractions(lectureImportApplicationService);
    }

    @Test
    void shouldReturn400WhenLectureFileExtensionIsUnsupported() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12", "excel".getBytes());

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("导入模板错误：文件不可解析"));

        verifyNoInteractions(lectureImportApplicationService);
    }

    @Test
    void shouldReturn400WhenLectureHeldAtInvalid() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
        given(lectureImportApplicationService.importLectures(any(ImportLecturesCommand.class)))
                .willThrow(new ValidationException("heldAt 格式非法"));

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30Z")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("heldAt 格式非法"));
    }

    @Test
    void shouldReturn400WhenLectureHeldAtParameterIsMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("heldAt 格式非法"));

        verifyNoInteractions(lectureImportApplicationService);
    }

    @Test
    void shouldReturn400WhenLectureAcademicYearParameterIsMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("academicYear 不合法"));

        verifyNoInteractions(lectureImportApplicationService);
    }

    @Test
    void shouldReturn200WhenLectureWorkbookHasOnlyHeader() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
        given(lectureImportApplicationService.importLectures(any(ImportLecturesCommand.class)))
                .willReturn(new LectureImportResult(
                        "LECTURE-20252026-20260518143000-ABCDEF123456",
                        "学院学术讲座",
                        LocalDateTime.parse("2026-05-18T14:30:00"),
                        "2025-2026",
                        0,
                        0,
                        0,
                        List.of()
                ));

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.failedRows").isEmpty());
    }

    @Test
    void shouldReturn409WhenLectureServiceReportsDuplicateBatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
        given(lectureImportApplicationService.importLectures(any(ImportLecturesCommand.class)))
                .willThrow(new ConflictException("同一讲座批次已导入"));

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BIZ-4090"))
                .andExpect(jsonPath("$.message").value("同一讲座批次已导入"));
    }

    @Test
    void shouldReturn503WhenLectureMultipartReadFails() throws Exception {
        MockMultipartFile file = new FailingLectureMockMultipartFile();

        mockMvc.perform(multipart("/api/admin/imports/lectures")
                        .file(file)
                        .param("title", "学院学术讲座")
                        .param("heldAt", "2026-05-18T14:30")
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

    static class FailingLectureMockMultipartFile extends MockMultipartFile {

        FailingLectureMockMultipartFile() {
            super("file", "lectures.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel".getBytes());
        }

        @Override
        public byte[] getBytes() throws IOException {
            throw new IOException("read failed");
        }
    }
}
