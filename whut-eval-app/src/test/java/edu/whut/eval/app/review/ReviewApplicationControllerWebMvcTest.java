package edu.whut.eval.app.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.application.query.ApplicationAttachmentView;
import edu.whut.eval.application.application.query.ReviewActionResultView;
import edu.whut.eval.application.application.query.ReviewApplicationDetailView;
import edu.whut.eval.application.application.query.ReviewApplicationListItemView;
import edu.whut.eval.application.application.query.ReviewApplicationSummaryView;
import edu.whut.eval.application.application.query.ReviewApplicantView;
import edu.whut.eval.application.application.query.ReviewLogView;
import edu.whut.eval.application.application.query.ReviewScoringSnapshotView;
import edu.whut.eval.application.application.service.ReviewApplicationCommandApplicationService;
import edu.whut.eval.application.application.service.ReviewApplicationQueryApplicationService;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.review.ReviewApplicationController;
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

class ReviewApplicationControllerWebMvcTest {

    private ReviewApplicationQueryApplicationService queryService;
    private ReviewApplicationCommandApplicationService commandService;
    private ReviewApplicationController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        queryService = mock(ReviewApplicationQueryApplicationService.class);
        commandService = mock(ReviewApplicationCommandApplicationService.class);
        controller = new ReviewApplicationController(queryService, commandService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldListReviewApplications() throws Exception {
        given(queryService.pageReviewApplications(any()))
                .willReturn(new PageResult<>(1, List.of(listItem())));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/review/applications")
                        .param("academicYear", "2025-2026")
                        .param("status", "SUBMITTED")
                        .param("keyword", "论文"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].applicationId").value(21013))
                .andExpect(jsonPath("$.data.records[0].currentReviewNode").value("SINGLE_REVIEW"));
    }

    @Test
    void shouldReturnReviewDetailWithoutStorageKey() throws Exception {
        given(queryService.getReviewDetail(21013L)).willReturn(detailView(ApplicationSubmissionStatus.SUBMITTED));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/review/applications/21013"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.applicationId").value(21013))
                .andExpect(jsonPath("$.data.attachments[0].fileId").value("file-1"))
                .andExpect(jsonPath("$.data.attachments[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data.reviewLogs[0].action").value("RETURN"))
                .andExpect(jsonPath("$.data.allowedActions[0]").value("APPROVE"));
    }

    @Test
    void shouldApproveApplication() throws Exception {
        given(commandService.approve(any())).willReturn(actionResult(ApplicationSubmissionStatus.APPROVED));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/review/applications/21013/approve")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApprovePayload(1L, "同意"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewLogId").value(31001));
    }

    @Test
    void shouldReturn400WhenReturnReasonIsBlank() throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/review/applications/21013/return")
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    @Test
    void shouldRejectApplication() throws Exception {
        given(commandService.reject(any())).willReturn(actionResult(ApplicationSubmissionStatus.REJECTED));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/review/applications/21013/reject")
                        .contentType(APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"reason\":\"不符合要求\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    private ReviewApplicationListItemView listItem() {
        return new ReviewApplicationListItemView(
                21013L,
                1001L,
                "张三",
                "2024305999",
                2010L,
                "计算机学院 1 班",
                "INTELLECTUAL",
                "INTELLECTUAL_PAPER",
                "论文申请",
                "SUBMITTED",
                Instant.parse("2026-07-07T10:00:00Z"),
                "SINGLE_REVIEW"
        );
    }

    private ReviewApplicationDetailView detailView(ApplicationSubmissionStatus status) {
        return new ReviewApplicationDetailView(
                new ReviewApplicationSummaryView(
                        21013L,
                        status.name(),
                        "论文申请",
                        "申请说明",
                        "INTELLECTUAL",
                        "INTELLECTUAL_PAPER",
                        "2025-2026",
                        "上学期",
                        Instant.parse("2026-07-07T10:00:00Z"),
                        1L,
                        new ReviewScoringSnapshotView("OPTION_A", new BigDecimal("2.00"), new BigDecimal("6.00"), 1, false, null)
                ),
                new ReviewApplicantView(1001L, "2024305999", "张三", 2010L, "计算机学院 1 班"),
                List.of(new ApplicationAttachmentView("file-1", "a.pdf", "application/pdf", 128L, 0)),
                List.of(new ReviewLogView(31000L, "RETURN", 1010L, null, "COUNSELOR", "补充材料", Instant.parse("2026-07-07T11:00:00Z"))),
                status == ApplicationSubmissionStatus.SUBMITTED ? List.of("APPROVE", "RETURN", "REJECT") : List.of()
        );
    }

    private ReviewActionResultView actionResult(ApplicationSubmissionStatus status) {
        return new ReviewActionResultView(21013L, status, 2L, 31001L, Instant.parse("2026-07-07T12:00:00Z"));
    }

    private record ApprovePayload(Long expectedVersion, String comment) {
    }
}
