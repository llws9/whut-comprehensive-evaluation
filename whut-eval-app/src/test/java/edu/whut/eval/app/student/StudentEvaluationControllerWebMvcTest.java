package edu.whut.eval.app.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.application.query.StudentEvaluationItemView;
import edu.whut.eval.application.application.query.StudentEvaluationOptionView;
import edu.whut.eval.application.application.query.StudentEvaluationPointsView;
import edu.whut.eval.application.application.service.StudentEvaluationApplicationService;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.student.StudentEvaluationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class StudentEvaluationControllerWebMvcTest {

    private StudentEvaluationApplicationService studentEvaluationApplicationService;
    private StudentEvaluationController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        studentEvaluationApplicationService = mock(StudentEvaluationApplicationService.class);
        controller = new StudentEvaluationController(studentEvaluationApplicationService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldReturnStudentEvaluationItemsWithOptions() throws Exception {
        given(studentEvaluationApplicationService.listItems("INTELLECTUAL"))
                .willReturn(List.of(new StudentEvaluationItemView(
                        "INTELLECTUAL_PAPER",
                        "论文发表",
                        "INTELLECTUAL",
                        "智育",
                        "学术论文发表加分",
                        new BigDecimal("36.00"),
                        "STUDENT_APPLY",
                        true,
                        List.of(new StudentEvaluationOptionView(
                                "PAPER_I_1",
                                "I类第一档",
                                new BigDecimal("36.00"),
                                "发表在高水平期刊或会议"
                        ))
                )));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/student/evaluation/items")
                        .param("categoryCode", "INTELLECTUAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].itemCode").value("INTELLECTUAL_PAPER"))
                .andExpect(jsonPath("$.data[0].categoryCode").value("INTELLECTUAL"))
                .andExpect(jsonPath("$.data[0].maxPoints").value(36.00))
                .andExpect(jsonPath("$.data[0].options[0].optionCode").value("PAPER_I_1"))
                .andExpect(jsonPath("$.data[0].options[0].points").value(36.00));
    }

    @Test
    void shouldCalculateStudentEvaluationPoints() throws Exception {
        given(studentEvaluationApplicationService.calculatePoints("INTELLECTUAL_PAPER", "PAPER_I_1"))
                .willReturn(new StudentEvaluationPointsView(
                        "INTELLECTUAL_PAPER",
                        "PAPER_I_1",
                        new BigDecimal("36.00"),
                        "I类第一档"
                ));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/student/evaluation/calculate-points")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CalculatePayload(
                                "INTELLECTUAL_PAPER",
                                "PAPER_I_1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemCode").value("INTELLECTUAL_PAPER"))
                .andExpect(jsonPath("$.data.optionCode").value("PAPER_I_1"))
                .andExpect(jsonPath("$.data.points").value(36.00))
                .andExpect(jsonPath("$.data.optionName").value("I类第一档"));

        verify(studentEvaluationApplicationService).calculatePoints("INTELLECTUAL_PAPER", "PAPER_I_1");
    }

    @Test
    void shouldRejectCalculatePointsRequestWithoutItemCode() throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/student/evaluation/calculate-points")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CalculatePayload(
                                "",
                                "PAPER_I_1"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    private record CalculatePayload(String itemCode, String optionCode) {
    }
}
