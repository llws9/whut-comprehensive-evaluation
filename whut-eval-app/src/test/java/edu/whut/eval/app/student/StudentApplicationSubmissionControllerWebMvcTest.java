package edu.whut.eval.app.student;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.application.command.CreateApplicationDraftCommand;
import edu.whut.eval.application.application.command.DeleteApplicationCommand;
import edu.whut.eval.application.application.command.SubmitApplicationCommand;
import edu.whut.eval.application.application.query.ApplicationAttachmentView;
import edu.whut.eval.application.application.query.ApplicationOverviewView;
import edu.whut.eval.application.application.query.ApplicationSubmissionDetailView;
import edu.whut.eval.application.application.query.ApplicationSubmissionView;
import edu.whut.eval.application.application.service.ApplicationOverviewQueryApplicationService;
import edu.whut.eval.application.application.service.ApplicationSubmissionCommandApplicationService;
import edu.whut.eval.application.application.service.ApplicationSubmissionDetailApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.domain.application.model.ApplicationSubmissionStatus;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.student.StudentApplicationSubmissionController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class StudentApplicationSubmissionControllerWebMvcTest {

    private ApplicationSubmissionCommandApplicationService applicationSubmissionCommandApplicationService;
    private ApplicationSubmissionDetailApplicationService applicationSubmissionDetailApplicationService;
    private ApplicationOverviewQueryApplicationService applicationOverviewQueryApplicationService;
    private ObjectMapper objectMapper;
    private StudentApplicationSubmissionController controller;

    @BeforeEach
    void setUp() {
        applicationSubmissionCommandApplicationService = mock(ApplicationSubmissionCommandApplicationService.class);
        applicationSubmissionDetailApplicationService = mock(ApplicationSubmissionDetailApplicationService.class);
        applicationOverviewQueryApplicationService = mock(ApplicationOverviewQueryApplicationService.class);
        objectMapper = new ObjectMapper();
        controller = new StudentApplicationSubmissionController(
                applicationSubmissionCommandApplicationService,
                applicationSubmissionDetailApplicationService,
                applicationOverviewQueryApplicationService
        );
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

    @Test
    void shouldReturnOwnedApplicationDetail() throws Exception {
        given(applicationSubmissionDetailApplicationService.getOwnedDetail(1L)).willReturn(detailView());

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/student/applications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.applicationId").value(1))
                .andExpect(jsonPath("$.data.attachments[0].fileId").value("file-1"))
                .andExpect(jsonPath("$.data.attachments[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data.optionCode").value("OPTION_A"));
    }

    @Test
    void shouldReturnStudentApplicationOverview() throws Exception {
        given(applicationOverviewQueryApplicationService.getCurrentStudentOverview())
                .willReturn(new ApplicationOverviewView(2, 3, 1, 4, 5, "2025-2026"));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/student/applications/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.draftCount").value(2))
                .andExpect(jsonPath("$.data.submittedCount").value(3))
                .andExpect(jsonPath("$.data.returnedCount").value(1))
                .andExpect(jsonPath("$.data.approvedCount").value(4))
                .andExpect(jsonPath("$.data.rejectedCount").value(5))
                .andExpect(jsonPath("$.data.latestAcademicYear").value("2025-2026"));
    }

    @Test
    void shouldDeleteDraftApplicationSuccessfully() throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(delete("/api/student/applications/1")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeletePayload(0L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(applicationSubmissionCommandApplicationService).deleteOwnedApplication(any(DeleteApplicationCommand.class));
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

    private ApplicationSubmissionDetailView detailView() {
        ApplicationSubmissionDetailView view = new ApplicationSubmissionDetailView();
        view.setApplicationId(1L);
        view.setOrgUnitId(2010L);
        view.setCategoryCode("INTELLECTUAL");
        view.setItemCode("INTELLECTUAL_PAPER");
        view.setAcademicYear("2025-2026");
        view.setTerm("上学期");
        view.setTitle("申请标题");
        view.setDescription("申请说明");
        view.setStatus(ApplicationSubmissionStatus.SUBMITTED);
        view.setSubmittedAt(Instant.parse("2026-07-06T10:00:00Z"));
        view.setCreatedAt(Instant.parse("2026-07-06T09:00:00Z"));
        view.setUpdatedAt(Instant.parse("2026-07-06T10:00:00Z"));
        view.setVersion(1L);
        view.setOptionCode("OPTION_A");
        view.setEvidenceCount(1);
        view.setAttachments(List.of(new ApplicationAttachmentView("file-1", "a.pdf", "application/pdf", 128L, 0)));
        return view;
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

    private record DeletePayload(Long expectedVersion) {
    }
}
