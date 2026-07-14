package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportApplicationService;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportFile;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportGenerationException;
import edu.whut.eval.application.finalrecord.exporting.FinalScoreExportQuery;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.interfaces.admin.AdminFinalScoreExportController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminFinalScoreExportControllerWebMvcTest {

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private FinalScoreExportApplicationService exportApplicationService;
    private AdminFinalScoreExportController controller;

    @BeforeEach
    void setUp() {
        exportApplicationService = mock(FinalScoreExportApplicationService.class);
        controller = new AdminFinalScoreExportController(exportApplicationService);
    }

    @Test
    void shouldExportFinalScoresAsAttachment() throws Exception {
        byte[] workbook = "xlsx-bytes".getBytes();
        given(exportApplicationService.export(any(FinalScoreExportQuery.class)))
                .willReturn(new FinalScoreExportFile("final-scores-2025-2026.xlsx", XLSX_CONTENT_TYPE, workbook));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/exports/final-scores")
                        .param("academicYear", "2025-2026")
                        .param("status", "SUBMITTED")
                        .param("grade", "CS2022")
                        .param("classes", "A,B")
                        .param("classes", "B,C")
                        .param("unknown", "ignored"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"final-scores-2025-2026.xlsx\""))
                .andExpect(content().contentType(XLSX_CONTENT_TYPE))
                .andExpect(content().bytes(workbook));

        ArgumentCaptor<FinalScoreExportQuery> captor = ArgumentCaptor.forClass(FinalScoreExportQuery.class);
        verify(exportApplicationService).export(captor.capture());
        assertThat(captor.getValue())
                .extracting(FinalScoreExportQuery::academicYear,
                        FinalScoreExportQuery::status,
                        FinalScoreExportQuery::grade,
                        FinalScoreExportQuery::classes)
                .containsExactly("2025-2026", "SUBMITTED", "CS2022", List.of("A", "B", "C"));
    }

    @Test
    void shouldRejectPaginationParametersBeforeRepeatedSingleValueParameters() throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/exports/final-scores")
                        .param("pageNo", "1")
                        .param("academicYear", "2025-2026")
                        .param("academicYear", "2026-2027"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("导出接口不支持分页参数"));

        verifyNoInteractions(exportApplicationService);
    }

    @Test
    void shouldRejectAnyPaginationParameter() throws Exception {
        for (String parameterName : List.of("pageNo", "pageSize")) {
            for (String[] values : List.of(new String[]{"1"}, new String[]{""}, new String[]{"1", "2"})) {
                assertValidation(parameterName, values, "导出接口不支持分页参数");
            }
        }
    }

    @Test
    void shouldRejectRepeatedSingleValueParameters() throws Exception {
        assertValidation("academicYear", new String[]{"2025-2026", "2026-2027"}, "导出接口不支持重复单值参数");
        assertValidation("status", new String[]{"SUBMITTED", "CONFIRMED"}, "导出接口不支持重复单值参数");
        assertValidation("grade", new String[]{"CS2022", "CS2023"}, "导出接口不支持重复单值参数");
    }

    @Test
    void shouldValidateQueryThroughEndpoint() throws Exception {
        assertValidation("academicYear", new String[]{"bad"}, "academicYear 不合法");
        assertValidation("status", new String[]{"submitted"}, "status 仅允许 SUBMITTED 或 CONFIRMED");
        assertValidation("status", new String[]{"DRAFT"}, "status 仅允许 SUBMITTED 或 CONFIRMED");
        assertValidation("classes", fiveHundredOneClasses(), "classes 参数过多");
    }

    @Test
    void shouldMapServiceExceptions() throws Exception {
        given(exportApplicationService.export(any(FinalScoreExportQuery.class)))
                .willThrow(new ResourceNotFoundException("无匹配导出数据"));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/exports/final-scores")
                        .param("academicYear", "2025-2026")
                        .param("grade", "UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RES-4040"))
                .andExpect(jsonPath("$.message").value("无匹配导出数据"));

        given(exportApplicationService.export(any(FinalScoreExportQuery.class)))
                .willThrow(new FinalScoreExportGenerationException("Excel 生成失败"));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/exports/final-scores")
                        .param("academicYear", "2025-2026"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXT-5033"))
                .andExpect(jsonPath("$.message").value("Excel 生成失败"));
    }

    private void assertValidation(String parameterName, String[] values, String message) throws Exception {
        var requestBuilder = get("/api/admin/exports/final-scores");
        if (!"academicYear".equals(parameterName)) {
            requestBuilder.param("academicYear", "2025-2026");
        }
        requestBuilder.param(parameterName, values);

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(requestBuilder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value(message));
    }

    private String[] fiveHundredOneClasses() {
        List<String> classes = new ArrayList<>();
        for (int index = 1; index <= 501; index++) {
            classes.add("C" + index);
        }
        return classes.toArray(String[]::new);
    }
}
