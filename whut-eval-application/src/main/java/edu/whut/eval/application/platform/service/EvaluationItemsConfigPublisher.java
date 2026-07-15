package edu.whut.eval.application.platform.service;

import edu.whut.eval.domain.config.model.EvaluationItemsConfig;

import java.time.OffsetDateTime;

public interface EvaluationItemsConfigPublisher {

    boolean publish(EvaluationItemsConfig config, String reason, OffsetDateTime effectiveAt);
}
