package edu.whut.eval.app.platform;

import edu.whut.eval.application.platform.query.EvaluationItemCommandResult;
import edu.whut.eval.application.platform.query.EvaluationItemResponse;
import edu.whut.eval.application.platform.service.EvaluationItemCommandApplicationService;
import edu.whut.eval.application.platform.service.ConfigPublishException;
import edu.whut.eval.application.platform.service.PlatformReadApplicationService;
import edu.whut.eval.application.platform.service.PlatformRuleCommandApplicationService;
import edu.whut.eval.common.exception.ConflictException;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlatformReadController.class)
@AutoConfigureMockMvc
@ContextConfiguration(classes = EvaluationItemQueryControllerWebMvcTest.TestApplication.class)
@Import({
        PlatformReadController.class,
        GlobalExceptionHandler.class,
        EvaluationItemQueryControllerWebMvcTest.TestBeans.class
})
class EvaluationItemQueryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubPlatformReadApplicationService platformReadApplicationService;

    @Autowired
    private StubEvaluationItemCommandApplicationService evaluationItemCommandApplicationService;

    @BeforeEach
    void resetStubs() {
        platformReadApplicationService.items = List.of();
        platformReadApplicationService.lastCategoryCode = null;
        evaluationItemCommandApplicationService.reset();
    }

    @Test
    void shouldReturnFlatEvaluationItemList() throws Exception {
        platformReadApplicationService.items = List.of(new EvaluationItemResponse(
                "INTELLECTUAL",
                "智育",
                "INTELLECTUAL_PAPER",
                "论文发表",
                "学术论文发表加分",
                new BigDecimal("6.00"),
                "min(raw, 6)",
                "STUDENT_APPLY",
                true,
                20,
                "intellectual-paper"
        ));

        mockMvc.perform(get("/api/platform/evaluation-items")
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].categoryCode").value("INTELLECTUAL"))
                .andExpect(jsonPath("$.data[0].categoryName").value("智育"))
                .andExpect(jsonPath("$.data[0].itemCode").value("INTELLECTUAL_PAPER"))
                .andExpect(jsonPath("$.data[0].itemName").value("论文发表"))
                .andExpect(jsonPath("$.data[0].description").value("学术论文发表加分"))
                .andExpect(jsonPath("$.data[0].maxPoints").value(6.00))
                .andExpect(jsonPath("$.data[0].maxPointsExpression").value("min(raw, 6)"))
                .andExpect(jsonPath("$.data[0].applyMode").value("STUDENT_APPLY"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].sortOrder").value(20))
                .andExpect(jsonPath("$.data[0].optionsKey").value("intellectual-paper"));
    }

    @Test
    void shouldPassCategoryCodeToServiceAndNotExposeEnabledOnlyMode() throws Exception {
        platformReadApplicationService.items = List.of();

        mockMvc.perform(get("/api/platform/evaluation-items")
                        .param("categoryCode", "INTELLECTUAL")
                        .param("enabledOnly", "false")
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        assertThat(platformReadApplicationService.lastCategoryCode).isEqualTo("INTELLECTUAL");
    }

    @Test
    void shouldCreateEvaluationItemWithManageAuthority() throws Exception {
        evaluationItemCommandApplicationService.createResult = new EvaluationItemCommandResult(
                "INTELLECTUAL",
                "智育",
                "INTELLECTUAL_PATENT",
                "专利授权",
                "发明专利加分",
                new BigDecimal("8.00"),
                "min(raw, 8)",
                "STUDENT_APPLY",
                true,
                "ACTIVE",
                30,
                "intellectual-patent"
        );

        mockMvc.perform(post("/api/platform/evaluation-items")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categoryCode": "INTELLECTUAL",
                                  "itemCode": "INTELLECTUAL_PATENT",
                                  "itemName": "专利授权",
                                  "description": "发明专利加分",
                                  "maxPoints": 8.00,
                                  "maxPointsExpression": "min(raw, 8)",
                                  "applyMode": "STUDENT_APPLY",
                                  "status": "ACTIVE",
                                  "sortOrder": 30,
                                  "optionsKey": "intellectual-patent"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "evaluation.item.manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemCode").value("INTELLECTUAL_PATENT"))
                .andExpect(jsonPath("$.data.categoryName").value("智育"))
                .andExpect(jsonPath("$.data.maxPoints").value(8.00))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(evaluationItemCommandApplicationService.lastCreateCommand.itemCode())
                .isEqualTo("INTELLECTUAL_PATENT");
        assertThat(evaluationItemCommandApplicationService.lastCreateCommand.sortOrder())
                .isEqualTo(30);
    }

    @Test
    void shouldRejectEvaluationItemCreateWithoutManageAuthority() throws Exception {
        mockMvc.perform(post("/api/platform/evaluation-items")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categoryCode": "INTELLECTUAL",
                                  "itemCode": "INTELLECTUAL_PATENT",
                                  "itemName": "专利授权",
                                  "applyMode": "STUDENT_APPLY"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("student")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn409WhenEvaluationItemCreateConflicts() throws Exception {
        evaluationItemCommandApplicationService.createConflict = true;

        mockMvc.perform(post("/api/platform/evaluation-items")
                        .contentType("application/json")
                        .content("""
                                {
                                  "categoryCode": "INTELLECTUAL",
                                  "itemCode": "INTELLECTUAL_PAPER",
                                  "itemName": "论文发表",
                                  "applyMode": "STUDENT_APPLY"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "evaluation.item.manage")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BIZ-4090"))
                .andExpect(jsonPath("$.message").value("itemCode 已存在: INTELLECTUAL_PAPER"));
    }

    @Test
    void shouldPatchEvaluationItemWithManageAuthority() throws Exception {
        evaluationItemCommandApplicationService.patchResult = new EvaluationItemCommandResult(
                "INTELLECTUAL",
                "智育",
                "INTELLECTUAL_PAPER",
                "高水平论文",
                "学术论文发表加分",
                new BigDecimal("10.00"),
                "min(raw, 10)",
                "STUDENT_APPLY",
                false,
                "INACTIVE",
                25,
                "intellectual-paper"
        );

        mockMvc.perform(patch("/api/platform/evaluation-items/INTELLECTUAL_PAPER")
                        .contentType("application/json")
                        .content("""
                                {
                                  "itemName": "高水平论文",
                                  "maxPoints": 10.00,
                                  "status": "INACTIVE",
                                  "sortOrder": 25
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "evaluation.item.manage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemCode").value("INTELLECTUAL_PAPER"))
                .andExpect(jsonPath("$.data.itemName").value("高水平论文"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
        assertThat(evaluationItemCommandApplicationService.lastPatchCommand.itemCode())
                .isEqualTo("INTELLECTUAL_PAPER");
        assertThat(evaluationItemCommandApplicationService.lastPatchCommand.sortOrder())
                .isEqualTo(25);
    }

    @Test
    void shouldReturnExternalErrorWhenEvaluationItemPublishFails() throws Exception {
        evaluationItemCommandApplicationService.patchPublishFails = true;

        mockMvc.perform(patch("/api/platform/evaluation-items/INTELLECTUAL_PAPER")
                        .contentType("application/json")
                        .content("""
                                {
                                  "itemName": "高水平论文"
                                }
                                """)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("admin")
                                .authorities(() -> "evaluation.item.manage")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXT-5033"))
                .andExpect(jsonPath("$.message").value("Failed to publish evaluation-items-config"));
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
        PlatformRuleCommandApplicationService platformRuleCommandApplicationService() {
            return Mockito.mock(PlatformRuleCommandApplicationService.class);
        }

        @Bean
        StubEvaluationItemCommandApplicationService evaluationItemCommandApplicationService() {
            return new StubEvaluationItemCommandApplicationService();
        }
    }

    static class StubPlatformReadApplicationService extends PlatformReadApplicationService {

        private List<EvaluationItemResponse> items = List.of();
        private String lastCategoryCode;

        StubPlatformReadApplicationService() {
            super(null);
        }

        @Override
        public List<EvaluationItemResponse> listEvaluationItems(String categoryCode) {
            this.lastCategoryCode = categoryCode;
            return items;
        }
    }

    static class StubEvaluationItemCommandApplicationService extends EvaluationItemCommandApplicationService {

        private EvaluationItemCommandResult createResult;
        private EvaluationItemCommandResult patchResult;
        private edu.whut.eval.application.platform.command.CreateEvaluationItemCommand lastCreateCommand;
        private edu.whut.eval.application.platform.command.PatchEvaluationItemCommand lastPatchCommand;
        private boolean createConflict;
        private boolean patchPublishFails;

        StubEvaluationItemCommandApplicationService() {
            super(null, null);
        }

        void reset() {
            createResult = null;
            patchResult = null;
            lastCreateCommand = null;
            lastPatchCommand = null;
            createConflict = false;
            patchPublishFails = false;
        }

        @Override
        public EvaluationItemCommandResult create(
                edu.whut.eval.application.platform.command.CreateEvaluationItemCommand command) {
            lastCreateCommand = command;
            if (createConflict) {
                throw new ConflictException("itemCode 已存在: " + command.itemCode());
            }
            return createResult;
        }

        @Override
        public EvaluationItemCommandResult patch(
                edu.whut.eval.application.platform.command.PatchEvaluationItemCommand command) {
            lastPatchCommand = command;
            if (patchPublishFails) {
                throw new ConfigPublishException("Failed to publish evaluation-items-config");
            }
            return patchResult;
        }
    }
}
