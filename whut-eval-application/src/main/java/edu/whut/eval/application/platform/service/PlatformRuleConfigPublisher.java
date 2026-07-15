package edu.whut.eval.application.platform.service;

import edu.whut.eval.domain.config.model.PlatformRuleConfig;

import java.time.OffsetDateTime;

public interface PlatformRuleConfigPublisher {

    boolean publish(PlatformRuleConfig config, String reason, OffsetDateTime effectiveAt);
}
