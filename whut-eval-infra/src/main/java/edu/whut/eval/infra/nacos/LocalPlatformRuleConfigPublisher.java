package edu.whut.eval.infra.nacos;

import edu.whut.eval.application.platform.service.PlatformRuleConfigPublisher;
import edu.whut.eval.domain.config.model.PlatformRuleConfig;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@Profile("local")
public class LocalPlatformRuleConfigPublisher implements PlatformRuleConfigPublisher {

    @Override
    public boolean publish(PlatformRuleConfig config, String reason, OffsetDateTime effectiveAt) {
        return true;
    }
}
