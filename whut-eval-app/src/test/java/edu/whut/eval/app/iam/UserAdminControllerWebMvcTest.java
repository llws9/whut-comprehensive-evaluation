package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.command.CreateUserCommand;
import edu.whut.eval.application.iam.command.ImportUsersCommand;
import edu.whut.eval.application.iam.command.UpdateUserStatusCommand;
import edu.whut.eval.application.iam.query.UserImportFailedRowView;
import edu.whut.eval.application.iam.query.UserImportResultView;
import edu.whut.eval.application.iam.query.UserAdminView;
import edu.whut.eval.application.iam.query.UserAdminPageItemView;
import edu.whut.eval.application.iam.query.UserAdminPageQuery;
import edu.whut.eval.application.iam.service.UserAdminCommandApplicationService;
import edu.whut.eval.application.iam.service.UserAdminQueryApplicationService;
import edu.whut.eval.common.exception.FileStorageException;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.iam.UserAdminController;
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

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
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
    private UserAdminController userAdminController;

    @MockBean
    private UserAdminQueryApplicationService userAdminQueryApplicationService;

    @MockBean
    private UserAdminCommandApplicationService userAdminCommandApplicationService;

    @Test
    void shouldReturnPagedUsers() throws Exception {
        given(userAdminQueryApplicationService.pageUsers(argThat(queryMatches(2L, 5L, "王", "ACTIVE", 2002L))))
                .willReturn(new PageResult<>(1, List.of(
                        new UserAdminPageItemView(
                                1010L,
                                "2024305001",
                                "王老师",
                                "ACTIVE",
                                List.of("计算机与人工智能学院", "计科2201"),
                                List.of("COUNSELOR", "REVIEWER"),
                                "2026-05-20T10:00:00"
                        )
                )));

        mockMvc.perform(get("/api/admin/users")
                        .param("pageNo", "2")
                        .param("pageSize", "5")
                        .param("keyword", "王")
                        .param("status", "ACTIVE")
                        .param("orgUnitId", "2002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].userId").value(1010))
                .andExpect(jsonPath("$.data.records[0].userNo").value("2024305001"))
                .andExpect(jsonPath("$.data.records[0].userName").value("王老师"))
                .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.records[0].orgUnits[0]").value("计算机与人工智能学院"))
                .andExpect(jsonPath("$.data.records[0].roleCodes[1]").value("REVIEWER"))
                .andExpect(jsonPath("$.data.records[0].createdAt").value("2026-05-20T10:00:00"));
    }

    @Test
    void shouldCreateUser() throws Exception {
        given(userAdminCommandApplicationService.createUser(argThat(commandMatches(
                "2024305001",
                "王老师",
                "ChangeMe123!",
                "wang@example.com",
                "13800000000",
                2002L
        )))).willReturn(new UserAdminView(1010L, "2024305001", "王老师", "ACTIVE"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(APPLICATION_JSON)
                        .content("{\"userNo\":\"2024305001\",\"userName\":\"王老师\",\"password\":\"ChangeMe123!\",\"email\":\"wang@example.com\",\"phone\":\"13800000000\",\"primaryOrgUnitId\":2002}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1010))
                .andExpect(jsonPath("$.data.userNo").value("2024305001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldUpdateUserStatus() throws Exception {
        willDoNothing().given(userAdminCommandApplicationService).updateUserStatus(
                argThat(userId -> userId != null && userId == 1010L),
                argThat(statusCommandMatches("DISABLED", "manual"))
        );

        mockMvc.perform(patch("/api/admin/users/1010/status")
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"manual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldImportUsers() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-xlsx".getBytes()
        );
        given(userAdminCommandApplicationService.importUsers(argThat(importCommandMatches("UPSERT", "users.xlsx", 9L))))
                .willReturn(new UserImportResultView(
                        3,
                        2,
                        1,
                        List.of(new UserImportFailedRowView(4, "2024305003", "userName 不能为空"))
                ));

        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(file)
                        .param("importMode", "UPSERT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.failedRows[0].rowNo").value(4))
                .andExpect(jsonPath("$.data.failedRows[0].userNo").value("2024305003"))
                .andExpect(jsonPath("$.data.failedRows[0].reason").value("userName 不能为空"));
    }

    @Test
    void shouldReturn400WhenImportFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(emptyFile)
                        .param("importMode", "UPSERT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("导入文件不能为空"));
    }

    @Test
    void shouldReturn400WhenImportFilePartIsMissing() throws Exception {
        mockMvc.perform(multipart("/api/admin/users/import")
                        .param("importMode", "UPSERT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    @Test
    void shouldReturn400WhenImportModeIsIllegal() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-xlsx".getBytes()
        );
        given(userAdminCommandApplicationService.importUsers(any(ImportUsersCommand.class)))
                .willThrow(new ValidationException("importMode 仅允许 UPSERT 或 INSERT_ONLY"));

        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(file)
                        .param("importMode", "MERGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("importMode 仅允许 UPSERT 或 INSERT_ONLY"));
    }

    @Test
    void shouldReturn503WhenImportFileReadFails() throws Exception {
        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        given(file.isEmpty()).willReturn(false);
        given(file.getInputStream()).willThrow(new IOException("boom"));

        assertThatThrownBy(() -> userAdminController.importUsers(file, "UPSERT"))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("导入文件读取失败");
    }

    @Test
    void shouldReturn503WhenImportParsingFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-xlsx".getBytes()
        );
        given(userAdminCommandApplicationService.importUsers(any(ImportUsersCommand.class)))
                .willThrow(new FileStorageException("导入文件解析失败"));

        mockMvc.perform(multipart("/api/admin/users/import")
                        .file(file)
                        .param("importMode", "UPSERT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EXT-5033"))
                .andExpect(jsonPath("$.message").value("导入文件解析失败"));
    }

    private static org.mockito.ArgumentMatcher<CreateUserCommand> commandMatches(String userNo,
                                                                                 String userName,
                                                                                 String password,
                                                                                 String email,
                                                                                 String phone,
                                                                                 Long primaryOrgUnitId) {
        return command -> command != null
                && userNo.equals(command.userNo())
                && userName.equals(command.userName())
                && password.equals(command.password())
                && email.equals(command.email())
                && phone.equals(command.phone())
                && primaryOrgUnitId.equals(command.primaryOrgUnitId());
    }

    private static org.mockito.ArgumentMatcher<UserAdminPageQuery> queryMatches(long pageNo,
                                                                                long pageSize,
                                                                                String keyword,
                                                                                String status,
                                                                                Long orgUnitId) {
        return query -> query != null
                && query.pageNo() == pageNo
                && query.pageSize() == pageSize
                && keyword.equals(query.keyword())
                && status.equals(query.status())
                && orgUnitId.equals(query.orgUnitId());
    }

    private static org.mockito.ArgumentMatcher<UpdateUserStatusCommand> statusCommandMatches(String status, String reason) {
        return command -> command != null
                && status.equals(command.status())
                && reason.equals(command.reason());
    }

    private static org.mockito.ArgumentMatcher<ImportUsersCommand> importCommandMatches(String importMode,
                                                                                        String originalFilename,
                                                                                        long size) {
        return command -> command != null
                && command.inputStream() != null
                && importMode.equals(command.importMode())
                && originalFilename.equals(command.originalFilename())
                && command.size() == size;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
