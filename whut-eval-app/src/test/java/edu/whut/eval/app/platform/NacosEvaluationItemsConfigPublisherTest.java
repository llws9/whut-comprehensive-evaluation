package edu.whut.eval.app.platform;

import com.alibaba.nacos.api.config.ConfigService;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import edu.whut.eval.infra.nacos.ConfigDefinition;
import edu.whut.eval.infra.nacos.NacosEvaluationItemsConfigPublisher;
import edu.whut.eval.infra.nacos.StaticConfigDefinitionRegistry;
import edu.whut.eval.infra.nacos.model.ConfigFormat;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NacosEvaluationItemsConfigPublisherTest {

    @Test
    void shouldPublishEvaluationItemsConfigToRegisteredNacosResource() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.publishConfig(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        NacosEvaluationItemsConfigPublisher publisher = new NacosEvaluationItemsConfigPublisher(
                configService,
                new StaticConfigDefinitionRegistry(List.of(new ConfigDefinition(
                        "evaluation-items-config",
                        new ConfigResource("whut-eval-evaluation-items.yaml", "WHUT_EVAL", 3000, ConfigFormat.YAML),
                        true,
                        true
                )))
        );

        boolean published = publisher.publish(config(), "patch evaluation item INTELLECTUAL_PAPER",
                OffsetDateTime.parse("2026-07-16T00:55:00+08:00"));

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(configService).publishConfig(
                org.mockito.ArgumentMatchers.eq("whut-eval-evaluation-items.yaml"),
                org.mockito.ArgumentMatchers.eq("WHUT_EVAL"),
                contentCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("yaml")
        );
        assertThat(published).isTrue();
        assertThat(contentCaptor.getValue()).contains("evaluation-items:");
        assertThat(contentCaptor.getValue()).contains("itemCode: \"INTELLECTUAL_PAPER\"");
        assertThat(contentCaptor.getValue()).contains("maxPoints: 10.00");
    }

    private EvaluationItemsConfig config() {
        EvaluationItemsConfig.EvaluationItem item = new EvaluationItemsConfig.EvaluationItem();
        item.setCategoryCode("INTELLECTUAL");
        item.setCategoryName("智育");
        item.setItemCode("INTELLECTUAL_PAPER");
        item.setItemName("高水平论文");
        item.setDescription("学术论文发表加分");
        item.setMaxPoints(new BigDecimal("10.00"));
        item.setMaxPointsExpression("min(raw, 10)");
        item.setApplyMode("STUDENT_APPLY");
        item.setEnabled(true);
        item.setSortOrder(20);
        item.setOptionsKey("intellectual-paper");

        EvaluationItemsConfig config = new EvaluationItemsConfig();
        config.setEvaluationItems(Map.of("INTELLECTUAL", List.of(item)));
        return config;
    }
}
