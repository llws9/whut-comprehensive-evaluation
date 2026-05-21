package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.command.CreateRoleCommand;
import edu.whut.eval.application.iam.command.ReplaceRolePermissionsCommand;
import edu.whut.eval.application.iam.command.UpdateRoleCommand;
import edu.whut.eval.application.iam.query.RoleAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAdminPageQuery;
import edu.whut.eval.application.iam.query.RoleAdminView;
import edu.whut.eval.application.iam.service.RoleAdminCommandApplicationService;
import edu.whut.eval.application.iam.service.RoleAdminQueryApplicationService;
import edu.whut.eval.common.api.ApiResponse;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.shared.PageResult;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.iam.RoleAdminController;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoleAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = RoleAdminControllerWebMvcTest.TestApplication.class)
@Import({
        RoleAdminController.class,
        GlobalExceptionHandler.class
})
class RoleAdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleAdminQueryApplicationService roleAdminQueryApplicationService;

    @MockBean
    private RoleAdminCommandApplicationService roleAdminCommandApplicationService;

    @Test
    void shouldReturnPagedRoles() throws Exception {
        given(roleAdminQueryApplicationService.pageRoles(argThat(queryMatches(2L, 5L, "辅导", "ACTIVE"))))
                .willReturn(new PageResult<>(1, List.of(
                        new RoleAdminPageItemView(
                                21L,
                                "COUNSELOR",
                                "辅导员",
                                "ORG_SUBTREE",
                                "ACTIVE",
                                6,
                                "2026-05-20T10:00:00"
                        )
                )));

        mockMvc.perform(get("/api/admin/roles")
                        .param("pageNo", "2")
                        .param("pageSize", "5")
                        .param("keyword", "辅导")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].roleId").value(21))
                .andExpect(jsonPath("$.data.records[0].roleCode").value("COUNSELOR"))
                .andExpect(jsonPath("$.data.records[0].roleName").value("辅导员"))
                .andExpect(jsonPath("$.data.records[0].roleScope").value("ORG_SUBTREE"))
                .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.records[0].permissionCount").value(6))
                .andExpect(jsonPath("$.data.records[0].createdAt").value("2026-05-20T10:00:00"));
    }

    @Test
    void shouldCreateRole() throws Exception {
        given(roleAdminCommandApplicationService.createRole(argThat(createCommandMatches(
                "COUNSELOR",
                "辅导员",
                "ORG_SUBTREE",
                "ACTIVE"
        )))).willReturn(new RoleAdminView(21L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE"));

        mockMvc.perform(post("/api/admin/roles")
                        .contentType(APPLICATION_JSON)
                        .content("{\"roleCode\":\"COUNSELOR\",\"roleName\":\"辅导员\",\"roleScope\":\"ORG_SUBTREE\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleId").value(21))
                .andExpect(jsonPath("$.data.roleCode").value("COUNSELOR"))
                .andExpect(jsonPath("$.data.roleName").value("辅导员"))
                .andExpect(jsonPath("$.data.roleScope").value("ORG_SUBTREE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldUpdateRole() throws Exception {
        willDoNothing().given(roleAdminCommandApplicationService).updateRole(
                argThat(roleId -> roleId != null && roleId == 21L),
                argThat(updateCommandMatches("学院辅导员", "ORG_UNIT", "DISABLED"))
        );

        mockMvc.perform(patch("/api/admin/roles/21")
                        .contentType(APPLICATION_JSON)
                        .content("{\"roleName\":\"学院辅导员\",\"roleScope\":\"ORG_UNIT\",\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReplaceRolePermissions() throws Exception {
        willDoNothing().given(roleAdminCommandApplicationService).replaceRolePermissions(
                argThat((Long roleId) -> roleId != null && roleId == 21L),
                argThat(replacePermissionsCommandMatches(List.of("permission.manage", "role.manage"), true))
        );

        mockMvc.perform(post("/api/admin/roles/21/permissions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"permission.manage\",\"role.manage\"],\"replaceAll\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturn400WhenCreateRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .contentType(APPLICATION_JSON)
                        .content("{\"roleCode\":\"\",\"roleName\":\"辅导员\",\"roleScope\":\"ORG_SUBTREE\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    @Test
    void shouldReturn400WhenQueryStatusIsIllegal() throws Exception {
        given(roleAdminQueryApplicationService.pageRoles(any(RoleAdminPageQuery.class)))
                .willThrow(new ValidationException("status 仅允许 ACTIVE 或 DISABLED"));

        mockMvc.perform(get("/api/admin/roles")
                        .param("status", "LOCKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"))
                .andExpect(jsonPath("$.message").value("status 仅允许 ACTIVE 或 DISABLED"));
    }

    @Test
    void shouldAllowReplacingRolePermissionsWithEmptyCollection() throws Exception {
        willDoNothing().given(roleAdminCommandApplicationService).replaceRolePermissions(
                argThat((Long roleId) -> roleId != null && roleId == 21L),
                argThat(replacePermissionsCommandMatches(List.of(), true))
        );

        mockMvc.perform(post("/api/admin/roles/21/permissions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissionCodes\":[],\"replaceAll\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturn400WhenPermissionCodesContainsBlank() throws Exception {
        mockMvc.perform(post("/api/admin/roles/21/permissions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"permission.manage\",\"\"],\"replaceAll\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    private static org.mockito.ArgumentMatcher<RoleAdminPageQuery> queryMatches(long pageNo,
                                                                                long pageSize,
                                                                                String keyword,
                                                                                String status) {
        return query -> query != null
                && query.pageNo() == pageNo
                && query.pageSize() == pageSize
                && keyword.equals(query.keyword())
                && status.equals(query.status());
    }

    private static org.mockito.ArgumentMatcher<CreateRoleCommand> createCommandMatches(String roleCode,
                                                                                        String roleName,
                                                                                        String roleScope,
                                                                                        String status) {
        return command -> command != null
                && roleCode.equals(command.roleCode())
                && roleName.equals(command.roleName())
                && roleScope.equals(command.roleScope())
                && status.equals(command.status());
    }

    private static org.mockito.ArgumentMatcher<UpdateRoleCommand> updateCommandMatches(String roleName,
                                                                                        String roleScope,
                                                                                        String status) {
        return command -> command != null
                && roleName.equals(command.roleName())
                && roleScope.equals(command.roleScope())
                && status.equals(command.status());
    }

    private static org.mockito.ArgumentMatcher<ReplaceRolePermissionsCommand> replacePermissionsCommandMatches(
            List<String> permissionCodes,
            boolean replaceAll) {
        return command -> command != null
                && permissionCodes.equals(command.permissionCodes())
                && replaceAll == command.replaceAll();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
