package edu.whut.eval.app.platform;

import edu.whut.eval.application.platform.command.ReplacePlatformDeadlineCommand;
import edu.whut.eval.application.platform.command.UpdatePlatformMenuStatusCommand;
import edu.whut.eval.application.platform.query.PlatformMenuDeadlineUpdateResult;
import edu.whut.eval.application.platform.query.PlatformMenuStatusUpdateResult;
import edu.whut.eval.application.platform.service.ConfigPublishException;
import edu.whut.eval.application.platform.service.PlatformRuleCommandApplicationService;
import edu.whut.eval.application.platform.service.PlatformRuleConfigPublisher;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import edu.whut.eval.infra.nacos.InMemoryTypedConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformRuleCommandApplicationServiceTest {

    private final InMemoryTypedConfigRepository typedConfigRepository = new InMemoryTypedConfigRepository();
    private final StubPlatformRuleConfigPublisher publisher = new StubPlatformRuleConfigPublisher();
    private final PlatformRuleCommandApplicationService service =
            new PlatformRuleCommandApplicationService(typedConfigRepository, publisher);

    @BeforeEach
    void setUpConfig() {
        typedConfigRepository.save(PlatformRuleCommandApplicationService.PLATFORM_RULE_CONFIG, config(
                true,
                false,
                100,
                "2026-09-30T23:59:59+08:00",
                "2026-10-15T23:59:59+08:00"
        ));
    }

    @Test
    void shouldPatchMenuStatusAndPreserveDeadlineFields() {
        PlatformMenuStatusUpdateResult result = service.updateMenuStatus(new UpdatePlatformMenuStatusCommand(
                null,
                true,
                "开放最终提交"
        ));

        assertThat(result.isStudentApplyEnabled()).isTrue();
        assertThat(result.isFinalSubmitEnabled()).isTrue();
        assertThat(result.getSource()).isEqualTo("NACOS");
        assertThat(result.getEffectiveAt()).isNotNull();
        assertThat(publisher.published.isStudentApplyEnabled()).isTrue();
        assertThat(publisher.published.isFinalSubmitEnabled()).isTrue();
        assertThat(publisher.published.getStudentApplyDeadline()).isEqualTo("2026-09-30T23:59:59+08:00");
        assertThat(publisher.reason).isEqualTo("开放最终提交");
        assertThat(typedConfigRepository.find(PlatformRuleCommandApplicationService.PLATFORM_RULE_CONFIG, PlatformRuleConfig.class)
                .orElseThrow().isFinalSubmitEnabled()).isTrue();
    }

    @Test
    void shouldRejectMenuStatusPatchWithoutSwitchField() {
        assertThatThrownBy(() -> service.updateMenuStatus(new UpdatePlatformMenuStatusCommand(
                null,
                null,
                "缺少开关字段"
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("至少提供一个开关字段");
    }

    @Test
    void shouldReplaceOneDeadlineAndPreserveOtherFields() {
        PlatformMenuDeadlineUpdateResult result = service.replaceDeadline(new ReplacePlatformDeadlineCommand(
                "2026-10-01T23:59:59+08:00",
                null,
                "学生申请延期"
        ));

        assertThat(result.getStudentApplyDeadline()).isEqualTo("2026-10-01T23:59:59+08:00");
        assertThat(result.getFinalSubmitDeadline()).isEqualTo("2026-10-15T23:59:59+08:00");
        assertThat(result.getTimezone()).isEqualTo("Asia/Shanghai");
        assertThat(result.getSource()).isEqualTo("NACOS");
        assertThat(publisher.published.isStudentApplyEnabled()).isTrue();
        assertThat(publisher.published.getStudentApplyDeadline()).isEqualTo("2026-10-01T23:59:59+08:00");
        assertThat(publisher.published.getFinalSubmitDeadline()).isEqualTo("2026-10-15T23:59:59+08:00");
    }

    @Test
    void shouldRejectDeadlineWhenFinalSubmitBeforeStudentApply() {
        assertThatThrownBy(() -> service.replaceDeadline(new ReplacePlatformDeadlineCommand(
                "2026-10-20T00:00:00+08:00",
                "2026-10-15T23:59:59+08:00",
                "错误顺序"
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("finalSubmitDeadline 不能早于 studentApplyDeadline");
    }

    @Test
    void shouldRejectInvalidDeadlineFormat() {
        assertThatThrownBy(() -> service.replaceDeadline(new ReplacePlatformDeadlineCommand(
                "2026/10/01 23:59:59",
                null,
                "格式错误"
        )))
                .isInstanceOf(ValidationException.class)
                .hasMessage("studentApplyDeadline 必须为 ISO-8601 时间");
    }

    @Test
    void shouldFailWhenPublishFails() {
        publisher.publishResult = false;

        assertThatThrownBy(() -> service.updateMenuStatus(new UpdatePlatformMenuStatusCommand(
                false,
                null,
                "关闭申请"
        )))
                .isInstanceOf(ConfigPublishException.class)
                .hasMessage("Failed to publish platform-rule-config");
    }

    private PlatformRuleConfig config(boolean studentApplyEnabled,
                                      boolean finalSubmitEnabled,
                                      int maxReviewBatchSize,
                                      String studentApplyDeadline,
                                      String finalSubmitDeadline) {
        PlatformRuleConfig config = new PlatformRuleConfig();
        config.setStudentApplyEnabled(studentApplyEnabled);
        config.setFinalSubmitEnabled(finalSubmitEnabled);
        config.setMaxReviewBatchSize(maxReviewBatchSize);
        config.setStudentApplyDeadline(studentApplyDeadline);
        config.setFinalSubmitDeadline(finalSubmitDeadline);
        return config;
    }

    private static final class StubPlatformRuleConfigPublisher implements PlatformRuleConfigPublisher {

        private PlatformRuleConfig published;
        private String reason;
        private boolean publishResult = true;

        @Override
        public boolean publish(PlatformRuleConfig config, String reason, OffsetDateTime effectiveAt) {
            published = config;
            this.reason = reason;
            return publishResult;
        }
    }
}
