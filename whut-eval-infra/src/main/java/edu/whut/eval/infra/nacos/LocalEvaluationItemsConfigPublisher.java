package edu.whut.eval.infra.nacos;

import edu.whut.eval.application.platform.service.EvaluationItemsConfigPublisher;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@Profile("local")
public class LocalEvaluationItemsConfigPublisher implements EvaluationItemsConfigPublisher {

    @Override
    public boolean publish(EvaluationItemsConfig config, String reason, OffsetDateTime effectiveAt) {
        return true;
    }
}
