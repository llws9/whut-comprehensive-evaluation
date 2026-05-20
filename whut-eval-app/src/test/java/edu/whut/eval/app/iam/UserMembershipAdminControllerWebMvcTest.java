package edu.whut.eval.app.iam;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.iam.command.ReplaceUserMembershipsCommand;
import edu.whut.eval.application.iam.query.UserMembershipAdminView;
import edu.whut.eval.application.iam.service.UserMembershipAdminApplicationService;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.iam.UserMembershipAdminController;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserMembershipAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = UserMembershipAdminControllerWebMvcTest.TestApplication.class)
@Import({
        UserMembershipAdminController.class,
        GlobalExceptionHandler.class
})
class UserMembershipAdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserMembershipAdminApplicationService userMembershipAdminApplicationService;

    @Test
    void shouldReturnUserMemberships() throws Exception {
        given(userMembershipAdminApplicationService.listMemberships(1010L))
                .willReturn(List.of(
                        new UserMembershipAdminView(
                                70021L,
                                2002L,
                                "计算机与人工智能学院",
                                "COLLEGE",
                                true,
                                "ACTIVE"
                        ),
                        new UserMembershipAdminView(
                                70022L,
                                2009L,
                                "计科2201",
                                "CLASS",
                                false,
                                "ACTIVE"
                        )
                ));

        mockMvc.perform(get("/api/admin/users/1010/memberships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].membershipId").value(70021))
                .andExpect(jsonPath("$.data[0].orgUnitType").value("COLLEGE"))
                .andExpect(jsonPath("$.data[0].isPrimary").value(true))
                .andExpect(jsonPath("$.data[1].orgUnitId").value(2009));
    }

    @Test
    void shouldReplaceUserMemberships() throws Exception {
        String payload = objectMapper.writeValueAsString(new ReplaceMembershipsPayload(List.of(
                new MembershipPayload(2002L, true),
                new MembershipPayload(2009L, false)
        )));

        mockMvc.perform(put("/api/admin/users/1010/memberships")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    private record ReplaceMembershipsPayload(List<MembershipPayload> memberships) {
    }

    private record MembershipPayload(Long orgUnitId, Boolean isPrimary) {
    }
}
