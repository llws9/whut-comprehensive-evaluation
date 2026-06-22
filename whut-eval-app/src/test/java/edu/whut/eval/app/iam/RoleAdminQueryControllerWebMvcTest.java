package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.query.RoleAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAdminPageQuery;
import edu.whut.eval.application.iam.query.RoleCreatedView;
import edu.whut.eval.application.iam.service.RoleAdminApplicationService;
import edu.whut.eval.application.iam.service.RoleAdminQueryApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.common.exception.ResourceNotFoundException;
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
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RoleAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = RoleAdminQueryControllerWebMvcTest.TestApplication.class)
@Import({
        RoleAdminController.class,
        GlobalExceptionHandler.class
})
class RoleAdminQueryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoleAdminQueryApplicationService roleAdminQueryApplicationService;

    @MockBean
    private RoleAdminApplicationService roleAdminApplicationService;

    @Test
    void shouldReturnPagedRoles() throws Exception {
        given(roleAdminQueryApplicationService.pageRoles(argThat(query -> query != null
                && query.pageNo() == 2
                && query.pageSize() == 5
                && "辅导".equals(query.keyword())
                && "ACTIVE".equals(query.status()))))
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
    void shouldCreateRole() throws Exception {
        given(roleAdminApplicationService.createRole(any()))
                .willReturn(new RoleCreatedView(31L, "COUNSELOR", "辅导员", "ORG_SUBTREE", "ACTIVE"));

        mockMvc.perform(post("/api/admin/roles")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"roleCode":"COUNSELOR","roleName":"辅导员","roleScope":"ORG_SUBTREE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleId").value(31))
                .andExpect(jsonPath("$.data.roleCode").value("COUNSELOR"))
                .andExpect(jsonPath("$.data.roleScope").value("ORG_SUBTREE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn409WhenCreateRoleCodeConflict() throws Exception {
        willThrow(new ConflictException("角色编码已存在: COUNSELOR"))
                .given(roleAdminApplicationService)
                .createRole(any());

        mockMvc.perform(post("/api/admin/roles")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"roleCode":"COUNSELOR","roleName":"辅导员","roleScope":"ORG_SUBTREE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BIZ-4090"));
    }

    @Test
    void shouldUpdateRoleWithSnapshot() throws Exception {
        mockMvc.perform(patch("/api/admin/roles/21")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName":"辅导员(新)",
                                  "roleScope":"ORG_SUBTREE",
                                  "status":"ACTIVE",
                                  "snapshotRoleName":"辅导员",
                                  "snapshotRoleScope":"ORG_SUBTREE",
                                  "snapshotStatus":"ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldReturn404WhenUpdateRoleNotFound() throws Exception {
        willThrow(new ResourceNotFoundException("角色不存在: 21"))
                .given(roleAdminApplicationService)
                .updateRole(any(), any());

        mockMvc.perform(patch("/api/admin/roles/21")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName":"辅导员(新)",
                                  "roleScope":"ORG_SUBTREE",
                                  "status":"ACTIVE",
                                  "snapshotRoleName":"辅导员",
                                  "snapshotRoleScope":"ORG_SUBTREE",
                                  "snapshotStatus":"ACTIVE"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RES-4040"));
    }

    @Test
    void shouldReturn409WhenUpdateRoleSnapshotConflict() throws Exception {
        willThrow(new ConflictException("角色模板已被更新，请刷新后重试"))
                .given(roleAdminApplicationService)
                .updateRole(any(), any());

        mockMvc.perform(patch("/api/admin/roles/21")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName":"辅导员(新)",
                                  "roleScope":"ORG_SUBTREE",
                                  "status":"ACTIVE",
                                  "snapshotRoleName":"辅导员",
                                  "snapshotRoleScope":"ORG_SUBTREE",
                                  "snapshotStatus":"ACTIVE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIZ-4090"));
    }

    @Test
    void shouldReturn400WhenReplaceAllIsMissingOnReplacePermissions() throws Exception {
        mockMvc.perform(post("/api/admin/roles/21/permissions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"permissionCodes":["user.manage"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
