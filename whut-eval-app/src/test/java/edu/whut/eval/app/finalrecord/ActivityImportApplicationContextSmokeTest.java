package edu.whut.eval.app.finalrecord;

import edu.whut.eval.app.WhutComprehensiveEvaluationApplication;
import edu.whut.eval.application.finalrecord.importing.ActivityImportApplicationService;
import edu.whut.eval.application.finalrecord.importing.ActivityImportBatchLock;
import edu.whut.eval.application.finalrecord.importing.ActivityImportParser;
import edu.whut.eval.application.finalrecord.importing.ActivityImportRepository;
import edu.whut.eval.infra.persistence.mapper.ActivityImportMapper;
import edu.whut.eval.interfaces.admin.AdminScoreImportController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = WhutComprehensiveEvaluationApplication.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:activity_import_context_smoke;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "infra.security.jwt.enabled=true",
        "infra.security.jwt.algorithm=HS256",
        "infra.security.jwt.issuer=whut-eval",
        "infra.security.jwt.audience=whut-eval-api",
        "infra.security.jwt.access-token-ttl-seconds=7200",
        "infra.security.jwt.refresh-token-ttl-seconds=604800",
        "infra.security.jwt.clock-skew-seconds=60",
        "infra.security.jwt.secret=test-jwt-secret-should-be-long-enough-1234567890",
        "infra.security.jwt.user-id-claim=uid",
        "infra.security.jwt.user-no-claim=uno",
        "infra.security.jwt.user-name-claim=uname",
        "infra.security.jwt.identity-claim=identity",
        "infra.security.jwt.roles-claim=roles",
        "infra.security.jwt.authorities-claim=authorities",
        "infra.security.jwt.token-type-claim=token_type",
        "infra.security.jwt.access-token-type=access",
        "infra.security.jwt.refresh-token-type=refresh"
})
class ActivityImportApplicationContextSmokeTest {

    @Autowired
    private AdminScoreImportController controller;

    @Autowired
    private ActivityImportApplicationService service;

    @Autowired
    private ActivityImportParser parser;

    @Autowired
    private ActivityImportRepository repository;

    @MockBean
    private ActivityImportBatchLock batchLock;

    @Autowired
    private ActivityImportMapper mapper;

    @Test
    void shouldAssembleActivityImportBeansThroughRealApplicationContext() {
        assertThat(controller).isNotNull();
        assertThat(service).isNotNull();
        assertThat(parser).isNotNull();
        assertThat(repository).isNotNull();
        // H2 smoke tests replace the MySQL named-lock adapter at the test boundary.
        assertThat(batchLock).isNotNull();
        assertThat(mapper).isNotNull();
    }
}
