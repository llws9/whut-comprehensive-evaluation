package edu.whut.eval.app.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserCreatedView;
import edu.whut.eval.application.iam.query.UserImportFailedRowView;
import edu.whut.eval.application.iam.query.UserImportResultView;
import edu.whut.eval.application.iam.service.UserAdminApplicationService;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.iam.UserAdminController;
import edu.whut.eval.interfaces.iam.request.CreateUserRequest;
import edu.whut.eval.interfaces.iam.request.UpdateUserStatusRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = UserAdminControllerWebMvcTest.TestApplication.class)
@Import({
        UserAdminController.class,
        GlobalExceptionHandler.class
})
class UserAdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserAdminApplicationService userAdminApplicationService;

    @Test
    void shouldReturnPagedUsers() throws Exception {
        given(userAdminApplicationService.pageUsers(any()))
                .willReturn(new PageResult<>(1L, List.of(
                        new UserAdminPageItemView(
                                1010L,
                                "2024305001",
                                "王老师",
                                "ACTIVE",
                                List.of("计算机学院"),
                                List.of("COUNSELOR"),
                                "2026-05-01T10:00:00"
                        )
                )));

        mockMvc.perform(get("/api/admin/users")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(1010))
                .andExpect(jsonPath("$.data.records[0].userNo").value("2024305001"))
                .andExpect(jsonPath("$.data.records[0].userName").value("王老师"));
    }

    @Test
    void shouldCreateUser() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUserNo("2024305111");
        request.setUserName("李老师");
        request.setPassword("secret123");
        request.setEmail("li@example.com");
        request.setPhone("13800001111");

        given(userAdminApplicationService.createUser(any()))
                .willReturn(new UserCreatedView(1011L, "2024305111", "李老师", "ACTIVE"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1011))
                .andExpect(jsonPath("$.data.userNo").value("2024305111"));
    }

    @Test
    void shouldUpdateUserStatus() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest();
        request.setStatus("DISABLED");
        request.setReason("manual disable");

        mockMvc.perform(patch("/api/admin/users/1010/status")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldImportUsers() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );
        given(userAdminApplicationService.importUsers(any()))
                .willReturn(new UserImportResultView(
                        3,
                        2,
                        1,
                        java.util.List.of(new UserImportFailedRowView(3, "userNo 重复"))
                ));

        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(file)
                        .param("importMode", "UPSERT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.failedRows[0].rowNo").value(3));
    }

    @Test
    void shouldReturn400WhenImportModeIllegal() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy".getBytes()
        );

        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(file)
                        .param("importMode", "MERGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}