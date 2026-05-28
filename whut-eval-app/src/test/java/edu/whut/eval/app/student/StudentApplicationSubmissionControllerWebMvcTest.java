package edu.whut.eval.app.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.SubmitApplicationCommand;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.student.StudentApplicationSubmissionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class StudentApplicationSubmissionControllerWebMvcTest {

    private ApplicationSubmissionCommandApplicationService applicationSubmissionCommandApplicationService;
    private ObjectMapper objectMapper;
    private StudentApplicationSubmissionController controller;

    @BeforeEach
    void setUp() {
        applicationSubmissionCommandApplicationService = mock(ApplicationSubmissionCommandApplicationService.class);
        objectMapper = new ObjectMapper();
        controller = new StudentApplicationSubmissionController(applicationSubmissionCommandApplicationService);
    }

    @Test
    void shouldCreateDraftSuccessfully() throws Exception {
        given(applicationSubmissionCommandApplicationService.createDraft(any(CreateApplicationDraftCommand.class)))
                .willReturn(new ApplicationSubmissionView(1L, ApplicationSubmissionStatus.DRAFT, "申请标题", "申请说明", 1, 0L));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/student/applications/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDraftPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(1))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.attachmentCount").value(1));
    }

    @Test
    void shouldReturn409WhenCreateDraftConflicts() throws Exception {
        given(applicationSubmissionCommandApplicationService.createDraft(any(CreateApplicationDraftCommand.class)))
                .willThrow(new ConflictException("当前项目在该学年学期下已存在活跃申请"));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/student/applications/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDraftPayload())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BIZ-4090"));
    }

    @Test
    void shouldReturn400WhenCreateDraftRequestIsInvalid() throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/student/applications/drafts")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDraftPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    @Test
    void shouldSubmitSuccessfully() throws Exception {
        given(applicationSubmissionCommandApplicationService.submit(any(SubmitApplicationCommand.class)))
                .willReturn(new ApplicationSubmissionView(1L, ApplicationSubmissionStatus.SUBMITTED, "申请标题", "申请说明", 1, 1L));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/student/applications/1/submit")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitPayload(0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    private DraftPayload createDraftPayload() {
        return new DraftPayload(
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "申请标题",
                "申请说明",
                List.of("file-1")
        );
    }

    private DraftPayload invalidDraftPayload() {
        return new DraftPayload(
                10L,
                "competition",
                "item-1",
                "2025-2026",
                "1",
                "",
                "申请说明",
                List.of("")
        );
    }

    private record DraftPayload(Long orgUnitId,
                                String categoryCode,
                                String itemCode,
                                String academicYear,
                                String term,
                                String title,
                                String description,
                                List<String> attachmentFileIds) {
    }

    private record SubmitPayload(Long expectedVersion) {
    }
}