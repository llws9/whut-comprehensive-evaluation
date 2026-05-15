package edu.whut.eval.app.query;

import edu.whut.eval.application.application.query.ApplicationRecordView;
import edu.whut.eval.application.application.service.ApplicationQueryApplicationService;
import edu.whut.eval.application.score.query.ScoreRecordView;
import edu.whut.eval.application.score.service.ScoreQueryApplicationService;
import edu.whut.eval.common.exception.AccessDeniedAppException;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.admin.AdminQueryController;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = AdminQueryControllerWebMvcTest.TestApplication.class)
@Import({
        AdminQueryController.class,
        GlobalExceptionHandler.class
})
class AdminQueryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApplicationQueryApplicationService applicationQueryApplicationService;

    @MockBean
    private ScoreQueryApplicationService scoreQueryApplicationService;

    @Test
    void shouldReturnPagedApplications() throws Exception {
        given(applicationQueryApplicationService.pageAccessibleApplications(any(), anyString()))
                .willReturn(new PageResult<>(1, List.of(
                        new ApplicationRecordView(9001L, 1001L, 3001L, "/1/3001/", "INTELLECTUAL", "ACADEMIC_LECTURE")
                )));

        mockMvc.perform(get("/api/admin/query/applications")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].applicationId").value(9001))
                .andExpect(jsonPath("$.data.records[0].categoryCode").value("INTELLECTUAL"));
    }

    @Test
    void shouldReturnPagedScores() throws Exception {
        given(scoreQueryApplicationService.pageAccessibleScores(any(), anyString()))
                .willReturn(new PageResult<>(1, List.of(
                        new ScoreRecordView(8001L, 1001L, 3001L, "/1/3001/", "ACADEMIC", "LECTURE", "2025-2026")
                )));

        mockMvc.perform(get("/api/admin/query/scores")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].scoreId").value(8001))
                .andExpect(jsonPath("$.data.records[0].academicYear").value("2025-2026"));
    }

    @Test
    void shouldReturn403WhenApplicationQueryIsDenied() throws Exception {
        given(applicationQueryApplicationService.pageAccessibleApplications(any(), anyString()))
                .willThrow(new AccessDeniedAppException("当前用户无权限访问申请列表"));

        mockMvc.perform(get("/api/admin/query/applications"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-4030"))
                .andExpect(jsonPath("$.message").value("当前用户无权限访问申请列表"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
