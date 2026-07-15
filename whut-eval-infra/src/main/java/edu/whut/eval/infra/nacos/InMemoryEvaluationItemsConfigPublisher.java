package edu.whut.eval.infra.nacos;

import edu.whut.eval.application.platform.service.EvaluationItemsConfigPublisher;
import edu.whut.eval.domain.config.model.EvaluationItemsConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@Profile("!local")
@ConditionalOnProperty(prefix = "infra.nacos", name = "enabled", havingValue = "false")
public class InMemoryEvaluationItemsConfigPublisher implements EvaluationItemsConfigPublisher {

    @Override
    public boolean publish(EvaluationItemsConfig config, String reason, OffsetDateTime effectiveAt) {
        return true;
    }
}
