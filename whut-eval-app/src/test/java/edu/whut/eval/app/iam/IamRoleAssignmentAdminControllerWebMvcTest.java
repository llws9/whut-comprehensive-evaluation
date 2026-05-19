package edu.whut.eval.app.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.iam.command.CreateRoleAssignmentCommand;
import edu.whut.eval.application.iam.command.CreateScopeRuleCommand;
import edu.whut.eval.application.iam.command.UpdateRoleAssignmentCommand;
import edu.whut.eval.application.iam.query.RoleAssignmentAdminView;
import edu.whut.eval.application.iam.query.ScopeRuleAdminView;
import edu.whut.eval.application.iam.service.RoleAssignmentAdminApplicationService;
import edu.whut.eval.application.iam.service.ScopeRuleAdminApplicationService;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.iam.IamRoleAssignmentAdminController;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IamRoleAssignmentAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = IamRoleAssignmentAdminControllerWebMvcTest.TestApplication.class)
@Import({
        IamRoleAssignmentAdminController.class,
        GlobalExceptionHandler.class
})
class IamRoleAssignmentAdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoleAssignmentAdminApplicationService roleAssignmentAdminApplicationService;

    @MockBean
    private ScopeRuleAdminApplicationService scopeRuleAdminApplicationService;

    @Test
    void shouldCreateRoleAssignment() throws Exception {
        given(roleAssignmentAdminApplicationService.createAssignment(any(CreateRoleAssignmentCommand.class)))
                .willReturn(new RoleAssignmentAdminView(
                        70021L,
                        1010L,
                        "COUNSELOR",
                        "辅导员",
                        2002L,
                        "计算机与人工智能学院",
                        "ACTIVE",
                        "2026-05-20T00:00:00",
                        "2027-07-01T00:00:00",
                        "MANUAL",
                        null
                ));

        mockMvc.perform(post("/api/admin/role-assignments")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRoleAssignmentPayload(
                                1010L,
                                "COUNSELOR",
                                2002L,
                                "2026-05-20T00:00:00",
                                "2027-07-01T00:00:00",
                                "MANUAL"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentId").value(70021))
                .andExpect(jsonPath("$.data.roleCode").value("COUNSELOR"))
                .andExpect(jsonPath("$.data.orgUnitId").value(2002));
    }

    @Test
    void shouldUpdateRoleAssignment() throws Exception {
        given(roleAssignmentAdminApplicationService.updateAssignment(eq(70021L), any(UpdateRoleAssignmentCommand.class)))
                .willReturn(new RoleAssignmentAdminView(
                        70021L,
                        1010L,
                        "COUNSELOR",
                        "辅导员",
                        2009L,
                        "计算机学院研工办",
                        "ACTIVE",
                        "2026-05-20T00:00:00",
                        "2027-07-01T00:00:00",
                        "MANUAL",
                        "2026-05-20T10:20:30"
                ));

        mockMvc.perform(patch("/api/admin/role-assignments/70021")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleAssignmentPayload(
                                "ACTIVE",
                                2009L,
                                "2026-05-20T00:00:00",
                                "2027-07-01T00:00:00"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentId").value(70021))
                .andExpect(jsonPath("$.data.orgUnitId").value(2009))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-05-20T10:20:30"));
    }

    @Test
    void shouldListScopeRules() throws Exception {
        given(scopeRuleAdminApplicationService.listScopeRules(70021L))
                .willReturn(List.of(
                        new ScopeRuleAdminView(
                                81001L,
                                70021L,
                                "manage.review.view",
                                "ORG_SUBTREE",
                                2002L,
                                "计算机与人工智能学院",
                                null,
                                null,
                                null,
                                100,
                                "ACTIVE",
                                null
                        ),
                        new ScopeRuleAdminView(
                                81002L,
                                70021L,
                                "manage.review.view",
                                "CATEGORY",
                                null,
                                null,
                                "MORAL",
                                null,
                                null,
                                90,
                                "ACTIVE",
                                null
                        )
                ));

        mockMvc.perform(get("/api/admin/role-assignments/70021/scope-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].scopeRuleId").value(81001))
                .andExpect(jsonPath("$.data[0].scopeType").value("ORG_SUBTREE"))
                .andExpect(jsonPath("$.data[1].categoryCode").value("MORAL"));
    }

    @Test
    void shouldCreateScopeRule() throws Exception {
        given(scopeRuleAdminApplicationService.createScopeRule(eq(70021L), any(CreateScopeRuleCommand.class)))
                .willReturn(new ScopeRuleAdminView(
                        81004L,
                        70021L,
                        "manage.review.view",
                        "CATEGORY",
                        null,
                        null,
                        "MORAL",
                        null,
                        null,
                        90,
                        "ACTIVE",
                        "2026-05-20T10:40:00"
                ));

        mockMvc.perform(post("/api/admin/role-assignments/70021/scope-rules")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateScopeRulePayload(
                                "manage.review.view",
                                "CATEGORY",
                                null,
                                "MORAL",
                                null,
                                null,
                                90
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scopeRuleId").value(81004))
                .andExpect(jsonPath("$.data.scopeType").value("CATEGORY"))
                .andExpect(jsonPath("$.data.categoryCode").value("MORAL"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    private record CreateRoleAssignmentPayload(Long userId,
                                               String roleCode,
                                               Long orgUnitId,
                                               String effectiveFrom,
                                               String effectiveTo,
                                               String sourceType) {
    }

    private record UpdateRoleAssignmentPayload(String status,
                                               Long orgUnitId,
                                               String effectiveFrom,
                                               String effectiveTo) {
    }

    private record CreateScopeRulePayload(String permissionCode,
                                          String scopeType,
                                          Long orgUnitId,
                                          String categoryCode,
                                          String itemCode,
                                          Map<String, Object> expressionJson,
                                          Integer priority) {
    }
}
