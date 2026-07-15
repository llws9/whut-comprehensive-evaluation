package edu.whut.eval.app.platform;

import edu.whut.eval.application.platform.query.PlatformMenuDeadlineUpdateResult;
import edu.whut.eval.application.platform.query.PlatformMenuDeadline;
import edu.whut.eval.application.platform.query.PlatformMenuStatus;
import edu.whut.eval.application.platform.query.PlatformMenuStatusUpdateResult;
import edu.whut.eval.application.platform.service.ConfigPublishException;
import edu.whut.eval.application.platform.service.EvaluationItemCommandApplicationService;
import edu.whut.eval.application.platform.service.PlatformReadApplicationService;
import edu.whut.eval.application.platform.service.PlatformRuleCommandApplicationService;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.platform.PlatformReadController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private StubPlatformRuleCommandApplicationService platformRuleCommandApplicationService;

    @BeforeEach
    void resetStubs() {
        platformReadApplicationService.reset();
        platformRuleCommandApplicationService.reset();
    }

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

    @Test
    void shouldPatchMenuStatusWithManageAuthority() throws Exception {
        platformRuleCommandApplicationService.statusUpdateResult = new PlatformMenuStatusUpdateResult(
                false,
                true,
                OffsetDateTime.parse("2026-07-15T20:00:00+08:00"),
                "NACOS"
        );

        mockMvc.perform(patch("/api/platform/menu/status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "finalSubmitEnabled": true,
                                  "reason": "开放最终提交"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "platform.switch.manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentApplyEnabled").value(false))
                .andExpect(jsonPath("$.data.finalSubmitEnabled").value(true))
                .andExpect(jsonPath("$.data.effectiveAt").exists())
                .andExpect(jsonPath("$.data.source").value("NACOS"));
        org.assertj.core.api.Assertions.assertThat(platformRuleCommandApplicationService.lastStatusCommand.finalSubmitEnabled())
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(platformRuleCommandApplicationService.lastStatusCommand.reason())
                .isEqualTo("开放最终提交");
    }

    @Test
    void shouldRejectMenuStatusPatchWithoutManageAuthority() throws Exception {
        mockMvc.perform(patch("/api/platform/menu/status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "studentApplyEnabled": false,
                                  "reason": "关闭申请"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnExternalErrorWhenMenuStatusPublishFails() throws Exception {
        platformRuleCommandApplicationService.statusPublishFails = true;

        mockMvc.perform(patch("/api/platform/menu/status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "studentApplyEnabled": false,
                                  "reason": "关闭申请"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "platform.switch.manage")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EXT-5033"))
                .andExpect(jsonPath("$.message").value("Failed to publish platform-rule-config"));
    }

    @Test
    void shouldPutMenuDeadlineWithManageAuthority() throws Exception {
        platformRuleCommandApplicationService.deadlineUpdateResult = new PlatformMenuDeadlineUpdateResult(
                "2026-10-01T23:59:59+08:00",
                "2026-10-20T23:59:59+08:00",
                "Asia/Shanghai",
                OffsetDateTime.parse("2026-07-15T20:05:00+08:00"),
                "NACOS"
        );

        mockMvc.perform(put("/api/platform/menu/deadline")
                        .contentType("application/json")
                        .content("""
                                {
                                  "studentApplyDeadline": "2026-10-01T23:59:59+08:00",
                                  "finalSubmitDeadline": "2026-10-20T23:59:59+08:00",
                                  "reason": "调整截止时间"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "platform.switch.manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentApplyDeadline").value("2026-10-01T23:59:59+08:00"))
                .andExpect(jsonPath("$.data.finalSubmitDeadline").value("2026-10-20T23:59:59+08:00"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.effectiveAt").exists())
                .andExpect(jsonPath("$.data.source").value("NACOS"));
        org.assertj.core.api.Assertions.assertThat(platformRuleCommandApplicationService.lastDeadlineCommand.reason())
                .isEqualTo("调整截止时间");
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

        @Bean
        StubPlatformRuleCommandApplicationService platformRuleCommandApplicationService() {
            return new StubPlatformRuleCommandApplicationService();
        }

        @Bean
        EvaluationItemCommandApplicationService evaluationItemCommandApplicationService() {
            return Mockito.mock(EvaluationItemCommandApplicationService.class);
        }
    }

    static class StubPlatformReadApplicationService extends PlatformReadApplicationService {

        private PlatformMenuStatus status;
        private PlatformMenuDeadline deadline;

        StubPlatformReadApplicationService() {
            super(null);
        }

        void reset() {
            status = null;
            deadline = null;
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

    static class StubPlatformRuleCommandApplicationService extends PlatformRuleCommandApplicationService {

        private PlatformMenuStatusUpdateResult statusUpdateResult;
        private PlatformMenuDeadlineUpdateResult deadlineUpdateResult;
        private edu.whut.eval.application.platform.command.UpdatePlatformMenuStatusCommand lastStatusCommand;
        private edu.whut.eval.application.platform.command.ReplacePlatformDeadlineCommand lastDeadlineCommand;
        private boolean statusPublishFails;

        StubPlatformRuleCommandApplicationService() {
            super(null, null);
        }

        void reset() {
            statusUpdateResult = null;
            deadlineUpdateResult = null;
            lastStatusCommand = null;
            lastDeadlineCommand = null;
            statusPublishFails = false;
        }

        @Override
        public PlatformMenuStatusUpdateResult updateMenuStatus(
                edu.whut.eval.application.platform.command.UpdatePlatformMenuStatusCommand command) {
            lastStatusCommand = command;
            if (statusPublishFails) {
                throw new ConfigPublishException("Failed to publish platform-rule-config");
            }
            return statusUpdateResult;
        }

        @Override
        public PlatformMenuDeadlineUpdateResult replaceDeadline(
                edu.whut.eval.application.platform.command.ReplacePlatformDeadlineCommand command) {
            lastDeadlineCommand = command;
            return deadlineUpdateResult;
        }
    }
}
