package edu.whut.eval.app.platform;

import edu.whut.eval.application.platform.query.PlatformMenuDeadline;
import edu.whut.eval.application.platform.query.PlatformMenuStatus;
import edu.whut.eval.application.platform.service.PlatformReadApplicationService;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.platform.PlatformReadController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlatformReadController.class)
@AutoConfigureMockMvc
@ContextConfiguration(classes = PlatformReadControllerWebMvcTest.TestApplication.class)
@Import({
        PlatformReadController.class,
        GlobalExceptionHandler.class,
        PlatformReadControllerWebMvcTest.TestBeans.class
})
class PlatformReadControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubPlatformReadApplicationService platformReadApplicationService;

    @Test
    void shouldReturnMenuStatusForAuthenticatedUser() throws Exception {
        platformReadApplicationService.status = new PlatformMenuStatus(true, false, "NACOS");

        mockMvc.perform(get("/api/platform/menu/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentApplyEnabled").value(true))
                .andExpect(jsonPath("$.data.finalSubmitEnabled").value(false))
                .andExpect(jsonPath("$.data.source").value("NACOS"));
    }

    @Test
    void shouldReturnMenuDeadlineForAuthenticatedUser() throws Exception {
        platformReadApplicationService.deadline = new PlatformMenuDeadline(
                "2026-09-30T23:59:59+08:00",
                "2026-10-15T23:59:59+08:00",
                "NACOS"
        );

        mockMvc.perform(get("/api/platform/menu/deadline")
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentApplyDeadline").value("2026-09-30T23:59:59+08:00"))
                .andExpect(jsonPath("$.data.finalSubmitDeadline").value("2026-10-15T23:59:59+08:00"))
                .andExpect(jsonPath("$.data.source").value("NACOS"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableMethodSecurity
    static class TestApplication {
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        StubPlatformReadApplicationService platformReadApplicationService() {
            return new StubPlatformReadApplicationService();
        }
    }

    static class StubPlatformReadApplicationService extends PlatformReadApplicationService {

        private PlatformMenuStatus status;
        private PlatformMenuDeadline deadline;

        StubPlatformReadApplicationService() {
            super(null);
        }

        @Override
        public PlatformMenuStatus getMenuStatus() {
            return status;
        }

        @Override
        public PlatformMenuDeadline getMenuDeadline() {
            return deadline;
        }
    }
}
