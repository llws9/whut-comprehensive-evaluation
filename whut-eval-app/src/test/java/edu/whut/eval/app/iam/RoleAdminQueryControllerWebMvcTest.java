package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.query.RoleAdminPageItemView;
import edu.whut.eval.application.iam.query.RoleAdminPageQuery;
import edu.whut.eval.application.iam.query.RoleAdminView;
import edu.whut.eval.application.iam.service.RoleAdminApplicationService;
import edu.whut.eval.application.iam.service.RoleAdminQueryApplicationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

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
    void shouldCreateRoleTemplate() throws Exception {
        given(roleAdminApplicationService.createRole(any()))
                .willReturn(new RoleAdminView(31L, "ACADEMIC_SECRETARY", "教学秘书", "ORG_UNIT", "ACTIVE", 0, "2026-06-04T10:00:00"));

        mockMvc.perform(post("/api/admin/roles")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"roleCode":"ACADEMIC_SECRETARY","roleName":"教学秘书","roleScope":"ORG_UNIT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleId").value(31))
                .andExpect(jsonPath("$.data.roleCode").value("ACADEMIC_SECRETARY"))
                .andExpect(jsonPath("$.data.roleScope").value("ORG_UNIT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldUpdateRoleTemplateWithSnapshot() throws Exception {
        mockMvc.perform(patch("/api/admin/roles/31")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"roleName":"教学秘书负责人","roleScope":"ORG_SUBTREE","status":"ACTIVE",
                                 "expectedRoleName":"教学秘书","expectedRoleScope":"ORG_UNIT","expectedStatus":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldReplaceRolePermissionsWithEmptySet() throws Exception {
        mockMvc.perform(post("/api/admin/roles/31/permissions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"permissionCodes":[],"replaceAll":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
