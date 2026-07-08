package edu.whut.eval.application.finalrecord.config;

import edu.whut.eval.domain.finalrecord.service.FinalSubmissionWindowPolicy;
import edu.whut.eval.domain.finalrecord.service.NoopFinalSubmissionWindowPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinalRecordApplicationConfiguration {

    @Bean
    public FinalSubmissionWindowPolicy finalSubmissionWindowPolicy() {
        return new NoopFinalSubmissionWindowPolicy();
    }
}
