package edu.whut.eval.app.platform;

import edu.whut.eval.application.platform.query.EvaluationItemResponse;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
