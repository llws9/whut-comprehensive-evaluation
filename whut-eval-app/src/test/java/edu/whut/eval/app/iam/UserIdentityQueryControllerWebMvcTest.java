package edu.whut.eval.app.iam;

import edu.whut.eval.application.iam.query.UserIdentityMembershipView;
import edu.whut.eval.application.iam.query.UserIdentityView;
import edu.whut.eval.application.iam.service.UserIdentityQueryApplicationService;
import edu.whut.eval.domain.iam.model.IamRoleAssignment;
import edu.whut.eval.domain.iam.model.IamUser;
import edu.whut.eval.common.exception.ResourceNotFoundException;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.iam.UserIdentityQueryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserIdentityQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = UserIdentityQueryControllerWebMvcTest.TestApplication.class)
@Import({
        UserIdentityQueryController.class,
        GlobalExceptionHandler.class,
        UserIdentityQueryControllerWebMvcTest.TestBeans.class
})
class UserIdentityQueryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubUserIdentityQueryApplicationService userIdentityQueryApplicationService;

    @Test
    void shouldKeepFrozenMembershipFieldsInIdentityResponse() throws Exception {
        userIdentityQueryApplicationService.willReturn(new UserIdentityView(
                new IamUser(1010L, "2024305001", "王老师", "w@example.com", "13800000000", "ACTIVE"),
                List.of(new IamRoleAssignment(70021L, 21L, "COUNSELOR", "辅导员", 2002L, "ACTIVE")),
                List.of(new UserIdentityMembershipView(80001L, 1010L, 2002L, "IMPORT", "ACTIVE"))
        ));

        mockMvc.perform(get("/api/iam/users/2024305001/identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberships[0].id").value(80001))
                .andExpect(jsonPath("$.data.memberships[0].userId").value(1010))
                .andExpect(jsonPath("$.data.memberships[0].orgUnitId").value(2002))
                .andExpect(jsonPath("$.data.memberships[0].membershipType").value("IMPORT"))
                .andExpect(jsonPath("$.data.memberships[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.memberships[0].joinedAt").doesNotExist())
                .andExpect(jsonPath("$.data.memberships[0].leftAt").doesNotExist())
                .andExpect(jsonPath("$.data.memberships[0].primary").doesNotExist())
                .andExpect(jsonPath("$.data.memberships[0].isPrimary").doesNotExist());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        StubUserIdentityQueryApplicationService userIdentityQueryApplicationService() {
            return new StubUserIdentityQueryApplicationService();
        }
    }

    static class StubUserIdentityQueryApplicationService extends UserIdentityQueryApplicationService {

        private UserIdentityView nextView;

        StubUserIdentityQueryApplicationService() {
            super(null, null, null);
        }

        void willReturn(UserIdentityView nextView) {
            this.nextView = nextView;
        }

        @Override
        public UserIdentityView getUserIdentityByUserNo(String userNo) {
            if (nextView == null) {
                throw new ResourceNotFoundException("用户不存在: " + userNo);
            }
            return nextView;
        }
    }
}
