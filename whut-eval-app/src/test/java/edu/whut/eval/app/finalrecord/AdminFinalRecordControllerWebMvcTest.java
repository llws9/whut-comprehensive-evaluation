package edu.whut.eval.app.finalrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordDetailView;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordListItemView;
import edu.whut.eval.application.finalrecord.query.ConfirmFinalRecordResultView;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.query.FinalRecordView;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.admin.AdminFinalRecordController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminFinalRecordControllerWebMvcTest {

    private FinalRecordQueryApplicationService queryService;
    private FinalRecordCommandApplicationService commandService;
    private AdminFinalRecordController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        queryService = mock(FinalRecordQueryApplicationService.class);
        commandService = mock(FinalRecordCommandApplicationService.class);
        controller = new AdminFinalRecordController(queryService, commandService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldPageAdminFinalRecords() throws Exception {
        given(queryService.pageAdminFinalRecords(any())).willReturn(new PageResult<>(1, List.of(listItem())));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/final-records")
                        .param("academicYear", "2025-2026")
                        .param("status", "SUBMITTED")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].finalRecordId").value(41001))
                .andExpect(jsonPath("$.data.pageNo").doesNotExist())
                .andExpect(jsonPath("$.data.pageSize").doesNotExist());
    }

    @Test
    void shouldGetAdminFinalRecordDetail() throws Exception {
        given(queryService.getAdminFinalRecordDetail(41001L)).willReturn(detail());

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/final-records/41001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.record.confirmComment").isEmpty())
                .andExpect(jsonPath("$.data.student.studentUserId").value(1001))
                .andExpect(jsonPath("$.data.components").isArray());
    }

    @Test
    void shouldConfirmAdminFinalRecord() throws Exception {
        given(commandService.confirm(any())).willReturn(new ConfirmFinalRecordResultView(
                41001L, FinalRecordStatus.CONFIRMED, "辅导员已复核，无异议", Instant.parse("2026-07-07T13:00:00Z"), 2L));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/admin/final-records/41001/confirm")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPayload("辅导员已复核，无异议", 1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmComment").value("辅导员已复核，无异议"));
    }

    private AdminFinalRecordListItemView listItem() {
        return new AdminFinalRecordListItemView(41001L, 1001L, "S1001", "Student", 2010L, "计科一班",
                "2025-2026", "SUBMITTED", new BigDecimal("0.80"), new BigDecimal("2.00"),
                new BigDecimal("0.60"), new BigDecimal("1.20"), new BigDecimal("4.60"),
                Instant.parse("2026-07-07T12:00:00Z"), null, 1L);
    }

    private AdminFinalRecordDetailView detail() {
        FinalRecordView record = new FinalRecordView(41001L, 1001L, "2025-2026", FinalRecordStatus.SUBMITTED,
                new BigDecimal("0.80"), new BigDecimal("2.00"), new BigDecimal("0.60"),
                new BigDecimal("1.20"), new BigDecimal("4.60"), Instant.parse("2026-07-07T12:00:00Z"),
                null, null, 1L);
        FinalRecordStudentView student = new FinalRecordStudentView(41001L, 1001L, "2025-2026", FinalRecordStatus.SUBMITTED,
                new BigDecimal("0.80"), new BigDecimal("2.00"), new BigDecimal("0.60"),
                new BigDecimal("1.20"), new BigDecimal("4.60"), Instant.parse("2026-07-07T12:00:00Z"),
                null, 1L);
        return new AdminFinalRecordDetailView(record, student, List.of());
    }

    private record ConfirmPayload(String comment, Long expectedVersion) {
    }
}
