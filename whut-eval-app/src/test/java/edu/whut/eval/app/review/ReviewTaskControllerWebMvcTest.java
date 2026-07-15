package edu.whut.eval.app.review;

import edu.whut.eval.application.application.query.ReviewMetaGradesView;
import edu.whut.eval.application.application.query.ReviewOrgUnitOptionView;
import edu.whut.eval.application.application.query.ReviewTaskSummaryView;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.review.ReviewMetaController;
import edu.whut.eval.interfaces.review.ReviewTaskController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ReviewTaskControllerWebMvcTest {

    private ReviewApplicationQueryApplicationService queryService;
    private ReviewTaskController controller;
    private ReviewMetaController metaController;

    @BeforeEach
    void setUp() {
        queryService = mock(ReviewApplicationQueryApplicationService.class);
        controller = new ReviewTaskController(queryService);
        metaController = new ReviewMetaController(queryService);
    }

    @Test
    void shouldReturnReviewTaskSummary() throws Exception {
        given(queryService.getReviewTaskSummary()).willReturn(new ReviewTaskSummaryView(5, 2, 1, 1, 4));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/review/tasks/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pendingCount").value(5))
                .andExpect(jsonPath("$.data.approvedToday").value(2))
                .andExpect(jsonPath("$.data.returnedToday").value(1))
                .andExpect(jsonPath("$.data.rejectedToday").value(1))
                .andExpect(jsonPath("$.data.processedToday").value(4));
    }

    @Test
    void shouldReturnReviewGradeAndOrgMetadata() throws Exception {
        given(queryService.getReviewGradeMetadata()).willReturn(new ReviewMetaGradesView(
                List.of("2024-2025", "2025-2026"),
                List.of(
                        new ReviewOrgUnitOptionView(2010L, "计算机 2101 班", "CLASS"),
                        new ReviewOrgUnitOptionView(2011L, "计算机 2102 班", "CLASS")
                ),
                2010L
        ));

        standaloneSetup(metaController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/review/meta/grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gradeList[0]").value("2024-2025"))
                .andExpect(jsonPath("$.data.gradeList[1]").value("2025-2026"))
                .andExpect(jsonPath("$.data.orgUnitList[0].orgUnitId").value(2010))
                .andExpect(jsonPath("$.data.orgUnitList[0].orgUnitName").value("计算机 2101 班"))
                .andExpect(jsonPath("$.data.orgUnitList[0].orgUnitType").value("CLASS"))
                .andExpect(jsonPath("$.data.defaultOrgUnitId").value(2010));
    }
}
