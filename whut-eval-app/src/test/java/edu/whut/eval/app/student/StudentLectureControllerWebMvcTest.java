package edu.whut.eval.app.student;

import edu.whut.eval.application.application.query.LectureCandidateView;
import edu.whut.eval.application.application.service.LectureCandidateQueryApplicationService;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.student.StudentLectureController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class StudentLectureControllerWebMvcTest {

    private LectureCandidateQueryApplicationService lectureCandidateQueryApplicationService;
    private StudentLectureController controller;

    @BeforeEach
    void setUp() {
        lectureCandidateQueryApplicationService = mock(LectureCandidateQueryApplicationService.class);
        controller = new StudentLectureController(lectureCandidateQueryApplicationService);
    }

    @Test
    void shouldReturnPagedStudentLectureCandidates() throws Exception {
        given(lectureCandidateQueryApplicationService.pageCurrentStudentLectures(any()))
                .willReturn(new PageResult<>(1, List.of(new LectureCandidateView(
                        7001L,
                        "学院学术讲座 讲座签到",
                        "2026-05-18T14:30:00",
                        "2025-2026",
                        new BigDecimal("1.25"),
                        "CLAIMED"
                ))));

        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/student/lectures")
                        .param("academicYear", "2025-2026")
                        .param("keyword", "学术")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].lectureId").value(7001))
                .andExpect(jsonPath("$.data.records[0].title").value("学院学术讲座 讲座签到"))
                .andExpect(jsonPath("$.data.records[0].heldAt").value("2026-05-18T14:30:00"))
                .andExpect(jsonPath("$.data.records[0].academicYear").value("2025-2026"))
                .andExpect(jsonPath("$.data.records[0].maxScore").value(1.25))
                .andExpect(jsonPath("$.data.records[0].attendanceStatus").value("CLAIMED"));

        verify(lectureCandidateQueryApplicationService).pageCurrentStudentLectures(any());
    }

    @Test
    void shouldRejectBlankAcademicYear() throws Exception {
        standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/api/student/lectures")
                        .param("academicYear", " ")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }
}
