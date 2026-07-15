package edu.whut.eval.app.platform;

import com.alibaba.nacos.api.config.ConfigService;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import edu.whut.eval.infra.nacos.ConfigDefinition;
import edu.whut.eval.infra.nacos.NacosPlatformRuleConfigPublisher;
import edu.whut.eval.infra.nacos.StaticConfigDefinitionRegistry;
import edu.whut.eval.infra.nacos.model.ConfigFormat;
import edu.whut.eval.infra.nacos.model.ConfigResource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NacosPlatformRuleConfigPublisherTest {

    @Test
    void shouldPublishPlatformRuleConfigToRegisteredNacosResource() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.publishConfig(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        NacosPlatformRuleConfigPublisher publisher = new NacosPlatformRuleConfigPublisher(
                configService,
                new StaticConfigDefinitionRegistry(List.of(new ConfigDefinition(
                        "platform-rule-config",
                        new ConfigResource("whut-eval-platform-rules.yaml", "WHUT_EVAL", 3000, ConfigFormat.YAML),
                        true,
                        true
                )))
        );

        boolean published = publisher.publish(config(), "调整开关", OffsetDateTime.parse("2026-07-15T20:00:00+08:00"));

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(configService).publishConfig(
                org.mockito.ArgumentMatchers.eq("whut-eval-platform-rules.yaml"),
                org.mockito.ArgumentMatchers.eq("WHUT_EVAL"),
                contentCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("yaml")
        );
        assertThat(published).isTrue();
        assertThat(contentCaptor.getValue()).contains("studentApplyEnabled: true");
        assertThat(contentCaptor.getValue()).contains("finalSubmitDeadline: \"2026-10-15T23:59:59+08:00\"");
    }

    private PlatformRuleConfig config() {
        PlatformRuleConfig config = new PlatformRuleConfig();
        config.setStudentApplyEnabled(true);
        config.setFinalSubmitEnabled(false);
        config.setMaxReviewBatchSize(100);
        config.setStudentApplyDeadline("2026-09-30T23:59:59+08:00");
        config.setFinalSubmitDeadline("2026-10-15T23:59:59+08:00");
        return config;
    }
}
