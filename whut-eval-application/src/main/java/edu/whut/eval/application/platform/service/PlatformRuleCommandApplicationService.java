package edu.whut.eval.application.platform.service;

import edu.whut.eval.application.platform.command.ReplacePlatformDeadlineCommand;
import edu.whut.eval.application.platform.command.UpdatePlatformMenuStatusCommand;
import edu.whut.eval.application.platform.query.PlatformMenuDeadlineUpdateResult;
import edu.whut.eval.application.platform.query.PlatformMenuStatusUpdateResult;
import edu.whut.eval.common.exception.ConfigLoadException;
import edu.whut.eval.common.exception.ValidationException;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import edu.whut.eval.domain.config.repository.TypedConfigRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Service
public class PlatformRuleCommandApplicationService {

    public static final String PLATFORM_RULE_CONFIG = "platform-rule-config";
    private static final String SOURCE_NACOS = "NACOS";
    private static final String TIMEZONE = "Asia/Shanghai";
    private static final ZoneId ZONE_ID = ZoneId.of(TIMEZONE);

    private final TypedConfigRepository typedConfigRepository;
    private final PlatformRuleConfigPublisher platformRuleConfigPublisher;

    public PlatformRuleCommandApplicationService(TypedConfigRepository typedConfigRepository,
                                                 PlatformRuleConfigPublisher platformRuleConfigPublisher) {
        this.typedConfigRepository = typedConfigRepository;
        this.platformRuleConfigPublisher = platformRuleConfigPublisher;
    }

    public PlatformMenuStatusUpdateResult updateMenuStatus(UpdatePlatformMenuStatusCommand command) {
        validateReason(command == null ? null : command.reason());
        if (command.studentApplyEnabled() == null && command.finalSubmitEnabled() == null) {
            throw new ValidationException("至少提供一个开关字段");
        }

        PlatformRuleConfig next = copyOf(requiredConfig());
        if (command.studentApplyEnabled() != null) {
            next.setStudentApplyEnabled(command.studentApplyEnabled());
        }
        if (command.finalSubmitEnabled() != null) {
            next.setFinalSubmitEnabled(command.finalSubmitEnabled());
        }

        OffsetDateTime effectiveAt = publish(next, command.reason());
        return new PlatformMenuStatusUpdateResult(
                next.isStudentApplyEnabled(),
                next.isFinalSubmitEnabled(),
                effectiveAt,
                SOURCE_NACOS
        );
    }

    public PlatformMenuDeadlineUpdateResult replaceDeadline(ReplacePlatformDeadlineCommand command) {
        validateReason(command == null ? null : command.reason());
        if (isBlank(command.studentApplyDeadline()) && isBlank(command.finalSubmitDeadline())) {
            throw new ValidationException("至少提供一个截止时间字段");
        }

        PlatformRuleConfig next = copyOf(requiredConfig());
        if (!isBlank(command.studentApplyDeadline())) {
            next.setStudentApplyDeadline(normalizeDeadline("studentApplyDeadline", command.studentApplyDeadline()));
        }
        if (!isBlank(command.finalSubmitDeadline())) {
            next.setFinalSubmitDeadline(normalizeDeadline("finalSubmitDeadline", command.finalSubmitDeadline()));
        }
        validateDeadlineOrder(next.getStudentApplyDeadline(), next.getFinalSubmitDeadline());

        OffsetDateTime effectiveAt = publish(next, command.reason());
        return new PlatformMenuDeadlineUpdateResult(
                next.getStudentApplyDeadline(),
                next.getFinalSubmitDeadline(),
                TIMEZONE,
                effectiveAt,
                SOURCE_NACOS
        );
    }

    private OffsetDateTime publish(PlatformRuleConfig config, String reason) {
        OffsetDateTime effectiveAt = OffsetDateTime.now(ZONE_ID);
        boolean published = platformRuleConfigPublisher.publish(config, reason.trim(), effectiveAt);
        if (!published) {
            throw new ConfigPublishException("Failed to publish " + PLATFORM_RULE_CONFIG);
        }
        typedConfigRepository.save(PLATFORM_RULE_CONFIG, copyOf(config));
        return effectiveAt;
    }

    private PlatformRuleConfig requiredConfig() {
        return typedConfigRepository.find(PLATFORM_RULE_CONFIG, PlatformRuleConfig.class)
                .orElseThrow(() -> new ConfigLoadException("Required typed config not found: " + PLATFORM_RULE_CONFIG));
    }

    private void validateReason(String reason) {
        if (isBlank(reason)) {
            throw new ValidationException("reason 不能为空");
        }
    }

    private String normalizeDeadline(String fieldName, String value) {
        String trimmed = value.trim();
        try {
            OffsetDateTime.parse(trimmed);
        } catch (DateTimeParseException exception) {
            throw new ValidationException(fieldName + " 必须为 ISO-8601 时间");
        }
        return trimmed;
    }

    private void validateDeadlineOrder(String studentApplyDeadline, String finalSubmitDeadline) {
        if (isBlank(studentApplyDeadline) || isBlank(finalSubmitDeadline)) {
            return;
        }
        OffsetDateTime studentApplyAt = OffsetDateTime.parse(studentApplyDeadline);
        OffsetDateTime finalSubmitAt = OffsetDateTime.parse(finalSubmitDeadline);
        if (finalSubmitAt.isBefore(studentApplyAt)) {
            throw new ValidationException("finalSubmitDeadline 不能早于 studentApplyDeadline");
        }
    }

    private PlatformRuleConfig copyOf(PlatformRuleConfig source) {
        PlatformRuleConfig copy = new PlatformRuleConfig();
        copy.setStudentApplyEnabled(source.isStudentApplyEnabled());
        copy.setFinalSubmitEnabled(source.isFinalSubmitEnabled());
        copy.setMaxReviewBatchSize(source.getMaxReviewBatchSize());
        copy.setStudentApplyDeadline(source.getStudentApplyDeadline());
        copy.setFinalSubmitDeadline(source.getFinalSubmitDeadline());
        return copy;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
