package edu.whut.eval.app.finalrecord;

import edu.whut.eval.application.auth.service.UserAuthorizationContextAssembler;
import edu.whut.eval.application.finalrecord.repository.FinalRecordQueryRepository;
import edu.whut.eval.application.finalrecord.service.FinalRecordAccessValidator;
import edu.whut.eval.application.finalrecord.service.FinalRecordCommandApplicationService;
import edu.whut.eval.domain.finalrecord.service.FinalSubmissionWindowPolicy;
import edu.whut.eval.domain.finalrecord.repository.FinalRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FinalRecordSpringBeanWiringTest {

    @Test
    void shouldRegisterDefaultFinalSubmissionWindowPolicyBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            assertThat(context.getBean(FinalSubmissionWindowPolicy.class)).isNotNull();
        }
    }

    @Configuration
    @Import(FinalRecordCommandApplicationService.class)
    @ComponentScan(basePackages = "edu.whut.eval.application.finalrecord.config")
    static class TestConfig {

        @Bean
        UserAuthorizationContextAssembler userAuthorizationContextAssembler() {
            return mock(UserAuthorizationContextAssembler.class);
        }

        @Bean
        FinalRecordRepository finalRecordRepository() {
            return mock(FinalRecordRepository.class);
        }

        @Bean
        FinalRecordQueryRepository finalRecordQueryRepository() {
            return mock(FinalRecordQueryRepository.class);
        }

        @Bean
        FinalRecordAccessValidator finalRecordAccessValidator() {
            return mock(FinalRecordAccessValidator.class);
        }
    }
}
