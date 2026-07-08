package edu.whut.eval.app.finalrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreListView;
import edu.whut.eval.application.finalrecord.query.FinalComponentScoreView;
import edu.whut.eval.application.finalrecord.query.FinalRecordStudentView;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.application.finalrecord.service.FinalRecordQueryApplicationService;
import edu.whut.eval.domain.finalrecord.model.FinalRecordStatus;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.student.StudentFinalRecordController;
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

class StudentFinalRecordControllerWebMvcTest {

    private FinalRecordQueryApplicationService queryService;
    private FinalRecordCommandApplicationService commandService;
    private StudentFinalRecordController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        queryService = mock(FinalRecordQueryApplicationService.class);
        commandService = mock(FinalRecordCommandApplicationService.class);
        controller = new StudentFinalRecordController(queryService, commandService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldGetStudentFinalRecord() throws Exception {
        given(queryService.getStudentFinalRecord("2025-2026")).willReturn(studentView());

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/student/final-records/2025-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalRecordId").value(41001))
                .andExpect(jsonPath("$.data.academicYear").value("2025-2026"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.confirmComment").doesNotExist());
    }

    @Test
    void shouldListStudentFinalRecordComponents() throws Exception {
        given(queryService.listStudentComponents("2025-2026")).willReturn(new FinalComponentScoreListView(List.of(
                new FinalComponentScoreView(42001L, 41001L, "INTELLECTUAL", "INTELLECTUAL_PAPER", null,
                        new BigDecimal("2.00"), "论文已审核通过", "APPLICATION", "21013", Instant.parse("2026-07-07T12:00:00Z"))
        )));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/student/final-records/2025-2026/components"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components[0].categoryCode").value("INTELLECTUAL"))
                .andExpect(jsonPath("$.data.components[0].itemName").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void shouldSubmitStudentFinalRecord() throws Exception {
        given(commandService.submit(any())).willReturn(studentView());

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/student/final-records/submit")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitPayload("2025-2026", 0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    private FinalRecordStudentView studentView() {
        return new FinalRecordStudentView(41001L, 1001L, "2025-2026", FinalRecordStatus.SUBMITTED,
                new BigDecimal("0.80"), new BigDecimal("2.00"), new BigDecimal("0.60"), new BigDecimal("1.20"),
                new BigDecimal("4.60"), Instant.parse("2026-07-07T12:00:00Z"), null, 1L);
    }

    private record SubmitPayload(String academicYear, Long expectedVersion) {
    }
}
