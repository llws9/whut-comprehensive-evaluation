package edu.whut.eval.app.review;

import edu.whut.eval.application.application.query.ReviewTaskSummaryView;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.review.ReviewTaskController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ReviewTaskControllerWebMvcTest {

    private ReviewApplicationQueryApplicationService queryService;
    private ReviewTaskController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(ReviewApplicationQueryApplicationService.class);
        controller = new ReviewTaskController(queryService);
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
}
