package edu.whut.eval.app.finalrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordDetailView;
import edu.whut.eval.application.finalrecord.query.AdminFinalRecordListItemView;
import edu.whut.eval.application.finalrecord.query.ConfirmFinalRecordResultView;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.query.FinalRecordView;
import edu.whut.eval.application.finalrecord.query.UnsubmittedStudentView;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import edu.whut.eval.domain.finalrecord.query.UnsubmittedFinalRecordQuery;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.admin.AdminFinalRecordController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void shouldPageUnsubmittedFinalRecordsAndMergeClassParameters() throws Exception {
        given(queryService.pageUnsubmittedStudents(any()))
                .willReturn(new PageResult<>(1, List.of(unsubmittedStudent())));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/final-records/unsubmitted")
                        .param("academicYear", "2025-2026")
                        .param("grade", "2022")
                        .param("classes", "CS2201", "CS2202")
                        .param("classes[]", "CS2203,keep")
                        .param("pageNo", "2")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].studentUserId").value(1002))
                .andExpect(jsonPath("$.data.records[0].grade").value("2022"))
                .andExpect(jsonPath("$.data.records[0].className").value("计科二班"))
                .andExpect(jsonPath("$.data.records[0].status").value("UNSUBMITTED"))
                .andExpect(jsonPath("$.data.records[0].lastUpdatedAt").value("2026-07-07T12:34:56Z"))
                .andExpect(jsonPath("$.data.pageNo").doesNotExist())
                .andExpect(jsonPath("$.data.pageSize").doesNotExist());

        ArgumentCaptor<UnsubmittedFinalRecordQuery> queryCaptor =
                ArgumentCaptor.forClass(UnsubmittedFinalRecordQuery.class);
        verify(queryService).pageUnsubmittedStudents(queryCaptor.capture());
        UnsubmittedFinalRecordQuery query = queryCaptor.getValue();
        assertThat(query.getAcademicYear()).isEqualTo("2025-2026");
        assertThat(query.getGrade()).isEqualTo("2022");
        assertThat(query.getClasses()).containsExactly("CS2201", "CS2202", "CS2203,keep");
        assertThat(query.getPageNo()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(50);
    }

    @Test
    void shouldRejectMissingAcademicYearForUnsubmittedFinalRecords() throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/final-records/unsubmitted"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("academicYear 不合法"));
    }

    @Test
    void shouldRejectRepeatedScalarParametersForUnsubmittedFinalRecords() throws Exception {
        assertUnsubmittedScalarParameterRejected("academicYear", "academicYear 不合法", "2025-2026", "2026-2027");
        assertUnsubmittedScalarParameterRejected("grade", "grade 不合法", "2022", "2023");
        assertUnsubmittedScalarParameterRejected("pageNo", "pageNo 不合法", "1", "2");
        assertUnsubmittedScalarParameterRejected("pageSize", "pageSize 不合法", "20", "50");
    }

    @Test
    void shouldRejectArrayStyleScalarParametersForUnsubmittedFinalRecords() throws Exception {
        assertUnsubmittedScalarParameterRejected("academicYear[]", "academicYear 不合法", "2025-2026");
        assertUnsubmittedScalarParameterRejected("grade[]", "grade 不合法", "2022");
        assertUnsubmittedScalarParameterRejected("pageNo[]", "pageNo 不合法", "1");
        assertUnsubmittedScalarParameterRejected("pageSize[]", "pageSize 不合法", "20");
    }

    @Test
    void shouldRejectInvalidPageNumbersForUnsubmittedFinalRecords() throws Exception {
        assertUnsubmittedScalarParameterRejected("pageNo", "pageNo 不合法", " ");
        assertUnsubmittedScalarParameterRejected("pageSize", "pageSize 不合法", "not-a-number");
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

    private UnsubmittedStudentView unsubmittedStudent() {
        return new UnsubmittedStudentView(1002L, "S1002", "Student B", "2022", "计科二班",
                "UNSUBMITTED", "2026-07-07T12:34:56Z");
    }

    private void assertUnsubmittedScalarParameterRejected(String parameterName, String expectedMessage, String... values)
            throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/admin/final-records/unsubmitted")
                        .param("academicYear", "2025-2026")
                        .param(parameterName, values))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value(expectedMessage));
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
